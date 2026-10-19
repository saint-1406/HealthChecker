package az.healthchecker.backup
import az.healthchecker.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
class BackupCodecTest {
    private fun snapshot()=UserSnapshot(profile=Profile(initialWeight=80.0,currentWeight=77.0),goals=listOf(DailyGoals("2026-10-18",2200.0)),entries=listOf(DiaryEntry(UUID.randomUUID().toString(),"missing-catalog-food","Яйцо",Quantity(2.0,Measure.PIECES,50.0),100.0,Nutrients(143.0,12.56),instant="2026-10-18T08:00:00Z",zoneId="Asia/Baku",localDate="2026-10-18")),onboarded=true)
    @Test fun preservesAllPersonalDataAndMissingCatalogReference() { val s=snapshot();assertEquals(s,BackupCodec.decode(BackupCodec.encode(s))) }
    @Test fun duplicateIdsRejected() { val s=snapshot();assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(entries=s.entries+s.entries))} }
    @Test fun futureSchemaAndBrokenJsonRejected() { val b=BackupCodec.encode(snapshot()).toString(Charsets.UTF_8);assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(b.replace("\"schemaVersion\":2","\"schemaVersion\":3").toByteArray())};assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(b.dropLast(8).toByteArray())} }
    @Test fun schemaOneFileFromVersionThreeStillRestores() {
        val v1="""{"format":"healthchecker-backup","schemaVersion":1,"snapshot":{"profile":null,"goals":[],"entries":[{"id":"b2e46cd0-e9e3-4516-83cd-3393fa23c6bd","foodId":"usda:173647","name":"Вода питьевая","quantity":{"value":250.0,"unit":"ML","portionGrams":null},"grams":250.0,"nutrients":{"kcal":0.0,"protein":0.0,"fat":0.0,"carbs":0.0},"waterMl":250.0,"instant":"2026-10-19T05:42:33.583184Z","zoneId":"Asia/Baku","localDate":"2026-10-19"}],"customFoods":[{"id":"custom:1","name":"Лаваш","aliases":[],"per100g":{"kcal":250.0,"protein":null,"fat":null,"carbs":null},"portions":[],"density":null,"source":"Пользователь","isWater":false}],"completedDays":["2026-10-18"],"onboarded":true}}"""
        val s=BackupCodec.decode(v1.toByteArray());assertEquals(1,s.entries.size);assertTrue(s.workouts.isEmpty());assertNull(s.customFoods.single().recipe);assertNull(s.trainingPrefs)
    }
    @Test fun schemaTwoKeepsWorkoutsMarksSettingsAndDishes() {
        val squat=Exercise("squat","Приседания",muscles=listOf(Muscle.LEGS),equipment=Equipment.NONE,kind=ExerciseKind.STRENGTH,level=1,met=3.0,metCode="02056",measure=ExerciseMeasure.REPS,secondsPerRep=3.0)
        val w=makeWorkout(UUID.randomUUID().toString(),squat,3,12,null,null,5.0,"2026-10-19",null,java.time.Instant.parse("2026-10-19T08:00:00Z"),java.time.ZoneId.of("Asia/Baku"),77.0)
        val dish=buildDish("custom:dish:1","Суп",Recipe(listOf(ingredientOf(Food("usda:1","Курица",per100g=Nutrients(120.0,22.0,2.0,0.0)),Quantity(300.0,Measure.GRAMS))),cookedGrams=900.0,servings=3))
        val s=snapshot().copy(workouts=listOf(w),trainingDays=mapOf("2026-10-19" to true,"2026-10-18" to false),trainingPrefs=TrainingPrefs(true,2),customFoods=listOf(dish))
        val bytes=BackupCodec.encode(s);assertTrue(bytes.toString(Charsets.UTF_8).contains("\"schemaVersion\":2"));assertEquals(s,BackupCodec.decode(bytes))
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(workouts=listOf(w.copy(minutes=-1.0))))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(trainingPrefs=TrainingPrefs(level=7)))}
    }
    @Test fun oversizedAndNegativeRejected() { assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(ByteArray(25*1024*1024+1))};val s=snapshot();assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(entries=listOf(s.entries.single().copy(grams=-1.0))))} }
}
