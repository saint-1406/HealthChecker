package az.healthchecker.reminders
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import az.healthchecker.MainActivity
import az.healthchecker.R
import az.healthchecker.data.HealthRepository
import az.healthchecker.data.LanguageStore
import az.healthchecker.data.SettingsRepository
import az.healthchecker.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Local reminders: medicine doses at their card's times, water through the day, the morning weigh-in, training days and
 * an evening "log your food". One inexact alarm at a time points at the next reminder (no exact-alarm permission is
 * needed; Android may deliver it a few minutes late). Nothing leaves the phone.
 */
object Reminders {
    private const val PREFS="reminders";private const val SETTINGS="settings";private const val HANDLED="handled"
    const val CHANNEL_MEDS="reminders-meds";const val CHANNEL_OTHER="reminders-other"
    private val json=Json{ignoreUnknownKeys=true}
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private fun prefs(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)

    fun settings(c:Context):ReminderSettings = prefs(c).getString(SETTINGS,null)?.let{runCatching{json.decodeFromString<ReminderSettings>(it)}.getOrNull()}?:ReminderSettings()
    fun save(c:Context,s:ReminderSettings){validateReminders(s);prefs(c).edit{putString(SETTINGS,json.encodeToString(ReminderSettings.serializer(),s))};reschedule(c)}
    fun notificationsAllowed(c:Context):Boolean = Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED

    /** After a settings or medicine change, a reboot or an update: plan the next alarm from now, without firing past ones. */
    fun reschedule(c:Context) {
        val app=c.applicationContext
        prefs(app).edit{putString(HANDLED,LocalDateTime.now().toString())}
        scope.launch{runCatching{schedule(app,HealthRepository(app).exportSnapshot().medications)}}
    }

