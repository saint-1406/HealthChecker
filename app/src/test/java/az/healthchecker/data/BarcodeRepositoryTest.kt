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

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class BarcodeRepositoryTest {
    private val code=normalizeBarcode("4006381333931")
    @Test fun barcodeSurvivesReopenAndBackupAndDuplicateCannotOverwriteAnotherCard()=runTest {
        val context=ApplicationProvider.getApplicationContext<Context>();val db="barcode-${UUID.randomUUID()}.db"
        val r=HealthRepository(context,db)
        val f=Food("custom:one","Test",per100g=Nutrients(200.0),barcode=code)
        r.saveFood(f)
        assertEquals(f,HealthRepository(context,db).foodByBarcode("4006381333931"))
        assertEquals(f,BackupCodec.decode(BackupCodec.encode(r.exportSnapshot())).customFoods.single())
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{r.saveFood(f.copy(id="custom:two"))}}
        assertEquals(listOf(f),r.exportSnapshot().customFoods)
        assertThrows(IllegalArgumentException::class.java){validateSnapshot(UserSnapshot(customFoods=listOf(f,f.copy(id="custom:two"))))}
    }
    @Test fun missingBarcodeKeepsOldFoodShapeAndSchema7Reads() {
        val s=UserSnapshot(customFoods=listOf(Food("custom:old","Old",per100g=Nutrients(1.0))))
        val bytes=BackupCodec.encode(s);assertFalse(bytes.toString(Charsets.UTF_8).contains("barcode"))
        val old=bytes.toString(Charsets.UTF_8).replace("\"schemaVersion\":8","\"schemaVersion\":7")
        assertEquals(s,BackupCodec.decode(old.toByteArray()))
    }
}
