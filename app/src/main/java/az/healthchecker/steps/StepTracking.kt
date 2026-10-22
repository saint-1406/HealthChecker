package az.healthchecker.steps
import androidx.core.content.edit
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import az.healthchecker.domain.CounterState

/**
 * Automatic step counting settings and the last reading of the phone's step counter. Plain preferences: the boot
 * receiver and the service read them synchronously.
 */
object StepTracking {
    private const val PREFS="steps";private const val ENABLED="enabled";private const val COUNTER="counter";private const val DATE="date"
    private fun prefs(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun sensorAvailable(context:Context):Boolean = context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!=null
    /** Android 10+ asks for "Physical activity"; older versions read the step counter without a permission. */
    fun permissionGranted(context:Context):Boolean = Build.VERSION.SDK_INT<29 || ContextCompat.checkSelfPermission(context,Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED
    fun enabled(context:Context):Boolean = prefs(context).getBoolean(ENABLED,false)
    fun counterState(context:Context):CounterState = prefs(context).getLong(COUNTER,-1).let{CounterState(it.takeIf{v->v>=0})}
    fun lastDate(context:Context):String? = prefs(context).getString(DATE,null)
    fun saveCounter(context:Context,state:CounterState,date:String) { prefs(context).edit{putLong(COUNTER,state.counter?:-1);putString(DATE,date)} }
    /** Switching on starts from the next reading: steps made while counting was off are unknown. */
    fun setEnabled(context:Context,value:Boolean) {
        prefs(context).edit{putBoolean(ENABLED,value);remove(COUNTER)}
        val intent=Intent(context,StepCounterService::class.java)
        if(value)ContextCompat.startForegroundService(context,intent) else context.stopService(intent)
    }
    /** Turns counting off without touching the service, e.g. when Android refused to start it. */
    fun disable(context:Context){prefs(context).edit{putBoolean(ENABLED,false)}}
    /** After a reboot or an app update the service is started again if the user left counting on. */
    fun resume(context:Context) {
        if(enabled(context)&&permissionGranted(context)&&sensorAvailable(context))runCatching{ContextCompat.startForegroundService(context,Intent(context,StepCounterService::class.java))}
    }
}