    private fun schedule(c:Context,meds:List<Medication>) {
        val am=c.getSystemService(AlarmManager::class.java)?:return
        val pi=PendingIntent.getBroadcast(c,0,Intent(c,ReminderReceiver::class.java).setAction(ACTION_FIRE),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val next=settings(c).takeIf{it.anyOn}?.nextSlot(meds,LocalDateTime.now())
        if(next==null){am.cancel(pi);return}
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),pi)
    }

    /** The alarm went off: show what is due and still needed, then point the alarm at the next one. */
    internal suspend fun fire(c:Context) {
        val now=LocalDateTime.now()
        val from=prefs(c).getString(HANDLED,null)?.let{runCatching{LocalDateTime.parse(it)}.getOrNull()}?:now.minusMinutes(STALE_MINUTES)
        prefs(c).edit{putString(HANDLED,now.toString())}
        val snapshot=HealthRepository(c).exportSnapshot()
        val s=settings(c)
        if(notificationsAllowed(c))s.dueSlots(snapshot.medications,from,now).filter{it.stillNeeded(snapshot)}.forEach{notify(c,it,snapshot)}
        schedule(c,snapshot.medications)
    }

    private fun channels(c:Context) {
        val nm=c.getSystemService(NotificationManager::class.java)?:return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_MEDS,tr("Лекарства и витамины","Medicines and vitamins"),NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_OTHER,tr("Вода, вес, тренировки","Water, weight, training"),NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun notificationId(slot:ReminderSlot):Int = when(slot.kind){ReminderKind.MEDICATION->1000+((slot.medicationId+slot.doseIndex).hashCode() and 0xFFFF);else->100+slot.kind.ordinal}

    private suspend fun notify(c:Context,slot:ReminderSlot,s:UserSnapshot) {
        Lang.current=LanguageStore.load(c);channels(c)
        val open=PendingIntent.getActivity(c,1,Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE)
        val id=notificationId(slot)
        val b=NotificationCompat.Builder(c,if(slot.kind==ReminderKind.MEDICATION)CHANNEL_MEDS else CHANNEL_OTHER).setSmallIcon(R.drawable.ic_reminder).setContentIntent(open).setAutoCancel(true)
        fun action(kind:String,label:String):NotificationCompat.Action {
            val i=Intent(c,ReminderActionReceiver::class.java).setAction(kind).putExtra(EXTRA_ID,id).putExtra(EXTRA_MED,slot.medicationId).putExtra(EXTRA_DATE,slot.at.toLocalDate().toString())
            return NotificationCompat.Action(0,label,PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        when(slot.kind) {
            ReminderKind.MEDICATION->{val m=s.medications.find{it.id==slot.medicationId}?:return
                b.setContentTitle(tr("Пора принять: ","Time to take: ")+m.name).setContentText(listOfNotNull(doseText(m),timingText(m.timing)).joinToString(" · ")).addAction(action(ACTION_TAKEN,tr("Принял","Taken")))}
            ReminderKind.WATER->{val ml=SettingsRepository(c).settings.first().waterMl
                b.setContentTitle(tr("Выпейте воды","Have some water")).setContentText(tr("Стакан ","A glass of ")+"$ml "+tr("мл","ml")).addAction(action(ACTION_WATER,"+$ml "+tr("мл","ml")))}
            ReminderKind.WEIGH->b.setContentTitle(tr("Утреннее взвешивание","Morning weigh-in")).setContentText(tr("Натощак, после туалета — так тренд точнее","Before breakfast — the trend is more accurate"))
            ReminderKind.TRAINING->b.setContentTitle(tr("Сегодня тренировка","Training today")).setContentText(tr("Подборка на сегодня — во вкладке «Тренировки»","Today's workout is on the Training tab"))
            ReminderKind.LOG->b.setContentTitle(tr("Запишите, что ели","Log what you ate")).setContentText(tr("И отметьте «День заполнен» — так расход считается точнее","And mark “Day complete” — the expenditure gets more accurate"))
        }
        c.getSystemService(NotificationManager::class.java)?.notify(id,b.build())
    }
    private fun doseText(m:Medication):String = (if(m.dose%1.0==0.0)m.dose.toLong().toString() else m.dose.toString().replace('.',if(Lang.en)'.' else ','))+" "+formShort(m.form)
    private fun formShort(f:MedForm):String = when(f){MedForm.TABLET->tr("табл.","tab.");MedForm.CAPSULE->tr("капс.","caps.");MedForm.DROPS->tr("кап.","drops");MedForm.LIQUID,MedForm.INJECTION->tr("мл","ml");MedForm.POWDER->tr("пак.","sach.");MedForm.SPRAY->tr("впрыск.","sprays");MedForm.GUMMY->tr("жев.","gum.");MedForm.OTHER->tr("шт.","pcs")}
    private fun timingText(t:FoodTiming):String? = when(t){FoodTiming.BEFORE->tr("до еды","before food");FoodTiming.WITH->tr("во время еды","with food");FoodTiming.AFTER->tr("после еды","after food");FoodTiming.EMPTY_STOMACH->tr("натощак","on an empty stomach");FoodTiming.ANY->null}

    /** "Taken" and "+250 ml" from the notification write to the diary like the buttons in the app. */
    internal suspend fun act(c:Context,action:String,medId:String?,date:String) {
        val repo=HealthRepository(c)
        when(action) {
            ACTION_TAKEN->{val m=repo.exportSnapshot().medications.find{it.id==medId}?:return;repo.upsertIntake(makeIntake(m,date,Instant.now(),ZoneId.systemDefault()))}
            ACTION_WATER->{val ml=SettingsRepository(c).settings.first().waterMl
                repo.upsertEntry(makeEntry(UUID.randomUUID().toString(),WATER_FOOD,Quantity(ml.toDouble(),Measure.ML),LocalDate.now().toString(),null,Instant.now(),ZoneId.systemDefault()))}
        }
    }
    const val ACTION_FIRE="az.healthchecker.REMINDER";const val ACTION_TAKEN="az.healthchecker.REMINDER_TAKEN";const val ACTION_WATER="az.healthchecker.REMINDER_WATER"
    const val EXTRA_ID="id";const val EXTRA_MED="med";const val EXTRA_DATE="date"
}

/** The alarm, and the moments when alarms are lost or move: reboot, app update, clock or time-zone change. */
class ReminderReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=Reminders.ACTION_FIRE){Reminders.reschedule(context);return}
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch{try{Reminders.fire(context.applicationContext)}catch(_:Exception){}finally{pending.finish()}}
    }
}

class ReminderActionReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch{
            try{
                Reminders.act(context.applicationContext,intent.action?:"",intent.getStringExtra(Reminders.EXTRA_MED),intent.getStringExtra(Reminders.EXTRA_DATE)?:LocalDate.now().toString())
                context.getSystemService(NotificationManager::class.java)?.cancel(intent.getIntExtra(Reminders.EXTRA_ID,0))
            }catch(_:Exception){}finally{pending.finish()}
        }
    }
}
