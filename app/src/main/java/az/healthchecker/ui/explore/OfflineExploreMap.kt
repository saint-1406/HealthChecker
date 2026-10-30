package az.healthchecker.ui.explore

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import android.view.ViewOutlineProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import az.healthchecker.data.ExploreRepository
import az.healthchecker.domain.*
import az.healthchecker.explore.OfflineMapInstaller
import az.healthchecker.explore.OfflineMapManifest
import az.healthchecker.explore.VisitedCells
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.model.*
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import kotlin.math.*

/** All base tiles, symbols and fonts are local. Opening this view never requests GPS. */
@Composable
fun OfflineExploreMap(
    repo: ExploreRepository,
    state: ExploreSnapshot,
    current: TrackPoint?,
    focus: GeoPoint?,
    selectedWalkId: String?,
    onPlaceClick: (SavedPlace) -> Unit,
    onLongPress: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext
    var mapFile by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var map by remember { mutableStateOf<MapView?>(null) }
    val cache = remember(repo) { VisitedCache() }
    val trackCache = remember(repo) { DisplayTrackCache() }
    val updatedPlaceClick by rememberUpdatedState(onPlaceClick)
    val updatedLongPress by rememberUpdatedState(onLongPress)
    val overlay = remember { ExploreOverlay(context.resources.displayMetrics.density, { updatedPlaceClick(it) }, { updatedLongPress(it) }) }
    var renderData by remember { mutableStateOf(RenderData()) }
    val walkKeys = state.walks

    LaunchedEffect(app) {
        try {
            mapFile = withContext(Dispatchers.IO) {
                val manifest = app.assets.open("maps/azerbaijan.json").bufferedReader().use { Json.decodeFromString<OfflineMapManifest>(it.readText()) }
                OfflineMapInstaller.install(File(app.noBackupFilesDir, "offline-map"), manifest) { app.assets.open(it) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: tr("Не удалось подготовить карту", "Could not prepare the map") }
    }
    LaunchedEffect(repo, walkKeys, selectedWalkId) {
        try {
            renderData = withContext(Dispatchers.IO) {
                val cells = cache.update(repo, state.walks)
                val selected = state.walks.firstOrNull { it.id == selectedWalkId }
                    ?: state.walks.firstOrNull { it.status == WalkStatus.ACTIVE || it.status == WalkStatus.PAUSED }
                RenderData(cells, selected?.let { trackCache.update(repo, it) } ?: emptyList())
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = tr("Не удалось прочитать прогулку: ", "Could not read walk: ") + failure.message }
    }
    LaunchedEffect(map, focus, selectedWalkId) {
        val target = focus ?: state.walks.firstOrNull { it.id == selectedWalkId }?.lastPoint?.point
        if (target != null) map?.setCenter(LatLong(target.latitude.coerceIn(-85.0, 85.0), target.longitude))
    }
    DisposableEffect(Unit) {
        onDispose { map?.destroyAll(); map = null }
    }

    Box(modifier.clipToBounds().background(Color(0xffe8eee7))) {
        if (mapFile != null) AndroidView(
            modifier = Modifier.fillMaxSize().clipToBounds().testTag("offlineMapReady"),
            factory = { ctx ->
                AndroidGraphicFactory.createInstance(app as Application)
                MapView(ctx).apply {
                    // Mapsforge paints an overdraw buffer; native views need an explicit clip in Compose.
                    outlineProvider = ViewOutlineProvider.BOUNDS
                    clipToOutline = true
                    setBuiltInZoomControls(true)
                    setZoomLevelMin(5.toByte()); setZoomLevelMax(19.toByte())
                    setCenter(LatLong((focus?.latitude ?: 40.4093).coerceIn(-85.0, 85.0), focus?.longitude ?: 49.8671))
                    setZoomLevel(14.toByte())
                    val tileCache = AndroidUtil.createTileCache(ctx, "explore-map", model.displayModel.tileSize, 1f, model.frameBufferModel.overdrawFactor)
                    try {
                        val renderer = TileRendererLayer(tileCache, MapFile(mapFile!!), model.mapViewPosition, AndroidGraphicFactory.INSTANCE)
                        renderer.setXmlRenderTheme(MapsforgeThemes.DEFAULT)
                        layerManager.layers.add(renderer)
                        layerManager.layers.add(overlay)
                    } catch (failure: Exception) { tileCache.destroy(); error = failure.message }
                    map = this
                }
            },
            update = { view ->
                overlay.places = state.places
                overlay.current = current
                overlay.data = renderData
                view.layerManager.redrawLayers()
            }
        )
        else Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (error == null) CircularProgressIndicator()
            Text(error ?: tr("Подготовка карты Азербайджана…", "Preparing the Azerbaijan map…"))
        }
        if (mapFile != null && error != null) Text(error!!, Modifier.align(Alignment.TopCenter).background(MaterialTheme.colorScheme.errorContainer).padding(8.dp))
        Text(
            "© OpenStreetMap contributors · ODbL · Mapsforge\n" + tr("Цвет: записанные посещения; без цвета — нет записей", "Color: recorded visits; uncolored: no records"),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)).padding(4.dp)
        )
    }
}

