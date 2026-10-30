package az.healthchecker.data

import android.content.Context
import az.healthchecker.domain.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.*
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Fixed-width BIG_ENDIAN records. A Room checkpoint commits a prefix after fd.sync(). */
object TrackBinary {
    const val BYTES=32
    fun encode(p:TrackPoint):ByteArray = ByteBuffer.allocate(BYTES).putDouble(p.point.latitude).putDouble(p.point.longitude).putLong(p.timeMillis).putFloat(p.accuracyMeters.toFloat()).putInt(if(p.breakBefore)1 else 0).array()
    fun decode(bytes:ByteArray):TrackPoint {
        val b=ByteBuffer.wrap(bytes);val lat=b.double;val lon=b.double;val time=b.long;val accuracy=b.float;val flags=b.int
        require(flags in 0..1){"Invalid track flags"}
        return TrackPoint(GeoPoint(lat,lon),time,accuracy.toDouble(),flags==1).also(::validateTrackPoint)
    }
}
internal class ExploreStore(context:Context,name:String,val db:HealthDatabase) {
    companion object { private val locks=ConcurrentHashMap<String,Mutex>() }
    private val suffix=if(name=="healthchecker.db")"" else "-"+java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).take(8).joinToString(""){"%02x".format(it)}
    private val base=File(context.applicationContext.filesDir,"explore$suffix").apply{mkdirs()}
    val media=File(base,"media")
    val lock=locks.getOrPut(base.absolutePath){Mutex()}
    val replacement=ExploreMediaReplacement(base)
    val dao=db.exploreDao()
    private val json=Json{encodeDefaults=true}
    fun decode(rows:List<ExploreRecord>):ExploreSnapshot = ExploreSnapshot(
        rows.filter{it.kind=="finding"}.map{json.decodeFromString<Finding>(it.payload)},
        rows.filter{it.kind=="place"}.map{json.decodeFromString<SavedPlace>(it.payload)},
        rows.filter{it.kind=="walk"}.map{json.decodeFromString<Walk>(it.payload)})
    suspend fun snapshot()=decode(dao.all())
    suspend fun walk(id:String):Walk { requireExploreId(id);return dao.get("walk:$id")?.let{json.decodeFromString<Walk>(it.payload)}?:throw IllegalArgumentException("Walk unavailable") }
    suspend fun recover()=replacement.recover(db.dao().get("explore-generation")?.payload)
    suspend fun changed() { val r=db.dao().revision()?:Revision();db.dao().putRevision(r.copy(current=r.current+1)) }
    suspend fun put(f:Finding)=dao.put(ExploreRecord("finding:${f.id}","finding",json.encodeToString(f)))
    suspend fun put(p:SavedPlace)=dao.put(ExploreRecord("place:${p.id}","place",json.encodeToString(p)))
    suspend fun put(w:Walk)=dao.put(ExploreRecord("walk:${w.id}","walk",json.encodeToString(w)))
    suspend fun replace(s:ExploreSnapshot) { dao.clear();s.findings.forEach{put(it)};s.places.forEach{put(it)};s.walks.forEach{put(it)} }
    fun photo(name:String):File { requirePhotoName(name);return File(media,"photos/$name") }
    fun track(id:String):File { requireExploreId(id);return File(media,"tracks/$id.bin") }
    fun cleanOrphans(current:ExploreSnapshot) {
        val retained=files(current).values.map{it.absolutePath}.toSet()
        listOf(File(media,"photos"),File(media,"tracks")).forEach { dir ->
            dir.listFiles()?.filter{it.isFile&&it.absolutePath !in retained}?.forEach{it.delete()}
        }
    }
    fun files(s:ExploreSnapshot):Map<String,File> = buildMap {
        s.findings.mapNotNull{it.photoFile}.distinct().forEach{put("photos/$it",photo(it))}
        s.walks.filter{it.pointCount>0}.forEach{put("tracks/${it.id}.bin",track(it.id))}
    }
}
class ExploreRepository(context:Context,databaseName:String="healthchecker.db") {
    private val db=HealthDatabase.open(context,databaseName)
    private val store=ExploreStore(context,databaseName,db)
    private val dao=store.dao
    private suspend fun <T> edit(block:suspend ()->T):T=db.withTransaction{store.recover();block()}
    /** Archive and restore hold this lock while they use immutable file names. Commit rows before deleting files. */
    private suspend fun <T> editMedia(block:suspend ()->T):T=store.lock.withLock {
        val result=edit(block)
        edit{store.cleanOrphans(store.snapshot())}
        result
    }
    val snapshots:Flow<ExploreSnapshot> = dao.observe().map{edit{store.decode(it)}}
    suspend fun exportSnapshot():ExploreSnapshot=edit{store.snapshot()}
    suspend fun saveFinding(finding:Finding,photoJpeg:ByteArray?=null,removePhoto:Boolean=false):Finding=editMedia {
        val old=store.snapshot();val previous=old.findings.find{it.id==finding.id}
        var f=finding.copy(photoFile=if(removePhoto)null else finding.photoFile?:previous?.photoFile)
        if(photoJpeg!=null) {
            require(photoJpeg.size in 4..MAX_FINDING_PHOTO_BYTES&&photoJpeg[0]==(-1).toByte()&&photoJpeg[1]==(-40).toByte()&&photoJpeg[photoJpeg.lastIndex-1]==(-1).toByte()&&photoJpeg.last()==(-39).toByte()){"Invalid or oversized JPEG"}
            f=f.copy(photoFile="${UUID.randomUUID()}.jpg")
        }
        validateExplore(old.copy(findings=old.findings.filterNot{it.id==f.id}+f))
        f.photoFile?.let { name ->
            val target=store.photo(name)
            if(photoJpeg!=null) { target.parentFile!!.mkdirs();FileOutputStream(target).use{it.write(photoJpeg);it.fd.sync()} }
            else require(target.isFile){"Finding picture unavailable"}
        }
        store.put(f);store.changed();f
    }
    suspend fun deleteFinding(id:String)=editMedia { requireExploreId(id);dao.delete("finding:$id");store.changed() }
    fun photoFile(finding:Finding):File?=finding.photoFile?.let{store.photo(it).takeIf(File::isFile)}
    suspend fun savePlace(place:SavedPlace)=edit {
        val old=store.snapshot();validateExplore(old.copy(places=old.places.filterNot{it.id==place.id}+place));store.put(place);store.changed()
    }
    suspend fun deletePlace(id:String)=edit {
        requireExploreId(id);store.snapshot().findings.filter{f->f.offers.any{it.placeId==id}}.forEach{store.put(it.copy(offers=it.offers.filterNot{o->o.placeId==id}))}
        dao.delete("place:$id");store.changed()
    }
    suspend fun beginWalk(name:String="",now:Long=System.currentTimeMillis()):Walk=edit {
        val s=store.snapshot();s.walks.find{it.status==WalkStatus.ACTIVE||it.status==WalkStatus.PAUSED}?.let{return@edit it}
        val w=Walk(UUID.randomUUID().toString(),name,now);validateExplore(s.copy(walks=s.walks+w));store.put(w);store.changed();w
    }
    suspend fun resumeWalk(id:String):Walk=edit {
        val s=store.snapshot();val w=s.walks.first{it.id==id}
        if(w.status==WalkStatus.ACTIVE)return@edit w
        require(w.status==WalkStatus.PAUSED||w.status==WalkStatus.INTERRUPTED)
        require(s.walks.none{it.id!=id&&(it.status==WalkStatus.ACTIVE||it.status==WalkStatus.PAUSED)})
        val updated=w.copy(status=WalkStatus.ACTIVE,endedAt=null);store.put(updated);dao.put(ExploreRecord("break:$id","break","true"));store.changed();updated
    }
    suspend fun appendPoint(walkId:String,point:TrackPoint,durationMs:Long):Walk=edit {
        val w=store.walk(walkId);if(w.status!=WalkStatus.ACTIVE)return@edit w
        validateTrackPoint(point);require(durationMs>=0&&w.pointCount<MAX_TRACK_POINTS)
        if(w.lastPoint!=null&&point.timeMillis==w.lastPoint.timeMillis)return@edit w
        val clockReversed=w.lastPoint!=null&&point.timeMillis<w.lastPoint.timeMillis
        val p=point.copy(breakBefore=point.breakBefore||clockReversed||w.pointCount==0||dao.get("break:$walkId")!=null)
        val file=store.track(walkId);file.parentFile!!.mkdirs()
        RandomAccessFile(file,"rw").use { out ->
            val committed=w.pointCount.toLong()*TrackBinary.BYTES;require(out.length()>=committed){"Truncated committed track"}
            out.setLength(committed);out.seek(committed);out.write(TrackBinary.encode(p));out.fd.sync()
        }
        // Persist exactly the float value present on disk so the manifest's lastPoint is reproducible.
        val diskPoint=p.copy(accuracyMeters=p.accuracyMeters.toFloat().toDouble())
        val distance=if(p.breakBefore||w.lastPoint==null)0.0 else geoDistanceMeters(w.lastPoint.point,p.point)
        val next=w.copy(pointCount=w.pointCount+1,lastPoint=diskPoint,durationMs=maxOf(w.durationMs,durationMs),distanceMeters=w.distanceMeters+distance)
        store.put(next);dao.delete("break:$walkId");next
    }
    suspend fun updateWalk(id:String,status:WalkStatus,durationMs:Long,at:Long=System.currentTimeMillis()):Walk=edit {
        val w=store.walk(id);require(durationMs>=0)
        if(w.status==WalkStatus.FINISHED)return@edit w
        require(status!=WalkStatus.ACTIVE||w.status==WalkStatus.ACTIVE){"Use resumeWalk to resume"}
        val next=w.copy(status=status,durationMs=maxOf(durationMs,w.durationMs),endedAt=if(status==WalkStatus.FINISHED)maxOf(at,w.startedAt) else null)
        store.put(next);if(status!=WalkStatus.ACTIVE)dao.put(ExploreRecord("break:$id","break","true"));store.changed();next
    }
    suspend fun deleteWalk(id:String)=editMedia {
        val w=store.snapshot().walks.firstOrNull{it.id==id}?:return@editMedia
        require(w.status==WalkStatus.FINISHED||w.status==WalkStatus.INTERRUPTED){"Finish the walk before deleting"}
        dao.delete("walk:$id");dao.delete("break:$id");store.changed()
    }
    fun trackFile(walk:Walk):File=store.track(walk.id)
    fun forEachTrackPoint(walk:Walk,fromIndex:Int=0,block:(TrackPoint)->Unit) {
        require(fromIndex in 0..walk.pointCount);if(walk.pointCount==0)return
        RandomAccessFile(trackFile(walk),"r").use { input ->
            require(input.length()>=walk.pointCount.toLong()*TrackBinary.BYTES){"Truncated committed track"};input.seek(fromIndex.toLong()*TrackBinary.BYTES)
            val bytes=ByteArray(TrackBinary.BYTES);repeat(walk.pointCount-fromIndex){input.readFully(bytes);block(TrackBinary.decode(bytes))}
        }
    }
    fun readTrack(walk:Walk,maxPoints:Int=20000):List<TrackPoint> {
        require(maxPoints>=1)
        if(maxPoints==1) { val one=ArrayList<TrackPoint>();if(walk.pointCount>0)forEachTrackPoint(walk,walk.pointCount-1){one.add(it.copy(breakBefore=true))};return one }
        val stride=maxOf(1,kotlin.math.ceil(walk.pointCount.toDouble()/(maxPoints-1)).toInt())
        val result=ArrayList<TrackPoint>();var index=0;var pendingBreak=false
        forEachTrackPoint(walk) { p ->
            pendingBreak=pendingBreak||p.breakBefore
            if(index%stride==0||index==walk.pointCount-1){result.add(p.copy(breakBefore=pendingBreak));pendingBreak=false};index++
        };return result
    }
}





