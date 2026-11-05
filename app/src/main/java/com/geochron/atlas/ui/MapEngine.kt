package com.geochron.atlas.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.geochron.atlas.data.GeoFeature
import com.geochron.atlas.data.MapGeometry
import com.geochron.atlas.data.RegionGeom
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

data class FitParams(val scale: Float, val originX: Float, val originY: Float, val cosMid: Float)

fun computeFit(geo: MapGeometry, w: Float, h: Float, pad: Float = 8f): FitParams {
    val midLat = (geo.minLat + geo.maxLat) / 2.0
    val cosMid = cos(Math.toRadians(midLat)).toFloat().coerceIn(0.25f, 1f)
    val effW = (geo.widthDeg * cosMid).toFloat()
    val effH = geo.heightDeg.toFloat()
    val scale = min((w - 2 * pad) / effW, (h - 2 * pad) / effH)
    return FitParams(scale, (w - effW * scale) / 2f, (h - effH * scale) / 2f, cosMid)
}

fun lonToX(geo: MapGeometry, fit: FitParams, lon: Double): Float =
    fit.originX + ((lon - geo.minLon) * fit.cosMid * fit.scale).toFloat()

fun latToY(geo: MapGeometry, fit: FitParams, lat: Double): Float =
    fit.originY + ((geo.maxLat - lat) * fit.scale).toFloat()

fun buildRegionPaths(geo: MapGeometry, fit: FitParams): Pair<Map<String, Path>, Map<String, Rect>> {
    val paths = LinkedHashMap<String, Path>()
    val boxes = HashMap<String, Rect>()
    for (region in geo.regions) {
        val path = Path()
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (poly in region.polys) for (ring in poly) {
            var i = 0
            while (i + 1 < ring.size) {
                val x = lonToX(geo, fit, ring[i].toDouble())
                val y = latToY(geo, fit, ring[i + 1].toDouble())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                i += 2
            }
            path.close()
        }
        paths[region.name] = path
        boxes[region.name] = Rect(minX, minY, maxX, maxY)
    }
    return paths to boxes
}

data class EmpireLayer(
    val datasetId: String,
    val name: String,
    val color: Color,
    val poly: List<Pair<Double, Double>>,
    val periodLabel: String = "",
    val expansionFactor: Float = 1f,
    val isSelected: Boolean = false
) {
    val centroid: Pair<Double, Double> by lazy {
        if (poly.isEmpty()) 0.0 to 0.0
        else {
            val sx = poly.sumOf { it.first }
            val sy = poly.sumOf { it.second }
            (sx / poly.size) to (sy / poly.size)
        }
    }

    val scaledPoly: List<Pair<Double, Double>> by lazy {
        if (expansionFactor >= 0.99f || poly.isEmpty()) poly
        else {
            val (cx, cy) = centroid
            val f = expansionFactor.coerceIn(0.45f, 1.0f).toDouble()
            poly.map { (lon, lat) ->
                (cx + (lon - cx) * f) to (cy + (lat - cy) * f)
            }
        }
    }
}

data class MapFocus(val lon: Double, val lat: Double, val zoom: Float, val signal: Int)

fun categoryColor(cat: String): Color = when (cat) {
    "RIVER" -> Color(0xFF0288D1)
    "MOUNTAIN" -> Color(0xFF795548)
    "DESERT" -> Color(0xFFE0A526)
    "LAKE" -> Color(0xFF039BE5)
    "WATERFALL", "FALLS" -> Color(0xFF00ACC1)
    "PEAK" -> Color(0xFF5D4037)
    "PASS" -> Color(0xFFFB8C00)
    "DAM" -> Color(0xFF3949AB)
    "PARK" -> Color(0xFF2E7D32)
    "CITY" -> Color(0xFFC62828)
    "OCEAN" -> Color(0xFF1565C0)
    "STRAIT" -> Color(0xFF00838F)
    "VOLCANO" -> Color(0xFFD84315)
    "POWER" -> Color(0xFF8E24AA)
    "ISLAND" -> Color(0xFF00897B)
    "SITE" -> Color(0xFF4E342E)
    "EVENT" -> Color(0xFFD84315)
    else -> Color(0xFF6A1B9A)
}

