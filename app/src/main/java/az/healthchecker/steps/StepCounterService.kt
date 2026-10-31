package az.healthchecker.steps
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import az.healthchecker.MainActivity
import az.healthchecker.R
import az.healthchecker.data.HealthRepository
import az.healthchecker.data.LanguageStore
import az.healthchecker.domain.*
import kotlinx.coroutines.*
import java.text.NumberFormat
import java.time.LocalDate

/**
 * Counts steps like a pedometer while the user has it switched on. Since Android 9 apps receive sensor events in the
 * background only from a foreground service, hence the quiet notification. The hardware counter batches steps itself,
 * so the phone is woken rarely; counts are written at most every 30 seconds.
 */
class StepCounterService:Service(),SensorEventListener {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO.limitedParallelism(1))
    private lateinit var repository:HealthRepository
    private var sensors:SensorManager?=null
    @Volatile private var latest:Long?=null
    private var lastWrite=0L

    override fun onBind(intent:Intent?)=null
    override fun onCreate() {
        super.onCreate()
        Lang.current=LanguageStore.load(this)
        repository=HealthRepository(this)
        val manager=getSystemService(SensorManager::class.java);val sensor=manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        try {
            if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION,notification(null),ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH) else startForeground(NOTIFICATION,notification(null))
        } catch(e:Exception) {
            // Without the "Physical activity" permission Android 14+ refuses a health service; counting stays off.
            StepTracking.disable(this);stopSelf();return
        }
        if(sensor==null){stopSelf();return}
        sensors=manager
        manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_NORMAL,BATCH_US)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int)=START_STICKY
    override fun onSensorChanged(event:SensorEvent) {
        latest=event.values[0].toLong()
        val now=SystemClock.elapsedRealtime()
        if(now-lastWrite>=WRITE_MS||StepTracking.lastDate(this)!=LocalDate.now().toString()){lastWrite=now;scope.launch{flush()}}
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
    /** Steps since the last write go to today; the first write of a new day also asks for a backup. */
    private suspend fun flush() {
        val counter=latest?:return;val date=LocalDate.now().toString()
        val (delta,state)=stepsSince(StepTracking.counterState(this),counter)
        val newDay=StepTracking.lastDate(this)!=date
        val today=repository.addSensorSteps(date,delta,newDay)
        StepTracking.saveCounter(this,state,date)
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION,notification(today.steps))
    }
    override fun onDestroy() {
        sensors?.unregisterListener(this)
        runBlocking(scope.coroutineContext){runCatching{flush()}}
        scope.cancel()
        super.onDestroy()
    }
    private fun notification(steps:Int?):Notification {
        val manager=getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(NotificationChannel(CHANNEL,tr("Шагомер","Step counter"),NotificationManager.IMPORTANCE_MIN).apply{setShowBadge(false)})
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_steps).setOngoing(true).setShowWhen(false).setContentIntent(open)
            .setContentTitle(tr("Считаем шаги","Counting steps"))
            .setContentText(steps?.let{tr("Сегодня: ","Today: ")+NumberFormat.getIntegerInstance(Lang.locale).format(it)}?:tr("Подсчёт идёт","Counting is on"))
            .build()
    }
    companion object {
        private const val CHANNEL="steps";private const val NOTIFICATION=7
        private const val WRITE_MS=30_000L
        // Let the sensor hub collect steps for up to a minute before waking the phone.
        private const val BATCH_US=60_000_000
    }
}