private data class RenderData(val cells: List<Pair<Double, Double>> = emptyList(), val track: List<TrackPoint> = emptyList())

/** Keep a bounded display polyline and append new committed fixes rather than rereading an active walk. */
private class DisplayTrackCache {
    private var id: String? = null
    private var count = 0
    private var lastPoint: TrackPoint? = null
    private var points = ArrayList<TrackPoint>()
    @Synchronized fun update(repo: ExploreRepository, walk: Walk): List<TrackPoint> {
        if (id != walk.id || count > walk.pointCount || (count == walk.pointCount && lastPoint != walk.lastPoint)) {
            points = ArrayList(repo.readTrack(walk, 20_000))
            id = walk.id; count = walk.pointCount
        } else if (count < walk.pointCount) {
            repo.forEachTrackPoint(walk, count) { point ->
                points.add(point); count++
                if (points.size > 20_000) {
                    val thinned = ArrayList<TrackPoint>(10_002)
                    var pendingBreak = false
                    for ((index, p) in points.withIndex()) {
                        pendingBreak = pendingBreak || p.breakBefore
                        if (index % 2 == 0 || index == points.lastIndex) { thinned.add(p.copy(breakBefore = pendingBreak)); pendingBreak = false }
                    }
                    points = thinned
                }
            }
        }
        lastPoint = walk.lastPoint
        return points.toList()
    }
}

/** Completed walks are scanned once. New fixes stream only their newly committed suffix. */
private class VisitedCache {
    private data class Entry(var count: Int = 0, var lastPoint: TrackPoint? = null, val cells: VisitedCells = VisitedCells())
    private val walks = HashMap<String, Entry>()
    @Synchronized fun update(repo: ExploreRepository, current: List<Walk>): List<Pair<Double, Double>> {
        walks.keys.retainAll(current.map { it.id }.toSet())
        for (walk in current) {
            var entry = walks.getOrPut(walk.id) { Entry() }
            if (entry.count > walk.pointCount || (entry.count == walk.pointCount && entry.lastPoint != walk.lastPoint)) { entry = Entry(); walks[walk.id] = entry }
            if (entry.count < walk.pointCount) {
                repo.forEachTrackPoint(walk, entry.count) { point ->
                    entry.cells.add(point.point.latitude, point.point.longitude, point.breakBefore)
                    entry.count++
                }
            }
            entry.lastPoint = walk.lastPoint
        }
        // Distinct cells are the overlay, never a retained list of all historic GPS points.
        return walks.values.asSequence().flatMap { it.cells.centers() }.distinct().toList()
    }
}