private data class PartialLineResult(
    val path: Path,
    val startPoint: Offset,
    val tipPoint: Offset,
    val progress: Float
)

/**
 * Builds a Path for a polyline from 0% up to [progress] (0f..1f) of its total length,
 * returning the start point (origin) and current flowing tip point.
 */
private fun buildPartialLinePath(
    geo: MapGeometry,
    fit: FitParams,
    coords: List<Pair<Double, Double>>,
    progress: Float
): PartialLineResult? {
    if (coords.isEmpty()) return null
    val pts = coords.map { (lon, lat) -> Offset(lonToX(geo, fit, lon), latToY(geo, fit, lat)) }
    val start = pts.first()
    val p = progress.coerceIn(0f, 1f)
    val path = Path()
    path.moveTo(start.x, start.y)
    if (pts.size == 1 || p <= 0.001f) {
        return PartialLineResult(path, start, start, p)
    }
    if (p >= 0.999f) {
        for (i in 1 until pts.size) {
            path.lineTo(pts[i].x, pts[i].y)
        }
        return PartialLineResult(path, start, pts.last(), 1f)
    }

    val segLens = FloatArray(pts.size - 1)
    var totalLen = 0f
    for (i in 0 until pts.size - 1) {
        val d = hypot(pts[i + 1].x - pts[i].x, pts[i + 1].y - pts[i].y)
        segLens[i] = d
        totalLen += d
    }
    if (totalLen <= 0.001f) return PartialLineResult(path, start, pts.last(), p)

    val targetLen = totalLen * p
    var acc = 0f
    var tip = start
    for (i in 0 until pts.size - 1) {
        val seg = segLens[i]
        if (acc + seg <= targetLen) {
            path.lineTo(pts[i + 1].x, pts[i + 1].y)
            tip = pts[i + 1]
            acc += seg
        } else {
            val rem = targetLen - acc
            val frac = if (seg > 0f) (rem / seg).coerceIn(0f, 1f) else 0f
            val tx = pts[i].x + (pts[i + 1].x - pts[i].x) * frac
            val ty = pts[i].y + (pts[i + 1].y - pts[i].y) * frac
            tip = Offset(tx, ty)
            path.lineTo(tx, ty)
            break
        }
    }
    return PartialLineResult(path, start, tip, p)
}

private fun clampOffset(offset: Offset, zoom: Float, size: IntSize): Offset {
    if (size.width <= 0 || size.height <= 0) return offset
    if (zoom <= 1.01f) return Offset.Zero
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val minX = w - w * zoom - w * 0.25f
    val maxX = w * 0.25f
    val minY = h - h * zoom - h * 0.25f
    val maxY = h * 0.25f
    return Offset(
        x = offset.x.coerceIn(minX, maxX),
        y = offset.y.coerceIn(minY, maxY)
    )
}

private fun ringContainsPairs(ring: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val xi = ring[i].first; val yi = ring[i].second
        val xj = ring[j].first; val yj = ring[j].second
        if ((yi > y) != (yj > y)) {
            val xInt = (xj - xi) * (y - yi) / (yj - yi) + xi
            if (x < xInt) inside = !inside
        }
        j = i
    }
    return inside
}

private fun distToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax; val dy = by - ay
    val len2 = dx * dx + dy * dy
    val tt = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
    val cx = ax + tt * dx; val cy = ay + tt * dy
    return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
}

