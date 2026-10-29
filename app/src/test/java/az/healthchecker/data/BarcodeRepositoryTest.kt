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
    @Test fun productPhotoSurvivesDatabaseReopenBackupAndDoesNotChangeNutrition()=runTest {
        val jpeg=byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0xe0.toByte(),1,2,3,4,0xff.toByte(),0xd9.toByte())
        val photo=java.util.Base64.getEncoder().encodeToString(jpeg)
        val context=ApplicationProvider.getApplicationContext<Context>();val db="product-photo-${UUID.randomUUID()}.db"
        val r=HealthRepository(context,db)
        val f=Food("custom:photo","Йогурт",per100g=Nutrients(60.0,4.0),barcode=code,productPhoto=photo)
        r.saveFood(f)
        val reopened=HealthRepository(context,db).foodByBarcode(code)!!
        assertEquals(f,reopened)
        assertEquals(30.0,calculate(reopened,Quantity(50.0,Measure.GRAMS)).nutrients.kcal!!,0.0)
        assertEquals(f,BackupCodec.decode(BackupCodec.encode(r.exportSnapshot())).customFoods.single())
        assertThrows(IllegalArgumentException::class.java){validateSnapshot(UserSnapshot(customFoods=listOf(f.copy(productPhoto="not a jpeg"))))}
        assertThrows(IllegalArgumentException::class.java){validateSnapshot(UserSnapshot(customFoods=listOf(f.copy(productPhoto="A".repeat(300000)))))}
        val old=BackupCodec.encode(UserSnapshot(customFoods=listOf(f.copy(productPhoto=null)))).toString(Charsets.UTF_8)
        assertFalse(old.contains("productPhoto"))
        assertNull(BackupCodec.decode(old.replace("\"schemaVersion\":${BackupCodec.SCHEMA_VERSION}","\"schemaVersion\":8").toByteArray()).customFoods.single().productPhoto)
    }
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
        val old=bytes.toString(Charsets.UTF_8).replace("\"schemaVersion\":${BackupCodec.SCHEMA_VERSION}","\"schemaVersion\":7")
        assertEquals(s,BackupCodec.decode(old.toByteArray()))
    }
}
