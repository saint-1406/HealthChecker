package az.healthchecker.backup
import az.healthchecker.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
class BackupCodecTest {
    private fun snapshot()=UserSnapshot(profile=Profile(initialWeight=80.0,currentWeight=77.0),goals=listOf(DailyGoals("2026-10-18",2200.0)),entries=listOf(DiaryEntry(UUID.randomUUID().toString(),"missing-catalog-food","Яйцо",Quantity(2.0,Measure.PIECES,50.0),100.0,Nutrients(143.0,12.56),instant="2026-10-18T08:00:00Z",zoneId="Asia/Baku",localDate="2026-10-18")),onboarded=true)
    @Test fun preservesAllPersonalDataAndMissingCatalogReference() { val s=snapshot();assertEquals(s,BackupCodec.decode(BackupCodec.encode(s))) }
    @Test fun duplicateIdsRejected() { val s=snapshot();assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(entries=s.entries+s.entries))} }
    @Test fun schemaFourKeepsWeightsAndExercisePictures() {
        val jpeg=byteArrayOf(0xFF.toByte(),0xD8.toByte(),0xFF.toByte(),0xE0.toByte(),1,2,3,4,0xFF.toByte(),0xD9.toByte())
        val picture=java.util.Base64.getEncoder().encodeToString(jpeg)
        val weights=listOf(makeWeight("2026-10-20",80.4,java.time.Instant.parse("2026-10-20T04:30:00Z"),java.time.ZoneId.of("Asia/Baku")),makeWeight("2026-10-21",80.1,java.time.Instant.parse("2026-10-21T04:30:00Z"),java.time.ZoneId.of("Asia/Baku")))
        val s=snapshot().copy(weights=weights,exerciseImages=mapOf("prisoner_squat" to picture))
        val bytes=BackupCodec.encode(s);assertTrue(bytes.toString(Charsets.UTF_8).contains("\"schemaVersion\":${BackupCodec.SCHEMA_VERSION}"));assertEquals(s,BackupCodec.decode(bytes))
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(exerciseImages=mapOf("prisoner_squat" to java.util.Base64.getEncoder().encodeToString("not a jpeg".toByteArray()))))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(exerciseImages=mapOf("../evil" to picture)))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(weights=weights+weights.first()))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(weights=listOf(weights.first().copy(kg=Double.NaN))))}
    }
    @Test fun schemaSixKeepsTheUsersVideosAndTheirChangesAndFourStillRestores() {
        val now=java.time.Instant.parse("2026-10-28T08:00:00Z")
        // An own link, an own file and the user's changes to a built-in video (saved under its id, without the timeline).
        val changed=editVideo(WorkoutVideo("yt:eoY6qlkp1xA","30 MIN",youtubeId="eoY6qlkp1xA",embeddable=false),"Моя 30-минутка","По вторникам","https://youtu.be/eoY6qlkp1xA").copy(addedAt=now.toString())
        val s=snapshot().copy(videos=listOf(makeOwnVideo("video:${UUID.randomUUID()}","Утренняя","https://youtu.be/ml6cT4AZdqI",now),makeOwnVideo("video:${UUID.randomUUID()}","Файл","",now),changed))
        val text=BackupCodec.encode(s).toString(Charsets.UTF_8)
        assertTrue(text.contains("\"schemaVersion\":6"));assertTrue(text.contains("ml6cT4AZdqI"));assertEquals(s,BackupCodec.decode(text.toByteArray()))
        val old=snapshot()
        val four=BackupCodec.encode(old).toString(Charsets.UTF_8).replace("\"schemaVersion\":6","\"schemaVersion\":4").replace(",\"videos\":[]","")
        assertFalse(four.contains("videos"));assertEquals(old,BackupCodec.decode(four.toByteArray()))
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(videos=s.videos+s.videos.first()))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(videos=listOf(s.videos.first().copy(youtubeId="<script>"))))}
    }
    @Test fun futureSchemaAndBrokenJsonRejected() { val b=BackupCodec.encode(snapshot()).toString(Charsets.UTF_8);assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(b.replace("\"schemaVersion\":${BackupCodec.SCHEMA_VERSION}","\"schemaVersion\":${BackupCodec.SCHEMA_VERSION+1}").toByteArray())};assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(b.dropLast(8).toByteArray())} }
    @Test fun schemaOneFileFromVersionThreeStillRestores() {
        val v1="""{"format":"healthchecker-backup","schemaVersion":1,"snapshot":{"profile":null,"goals":[],"entries":[{"id":"b2e46cd0-e9e3-4516-83cd-3393fa23c6bd","foodId":"usda:173647","name":"Вода питьевая","quantity":{"value":250.0,"unit":"ML","portionGrams":null},"grams":250.0,"nutrients":{"kcal":0.0,"protein":0.0,"fat":0.0,"carbs":0.0},"waterMl":250.0,"instant":"2026-10-19T05:42:33.583184Z","zoneId":"Asia/Baku","localDate":"2026-10-19"}],"customFoods":[{"id":"custom:1","name":"Лаваш","aliases":[],"per100g":{"kcal":250.0,"protein":null,"fat":null,"carbs":null},"portions":[],"density":null,"source":"Пользователь","isWater":false}],"completedDays":["2026-10-18"],"onboarded":true}}"""
        val s=BackupCodec.decode(v1.toByteArray());assertEquals(1,s.entries.size);assertTrue(s.workouts.isEmpty());assertNull(s.customFoods.single().recipe);assertNull(s.trainingPrefs)
    }
    @Test fun schemaTwoKeepsWorkoutsMarksSettingsAndDishes() {
        val squat=Exercise("squat","Приседания",muscles=listOf(Muscle.LEGS),equipment=Equipment.NONE,kind=ExerciseKind.STRENGTH,level=1,met=3.0,metCode="02056",measure=ExerciseMeasure.REPS,secondsPerRep=3.0)
        val w=makeWorkout(UUID.randomUUID().toString(),squat,3,12,null,null,5.0,"2026-10-19",null,java.time.Instant.parse("2026-10-19T08:00:00Z"),java.time.ZoneId.of("Asia/Baku"),77.0)
        val dish=buildDish("custom:dish:1","Суп",Recipe(listOf(ingredientOf(Food("usda:1","Курица",per100g=Nutrients(120.0,22.0,2.0,0.0)),Quantity(300.0,Measure.GRAMS))),cookedGrams=900.0,servings=3))
        val s=snapshot().copy(workouts=listOf(w),trainingDays=mapOf("2026-10-19" to true,"2026-10-18" to false),trainingPrefs=TrainingPrefs(true,2),customFoods=listOf(dish))
        val bytes=BackupCodec.encode(s);assertTrue(bytes.toString(Charsets.UTF_8).contains("\"schemaVersion\":${BackupCodec.SCHEMA_VERSION}"));assertEquals(s,BackupCodec.decode(bytes))
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(workouts=listOf(w.copy(minutes=-1.0))))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(trainingPrefs=TrainingPrefs(level=7)))}
    }
    @Test fun schemaThreeKeepsFiberMedicationsAndSteps() {
        val med=Medication("med:1","Витамин D3",MedKind.VITAMIN,MedForm.CAPSULE,listOf(ActiveSubstance("Холекальциферол",2000.0,StrengthUnit.IU)),1.0,1,listOf("09:00"),FoodTiming.WITH,"2026-10-01","2026-12-31","Дефицит","Терапевт","")
        val intake=makeIntake(med,"2026-10-18",java.time.Instant.parse("2026-10-18T05:00:00Z"),java.time.ZoneId.of("Asia/Baku"))
        val s=snapshot().let{it.copy(entries=it.entries.map{e->e.copy(nutrients=e.nutrients.copy(fiber=1.5))},medications=listOf(med),medicationIntakes=listOf(intake),steps=listOf(StepDay("2026-10-18",8000,null),StepDay("2026-10-17",0,12000)))}
        val text=BackupCodec.encode(s).toString(Charsets.UTF_8)
        assertTrue(text.contains("\"schemaVersion\":${BackupCodec.SCHEMA_VERSION}"));assertTrue(text.contains("\"fiber\":1.5"));assertEquals(s,BackupCodec.decode(text.toByteArray()))
        // Unknown fibre is left out, so an entry without it keeps the shape it had in schema 2.
        assertFalse(BackupCodec.encode(snapshot()).toString(Charsets.UTF_8).contains("fiber"))
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(medications=listOf(med.copy(timesPerDay=0))))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(steps=listOf(StepDay("2026-10-18",-5))))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(steps=s.steps+StepDay("2026-10-18",1)))}
    }
    @Test fun schemaTwoFileStillRestores() {
        val v2="""{"format":"healthchecker-backup","schemaVersion":2,"snapshot":{"profile":null,"goals":[],"entries":[],"customFoods":[],"completedDays":[],"onboarded":true,"workouts":[],"trainingDays":{"2026-10-19":true},"trainingPrefs":{"dumbbells":true,"level":2}}}"""
        val s=BackupCodec.decode(v2.toByteArray());assertEquals(mapOf("2026-10-19" to true),s.trainingDays);assertTrue(s.medications.isEmpty());assertTrue(s.steps.isEmpty())
    }
    @Test fun oversizedAndNegativeRejected() { assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(ByteArray(25*1024*1024+1))};val s=snapshot();assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(entries=listOf(s.entries.single().copy(grams=-1.0))))} }
}
