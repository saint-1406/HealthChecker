package az.healthchecker.data

import android.database.sqlite.SQLiteDatabase
import az.healthchecker.barcode.*
import az.healthchecker.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class OfflineBarcodeCatalogTest {
    private fun fixture():Pair<BarcodeManifest,ByteArray> {
        val file=Files.createTempFile("off-fixture",".sqlite").toFile()
        SQLiteDatabase.openOrCreateDatabase(file,null).use{db->
            db.execSQL("PRAGMA user_version=1")
            db.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
            db.execSQL("INSERT INTO metadata VALUES('revision','fixture')")
            db.execSQL("CREATE TABLE products(code TEXT PRIMARY KEY,name TEXT NOT NULL,brand TEXT NOT NULL,kcal REAL,protein REAL,fat REAL,carbs REAL,fiber REAL,package_amount REAL,basis TEXT NOT NULL) WITHOUT ROWID")
            db.execSQL("INSERT INTO products VALUES('03017620422003','Test spread','Brand',539,6.3,30.9,57.5,NULL,400,'MASS')")
            db.execSQL("INSERT INTO products VALUES('00012345678905','No nutrition','',NULL,NULL,NULL,NULL,NULL,NULL,'VOLUME')")
        }
        val raw=file.readBytes();file.delete()
        val hash=MessageDigest.getInstance("SHA-256").digest(raw).joinToString(""){"%02x".format(it)}
        val out=ByteArrayOutputStream();GZIPOutputStream(out).use{it.write(raw)}
        return BarcodeManifest(revision="fixture",snapshotDate="2026-10-30",products=2,withCalories=1,uncompressedBytes=raw.size.toLong(),sha256=hash,parts=listOf("products.sqlite.gz.part000")) to out.toByteArray()
    }
    @Test fun lookupUsesCanonicalIndexAndReusesInstalledDatabaseAcrossReopen() {
        val (manifest,gzip)=fixture();val root=Files.createTempDirectory("off-installed").toFile();var opens=0
        OfflineBarcodeCatalog(root,manifest){opens++;ByteArrayInputStream(gzip)}.use{catalog->
            val product=catalog.lookup("3017620422003")!!
            assertEquals("Test spread",product.name);assertEquals(539.0,product.nutrients.kcal!!,0.0)
            assertNull(product.nutrients.fiber);assertTrue(product.fromOpenFoodFacts)
            assertEquals(product,catalog.lookup("03017620422003"));assertNull(catalog.lookup("4006381333931"))
            assertEquals(1,opens)
            val missing=catalog.lookup("012345678905")!!
            assertNull(missing.nutrients.kcal);assertEquals(NutritionBasis.VOLUME,missing.basis)
        }
        OfflineBarcodeCatalog(root,manifest){error("The installed database must be reused")}.use{assertEquals("Test spread",it.lookup("3017620422003")!!.name)}
    }
    @Test fun corruptOrInterruptedAssetIsNeverActivatedAndRetryCanInstall() {
        val (manifest,gzip)=fixture();val root=Files.createTempDirectory("off-corrupt").toFile()
        assertThrows(Exception::class.java){OfflineBarcodeCatalog(root,manifest){ByteArrayInputStream(gzip.copyOf(gzip.size/2))}.use{it.lookup("3017620422003")}}
        assertTrue(root.listFiles().orEmpty().none{it.extension=="sqlite"||it.extension=="tmp"})
        assertThrows(IllegalArgumentException::class.java){OfflineBarcodeCatalog(root,manifest.copy(sha256="0".repeat(64))){ByteArrayInputStream(gzip)}.use{it.lookup("3017620422003")}}
        OfflineBarcodeCatalog(root,manifest){ByteArrayInputStream(gzip)}.use{assertNotNull(it.lookup("3017620422003"))}
    }
}
