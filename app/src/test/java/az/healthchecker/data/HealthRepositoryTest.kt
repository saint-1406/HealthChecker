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
    @Test fun restoredSnapshotsDoNotDuplicate()=runTest { val r=repository();val s=UserSnapshot(entries=listOf(entry()));r.replaceSnapshot(s);r.replaceSnapshot(s);assertEquals(s,r.exportSnapshot()) }
}
