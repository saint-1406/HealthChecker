package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ExpenditureTest {
    private val end="2026-11-18"
    private fun day(back:Int)=LocalDate.parse(end).minusDays(back.toLong()).toString()
    private fun meal(date:String,kcal:Double)=DiaryEntry(java.util.UUID.randomUUID().toString(),"f","f",Quantity(100.0,Measure.GRAMS),100.0,Nutrients(kcal,0.0,0.0,0.0),0.0,"${date}T12:00:00Z","UTC",date)
    private fun weigh(date:String,kg:Double)=WeightEntry(date,kg,"${date}T07:00:00Z","UTC")
    private val profile=Profile("male",35,180.0,90.0,85.0,1.375)

    /** 2 000 kcal a day while losing 0,5 kg a week means spending ≈ 2 000 + 0,5 × 7 700 / 7 = 2 550 kcal. */
    private fun steadyLoss(days:Int=28,weighEvery:Int=1)=UserSnapshot(profile=profile,
        entries=(1..days).map{meal(day(it),2000.0)},
        // Weighing started long before the window, so the trend is settled; ±0,6 kg of daily noise.
        weights=(90 downTo 0 step weighEvery).map{weigh(day(it),85.0+it*0.5/7+if(it%2==0)0.6 else -0.6)})

    @Test fun lossWhileEatingTwoThousandMeansHigherExpenditure() {
        val e=expenditure(steadyLoss(),end)
        assertEquals(ExpenditureStatus.OK,e.status)
        assertEquals(2550.0,e.observed!!,60.0)
        assertEquals(-0.5,e.trendPerWeek!!,0.08)
        assertEquals(1.0,e.confidence,0.0)
        assertEquals(e.observed!!,e.estimate!!,0.001)
        assertEquals(27,e.loggedDays) // today is still going on
    }

    @Test fun flatWeightMeansMaintenanceEqualsIntake() {
        val s=UserSnapshot(profile=profile,entries=(1..28).map{meal(day(it),2300.0)},weights=(60 downTo 0).map{weigh(day(it),80.0)})
        assertEquals(2300.0,expenditure(s,end).observed!!,1.0)
    }

    @Test fun fewDataLeansOnTheFormula() {
        val s=steadyLoss().let{it.copy(entries=it.entries.filter{e->e.localDate>=day(9)},weights=it.weights.filter{w->w.date>=day(12)})}
        val e=expenditure(s,end)
        assertEquals(ExpenditureStatus.OK,e.status)
        assertTrue(e.confidence in 0.1..0.5)
        val formula=e.formula!!
        assertEquals(e.observed!!*e.confidence+formula*(1-e.confidence),e.estimate!!,0.001)
    }

    @Test fun halfLoggedDaysAndMissingWeighInsAreReported() {
        val few=UserSnapshot(profile=profile,entries=(1..20).map{meal(day(it),if(it%2==0)500.0 else 2000.0)},weights=listOf(weigh(day(3),85.0)))
        val e=expenditure(few,end)
        assertEquals(10,e.loggedDays) // the 500 kcal days are taken for half-logged ones
        assertEquals(ExpenditureStatus.NEED_WEIGHT,e.status)
        assertNull(e.observed);assertEquals(e.formula!!,e.estimate!!,0.0)
        assertEquals(ExpenditureStatus.NEED_FOOD,expenditure(UserSnapshot(profile=profile),end).status)
    }

    @Test fun completedDaysWinWhenThereAreEnough() {
        val s=steadyLoss().let{it.copy(completedDays=(1..10).map{d->day(d)}.toSet())}
        val e=expenditure(s,end)
        assertTrue(e.usedCompletedDays);assertEquals(10,e.loggedDays)
    }

    @Test fun targetFollowsTheGoalWithinSafeLimits() {
        val s=steadyLoss()
        val e=expenditure(s,end)
        assertEquals(2550.0,adaptiveTarget(e,s)!!,70.0) // no program: maintenance
        val cut=s.copy(setup=ProgramSetup("male",35,180.0,85.0,80.0,emptySet(),emptySet(),FitnessGoal.CUT,1.375,2,3,30,"2026-10-01"))
        assertEquals(e.estimate!!*0.8,adaptiveTarget(e,cut)!!,10.0)
        assertTrue(shouldSuggest(e,2000.0,adaptiveTarget(e,s)))
        assertFalse(shouldSuggest(e,2560.0,2550.0))
    }

    @Test fun trendSmoothsAndFillsGaps() {
        val t=weightTrend(listOf(weigh("2026-11-01",80.0),weigh("2026-11-11",70.0)),LocalDate.parse("2026-11-11"))
        assertEquals(11,t.size)
        assertTrue(t.values.zipWithNext().all{(a,b)->b<=a})
        assertTrue(t[LocalDate.parse("2026-11-11")]!! > 70.0)
    }
}