private class ExploreOverlay(val density: Float, val onPlace: (SavedPlace) -> Unit, val onLong: (GeoPoint) -> Unit) : Layer() {
    @Volatile var places: List<SavedPlace> = emptyList()
    @Volatile var current: TrackPoint? = null
    @Volatile var data = RenderData()
    private val visited = AndroidGraphicFactory.INSTANCE.createPaint().apply { setColor(0x48439d6b) }
    private val path = AndroidGraphicFactory.INSTANCE.createPaint().apply { setColor(0xff176fbb.toInt()); setStrokeWidth(3f * density) }
    private val pin = AndroidGraphicFactory.INSTANCE.createPaint().apply { setColor(0xffd56b31.toInt()) }
    private val locationPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply { setColor(0xff174ddb.toInt()) }

    override fun draw(bounds: BoundingBox, zoom: Byte, canvas: Canvas, topLeft: Point, rotation: Rotation) {
        lastZoom = zoom
        val mapSize = MercatorProjection.getMapSize(zoom, displayModel.tileSize)
        fun xy(lat: Double, lon: Double) = Point(MercatorProjection.longitudeToPixelX(lon, mapSize) - topLeft.x,
            MercatorProjection.latitudeToPixelY(lat.coerceIn(-85.0, 85.0), mapSize) - topLeft.y)
        val local = data
        val cellPath = AndroidGraphicFactory.INSTANCE.createPath()
        for ((lat, lon) in local.cells) {
            if (!bounds.contains(lat, lon)) continue
            val p = xy(lat, lon)
            val pixelRadius = 25.0 / (2 * Math.PI * 6_378_137 / mapSize)
            if (pixelRadius < 0.5) continue
            val radius = pixelRadius.toFloat()
            val x = p.x.toFloat(); val y = p.y.toFloat()
            cellPath.moveTo(x - radius, y - radius)
            cellPath.lineTo(x + radius, y - radius)
            cellPath.lineTo(x + radius, y + radius)
            cellPath.lineTo(x - radius, y + radius)
            cellPath.close()
        }
        if (!cellPath.isEmpty) canvas.drawPath(cellPath, visited)
        var previous: TrackPoint? = null
        for (point in local.track) {
            val old = previous
            if (old != null && !point.breakBefore) {
                val a = xy(old.point.latitude, old.point.longitude); val b = xy(point.point.latitude, point.point.longitude)
                canvas.drawLine(a.x.roundToInt(), a.y.roundToInt(), b.x.roundToInt(), b.y.roundToInt(), path)
            }
            previous = point
        }
        for (place in places) {
            if (!bounds.contains(place.point.latitude, place.point.longitude)) continue
            val p = xy(place.point.latitude, place.point.longitude)
            canvas.drawCircle(p.x.roundToInt(), p.y.roundToInt(), (8 * density).roundToInt(), pin)
        }
        current?.let {
            val p = xy(it.point.latitude, it.point.longitude)
            canvas.drawCircle(p.x.roundToInt(), p.y.roundToInt(), (7 * density).roundToInt(), locationPaint)
        }
    }

    override fun onLongPress(tapLatLong: LatLong, layerXY: Point?, tapXY: Point): Boolean {
        onLong(GeoPoint(tapLatLong.latitude, tapLatLong.longitude)); return true
    }

    override fun onTap(tapLatLong: LatLong, layerXY: Point?, tapXY: Point): Boolean {
        // Geographic distance avoids framebuffer-overdraw offsets and respects the current zoom.
        val nearest = places.minByOrNull { geoDistanceMeters(it.point, GeoPoint(tapLatLong.latitude, tapLatLong.longitude)) } ?: return false
        val metersPerPixel = cos(Math.toRadians(tapLatLong.latitude)) * 2 * Math.PI * 6_378_137 /
            MercatorProjection.getMapSize(lastZoom, displayModel.tileSize)
        if (geoDistanceMeters(nearest.point, GeoPoint(tapLatLong.latitude, tapLatLong.longitude)) <= metersPerPixel * 32 * density) {
            onPlace(nearest); return true
        }
        return false
    }
    @Volatile private var lastZoom: Byte = 14
}
