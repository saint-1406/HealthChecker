package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
class TrainingTest {
    private fun ex(id:String,muscle:Muscle,equipment:Equipment=Equipment.NONE,kind:ExerciseKind=ExerciseKind.STRENGTH,level:Int=1,met:Double=3.0,measure:ExerciseMeasure=ExerciseMeasure.REPS,category:ExerciseCategory=ExerciseCategory.STRENGTH)=
        Exercise(id,id,muscles=listOf(muscle),equipment=equipment,kind=kind,level=level,met=met,metCode="02056",measure=measure,secondsPerRep=if(measure==ExerciseMeasure.REPS)2.0 else null,category=category)
    private val catalog=Muscle.entries.flatMap{m->listOf(ex("bw_$m",m),ex("bw2_$m",m),ex("db_$m",m,Equipment.DUMBBELLS),ex("hard_$m",m,level=3))}+
        listOf(ex("jog",Muscle.LEGS,kind=ExerciseKind.CARDIO,met=4.8,measure=ExerciseMeasure.MINUTES),ex("tabata",Muscle.LEGS,kind=ExerciseKind.CARDIO,met=11.0,level=3,measure=ExerciseMeasure.MINUTES),
            ex("arm_circles",Muscle.SHOULDERS,kind=ExerciseKind.MOBILITY,met=2.3,measure=ExerciseMeasure.SECONDS,category=ExerciseCategory.WARMUP),ex("hamstring",Muscle.LEGS,kind=ExerciseKind.MOBILITY,met=2.3,measure=ExerciseMeasure.SECONDS,category=ExerciseCategory.STRETCH),
            ex("yoga",Muscle.BACK,kind=ExerciseKind.MOBILITY,met=2.3,measure=ExerciseMeasure.MINUTES))
    private val zone=ZoneId.of("Asia/Baku");private val now=Instant.parse("2026-10-19T08:00:00Z")
    private fun done(exercise:Exercise,date:String,minutes:Double=10.0)=makeWorkout(UUID.randomUUID().toString(),exercise,null,null,null,null,minutes,date,null,now,zone,70.0)

