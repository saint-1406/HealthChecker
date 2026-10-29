package az.healthchecker.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="records") data class StoredRecord(@PrimaryKey val key:String,val kind:String,val payload:String)
@Entity(tableName="revision") data class Revision(@PrimaryKey val id:Int=1,val current:Long=0,val backedUp:Long=-1)
@Dao interface HealthDao {
    @Query("SELECT * FROM records") suspend fun all():List<StoredRecord>
    @Query("SELECT * FROM records WHERE kind=:kind") suspend fun byKind(kind:String):List<StoredRecord>
    @Query("SELECT * FROM records") fun observe():Flow<List<StoredRecord>>
    @Query("SELECT * FROM records WHERE `key`=:key") suspend fun get(key:String):StoredRecord?
    @Query("SELECT COUNT(*) FROM records WHERE kind=:kind") suspend fun countKind(kind:String):Int
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(record:StoredRecord)
    @Query("DELETE FROM records WHERE `key`=:key") suspend fun delete(key:String)
    @Query("DELETE FROM records") suspend fun clear()
    @Query("SELECT * FROM revision WHERE id=1") suspend fun revision():Revision?
    @Query("SELECT * FROM revision WHERE id=1") fun observeRevision():Flow<Revision?>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putRevision(revision:Revision)
}
@Database(entities=[StoredRecord::class,Revision::class],version=1,exportSchema=true)
abstract class HealthDatabase:RoomDatabase() { abstract fun dao():HealthDao }
