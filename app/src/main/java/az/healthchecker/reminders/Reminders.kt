package az.healthchecker.reminders
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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

    /**
     * Whether Android lets the app run its alarms freely. With battery optimisation on, Doze delays reminders, and
     * MIUI/HyperOS on Xiaomi phones may drop them altogether ("Battery saver" for the app).
     */
    fun batteryUnrestricted(c:Context):Boolean = c.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(c.packageName)?:true
    /** Xiaomi, Redmi and POCO phones: MIUI/HyperOS add their own autostart switch on top of Android's battery setting. */
    val xiaomi:Boolean get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi","redmi","poco")
    /** Android's "Allow the app to run in the background?" dialog; the app list in battery settings when it is missing. */
    fun allowBackground(c:Context) = open(c,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+c.packageName)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),appSettings(c))
    /** MIUI's autostart list (Security app); the app's own settings page on other versions. */
    fun openAutostart(c:Context) = open(c,
        Intent().setComponent(ComponentName("com.miui.securitycenter","com.miui.permcenter.autostart.AutoStartManagementActivity")),appSettings(c))
    private fun appSettings(c:Context)=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+c.packageName))
    /** The first screen this phone has; false when none of them opens. */
    private fun open(c:Context,vararg intents:Intent):Boolean = intents.any{i->
        try{c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true}catch(_:ActivityNotFoundException){false}catch(_:SecurityException){false}}

    /** After a settings or medicine change, a reboot or an update: plan the next alarm from now, without firing past ones. */
    fun reschedule(c:Context) {
        val app=c.applicationContext
        prefs(app).edit{putString(HANDLED,LocalDateTime.now().toString())}
        scope.launch{runCatching{schedule(app,HealthRepository(app).exportSnapshot().medications)}}
    }

    private fun schedule(c:Context,meds:List<Medication>) {
        val am=c.getSystemService(AlarmManager::class.java)?:return
        fun pi(code:Int)=PendingIntent.getBroadcast(c,code,Intent(c,ReminderReceiver::class.java).setAction(ACTION_FIRE),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val next=settings(c).takeIf{it.anyOn}?.nextSlot(meds,LocalDateTime.now())
        if(next==null){am.cancel(pi(0));am.cancel(pi(1));return}
        val at=next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Two inexact alarms, no exact-alarm permission: a 10-minute window while the phone is in use, and one that
        // also runs in Doze (Android may give it up to an hour). Whichever comes first shows the reminder; the other
        // then finds nothing new.
        am.setWindow(AlarmManager.RTC_WAKEUP,at,10*60_000L,pi(1))
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi(0))
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
