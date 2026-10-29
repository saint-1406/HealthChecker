package az.healthchecker.data
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class DailySettingsTest {
    @Test fun portionsWaterAndOverviewChoicesPersistIndependently()=runTest {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val r=SettingsRepository(context)
        val q=Quantity(2.0,Measure.PIECES,50.0)
        r.portion("usda:egg",q);r.waterMl(500);r.cards(OverviewCards(weight=false,steps=false,training=true));r.dismissBackupPrompt()
        val s=SettingsRepository(context).settings.first()
        assertEquals(q,s.lastPortions["usda:egg"]);assertEquals(500,s.waterMl)
        assertEquals(OverviewCards(false,false,true),s.overviewCards);assertTrue(s.backupPromptDismissed)
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{r.portion("egg",Quantity(Double.NaN,Measure.GRAMS))}}
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{r.waterMl(-1)}}
    }
}
