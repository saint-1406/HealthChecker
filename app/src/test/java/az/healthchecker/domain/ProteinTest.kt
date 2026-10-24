package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
class ProteinTest {
    private fun food(id:String,protein:Double?,kcal:Double?=100.0,water:Boolean=false)=Food(id,id,per100g=Nutrients(kcal,protein,1.0,1.0),isWater=water)
    private val chicken=food("chicken",31.0,165.0);private val rice=food("rice",2.7,130.0);private val mystery=food("mystery",null)
    private fun eat(f:Food,grams:Double,date:String)=makeEntry("$date-${f.id}-$grams",f,Quantity(grams,Measure.GRAMS),date,null,Instant.parse("${date}T12:00:00Z"),ZoneId.of("Asia/Baku"))

    @Test fun statsAverageLoggedDaysAndCountTheGoal() {
        val s=UserSnapshot(profile=Profile(currentWeight=80.0),goals=listOf(DailyGoals("2026-10-01",protein=100.0)),
            entries=listOf(eat(chicken,400.0,"2026-10-20"),eat(rice,200.0,"2026-10-20"),eat(chicken,200.0,"2026-10-22"),eat(chicken,100.0,"2026-10-22")))
        val st=proteinStats(s,"2026-10-22",7)
        assertEquals(7,st.days.size);assertEquals("2026-10-22",st.days.last().date)
        // 124 + 5,4 g and 93 g on the two logged days; empty days are not averaged in.
        assertEquals((129.4+93.0)/2,st.average!!,1e-9);assertEquals(1,st.goalMet);assertEquals(2,st.goalDays)
        assertEquals(st.average!!/80,st.perKg!!,1e-9)
        assertEquals(listOf("chicken","rice"),st.sources.map{it.foodId});assertEquals(3,st.sources.first().times);assertEquals(217.0,st.sources.first().grams,1e-9)
        assertEquals(129.4*4/(660+260),st.days[4].energyShare!!,1e-9);assertNull(st.days[0].energyShare)
    }
    @Test fun unknownProteinKeepsADayOutOfTheAverage() {
        val s=UserSnapshot(entries=listOf(eat(mystery,100.0,"2026-10-22"),eat(chicken,100.0,"2026-10-22")))
        val st=proteinStats(s,"2026-10-22",1)
        assertFalse(st.days.single().grams.complete);assertNull(st.average);assertNull(st.perKg)
    }
    @Test fun foodsFilterByProteinAndSortBothWays() {
        val foods=listOf(chicken,rice,mystery,food("egg",12.6,143.0),food("lentils",24.6,352.0),food("water",0.0,0.0,water=true))
        assertEquals(listOf("chicken","lentils","egg","rice"),proteinFoods(foods,null,ProteinMatch.AT_LEAST,ProteinSort.HIGH_FIRST).map{it.id})
        assertEquals(listOf("rice","egg","lentils","chicken"),proteinFoods(foods,null,ProteinMatch.AT_LEAST,ProteinSort.LOW_FIRST).map{it.id})
        assertEquals(listOf("chicken","lentils"),proteinFoods(foods,20.0,ProteinMatch.AT_LEAST,ProteinSort.HIGH_FIRST).map{it.id})
        assertEquals(listOf("egg","rice"),proteinFoods(foods,13.0,ProteinMatch.AT_MOST,ProteinSort.HIGH_FIRST).map{it.id})
        // "About 25 g" means within 10 %: 24,6 g matches, 31 g does not; small amounts allow ±1 g.
        assertEquals(listOf("lentils"),proteinFoods(foods,25.0,ProteinMatch.ABOUT,ProteinSort.HIGH_FIRST).map{it.id})
        assertEquals(listOf("rice"),proteinFoods(foods,3.5,ProteinMatch.ABOUT,ProteinSort.HIGH_FIRST).map{it.id})
        // Per 100 kcal chicken (18,8 g) beats lentils (7,0 g) and egg (8,8 g).
        assertEquals(listOf("chicken","egg","lentils","rice"),proteinFoods(foods,null,ProteinMatch.AT_LEAST,ProteinSort.PER_KCAL).map{it.id})
    }
}
