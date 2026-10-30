package az.healthchecker.domain

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.*

@Serializable data class GeoPoint(val latitude:Double,val longitude:Double)
@Serializable data class TrackPoint(val point:GeoPoint,val timeMillis:Long,val accuracyMeters:Double,val breakBefore:Boolean=false)
@Serializable enum class FindingCategory { FOOD,CARE,OTHER }
@Serializable enum class PlaceKind { SHOP,INTEREST,OTHER }
@Serializable enum class WalkStatus { ACTIVE,PAUSED,FINISHED,INTERRUPTED }
@Serializable data class FindOffer(val placeId:String,val price:String="",val note:String="")
@Serializable data class Finding(val id:String,val name:String,val barcode:String?=null,val category:FindingCategory=FindingCategory.OTHER,val brand:String="",val notes:String="",val favorite:Boolean=false,val photoFile:String?=null,val offers:List<FindOffer> = emptyList(),val createdAt:Long=0,val updatedAt:Long=0)
@Serializable data class SavedPlace(val id:String,val name:String,val point:GeoPoint,val kind:PlaceKind=PlaceKind.SHOP,val note:String="",val createdAt:Long=0)
@Serializable data class Walk(val id:String,val name:String,val startedAt:Long,val endedAt:Long?=null,val durationMs:Long=0,val distanceMeters:Double=0.0,val pointCount:Int=0,val status:WalkStatus=WalkStatus.ACTIVE,val lastPoint:TrackPoint?=null)
@Serializable data class ExploreSnapshot(val findings:List<Finding> = emptyList(),val places:List<SavedPlace> = emptyList(),val walks:List<Walk> = emptyList())
const val MAX_TRACK_POINTS=2_000_000
const val MAX_FINDING_PHOTO_BYTES=180*1024
fun requireExploreId(id:String) { require(UUID.fromString(id).toString()==id){"Invalid explore identity"} }
fun validateGeoPoint(point:GeoPoint) { require(point.latitude.isFinite()&&point.latitude in -90.0..90.0&&point.longitude.isFinite()&&point.longitude in -180.0..180.0){"Invalid coordinates"} }
fun validateTrackPoint(point:TrackPoint) { validateGeoPoint(point.point);require(point.timeMillis>=0&&point.accuracyMeters.isFinite()&&point.accuracyMeters in 0.0..100000.0) }
fun requirePhotoName(name:String) { require(name.endsWith(".jpg"));requireExploreId(name.removeSuffix(".jpg")) }
fun geoDistanceMeters(a:GeoPoint,b:GeoPoint):Double {
    val dLat=Math.toRadians(b.latitude-a.latitude);val dLon=Math.toRadians(b.longitude-a.longitude)
    val h=sin(dLat/2).pow(2)+cos(Math.toRadians(a.latitude))*cos(Math.toRadians(b.latitude))*sin(dLon/2).pow(2)
    return 6371000.0*2*asin(sqrt(h.coerceIn(0.0,1.0)))
}
fun validateExplore(snapshot:ExploreSnapshot) {
    require(snapshot.findings.size<=20000&&snapshot.places.size<=20000&&snapshot.walks.size<=20000)
    fun ids(values:List<String>) { values.forEach(::requireExploreId);require(values.distinct().size==values.size) }
    ids(snapshot.findings.map{it.id});ids(snapshot.places.map{it.id});ids(snapshot.walks.map{it.id})
    val places=snapshot.places.map{it.id}.toSet()
    snapshot.findings.forEach { f ->
        require(f.name.isNotBlank()&&f.name.length<=200&&f.brand.length<=500&&f.notes.length<=10000&&f.createdAt>=0&&f.updatedAt>=0)
        require(f.barcode==null||normalizeBarcode(f.barcode)==f.barcode);f.photoFile?.let(::requirePhotoName)
        require(f.offers.size<=500&&f.offers.map{it.placeId}.distinct().size==f.offers.size)
        f.offers.forEach{require(it.placeId in places&&it.price.length<=500&&it.note.length<=2000)}
    }
    snapshot.places.forEach{require(it.name.isNotBlank()&&it.name.length<=200&&it.note.length<=10000&&it.createdAt>=0);validateGeoPoint(it.point)}
    snapshot.walks.forEach { w ->
        require(w.name.length<=200&&w.startedAt>=0&&w.durationMs>=0&&w.distanceMeters.isFinite()&&w.distanceMeters>=0&&w.pointCount in 0..MAX_TRACK_POINTS)
        require(w.endedAt==null||w.endedAt>=w.startedAt);require((w.pointCount==0)==(w.lastPoint==null));w.lastPoint?.let(::validateTrackPoint)
        require(w.status!=WalkStatus.FINISHED||w.endedAt!=null)
    }
    require(snapshot.walks.count{it.status==WalkStatus.ACTIVE||it.status==WalkStatus.PAUSED}<=1)
}