@Composable
fun AtlasMap(
    geo: MapGeometry,
    dark: Boolean,
    features: List<GeoFeature>,
    empireLayers: List<EmpireLayer> = emptyList(),
    highlightRegion: String? = null,
    selectedFeatureId: String? = null,
    regionLabels: Map<String, String> = emptyMap(),
    focus: MapFocus? = null,
    interactive: Boolean = true,
    hindi: Boolean = true,
    showFeatureLabels: Boolean = false,
    historicalMode: Boolean = false,
    riverProgressMap: Map<String, Float> = emptyMap(),
    activeAnimatingRiverId: String? = null,
    isFullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null,
    controlsBottomPadding: Dp = 44.dp,
    onRegionTap: (RegionGeom) -> Unit = {},
    onFeatureTap: (GeoFeature) -> Unit = {},
    onEmpireTap: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(geo) { mutableFloatStateOf(1f) }
    var offset by remember(geo) { mutableStateOf(Offset.Zero) }

    val fit = remember(geo, size) { if (size.width == 0) null else computeFit(geo, size.width.toFloat(), size.height.toFloat()) }
    val built = remember(geo, fit) { if (fit == null) null else buildRegionPaths(geo, fit) }

    LaunchedEffect(focus?.signal) {
        val f = focus ?: return@LaunchedEffect
        val ft = fit ?: return@LaunchedEffect
        zoom = f.zoom.coerceIn(1f, 12f)
        if (zoom <= 1.01f) {
            offset = Offset.Zero
        } else {
            val bx = lonToX(geo, ft, f.lon)
            val by = latToY(geo, ft, f.lat)
            offset = clampOffset(
                Offset(size.width / 2f - bx * zoom, size.height / 2f - by * zoom),
                zoom,
                size
            )
        }
    }

    val gestureModifier = if (interactive) {
        Modifier.pointerInput(geo, fit, features, empireLayers) {
            val slop = viewConfiguration.touchSlop
            awaitPointerEventScope {
                while (true) {
                    val downEvent = awaitPointerEvent(PointerEventPass.Main)
                    val down = downEvent.changes.firstOrNull { it.pressed } ?: continue
                    var totalMove = 0f
                    var pinching = false
                    var prevDist = 0f
                    var prevCentroid = Offset.Zero
                    var up = false
                    while (!up) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            up = true
                        } else if (pressed.size >= 2) {
                            pinching = true
                            val a = pressed[0].position; val b = pressed[1].position
                            val centroid = (a + b) / 2f
                            val dist = (a - b).getDistance()
                            if (prevDist > 0f) {
                                val factor = (dist / prevDist).coerceIn(0.8f, 1.25f)
                                val newZoom = (zoom * factor).coerceIn(1f, 12f)
                                val rawOffset = centroid - (centroid - offset) * (newZoom / zoom) + (centroid - prevCentroid)
                                zoom = newZoom
                                offset = clampOffset(rawOffset, zoom, size)
                            }
                            prevDist = dist; prevCentroid = centroid
                            event.changes.forEach { it.consume() }
                        } else {
                            val ch = pressed[0]
                            val delta = ch.position - ch.previousPosition
                            totalMove += delta.getDistance()
                            if (pinching || totalMove > slop) {
                                if (!pinching) {
                                    offset = clampOffset(offset + delta, zoom, size)
                                    ch.consume()
                                }
                            }
                        }
                        if (event.changes.all { !it.pressed }) up = true
                    }
                    if (!pinching && totalMove <= slop && fit != null) {
                        val base = (down.position - offset) / zoom
                        val lon = geo.minLon + (base.x - fit.originX) / (fit.scale * fit.cosMid)
                        val lat = geo.maxLat - (base.y - fit.originY) / fit.scale
                        handleTap(geo, features, empireLayers, lon, lat, onFeatureTap, onEmpireTap, onRegionTap)
                    }
                }
            }
        }
    } else Modifier

    Box(modifier = modifier.clipToBounds()) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .clipToBounds()
                .onSizeChanged {
                    size = it
                    offset = clampOffset(offset, zoom, it)
                }
                .then(gestureModifier)
        ) {
            // Ocean background
            drawRect(
                brush = Brush.verticalGradient(
                    if (dark) listOf(Color(0xFF0B1C2C), Color(0xFF122C44))
                    else if (historicalMode) listOf(Color(0xFFA8D5EA), Color(0xFF88C0DC))
                    else listOf(Color(0xFFCDEBF8), Color(0xFF9ED2EE))
                ),
                size = this.size
            )
            val f = fit ?: return@Canvas
            val (paths, boxes) = built ?: return@Canvas
            val tints = if (dark) REGION_TINTS_DARK else REGION_TINTS
            withTransform({
                translate(offset.x, offset.y)
                scale(zoom, zoom, Offset.Zero)
            }) {
                // Regions (states / countries)
                var idx = 0
                for ((name, path) in paths) {
                    val fill = when {
                        name == highlightRegion -> Color(0xFFE53935)
                        historicalMode -> if (dark) Color(0xFF22303F) else Color(0xFFF2EAD9)
                        else -> tints[idx % tints.size]
                    }
                    drawPath(path, color = fill, style = Fill)
                    idx++
                }
                for ((_, path) in paths) {
                    val borderColor = when {
                        historicalMode && dark -> Color(0xFF33475B).copy(alpha = 0.55f)
                        historicalMode -> Color(0xFFC8BCA6).copy(alpha = 0.65f)
                        dark -> Color(0xFF0E1621)
                        else -> Color.White
                    }
                    drawPath(path, color = borderColor, style = Stroke(width = (if (historicalMode) 0.8f else 1.1f) / zoom))
                }
                // Empire overlays (animated polygons + inner border)
                for (layer in empireLayers) {
                    val pts = layer.scaledPoly
                    if (pts.isEmpty()) continue
                    val path = Path()
                    pts.forEachIndexed { i, (lon, lat) ->
                        val x = lonToX(geo, f, lon); val y = latToY(geo, f, lat)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    val fillAlpha = if (layer.isSelected) 0.68f else 0.54f
                    drawPath(path, color = layer.color.copy(alpha = fillAlpha), style = Fill)
                    drawPath(
                        path,
                        color = if (layer.isSelected) Color.White else layer.color,
                        style = Stroke(width = (if (layer.isSelected) 3.4f else 2.0f) / zoom)
                    )
                    if (layer.isSelected) {
                        drawPath(
                            path,
                            color = layer.color,
                            style = Stroke(width = 1.8f / zoom)
                        )
                    }
                }

                val flowingRiverTips = ArrayList<Pair<GeoFeature, PartialLineResult>>()

                // Lines & area features
                for (feature in features) {
                    when (feature.geomType) {
                        "line" -> {
                            val isRiver = feature.cat == "RIVER"
                            val lineProg = if (isRiver && riverProgressMap.isNotEmpty()) {
                                riverProgressMap[feature.id] ?: 1f
                            } else 1f
                            if (lineProg <= 0.002f) continue

                            val partial = buildPartialLinePath(geo, f, feature.coords, lineProg) ?: continue
                            val isSelLine = feature.id == selectedFeatureId || feature.id == activeAnimatingRiverId
                            val baseColor = if (isSelLine) Color(0xFFFF6D00) else categoryColor(feature.cat)

                            // If it's a river, draw a soft water glow behind the channel
                            if (isRiver) {
                                drawPath(
                                    partial.path,
                                    color = (if (isSelLine) Color(0xFFFFAB40) else Color(0xFF29B6F6)).copy(alpha = 0.42f),
                                    style = Stroke(
                                        width = (if (isSelLine) 10.5f else 7.2f) / zoom,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            drawPath(
                                partial.path,
                                color = baseColor,
                                style = Stroke(
                                    width = (if (isSelLine) 5.5f else if (feature.cat == "MOUNTAIN") 5f else 3.6f) / zoom,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            if (isRiver && (showFeatureLabels || riverProgressMap.isNotEmpty() || isSelLine)) {
                                // Origin dot (उद्गम स्थल)
                                val originR = 4.8f / zoom
                                drawCircle(Color(0xFF00897B), radius = originR, center = partial.startPoint)
                                drawCircle(Color.White, radius = originR, center = partial.startPoint, style = Stroke(width = 1.4f / zoom))

                                // Moving flow tip or mouth dot
                                val isFlowingNow = (partial.progress in 0.01f..0.99f) || feature.id == activeAnimatingRiverId
                                val tipR = (if (isFlowingNow) 7.5f else 4.5f) / zoom
                                if (isFlowingNow) {
                                    drawCircle(Color(0xFF00E5FF).copy(alpha = 0.55f), radius = tipR * 1.9f, center = partial.tipPoint)
                                }
                                drawCircle(
                                    if (isFlowingNow) Color(0xFF00B0FF) else Color(0xFF01579B),
                                    radius = tipR,
                                    center = partial.tipPoint
                                )
                                drawCircle(Color.White, radius = tipR, center = partial.tipPoint, style = Stroke(width = 1.6f / zoom))
                                flowingRiverTips.add(feature to partial)
                            }
                        }
                        "poly" -> {
                            val path = Path()
                            feature.coords.forEachIndexed { i, (lon, lat) ->
                                val x = lonToX(geo, f, lon); val y = latToY(geo, f, lat)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            path.close()
                            val isSelPoly = feature.id == selectedFeatureId
                            drawPath(path, color = (if (isSelPoly) Color(0xFFF2994A) else categoryColor(feature.cat)).copy(alpha = 0.55f), style = Fill)
                            drawPath(path, color = if (isSelPoly) Color(0xFFF2994A) else categoryColor(feature.cat), style = Stroke(width = (if (isSelPoly) 3f else 1.4f) / zoom))
                        }
                    }
                }
                // Point markers
                for (feature in features) {
                    if (feature.geomType != "point") continue
                    val (lon, lat) = feature.coords.first()
                    val cx = lonToX(geo, f, lon); val cy = latToY(geo, f, lat)
                    val isSel = feature.id == selectedFeatureId
                    val r = (if (isSel) 11.5f else if (historicalMode) 7.5f else if (showFeatureLabels) 8.5f else 7f) / zoom
                    if (isSel) drawCircle(Color.White.copy(alpha = 0.7f), radius = r * 1.9f, center = Offset(cx, cy))
                    drawCircle(categoryColor(feature.cat), radius = r, center = Offset(cx, cy))
                    drawCircle(Color.White, radius = r, center = Offset(cx, cy), style = Stroke(width = 1.8f / zoom))
                    drawCircle(Color.White, radius = r * 0.38f, center = Offset(cx, cy))
                }
                // Labels
                val paint = Paint().apply { isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
                val haloPaint = Paint().apply {
                    isAntiAlias = true
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                    style = Paint.Style.STROKE
                }
                drawContext.canvas.nativeCanvas.apply {
                    // Region labels (states / countries)
                    if (zoom >= 1.15f && !showFeatureLabels && !historicalMode) {
                        for ((name, label) in regionLabels) {
                            val box = boxes[name] ?: continue
                            var fs = min(box.width / (label.length * 0.62f), box.height * 0.8f)
                            fs = min(fs, 26f / zoom * 1.6f)
                            if (fs < 6.5f / zoom) continue
                            paint.textSize = fs
                            paint.color = (if (dark) Color(0xFFE8EEF4) else Color(0xFF37474F)).copy(alpha = 0.9f).toArgb()
                            val region = geo.regions.firstOrNull { it.name == name } ?: continue
                            val cx = lonToX(geo, f, region.centroid.first)
                            val cy = latToY(geo, f, region.centroid.second)
                            drawText(label, cx, cy + fs * 0.35f, paint)
                        }
                    }

                    // Empire territory names drawn bold right inside their map territory
                    if (empireLayers.isNotEmpty()) {
                        for (layer in empireLayers) {
                            val (clon, clat) = layer.centroid
                            val cx = lonToX(geo, f, clon)
                            val cy = latToY(geo, f, clat)
                            val titleSize = (if (layer.isSelected) 16.5f else 14.5f) / zoom * 1.35f
                            paint.textSize = titleSize
                            haloPaint.textSize = titleSize
                            haloPaint.strokeWidth = 4.8f / zoom
                            haloPaint.color = (if (dark) Color(0xFF0A121C) else Color.White).copy(alpha = 0.95f).toArgb()
                            paint.color = (if (dark) Color.White else Color(0xFF102027)).toArgb()
                            drawText(layer.name, cx, cy, haloPaint)
                            drawText(layer.name, cx, cy, paint)

                            if (layer.periodLabel.isNotBlank()) {
                                val subSize = 10.5f / zoom * 1.3f
                                paint.textSize = subSize
                                haloPaint.textSize = subSize
                                haloPaint.strokeWidth = 3.5f / zoom
                                paint.color = (if (dark) Color(0xFFFFD54F) else Color(0xFF37474F)).toArgb()
                                drawText(layer.periodLabel, cx, cy + titleSize * 1.05f, haloPaint)
                                drawText(layer.periodLabel, cx, cy + titleSize * 1.05f, paint)
                            }
                        }
                    }

                    // River labels following the flowing tip during river animation!
                    for ((river, partial) in flowingRiverTips) {
                        val isActive = river.id == activeAnimatingRiverId || (partial.progress in 0.01f..0.99f)
                        val rSize = (if (isActive) 15f else 13f) / zoom * 1.35f
                        paint.textSize = rSize
                        haloPaint.textSize = rSize
                        haloPaint.strokeWidth = 4.2f / zoom
                        haloPaint.color = (if (dark) Color(0xFF0E1621) else Color.White).copy(alpha = 0.95f).toArgb()
                        paint.color = (if (isActive) Color(0xFFE65100) else Color(0xFF01579B)).toArgb()
                        val lx = if (partial.progress < 0.98f) partial.tipPoint.x else lonToX(geo, f, river.anchor.first)
                        val ly = (if (partial.progress < 0.98f) partial.tipPoint.y else latToY(geo, f, river.anchor.second)) - (10f / zoom)
                        val labelText = if (hindi) river.hi else river.en
                        drawText(labelText, lx, ly, haloPaint)
                        drawText(labelText, lx, ly, paint)
                    }

                    // Feature / Historical Event labels (for non-river or non-flowing features)
                    if (showFeatureLabels || zoom >= 2.3f) {
                        val labelSize = (if (historicalMode) 11.5f else if (showFeatureLabels) 13.5f else 12f) / zoom * 1.35f
                        paint.textSize = labelSize
                        haloPaint.textSize = labelSize
                        haloPaint.strokeWidth = 3.8f / zoom
                        haloPaint.color = (if (dark) Color(0xFF0E1621) else Color.White).copy(alpha = 0.92f).toArgb()
                        val drawnRiverIds = flowingRiverTips.map { it.first.id }.toSet()
                        for (feature in features) {
                            if (feature.cat == "OCEAN" || feature.id in drawnRiverIds) continue
                            val (lon, lat) = feature.anchor
                            val lx = lonToX(geo, f, lon)
                            val ly = latToY(geo, f, lat) - (10f / zoom)
                            val labelText = if (hindi) feature.hi else feature.en
                            paint.color = (if (historicalMode) (if (dark) Color(0xFFFFCC80) else Color(0xFF8D2800)) else categoryColor(feature.cat)).toArgb()
                            drawText(labelText, lx, ly, haloPaint)
                            drawText(labelText, lx, ly, paint)
                        }
                    }
                    // Ocean / sea labels
                    for (feature in features) {
                        if (feature.cat != "OCEAN") continue
                        val (lon, lat) = feature.coords.first()
                        paint.textSize = 15f / zoom * 1.35f
                        paint.color = (if (dark) Color(0xFF9CC8E8) else Color(0xFF1565C0)).copy(alpha = 0.78f).toArgb()
                        val labelText = if (hindi) feature.hi else feature.en
                        drawText(labelText, lonToX(geo, f, lon), latToY(geo, f, lat), paint)
                    }
                }
            }
        }

        // Floating zoom + reset + fullscreen controls
        if (interactive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = controlsBottomPadding),
                shape = RoundedCornerShape(14.dp),
                color = (if (dark) Color(0xFF182635) else Color.White).copy(alpha = 0.94f),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (onToggleFullScreen != null) {
                        IconButton(
                            onClick = onToggleFullScreen,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (isFullScreen) "Exit Fullscreen" else "Fullscreen",
                                tint = if (dark) Color.White else DeepNavy
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val newZoom = (zoom * 1.35f).coerceIn(1f, 12f)
                            val rawOffset = center - (center - offset) * (newZoom / zoom)
                            zoom = newZoom
                            offset = clampOffset(rawOffset, zoom, size)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Zoom In",
                            tint = if (dark) Color.White else DeepNavy
                        )
                    }
                    IconButton(
                        onClick = {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val newZoom = (zoom / 1.35f).coerceIn(1f, 12f)
                            val rawOffset = if (newZoom <= 1.01f) Offset.Zero else center - (center - offset) * (newZoom / zoom)
                            zoom = newZoom
                            offset = clampOffset(rawOffset, zoom, size)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Zoom Out",
                            tint = if (dark) Color.White else DeepNavy
                        )
                    }
                    IconButton(
                        onClick = {
                            zoom = 1f
                            offset = Offset.Zero
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CenterFocusStrong,
                            contentDescription = "Reset Map",
                            tint = if (dark) Color.White else AtlasBlue
                        )
                    }
                }
            }
        }
    }
}

private fun handleTap(
    geo: MapGeometry,
    features: List<GeoFeature>,
    empires: List<EmpireLayer>,
    lon: Double, lat: Double,
    onFeatureTap: (GeoFeature) -> Unit,
    onEmpireTap: (String) -> Unit,
    onRegionTap: (RegionGeom) -> Unit
) {
    // 1) point features — nearest within ~1.6 degrees
    var bestPoint: GeoFeature? = null; var bestD = 1.6
    for (f in features) {
        if (f.geomType != "point") continue
        val (flon, flat) = f.coords.first()
        val d = sqrt((flon - lon) * (flon - lon) + (flat - lat) * (flat - lat))
        if (d < bestD) { bestD = d; bestPoint = f }
    }
    if (bestPoint != null) { onFeatureTap(bestPoint); return }
    // 2) line features — within ~0.7 degrees of any segment
    var bestLine: GeoFeature? = null; var bestLD = 0.7
    for (f in features) {
        if (f.geomType != "line") continue
        for (i in 0 until f.coords.size - 1) {
            val a = f.coords[i]; val b = f.coords[i + 1]
            val d = distToSegment(lon, lat, a.first, a.second, b.first, b.second)
            if (d < bestLD) { bestLD = d; bestLine = f }
        }
    }
    if (bestLine != null) { onFeatureTap(bestLine); return }
    // 3) empire polygons (using scaledPoly)
    for (layer in empires) {
        if (ringContainsPairs(layer.scaledPoly, lon, lat)) { onEmpireTap(layer.datasetId); return }
    }
    // 4) area features (deserts)
    for (f in features) {
        if (f.geomType == "poly" && ringContainsPairs(f.coords, lon, lat)) { onFeatureTap(f); return }
    }
    // 5) regions
    geo.hitTest(lon, lat)?.let { onRegionTap(it) }
}
