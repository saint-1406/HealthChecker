package az.healthchecker.data
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class ExploreRestoreTest {
    @Test fun invalidExploreRestorePreservesBothTables()=runTest {
        val c=ApplicationProvider.getApplicationContext<Context>();val name="restore-${UUID.randomUUID()}.db"
        val core=HealthRepository(c,name);val explore=ExploreRepository(c,name)
        core.finishOnboarding();val place=SavedPlace(UUID.randomUUID().toString(),"Original",GeoPoint(40.0,49.0));explore.savePlace(place)
        val bad=UserSnapshot(explore=ExploreSnapshot(places=listOf(place.copy(point=GeoPoint(91.0,0.0)))))
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{core.replaceSnapshot(bad)}}
        assertTrue(core.exportSnapshot().onboarded);assertEquals(listOf(place),explore.exportSnapshot().places)
        core.replaceSnapshot(UserSnapshot(explore=ExploreSnapshot(places=listOf(place.copy(name="Restored")))))
        assertFalse(core.exportSnapshot().onboarded);assertEquals("Restored",explore.exportSnapshot().places.single().name)
        core.replaceSnapshot(UserSnapshot());assertTrue(explore.exportSnapshot().places.isEmpty())
    }
}

