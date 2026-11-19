package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test

class ProgressTest {
    private fun w(id:String,date:String,sets:Int?=3,reps:Int?=null,seconds:Int?=null,load:Double?=null,minutes:Double=5.0,ex:String="db_goblet_squat")=
        WorkoutEntry(id,ex,"Гоблет",ExerciseKind.STRENGTH,listOf(Muscle.LEGS),sets,reps,seconds,load,minutes,5.0,null,null,"${date}T10:00:00Z","UTC",date)

    @Test fun epleyIsLeftOutForLongSets() {
        assertEquals(10.0,epley(10.0,1)!!,1e-9)
        assertEquals(14.0,epley(10.0,12)!!,1e-9)
        assertNull(epley(10.0,13));assertNull(epley(null,8))
    }

    @Test fun recordsNeedSomethingToBeatAndRepsCompareAtTheSameLoad() {
        val a=w("a","2026-11-01",reps=10,load=8.0);val b=w("b","2026-11-08",reps=12,load=8.0)
        assertTrue(newRecords(a,emptyList()).isEmpty())
        assertEquals(setOf(RecordKind.E1RM,RecordKind.REPS,RecordKind.TOTAL_REPS),newRecords(b,listOf(a)))
        // A heavier dumbbell for fewer reps: a load and 1RM record, not a reps one.
        val c=w("c","2026-11-15",reps=8,load=9.0)
        assertEquals(setOf(RecordKind.LOAD,RecordKind.E1RM),newRecords(c,listOf(a,b)))
        // An entry does not compete with itself when edited.
        assertTrue(newRecords(b,listOf(a,b)).containsAll(setOf(RecordKind.REPS)))
        assertEquals(mapOf("b" to setOf(RecordKind.E1RM,RecordKind.REPS,RecordKind.TOTAL_REPS),"c" to setOf(RecordKind.LOAD,RecordKind.E1RM)),recordsAtTheTime(listOf(a,b,c)))
        val best=personalRecords(listOf(a,b,c))
        assertEquals("c",best.getValue(RecordKind.LOAD).id);assertEquals("b",best.getValue(RecordKind.REPS).id)
        assertFalse(RecordKind.SECONDS in best)
    }

    @Test fun nextStepAddsARepThenADumbbell() {
        assertEquals(NextStep(3,11,null,8.0),nextStep(w("a","2026-11-01",reps=10,load=8.0),ExerciseMeasure.REPS))
        assertEquals(NextStep(3,RESTART_REPS,null,9.0),nextStep(w("a","2026-11-01",reps=12,load=8.0),ExerciseMeasure.REPS))
        assertEquals(NextStep(3,RESTART_REPS,null,14.0),nextStep(w("a","2026-11-01",reps=12,load=12.0),ExerciseMeasure.REPS))
        assertEquals(NextStep(3,16,null,null),nextStep(w("a","2026-11-01",reps=15),ExerciseMeasure.REPS))
        assertEquals(NextStep(4,20,null,null),nextStep(w("a","2026-11-01",reps=20),ExerciseMeasure.REPS))
        assertNull(nextStep(w("a","2026-11-01",sets=5,reps=25),ExerciseMeasure.REPS))
        assertEquals(NextStep(2,null,35,null),nextStep(w("a","2026-11-01",sets=2,seconds=30),ExerciseMeasure.SECONDS))
        assertNull(nextStep(w("a","2026-11-01",sets=null,minutes=20.0),ExerciseMeasure.MINUTES))
    }

    @Test fun historyLastTimeAndTheChart() {
        val a=w("a","2026-11-01",reps=10,load=8.0);val b=w("b","2026-11-08",reps=12,load=8.0);val b2=w("b2","2026-11-08",reps=6,load=8.0)
        val other=w("x","2026-11-09",reps=5,ex="pushup")
        val s=UserSnapshot(workouts=listOf(b2,other,b,a).map{if(it.id=="b2")it.copy(instant="2026-11-08T11:00:00Z") else it})
        val h=s.exerciseHistory("db_goblet_squat")
        assertEquals(listOf("a","b","b2"),h.map{it.id})
        assertEquals("b2",lastPerformance(h,"2026-11-08")!!.id)
        assertEquals("b",lastPerformance(h,"2026-11-08",exceptId="b2")!!.id)
        assertEquals("a",lastPerformance(h,"2026-11-05")!!.id)
        assertNull(lastPerformance(h,"2026-10-01"))
        assertEquals(RecordKind.E1RM,mainMetric(h,ExerciseMeasure.REPS))
        // One point a day: the day's best set.
        assertEquals(listOf(8*(1+10/30.0),8*(1+12/30.0)),progressSeries(h,RecordKind.E1RM).map{it.value})
        val list=strengthProgress(s){if(it=="pushup")ExerciseMeasure.REPS else ExerciseMeasure.REPS}
        assertEquals(listOf("pushup","db_goblet_squat"),list.map{it.exerciseId})
        assertEquals(2,list[1].sessions);assertEquals(5,list[1].changePercent)
        assertNull(list[0].changePercent)
    }
}