    @Test fun kcalFollowsMetWeightAndTime() { assertEquals(133.0,burnedKcal(3.8,70.0,30.0)!!,1e-9);assertNull(burnedKcal(3.8,null,30.0)) }
    @Test fun setsAreTimedWithRest() {
        val pushup=ex("pushup",Muscle.CHEST)
        // 3 × 12 × 2 s of work plus two 60 s rests = 192 s.
        assertEquals(3.2,estimateMinutes(pushup,3,12,null)!!,1e-9)
        assertNull(estimateMinutes(pushup,3,null,null))
        assertEquals(2.0,estimateMinutes(ex("plank",Muscle.CORE,measure=ExerciseMeasure.SECONDS,kind=ExerciseKind.CARDIO),3,null,20)!!,1e-9)
    }
    @Test fun workoutSnapshotsWeightAndKeepsDayOnEdit() {
        val e=ex("squat",Muscle.LEGS)
        val first=makeWorkout(UUID.randomUUID().toString(),e,3,10,null,null,5.0,"2026-10-18",null,now,zone,80.0)
        assertEquals(20.0,first.kcal!!,1e-9)
        val edited=makeWorkout(first.id,e,3,12,null,null,6.0,"2026-10-19",first,now,zone,70.0)
        assertEquals("2026-10-18",edited.localDate);assertEquals(80.0,edited.bodyWeightKg!!,0.0);assertEquals(24.0,edited.kcal!!,1e-9)
        assertNull(makeWorkout(UUID.randomUUID().toString(),e,3,10,null,null,5.0,"2026-10-19",null,now,zone,null).kcal)
        assertThrows(IllegalArgumentException::class.java){makeWorkout(UUID.randomUUID().toString(),e,0,10,null,null,5.0,"2026-10-19",null,now,zone,70.0)}
        assertThrows(IllegalArgumentException::class.java){makeWorkout(UUID.randomUUID().toString(),e,3,10,null,null,Double.NaN,"2026-10-19",null,now,zone,70.0)}
    }
    @Test fun loggedExercisesMarkTheDayAndSumKcal() {
        val s=UserSnapshot(workouts=listOf(done(catalog.first(),"2026-10-19"),done(catalog.first(),"2026-10-19").copy(kcal=null)),trainingDays=mapOf("2026-10-18" to false))
        val today=s.training("2026-10-19");assertEquals(true,today.trained);assertFalse(today.burned.complete);assertEquals(35.0,today.burned.value,1e-9)
        assertEquals(false,s.training("2026-10-18").trained);assertNull(s.training("2026-10-17").trained)
    }
    @Test fun weeklyAerobicCountsVigorousTwiceAndOnlyCardio() {
        val jog=catalog.single{it.id=="jog"};val tabata=catalog.single{it.id=="tabata"}
        val s=UserSnapshot(workouts=listOf(done(jog,"2026-10-19",30.0),done(tabata,"2026-10-17",20.0),done(jog,"2026-10-12",60.0),done(catalog.first(),"2026-10-19",40.0),done(catalog.first(),"2026-10-15")))
        val w=weeklyActivity(s,"2026-10-19");assertEquals(70.0,w.aerobicMinutes,1e-9);assertEquals(2,w.strengthDays)
    }
    @Test fun recommendationIsStableAndRespectsEquipmentAndLevel() {
        val a=recommend(catalog,UserSnapshot(),"2026-10-19",TrainingPrefs(dumbbells=false,level=1))
        assertEquals(a,recommend(catalog,UserSnapshot(),"2026-10-19",TrainingPrefs(dumbbells=false,level=1)))
        assertTrue(a.items.none{it.exercise.equipment==Equipment.DUMBBELLS||it.exercise.level>1})
        assertEquals(4,a.items.count{it.role==Role.MAIN});assertTrue(a.items.any{it.role==Role.WARMUP}&&a.items.any{it.role==Role.COOLDOWN}&&a.items.any{it.role==Role.CARDIO})
        val withDumbbells=(0..20).flatMap{v->recommend(catalog,UserSnapshot(),"2026-10-19",TrainingPrefs(true,3),v).items}
        assertTrue(withDumbbells.any{it.exercise.equipment==Equipment.DUMBBELLS});assertEquals(6,recommend(catalog,UserSnapshot(),"2026-10-19",TrainingPrefs(true,3)).items.count{it.role==Role.MAIN})
    }
    @Test fun recentlyTrainedMusclesRestAndAllTrainedMeansRecovery() {
        val legs=done(catalog.single{it.id=="bw_LEGS"},"2026-10-18")
        val r=recommend(catalog,UserSnapshot(workouts=listOf(legs)),"2026-10-19",TrainingPrefs())
        assertFalse(Muscle.LEGS in r.focus);assertFalse(r.recovery)
        val all=Muscle.entries.map{m->done(catalog.single{it.id=="bw_$m"},"2026-10-18")}
        val rest=recommend(catalog,UserSnapshot(workouts=all),"2026-10-19",TrainingPrefs())
        assertTrue(rest.recovery);assertTrue(rest.items.none{it.exercise.kind==ExerciseKind.STRENGTH})
        // Two days later the same groups are fresh again.
        assertFalse(recommend(catalog,UserSnapshot(workouts=all),"2026-10-20",TrainingPrefs()).recovery)
    }
    @Test fun cardioOnlyWhileWeeklyTargetIsMissing() {
        val jog=catalog.single{it.id=="jog"}
        val met=UserSnapshot(workouts=listOf(done(jog,"2026-10-19",160.0)))
        assertTrue(recommend(catalog,met,"2026-10-19",TrainingPrefs()).items.none{it.role==Role.CARDIO})
    }
    @Test fun prescriptionGrowsWithLevel() {
        val squat=ex("squat",Muscle.LEGS)
        assertEquals(Prescription(2,10),prescribe(squat,1));assertEquals(Prescription(4,15),prescribe(squat,3))
        assertEquals(10.0,prescribe(catalog.single{it.id=="jog"},1).minutes!!,0.0)
    }
}
