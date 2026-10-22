package az.healthchecker.data
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import az.healthchecker.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class HealthRepositoryTest {
    private fun repository()=HealthRepository(ApplicationProvider.getApplicationContext<Context>(), "test-${UUID.randomUUID()}.db")
    private fun entry()=DiaryEntry(UUID.randomUUID().toString(),"food","Original",Quantity(100.0,Measure.GRAMS),100.0,Nutrients(200.0),instant="2026-10-18T10:00:00Z",zoneId="Asia/Baku",localDate="2026-10-18")
    @Test fun repeatedSaveIsIdempotentAndEditRetainsSnapshot()=runTest { val r=repository();val e=entry();r.upsertEntry(e);r.upsertEntry(e);assertEquals(listOf(e),r.exportSnapshot().entries);r.upsertEntry(e.copy(name="Edited"));assertEquals(1,r.exportSnapshot().entries.size);assertEquals("Edited",r.exportSnapshot().entries.single().name) }
    @Test fun initialWeightAndManualGoalsPersist()=runTest { val r=repository();r.saveProfile(Profile(initialWeight=80.0,currentWeight=80.0));r.saveGoals(DailyGoals("2026-10-18",2200.0));r.saveProfile(Profile(initialWeight=70.0,currentWeight=70.0));assertEquals(80.0,r.exportSnapshot().profile!!.initialWeight,0.0);assertEquals(2200.0,r.exportSnapshot().goals.single().kcal!!,0.0) }
    @Test fun invalidRestoreLeavesOldDataIntact()=runTest { val r=repository();val e=entry();r.upsertEntry(e);assertThrows(IllegalArgumentException::class.java){ kotlinx.coroutines.runBlocking{r.replaceSnapshot(UserSnapshot(entries=listOf(e,e)))}};assertEquals(listOf(e),r.exportSnapshot().entries) }
    @Test fun trainingDataPersistsAndMarksCanBeCleared()=runTest {
        val r=repository();val squat=Exercise("squat","Приседания",muscles=listOf(Muscle.LEGS),equipment=Equipment.NONE,kind=ExerciseKind.STRENGTH,level=1,met=3.0,metCode="02056",measure=ExerciseMeasure.REPS,secondsPerRep=3.0)
        val w=makeWorkout(UUID.randomUUID().toString(),squat,3,10,null,null,5.0,"2026-10-19",null,java.time.Instant.parse("2026-10-19T08:00:00Z"),java.time.ZoneId.of("Asia/Baku"),70.0)
        r.upsertWorkout(w);r.upsertWorkout(w);r.markTraining("2026-10-18",false);r.markTraining("2026-10-19",true);r.saveTrainingPrefs(TrainingPrefs(true,3))
        val s=r.exportSnapshot();assertEquals(listOf(w),s.workouts);assertEquals(mapOf("2026-10-18" to false,"2026-10-19" to true),s.trainingDays);assertEquals(TrainingPrefs(true,3),s.trainingPrefs)
        r.markTraining("2026-10-19",null);r.deleteWorkout(w.id);assertEquals(mapOf("2026-10-18" to false),r.exportSnapshot().trainingDays);assertTrue(r.exportSnapshot().workouts.isEmpty())
        r.replaceSnapshot(s);assertEquals(s,r.exportSnapshot())
    }
    @Test fun medicationsIntakesAndStepsPersist()=runTest {
        val r=repository()
        val med=Medication("med:1","Омега-3",MedKind.SUPPLEMENT,MedForm.CAPSULE,dose=1.0,timesPerDay=1)
        r.saveMedication(med);r.saveMedication(med.copy(notes="после завтрака"))
        val t=makeIntake(med,"2026-10-20",java.time.Instant.parse("2026-10-20T06:00:00Z"),java.time.ZoneId.of("Asia/Baku"));r.upsertIntake(t)
        r.setManualSteps("2026-10-20",9000)
        var s=r.exportSnapshot();assertEquals("после завтрака",s.medications.single().notes);assertEquals(listOf(t),s.medicationIntakes);assertEquals(StepDay("2026-10-20",0,9000),s.steps.single())
        r.setManualSteps("2026-10-20",null);r.deleteIntake(t.id);r.deleteMedication(med.id)
        s=r.exportSnapshot();assertTrue(s.medications.isEmpty());assertTrue(s.medicationIntakes.isEmpty());assertEquals(StepDay("2026-10-20"),s.steps.single())
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{r.saveMedication(med.copy(dose=-1.0))}}
    }
    @Test fun sensorStepsAddUpAndAskForABackupOncePerDay()=runTest {
        val r=repository()
        val start=r.exportVersioned().first
        assertEquals(StepDay("2026-10-20",0),r.addSensorSteps("2026-10-20",0,true))
        val afterFirst=r.exportVersioned().first;assertEquals(start+1,afterFirst)
        r.addSensorSteps("2026-10-20",1200,false);assertEquals(StepDay("2026-10-20",2000),r.addSensorSteps("2026-10-20",800,false))
        // Frequent counter writes do not each trigger a backup.
        assertEquals(afterFirst,r.exportVersioned().first)
        r.setManualSteps("2026-10-20",5000);assertEquals(StepDay("2026-10-20",2000,5000),r.exportSnapshot().steps.single())
    }
    @Test fun fiberIsFilledIntoOldCatalogEntriesOnce()=runTest {
        val r=repository();val old=entry().copy(foodId="usda:1",grams=50.0,quantity=Quantity(50.0,Measure.GRAMS))
        r.upsertEntry(old)
        val catalog=mapOf("usda:1" to Food("usda:1","Овсянка",per100g=Nutrients(379.0,fiber=10.0)))
        assertEquals(1,r.backfillFiber{catalog[it]});assertEquals(5.0,r.exportSnapshot().entries.single().nutrients.fiber!!,1e-9)
        assertEquals(0,r.backfillFiber{catalog[it]})
    }
    @Test fun restoredSnapshotsDoNotDuplicate()=runTest { val r=repository();val s=UserSnapshot(entries=listOf(entry()));r.replaceSnapshot(s);r.replaceSnapshot(s);assertEquals(s,r.exportSnapshot()) }
}
