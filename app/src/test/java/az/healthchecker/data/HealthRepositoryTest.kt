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
    @Test fun newestWeighInUpdatesCurrentWeightOnlyAndPicturesTravelWithBackups()=runTest {
        val r=repository();val zone=java.time.ZoneId.of("Asia/Baku");fun w(d:String,kg:Double)=makeWeight(d,kg,java.time.Instant.parse("${d}T04:00:00Z"),zone)
        r.saveProfile(Profile(initialWeight=82.0,currentWeight=82.0))
        r.saveWeight(w("2026-10-21",80.5));r.saveWeight(w("2026-10-15",81.5))
        var s=r.exportSnapshot();assertEquals(80.5,s.profile!!.currentWeight,0.0);assertEquals(82.0,s.profile!!.initialWeight,0.0);assertEquals(2,s.weights.size)
        r.saveWeight(w("2026-10-21",80.2));s=r.exportSnapshot();assertEquals(80.2,s.weights.single{it.date=="2026-10-21"}.kg,0.0);assertEquals(80.2,s.profile!!.currentWeight,0.0)
        val jpeg=byteArrayOf(0xFF.toByte(),0xD8.toByte(),0xFF.toByte(),0xE0.toByte(),9,9,0xFF.toByte(),0xD9.toByte())
        val before=r.exportVersioned().first;r.saveExerciseImage("prisoner_squat",jpeg);assertEquals(before+1,r.exportVersioned().first)
        assertEquals(setOf("prisoner_squat"),r.imageVersions().keys);assertTrue(r.exportSnapshot().exerciseImages.isEmpty())
        val backup=r.exportVersioned().second;assertEquals(1,backup.exerciseImages.size)
        r.deleteExerciseImage("prisoner_squat");r.deleteWeight("2026-10-15");assertTrue(r.imageVersions().isEmpty())
        r.replaceSnapshot(backup);assertArrayEquals(jpeg,r.imageFile("prisoner_squat").readBytes());assertEquals(2,r.exportSnapshot().weights.size)
        r.replaceSnapshot(backup.copy(exerciseImages=emptyMap()));assertTrue(r.imageVersions().isEmpty())
    }
    @Test fun ownVideosAndAWholeVideoWorkoutPersistAsOneChange()=runTest {
        val r=repository();val now=java.time.Instant.parse("2026-10-28T08:00:00Z");val zone=java.time.ZoneId.of("Asia/Baku")
        val v=makeOwnVideo("video:${UUID.randomUUID()}","Утренняя","https://youtu.be/ml6cT4AZdqI",now)
        r.saveVideo(v);r.saveVideo(v);assertEquals(listOf(v),r.exportSnapshot().videos)
        val squat=Exercise("squat","Приседания",muscles=listOf(Muscle.LEGS),equipment=Equipment.NONE,kind=ExerciseKind.STRENGTH,level=1,met=3.0,metCode="02056",measure=ExerciseMeasure.REPS,secondsPerRep=3.0)
        val done=listOf(VideoWork("squat",1,30),VideoWork("squat",2,80)).map{videoWorkout(UUID.randomUUID().toString(),squat,it,"2026-10-28",now,zone,70.0)}
        val before=r.exportVersioned().first
        r.upsertWorkouts(done);assertEquals(before+1,r.exportVersioned().first);assertEquals(done.toSet(),r.exportSnapshot().workouts.toSet())
        r.deleteWorkouts(done.map{it.id});assertTrue(r.exportSnapshot().workouts.isEmpty())
        val s=r.exportSnapshot();r.deleteVideo(v.id);assertTrue(r.exportSnapshot().videos.isEmpty());r.replaceSnapshot(s);assertEquals(listOf(v),r.exportSnapshot().videos)
    }
    @Test fun restoredSnapshotsDoNotDuplicate()=runTest { val r=repository();val s=UserSnapshot(entries=listOf(entry()));r.replaceSnapshot(s);r.replaceSnapshot(s);assertEquals(s,r.exportSnapshot()) }
    @Test fun pictureActivationFailureRollsBackTheWholeRestore()=runTest {
        val context=ApplicationProvider.getApplicationContext<Context>();val r=repository();val original=entry();r.upsertEntry(original)
        val pictures=java.io.File(context.filesDir,"exercise-images")
        pictures.deleteRecursively();pictures.writeText("blocked")
        try {
            val jpeg=java.util.Base64.getEncoder().encodeToString(byteArrayOf(0xFF.toByte(),0xD8.toByte(),1,2))
            assertThrows(java.io.IOException::class.java){kotlinx.coroutines.runBlocking{r.replaceSnapshot(UserSnapshot(entries=listOf(entry().copy(name="New")),exerciseImages=mapOf("squat" to jpeg)))}}
            assertEquals(listOf(original),r.exportSnapshot().entries);assertEquals("blocked",pictures.readText())
        } finally { pictures.delete() }
    }
    @Test fun deletingTheNewestWeightRemovesItFromSubsequentCalorieEstimates()=runTest {
        val r=repository();val zone=java.time.ZoneId.of("Asia/Baku")
        r.saveProfile(Profile(initialWeight=82.0,currentWeight=82.0))
        r.saveWeight(makeWeight("2026-10-20",81.0,java.time.Instant.parse("2026-10-20T04:00:00Z"),zone))
        r.saveWeight(makeWeight("2026-10-21",70.0,java.time.Instant.parse("2026-10-21T04:00:00Z"),zone))
        r.deleteWeight("2026-10-21")
        assertEquals(81.0,r.exportSnapshot().profile!!.currentWeight,0.0)
        r.deleteWeight("2026-10-20")
        assertEquals(82.0,r.exportSnapshot().profile!!.currentWeight,0.0)
    }
}
