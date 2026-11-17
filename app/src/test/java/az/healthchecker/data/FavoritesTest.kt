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

/** Starred exercises and videos are stored, backed up and restored; "often" lists skip what is already starred. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class FavoritesTest {
    private fun repository()=HealthRepository(ApplicationProvider.getApplicationContext<Context>(),"test-${UUID.randomUUID()}.db")

    @Test fun favoriteWorkoutsSurviveBackupAndRestore()=runTest {
        val r=repository()
        r.setFavoriteWorkout(FAVORITE_EXERCISE,"pushup",true);r.setFavoriteWorkout(FAVORITE_EXERCISE,"squat",true)
        r.setFavoriteWorkout(FAVORITE_VIDEO,"yt:abc",true);r.setFavoriteWorkout(FAVORITE_EXERCISE,"squat",false)
        r.setFavorite("food-1",true)
        val s=r.exportSnapshot()
        assertEquals(setOf("pushup"),s.favoriteExercises);assertEquals(setOf("yt:abc"),s.favoriteVideos);assertEquals(setOf("food-1"),s.favoriteFoods)
        val text=BackupCodec.encode(s).toString(Charsets.UTF_8)
        assertTrue(text.contains("\"schemaVersion\":12"));assertTrue(text.contains("\"favoriteVideos\":[\"yt:abc\"]"))
        val restored=repository();restored.replaceSnapshot(BackupCodec.decode(text.toByteArray()))
        assertEquals(s,restored.exportSnapshot())
    }

    @Test fun olderBackupsHaveNoWorkoutFavorites() {
        val eleven="""{"format":"healthchecker-backup","schemaVersion":11,"snapshot":{"profile":null,"goals":[],"entries":[],"customFoods":[],"completedDays":[],"onboarded":true}}"""
        val s=BackupCodec.decode(eleven.toByteArray())
        assertTrue(s.favoriteExercises.isEmpty());assertTrue(s.favoriteVideos.isEmpty())
        // Without favorites the file does not even mention them.
        assertFalse(BackupCodec.encode(s).toString(Charsets.UTF_8).contains("favorite"))
    }

    @Test fun unknownKindsAndBadIdsAreRefused()=runTest {
        val r=repository()
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{r.setFavoriteWorkout("food","x",true)}}
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{r.setFavoriteWorkout(FAVORITE_VIDEO,"",true)}}
        assertTrue(r.exportSnapshot().favoriteVideos.isEmpty())
    }

    private fun entry(food:String,date:String,hour:Int,water:Double=0.0)=DiaryEntry(UUID.randomUUID().toString(),food,food,Quantity(100.0,Measure.GRAMS),100.0,Nutrients(100.0,1.0,1.0,1.0),water,"${date}T%02d:00:00Z".format(hour),"UTC",date)
    private fun workout(ex:String,date:String,hour:Int)=WorkoutEntry(UUID.randomUUID().toString(),ex,ex,ExerciseKind.STRENGTH,emptyList(),minutes=5.0,met=4.0,instant="${date}T%02d:00:00Z".format(hour),zoneId="UTC",localDate=date)

    @Test fun oftenEatenSkipsWaterOldDaysAndStarredFoods() {
        val s=UserSnapshot(entries=listOf(entry("oats","2026-11-17",8),entry("oats","2026-11-16",8),entry("egg","2026-11-17",9),entry("egg","2026-11-15",9),
            entry("tea","2026-11-17",10),entry("water","2026-11-17",11,250.0),entry("cake","2026-08-01",12),entry("tomorrow","2026-11-18",8)),favoriteFoods=setOf("tea"))
        // oats and egg were eaten twice each; oats more recently wins the tie only if later — egg's last was 09:00 on the 17th.
        assertEquals(listOf("egg","oats"),s.frequentFoods("2026-11-17"))
        assertEquals(listOf("egg"),s.frequentFoods("2026-11-17",limit=1))
    }

    @Test fun oftenDoneSkipsStarredExercises() {
        val s=UserSnapshot(workouts=listOf(workout("squat","2026-11-17",7),workout("squat","2026-11-10",7),workout("plank","2026-11-17",8),workout("pushup","2026-11-17",9)),favoriteExercises=setOf("pushup"))
        assertEquals(listOf("squat","plank"),s.frequentExercises("2026-11-17"))
    }
}
