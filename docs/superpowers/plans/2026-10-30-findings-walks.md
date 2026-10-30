# Findings and walks1.16.2 implementation plan

**Goal:** Private findings gallery, pinned places, offline Azerbaijan map and opt-in walking history.
**Architecture:** Compose explore module; Mapsforge local map; GNSS foreground service; Roomv2 explore metadata, file-backed JPEG/track data; streaming verified ZIP backups with legacy JSON restore.
**Spec:** docs/superpowers/specs/2026-10-30-findings-walks-design.md
**Execution:** Separate file ownership for persistence/backup and map/GPS; root integrates UI and tests. Reuse the clean checkout on codex/1-16-2-explore. Only root runs Gradle/emulator, no concurrent builds.

## Contracts (package az.healthchecker.domain unless noted)
Data classes are Serializable; optional fields have defaults. UUID strings for all identities.
- GeoPoint(latitude:Double,longitude:Double).
- TrackPoint(point:GeoPoint,timeMillis:Long,accuracyMeters:Double,breakBefore:Boolean=false).
- FindingCategory FOOD,CARE,OTHER; PlaceKind SHOP,INTEREST,OTHER; WalkStatus ACTIVE,PAUSED,FINISHED,INTERRUPTED.
- FindOffer(placeId:String,price:String="",note:String="").
- Finding(id:String,name:String,barcode:String?=null,category:FindingCategory=OTHER,brand:String="",notes:String="",favorite:Boolean=false,photoFile:String?=null,offers:List<FindOffer>=emptyList(),createdAt:Long=0,updatedAt:Long=0).
- SavedPlace(id:String,name:String,point:GeoPoint,kind:PlaceKind=SHOP,note:String="",createdAt:Long=0).
- Walk(id:String,name:String,startedAt:Long,endedAt:Long?=null,durationMs:Long=0,distanceMeters:Double=0.0,pointCount:Int=0,status:WalkStatus=ACTIVE,lastPoint:TrackPoint?=null).
- ExploreSnapshot(findings:List<Finding>=emptyList(),places:List<SavedPlace>=emptyList(),walks:List<Walk>=emptyList()).
- geoDistanceMeters(a:GeoPoint,b:GeoPoint):Double; validateExplore(snapshot:ExploreSnapshot).

data.ExploreRepository(context:Context,databaseName:String="healthchecker.db"):
- val snapshots:Flow<ExploreSnapshot>; suspend exportSnapshot():ExploreSnapshot.
- suspend saveFinding(finding:Finding,photoJpeg:ByteArray?=null,removePhoto:Boolean=false):Finding; deleteFinding(id:String); photoFile(finding:Finding):File?.
- suspend savePlace(place:SavedPlace); deletePlace(id:String) (unlink offers, keep findings).
- suspend beginWalk(name:String="",now:Long=System.currentTimeMillis()):Walk; resumeWalk(id:String):Walk.
- suspend appendPoint(walkId:String,point:TrackPoint,durationMs:Long):Walk (ignore inactive stale writes; distance uses breakBefore).
- suspend updateWalk(id:String,status:WalkStatus,durationMs:Long,at:Long=System.currentTimeMillis()):Walk; deleteWalk(id:String).
- readTrack(walk:Walk,maxPoints:Int=20000):List<TrackPoint>; forEachTrackPoint(walk:Walk,fromIndex:Int=0,block:(TrackPoint)->Unit) (blocking IO, committed prefix only).
- media path helpers for backup implemented by persistence owner; root UI never assembles unsafe paths.

explore.WalkTracking:
- val state:StateFlow<WalkRecordingState> (walkId:String?,latestPoint:TrackPoint?,durationMs:Long,waitingForFix:Boolean,message:String?).
- suspend start(context:Context,walkId:String?=null):String; pause(context),finish(context),stopForRestore(context).
- suspend currentPosition(context:Context):GeoPoint (permission already granted, bounded GPS wait; no network).

ui.explore.OfflineExploreMap(repo:ExploreRepository,state:ExploreSnapshot,current:TrackPoint?,focus:GeoPoint?,selectedWalkId:String?,onPlaceClick:(SavedPlace)->Unit,onLongPress:(GeoPoint)->Unit,modifier:Modifier=Modifier) composable. Map owner defines WalkRecordingState in az.healthchecker.explore. Root owns all other UI and runtime permission prompts.

## Tasks and ownership
- [x] Persistence/backup owner: write domain/repository/backup tests first, notify root, wait for red-run signal. Implement domain/contracts, Roomv2 migration/open factory, ExploreRepository/media records, ZIP backup and validated atomic restore. Own HealthDatabase,HealthRepository,UserSnapshot,Validation,BackupCodec,backup/*,AppViewModel backup wiring; no HealthApp edits. Expose UserSnapshot.explore:ExploreSnapshot? defaultnull (EncodeDefaultNever), existing restorePreview UserSnapshot? stays. Tests migration/food separation/cascades/committed binary prefix/backup hashes/legacy JSON/failed restore preservation.
- [x] Map/GPS owner: write pure GPS filtering tests first, notify root, wait for red-run signal. Implement Mapsforge0.25.0/offline renderer/map asset installer, walked-area overlay cache, WalkTracking+foreground service. Own explore/*,ui/explore/OfflineExploreMap.kt,app/build.gradle.kts dependency additions,manifest permissions/service only. No backend/core/UI files. Root provides map assets. Test bad fixes/gaps/pause segments/distance and service cancellation where meaningful.
- [x] Root: pin map source/hash, write gallery/place/walk Compose screens, ExploreViewModel, HealthApp5-tab navigation preserving profile id3, Training entry point if useful; runtime location permission prompts, image import and barcode lookup. No backend or map-owner edits during parallel work. Write native flow tests and integration checks.
- [x] Root: whole-change review, Python/JVM/lint/native tests with simulated GPS/permissions/offline/recreation; actual release upgrade from16.1, photos/places/routes backup restore. Fix regressions before completion.
- [x] Root: build full signed APK versionName1.16.2/code18, document proof and outstanding physical-GPS limits. Leave external publication for an explicit GitHub request.

## Review focus
- No INTERNET permission or map-style network URLs; all map labels/styles local, OSM attribution visible.
- GPS consent/start visible; foreground notification/stop works with locked screen; no hidden automatic tracking.
- Pause/lost GNSS never bridge distance/visited cells; only recorded locations marked.
- Non-food finding never enters calorie totals; duplicate SKU opens saved card; deleting a place keeps finding.
- Storage never loads full gallery/tracks into RAM; corrupted/traversal ZIP and process death cannot erase old data.
- All original JSON backups, signed upgrade and existing navigation remain functional.
