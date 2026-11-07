package az.healthchecker.data
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.backup.BackupCodec
import az.healthchecker.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** The program setup answers are stored, applied together with the targets, backed up and restored. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class SetupPersistenceTest {
    private fun repository()=HealthRepository(ApplicationProvider.getApplicationContext<Context>(),"test-${UUID.randomUUID()}.db")
    private val setup=ProgramSetup("female",34,170.0,72.0,68.0,setOf(Limitation.KNEE),setOf(HomeEquipment.DUMBBELLS,HomeEquipment.MAT),FitnessGoal.CUT,1.55,2,4,45,"2026-11-07")
    private val goals=DailyGoals("2026-11-07",1850.0,135.0,2400.0,manual=false,fat=50.0,carbs=210.0)

    @Test fun applyingTheSetupUpdatesTargetsBodyAndTrainingTogether()=runTest {
        val r=repository()
        r.saveProfile(Profile("female",34,170.0,80.0,75.0,1.2));r.saveGoals(DailyGoals("2026-11-01",2000.0));r.saveTrainingPrefs(TrainingPrefs(false,1))
        r.applySetup(setup,goals)
        val s=r.exportSnapshot()
        assertEquals(setup,s.setup);assertTrue(s.onboarded)
        // The starting weight stays; the rest comes from the answers.
        assertEquals(Profile("female",34,170.0,80.0,72.0,1.55),s.profile)
        assertEquals(TrainingPrefs(true,2),s.trainingPrefs)
        assertEquals(goals,s.day("2026-11-07").goals);assertEquals(2000.0,s.day("2026-11-03").goals!!.kcal!!,0.0)
        // A restore brings the same answers back.
        val restored=repository();restored.replaceSnapshot(s);assertEquals(s,restored.exportSnapshot())
    }
    @Test fun invalidAnswersChangeNothing()=runTest {
        val r=repository();r.saveGoals(DailyGoals("2026-11-07",2000.0))
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{r.applySetup(setup.copy(sex="male",limitations=setOf(Limitation.PREGNANCY)),goals)}}
        val s=r.exportSnapshot();assertNull(s.setup);assertNull(s.profile);assertEquals(2000.0,s.day("2026-11-07").goals!!.kcal!!,0.0)
    }
    @Test fun backupSchema11KeepsTheSetupAndOlderFilesStillRestore() {
        val s=UserSnapshot(profile=Profile(initialWeight=80.0,currentWeight=72.0),goals=listOf(goals),onboarded=true,setup=setup)
        val text=BackupCodec.encode(s).toString(Charsets.UTF_8)
        assertTrue(text.contains("\"schemaVersion\":11"));assertTrue(text.contains("\"setup\""));assertTrue(text.contains("\"carbs\":210.0"))
        assertEquals(s,BackupCodec.decode(text.toByteArray()))
        // A schema 10 file has no setup and no fat/carb goals: it restores with them empty.
        val ten="""{"format":"healthchecker-backup","schemaVersion":10,"snapshot":{"profile":null,"goals":[{"effectiveFrom":"2026-10-18","kcal":2200.0,"protein":120.0,"waterMl":null,"manual":true}],"entries":[],"customFoods":[],"completedDays":[],"onboarded":true}}"""
        val old=BackupCodec.decode(ten.toByteArray())
        assertNull(old.setup);assertNull(old.goals.single().fat);assertNull(old.goals.single().carbs);assertEquals(2200.0,old.goals.single().kcal!!,0.0)
        // Snapshots without a setup are written exactly as before: no new keys.
        val plain=BackupCodec.encode(UserSnapshot(goals=listOf(DailyGoals("2026-10-18",2200.0)))).toString(Charsets.UTF_8)
        assertFalse(plain.contains("setup"));assertFalse(plain.contains("carbs"))
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(setup=setup.copy(age=5)))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(s.copy(goals=listOf(goals.copy(fat=-1.0))))}
    }
}
