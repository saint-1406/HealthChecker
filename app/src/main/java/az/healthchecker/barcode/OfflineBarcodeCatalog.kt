package az.healthchecker.barcode

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import az.healthchecker.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.SequenceInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

@Serializable data class BarcodeManifest(
    val revision:String,val snapshotDate:String,val products:Long,val withCalories:Long,
    val uncompressedBytes:Long,val sha256:String,val parts:List<String>
)

/** Indexed immutable OFF snapshot. Call lookup on an IO worker; no product list is loaded into RAM. */
class OfflineBarcodeCatalog(private val directory:File,val manifest:BarcodeManifest,private val openPart:(String)->InputStream):Closeable {
    private var database:SQLiteDatabase?=null
    init {
        require(manifest.sha256.matches(Regex("[a-f0-9]{64}")))
        require(manifest.uncompressedBytes in 1..4_000_000_000L && manifest.products>0)
        require(manifest.parts.size in 1..128 && manifest.parts.distinct().size==manifest.parts.size)
        require(manifest.parts.all{it.matches(Regex("products\\.sqlite\\.gz\\.part[0-9]{3}"))})
    }
    @Synchronized fun lookup(raw:String):LabelProduct? {
        val code=normalizeBarcode(raw)
        val db=database?:open().also{database=it}
        db.rawQuery("SELECT code,name,brand,kcal,protein,fat,carbs,fiber,package_amount,basis FROM products WHERE code=?",arrayOf(code)).use{c->
            if(!c.moveToFirst())return null
            fun number(i:Int)=if(c.isNull(i))null else c.getDouble(i)
            return LabelProduct(c.getString(0),c.getString(1),c.getString(2),
                Nutrients(number(3),number(4),number(5),number(6),number(7)),number(8),NutritionBasis.valueOf(c.getString(9)),true)
        }
    }
    private fun open():SQLiteDatabase {
        directory.mkdirs()
        val target=File(directory,"products-${manifest.sha256.take(16)}.sqlite")
        if(!target.isFile||target.length()!=manifest.uncompressedBytes)install(target)
        val db=SQLiteDatabase.openDatabase(target.absolutePath,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        try {
            db.rawQuery("PRAGMA user_version",null).use{require(it.moveToFirst()&&it.getInt(0)==1)}
            db.rawQuery("SELECT value FROM metadata WHERE key='revision'",null).use{require(it.moveToFirst()&&it.getString(0)==manifest.revision)}
            // Remove only obsolete copies produced by this class, after the new database opened successfully.
            directory.listFiles()?.filter{it!=target&&it.name.matches(Regex("products-[a-f0-9]{16}\\.sqlite"))}?.forEach{it.delete()}
            return db
        }catch(e:Exception){db.close();throw e}
    }
    private fun install(target:File) {
        val temporary=File(directory,target.name+".tmp")
        try {
            val hash=MessageDigest.getInstance("SHA-256")
            var total=0L
            // Open each compressed part only when the previous one has been consumed.
            val iterator=manifest.parts.iterator()
            val sources=object:java.util.Enumeration<InputStream> {
                override fun hasMoreElements()=iterator.hasNext()
                override fun nextElement()=openPart(iterator.next())
            }
            GZIPInputStream(SequenceInputStream(sources),64*1024).use{input->temporary.outputStream().use{output->
                val buffer=ByteArray(64*1024)
                while(true){val n=input.read(buffer);if(n<0)break;total+=n
                    require(total<=manifest.uncompressedBytes){tr("Встроенная база повреждена","The bundled database is damaged")}
                    hash.update(buffer,0,n);output.write(buffer,0,n)
                }
                output.fd.sync()
            }}
            val actual=hash.digest().joinToString(""){"%02x".format(it)}
            require(total==manifest.uncompressedBytes&&actual==manifest.sha256){tr("Встроенная база повреждена. Переустановите обновление.","The bundled database is damaged. Reinstall the update.")}
            java.nio.file.Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }finally{temporary.delete()}
    }
    @Synchronized override fun close(){database?.close();database=null}
    companion object {
        private const val ROOT="catalog/barcodes/"
        @Volatile private var shared:OfflineBarcodeCatalog?=null
        fun shared(context:Context):OfflineBarcodeCatalog = shared?:synchronized(this){shared?:run {
            val app=context.applicationContext
            val manifest=app.assets.open(ROOT+"manifest.json").bufferedReader().use{Json.decodeFromString<BarcodeManifest>(it.readText())}
            OfflineBarcodeCatalog(File(app.noBackupFilesDir,"barcode-catalog"),manifest){app.assets.open(ROOT+it)}
        }.also{shared=it}}
    }
}
