package az.healthchecker.domain
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class FiberLanguageStepsMedicationTest {
    @After fun russianAgain(){Lang.current=AppLanguage.RU}
    private fun entry(foodId:String,grams:Double,n:Nutrients)=DiaryEntry(UUID.randomUUID().toString(),foodId,"Еда",Quantity(grams,Measure.GRAMS),grams,n,instant="2026-10-20T08:00:00Z",zoneId="Asia/Baku",localDate="2026-10-20")

    @Test fun fiberScalesWithTheAmountAndStaysUnknownWhenUnknown() {
        val oats=Food("usda:1","Овсянка",per100g=Nutrients(379.0,13.2,6.5,67.7,10.1))
        assertEquals(5.05,calculate(oats,Quantity(50.0,Measure.GRAMS)).nutrients.fiber!!,1e-9)
        assertNull(calculate(oats.copy(per100g=oats.per100g.copy(fiber=null)),Quantity(50.0,Measure.GRAMS)).nutrients.fiber)
    }
    @Test fun dayFiberTotalIsMarkedIncompleteWhenAnEntryLacksIt() {
        val s=summarize(listOf(entry("a",100.0,Nutrients(100.0,fiber=3.0)),entry("b",100.0,Nutrients(50.0,fiber=1.5))))
        assertEquals(NutrientTotal(4.5,true),s.fiber)
        assertFalse(summarize(listOf(entry("a",100.0,Nutrients(100.0,fiber=3.0)),entry("b",100.0,Nutrients(50.0)))).fiber.complete)
    }
    @Test fun oldEntriesGetFiberFromTheirCatalogFoodOnly() {
        val catalog=Food("usda:1","Овсянка",per100g=Nutrients(379.0,fiber=10.0))
        val old=entry("usda:1",40.0,Nutrients(151.6))
        assertEquals(4.0,old.withFiberFrom(catalog).nutrients.fiber!!,1e-9)
        assertEquals(151.6,old.withFiberFrom(catalog).nutrients.kcal!!,0.0)
        assertSame(old,old.withFiberFrom(null))
        assertSame(old,old.withFiberFrom(catalog.copy(id="usda:2")))
        val custom=entry("custom:1",40.0,Nutrients(100.0))
        assertSame(custom,custom.withFiberFrom(Food("custom:1","Своё",per100g=Nutrients(250.0,fiber=5.0))))
        val known=entry("usda:1",40.0,Nutrients(151.6,fiber=1.0))
        assertSame(known,known.withFiberFrom(catalog))
    }
    @Test fun dishesCarryFiberWhenEveryIngredientKnowsIt() {
        val a=Food("usda:a","А",per100g=Nutrients(100.0,1.0,1.0,10.0,2.0));val b=Food("usda:b","Б",per100g=Nutrients(50.0,1.0,1.0,5.0,1.0))
        val dish=buildDish("custom:dish:1","Суп",Recipe(listOf(ingredientOf(a,Quantity(200.0,Measure.GRAMS)),ingredientOf(b,Quantity(200.0,Measure.GRAMS)))))
        assertEquals(1.5,dish.per100g.fiber!!,1e-9)
        val unknown=buildDish("custom:dish:2","Суп",Recipe(listOf(ingredientOf(a,Quantity(200.0,Measure.GRAMS)),ingredientOf(b.copy(per100g=b.per100g.copy(fiber=null)),Quantity(200.0,Measure.GRAMS)))))
        assertNull(unknown.per100g.fiber)
    }
    @Test fun namesAndTextsFollowTheLanguage() {
        val f=Food("usda:1","Яйцо варёное",per100g=Nutrients(155.0),nameEn="Egg, hard-boiled",portions=listOf(Portion("Яйцо",50.0,"Egg")))
        assertEquals("Яйцо варёное",f.displayName);assertEquals("Яйцо",f.portions.single().displayName);assertEquals("5 записей",count(5,"запись","записи","записей","entry","entries"))
        assertEquals("22 записи",count(22,"запись","записи","записей","entry","entries"));assertEquals("11 записей",count(11,"запись","записи","записей","entry","entries"))
        Lang.current=AppLanguage.EN
        assertEquals("Egg, hard-boiled",f.displayName);assertEquals("Egg",f.portions.single().displayName);assertEquals("1 entry",count(1,"запись","записи","записей","entry","entries"));assertEquals("5 entries",count(5,"запись","записи","записей","entry","entries"))
        assertEquals("Check the amount",runCatching{calculate(f,Quantity(-1.0,Measure.GRAMS))}.exceptionOrNull()!!.message)
        // The user's own food has a single name in both languages.
        assertEquals("Лаваш",Food("custom:1","Лаваш",per100g=Nutrients(250.0)).displayName)
    }
    @Test fun stepCounterBaselineRebootAndLimits() {
        val (first,s1)=stepsSince(CounterState(),5000);assertEquals(0,first);assertEquals(5000L,s1.counter)
        val (walked,s2)=stepsSince(s1,6200);assertEquals(1200,walked)
        // After a reboot the counter starts again from zero: everything it shows is new.
        val (afterReboot,s3)=stepsSince(s2,300);assertEquals(300,afterReboot);assertEquals(300L,s3.counter)
        assertEquals(MAX_STEPS_PER_DAY,stepsSince(CounterState(0),10_000_000).first)
    }
    @Test fun stepsCaloriesUseHeightWeightAndWalkingMet() {
        val p=Profile("male",30,180.0,80.0,80.0)
        // 10 000 × 0.747 m = 7.47 km; at 5 km/h that is 1.494 h × 3.8 MET × 80 kg.
        assertEquals(7.47,stepDistanceKm(10000,p),1e-9)
        assertEquals(3.8*80*7.47/5.0,stepKcal(10000,p)!!,1e-6)
        assertTrue(stepKcal(10000,p.copy(sex="female"))!!<stepKcal(10000,p)!!)
        assertNull(stepKcal(10000,null))
        assertEquals(0.0,stepKcal(0,p)!!,0.0)
        val day=UserSnapshot(steps=listOf(StepDay("2026-10-20",sensor=4000,manual=6500)))
        assertEquals(6500,day.stepsOn("2026-10-20").steps);assertEquals(0,day.stepsOn("2026-10-21").steps)
        assertEquals(4000,StepDay("2026-10-20",4000).steps)
    }
    @Test fun medicationCardsAreCheckedAndShownOnlyDuringTheirCourse() {
        val med=Medication("med:1","Магний B6",MedKind.SUPPLEMENT,MedForm.TABLET,listOf(ActiveSubstance("Магний",48.0)),2.0,2,listOf("08:00","20:00"),FoodTiming.AFTER,"2026-10-10","2026-10-30")
        validateMedication(med)
        listOf(med.copy(name=" "),med.copy(dose=0.0),med.copy(timesPerDay=0),med.copy(times=listOf("25:00")),med.copy(startDate="2026-11-01"),med.copy(substances=listOf(ActiveSubstance("",1.0)))).forEach{bad->
            assertThrows(IllegalArgumentException::class.java){validateMedication(bad)}
        }
        val taken=makeIntake(med,"2026-10-20",Instant.parse("2026-10-20T05:00:00Z"),ZoneId.of("Asia/Baku"))
        val s=UserSnapshot(medications=listOf(med),medicationIntakes=listOf(taken))
        val today=s.medicationDay("2026-10-20").single();assertEquals(1,today.intakes.size);assertFalse(today.done)
        assertTrue(s.copy(medicationIntakes=listOf(taken,taken.copy(id=UUID.randomUUID().toString()))).medicationDay("2026-10-20").single().done)
        assertTrue(s.medicationDay("2026-11-05").isEmpty())
        assertTrue(s.medicationDay("2026-10-09").isEmpty())
        // A dose marked on a day outside the course still shows, so it can be seen and removed.
        val late=makeIntake(med,"2026-11-05",Instant.parse("2026-11-05T05:00:00Z"),ZoneId.of("Asia/Baku"))
        assertEquals(1,s.copy(medicationIntakes=listOf(late)).medicationDay("2026-11-05").single().intakes.size)
        assertTrue(s.copy(medications=listOf(med.copy(archived=true))).medicationDay("2026-10-20").let{it.single().intakes.size==1})
        validateSnapshot(s)
        assertThrows(IllegalArgumentException::class.java){validateSnapshot(s.copy(medications=listOf(med.copy(id="x"))))}
    }
}
