package az.healthchecker.watch
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import az.healthchecker.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/** Whether Health Connect can be used on this phone, and whether the user let the app read from it. */
enum class WatchStatus { UNSUPPORTED, INSTALL, UPDATE, NOT_CONNECTED, PARTIAL, CONNECTED }

/**
 * Reads steps, sleep, workouts and heart rate that a watch's app (Mi Fitness for Xiaomi watches) writes to Health
 * Connect. Read-only, on demand while the app is open: no background reading, nothing is written back.
 */
object HealthConnectSource {
    const val PROVIDER="com.google.android.apps.healthdata"
    val permissions:Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),HealthPermission.getReadPermission(HeartRateRecord::class))
    /** Days of history shown; Health Connect itself lets an app read 30 days back from the first permission. */
    const val DAYS=7

    fun sdkStatus(context:Context):Int = HealthConnectClient.getSdkStatus(context,PROVIDER)
    suspend fun status(context:Context):WatchStatus = when(sdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> if(installed(context))WatchStatus.UPDATE else WatchStatus.INSTALL
        HealthConnectClient.SDK_AVAILABLE -> granted(context).let{g->when{g.containsAll(permissions)->WatchStatus.CONNECTED;g.any{it in permissions}->WatchStatus.PARTIAL;else->WatchStatus.NOT_CONNECTED}}
        else -> WatchStatus.UNSUPPORTED
    }
    private fun installed(context:Context):Boolean = runCatching{context.packageManager.getPackageInfo(PROVIDER,0);true}.getOrDefault(false)
    suspend fun granted(context:Context):Set<String> = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    /** Play Store page of Health Connect (Android 9–13 install it as an app; 14+ have it built in). */
    fun installIntent():Intent = Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")).setPackage("com.android.vending").putExtra("overlay",true).putExtra("callerId","az.healthchecker.app")
    /** Health Connect's own screen: which apps write and read, and the permissions given to this app. */
    fun settingsIntent():Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    suspend fun read(context:Context,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()):WatchData {
        val client=HealthConnectClient.getOrCreate(context);val granted=granted(context)
        val from=today.minusDays((DAYS-1).toLong());val start=from.atStartOfDay(zone).toInstant();val now=Instant.now()
        val labels=Labels(context.packageManager)
        var data=WatchData()
        if(HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            val days=client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL),
                TimeRangeFilter.between(from.atStartOfDay(),today.plusDays(1).atStartOfDay()),Period.ofDays(1)))
            val totals=days.mapNotNull{d->d.result[StepsRecord.COUNT_TOTAL]?.takeIf{it>0}?.let{d.startTime.toLocalDate().toString() to it}}.toMap()
            val sources=days.flatMap{d->d.result.dataOrigins.map{it.packageName}}.toMutableSet()
            // Health Connect's total counts only apps on its priority list for steps. An app missing from it (Mi Fitness
            // before it is ordered there) leaves the total empty, so such days fall back to the raw records — the
            // largest single source of the day, never the sum of sources.
            val raw=if(totals.size<DAYS)readSteps(client,start,now).also{r->sources+=r.map{it.metadata.dataOrigin.packageName}} else emptyList()
            val bySource=stepsBySource(raw.map{RawSteps(it.startTime.atZone(zone).toLocalDate().toString(),it.metadata.dataOrigin.packageName,it.count)})
            data=data.copy(steps=(bySource+totals).mapValues{it.value.coerceIn(0,MAX_STEPS_PER_DAY.toLong()).toInt()},stepSources=sources.map(labels).toSet())
        }
        if(HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            // A night that started the evening before the first day still belongs to that day.
            val nights=client.readRecords(ReadRecordsRequest(SleepSessionRecord::class,TimeRangeFilter.between(start.minusSeconds(18*3600),now))).records
            data=data.copy(sleep=nights.map{n->
                val awake=n.stages.filter{it.stage in AWAKE_STAGES}.map{it.startTime to it.endTime}
                WatchSleep(n.startTime,n.endTime,asleepMinutes(n.startTime,n.endTime,awake),labels(n.metadata.dataOrigin.packageName))
            }.filter{it.date(zone)>=from.toString()}.sortedByDescending{it.end})
        }
        val heart=HealthPermission.getReadPermission(HeartRateRecord::class) in granted
        if(HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
            val sessions=client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,TimeRangeFilter.between(start,now))).records
            data=data.copy(workouts=sessions.map{s->
                val avg=if(heart)runCatching{client.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG),TimeRangeFilter.between(s.startTime,s.endTime)))[HeartRateRecord.BPM_AVG]?.toInt()}.getOrNull() else null
                WatchWorkout(s.startTime,s.endTime,activity(s.exerciseType),s.title?.takeIf{it.isNotBlank()},avg,labels(s.metadata.dataOrigin.packageName))
            }.sortedByDescending{it.start})
        }
        if(heart) {
            val r=client.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_MIN,HeartRateRecord.BPM_AVG,HeartRateRecord.BPM_MAX),TimeRangeFilter.between(today.atStartOfDay(zone).toInstant(),now)))
            val min=r[HeartRateRecord.BPM_MIN];val avg=r[HeartRateRecord.BPM_AVG];val max=r[HeartRateRecord.BPM_MAX]
            if(min!=null&&avg!=null&&max!=null)data=data.copy(heartToday=HeartRange(min.toInt(),avg.toInt(),max.toInt()),heartSources=r.dataOrigins.map{labels(it.packageName)}.toSet())
        }
        return data
    }

    private suspend fun readSteps(client:HealthConnectClient,start:Instant,end:Instant):List<StepsRecord> {
        val out=ArrayList<StepsRecord>();var token:String?=null
        repeat(30){ // 30 pages × 1000 records: a week of minute-by-minute records with room to spare
            val page=client.readRecords(ReadRecordsRequest(StepsRecord::class,TimeRangeFilter.between(start,end),pageSize=1000,pageToken=token))
            out+=page.records;token=page.pageToken;if(token==null)return out
        }
        return out
    }
    private val AWAKE_STAGES=setOf(SleepSessionRecord.STAGE_TYPE_AWAKE,SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)
    private fun activity(type:Int):WatchActivity = when(type) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> WatchActivity.WALK
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> WatchActivity.RUN
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> WatchActivity.BIKE
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> WatchActivity.SWIM
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> WatchActivity.STRENGTH
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> WatchActivity.YOGA
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> WatchActivity.PILATES
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> WatchActivity.STRETCH
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> WatchActivity.HIKE
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> WatchActivity.ELLIPTICAL
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING,ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> WatchActivity.ROWING
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> WatchActivity.HIIT
        else -> WatchActivity.OTHER
    }
    /** The installed app's name for a package, cached per read. */
    private class Labels(private val pm:PackageManager):(String)->String {
        private val cache=HashMap<String,String>()
        override fun invoke(p:String):String = cache.getOrPut(p){sourceName(p,runCatching{pm.getApplicationLabel(pm.getApplicationInfo(p,0)).toString()}.getOrNull())}
    }
}
