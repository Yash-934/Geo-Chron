package com.geochron.atlas.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A study feature: river, mountain, lake, dam, site, ocean, strait… */
data class GeoFeature(
    val id: String,
    val en: String,
    val hi: String,
    val cat: String,           // RIVER, MOUNTAIN, DESERT, LAKE, WATERFALL, PEAK, PASS, DAM, PARK, CITY, OCEAN, STRAIT
    val scope: String,         // india | world
    val geomType: String,      // point | line | poly
    val coords: List<Pair<Double, Double>>,
    val infoHi: String,
    val infoEn: String,
    val factHi: String,
    val source: String
) {
    val anchor: Pair<Double, Double>
        get() = if (coords.isEmpty()) 0.0 to 0.0
        else coords.fold(0.0 to 0.0) { a, p -> (a.first + p.first) to (a.second + p.second) }
            .let { (it.first / coords.size) to (it.second / coords.size) }
}

data class HistoryEvent(
    val en: String, val hi: String,
    val lon: Double, val lat: Double,
    val yearLabel: String,
    val infoHi: String, val infoEn: String, val factHi: String
)

data class HistoryDataset(
    val id: String,
    val en: String, val hi: String,
    val scope: String,
    val from: Int, val to: Int,       // negative = BCE
    val periodHi: String,
    val colorHex: String,
    val poly: List<Pair<Double, Double>>?,
    val summaryHi: String, val summaryEn: String,
    val confidence: String,
    val source: String,
    val events: List<HistoryEvent>
) {
    fun contains(year: Int) = year in from..to
    val midYear: Int get() = (from + to) / 2
}

data class StateInfo(val hi: String, val capitalHi: String, val capitalEn: String, val factHi: String)

/** A chapter of the bundled Geography study notes. */
data class NotesChapter(
    val id: String, val titleHi: String, val titleEn: String,
    val source: String, val bullets: List<String>
)

/** One question of the Geography notes quiz bank (question, answer, chapter). */
data class NotesQuestion(val q: String, val a: String, val ch: String)

/** Boundary geometry (same compact format as the map assets). */
data class RegionGeom(val name: String, val polys: List<List<FloatArray>>) {
    val centroid: Pair<Double, Double> by lazy {
        var best: FloatArray? = null; var bestLen = -1
        for (poly in polys) for (ring in poly) if (ring.size > bestLen) { bestLen = ring.size; best = ring }
        val r = best ?: return@lazy (0.0 to 0.0)
        var sx = 0.0; var sy = 0.0; var n = 0; var i = 0
        while (i + 1 < r.size) { sx += r[i]; sy += r[i + 1]; n++; i += 2 }
        if (n == 0) 0.0 to 0.0 else (sx / n) to (sy / n)
    }
}

data class MapGeometry(
    val regions: List<RegionGeom>,
    val minLon: Double, val maxLon: Double, val minLat: Double, val maxLat: Double
) {
    val widthDeg: Double get() = maxLon - minLon
    val heightDeg: Double get() = maxLat - minLat

    fun hitTest(lon: Double, lat: Double): RegionGeom? {
        var best: RegionGeom? = null; var bestArea = Double.MAX_VALUE
        for (region in regions) {
            var inside = false
            for (poly in region.polys) {
                var parity = false
                for (ring in poly) if (ringContains(ring, lon, lat)) parity = !parity
                if (parity) { inside = true; break }
            }
            if (inside) {
                // prefer the region whose centroid is closest (good enough for state/country picking)
                val c = region.centroid
                val d = (c.first - lon) * (c.first - lon) + (c.second - lat) * (c.second - lat)
                if (d < bestArea) { bestArea = d; best = region }
            }
        }
        return best
    }

    private fun ringContains(ring: FloatArray, x: Double, y: Double): Boolean {
        var inside = false; var i = 0; var j = ring.size - 2
        while (i < ring.size) {
            val xi = ring[i].toDouble(); val yi = ring[i + 1].toDouble()
            val xj = ring[j].toDouble(); val yj = ring[j + 1].toDouble()
            if ((yi > y) != (yj > y)) {
                val xInt = (xj - xi) * (y - yi) / (yj - yi) + xi
                if (x < xInt) inside = !inside
            }
            j = i; i += 2
        }
        return inside
    }
}

/** Everything the app needs, loaded once from bundled assets (offline-first). */
data class AtlasData(
    val boundaries: Map<String, MapGeometry>,       // "india" | "world"
    val indiaFeatures: List<GeoFeature>,
    val worldFeatures: List<GeoFeature>,
    val history: List<HistoryDataset>,
    val statesInfo: Map<String, StateInfo>,
    val countriesHi: Map<String, String>,
    val notesChapters: List<NotesChapter>,
    val notesQuiz: List<NotesQuestion>
) {
    fun featuresFor(scope: String) = if (scope == "india") indiaFeatures else worldFeatures
    fun allFeatures() = indiaFeatures + worldFeatures
}

object AtlasRepository {
    @Volatile private var cached: AtlasData? = null

    suspend fun load(context: Context): AtlasData = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        val india = loadBoundaries(context, "india")
        val world = loadBoundaries(context, "world")
        val data = AtlasData(
            boundaries = mapOf("india" to india, "world" to world),
            indiaFeatures = loadFeatures(context, "features_india.json") + loadFeatures(context, "features_extra_india.json"),
            worldFeatures = loadFeatures(context, "features_world.json") + loadFeatures(context, "features_extra_world.json"),
            history = loadHistory(context),
            statesInfo = loadStatesInfo(context),
            countriesHi = loadCountriesHi(context),
            notesChapters = loadNotes(context),
            notesQuiz = loadNotesQuiz(context)
        )
        cached = data
        data
    }

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().readText()

    private fun loadBoundaries(context: Context, mapId: String): MapGeometry {
        val root = JSONObject(readAsset(context, "maps/$mapId.json"))
        val arr = root.getJSONArray("regions")
        val regions = ArrayList<RegionGeom>()
        var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val polysJson = o.getJSONArray("p")
            val polys = ArrayList<List<FloatArray>>()
            for (p in 0 until polysJson.length()) {
                val ringsJson = polysJson.getJSONArray(p)
                val rings = ArrayList<FloatArray>()
                for (r in 0 until ringsJson.length()) {
                    val flat = ringsJson.getJSONArray(r)
                    val ring = FloatArray(flat.length())
                    for (k in 0 until flat.length()) {
                        val v = flat.getDouble(k); ring[k] = v.toFloat()
                        if (k % 2 == 0) { if (v < minLon) minLon = v; if (v > maxLon) maxLon = v }
                        else { if (v < minLat) minLat = v; if (v > maxLat) maxLat = v }
                    }
                    rings.add(ring)
                }
                polys.add(rings)
            }
            regions.add(RegionGeom(o.getString("n"), polys))
        }
        return MapGeometry(regions, minLon, maxLon, minLat, maxLat)
    }

    private fun loadFeatures(context: Context, file: String): List<GeoFeature> {
        val arr = JSONObject(readAsset(context, "data/$file")).getJSONArray("features")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val g = o.getJSONObject("geom")
            val type = g.getString("t")
            val coords = ArrayList<Pair<Double, Double>>()
            if (type == "point") {
                val c = g.getJSONArray("c"); coords.add(c.getDouble(0) to c.getDouble(1))
            } else {
                val c = g.getJSONArray("c")
                for (k in 0 until c.length()) {
                    val p = c.getJSONArray(k); coords.add(p.getDouble(0) to p.getDouble(1))
                }
            }
            val rawCat = o.getString("cat")
            val normalizedCat = if (rawCat == "FALLS") "WATERFALL" else rawCat
            GeoFeature(
                id = o.getString("id"), en = o.getString("en"), hi = o.getString("hi"),
                cat = normalizedCat, scope = o.getString("scope"),
                geomType = type, coords = coords,
                infoHi = o.optString("infoHi"), infoEn = o.optString("infoEn"),
                factHi = o.optString("factHi"), source = o.optString("source")
            )
        }
    }

    private fun loadNotes(context: Context): List<NotesChapter> {
        val arr = JSONObject(readAsset(context, "data/notes_geography.json")).getJSONArray("chapters")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val b = o.getJSONArray("bullets")
            NotesChapter(
                id = o.getString("id"), titleHi = o.getString("titleHi"), titleEn = o.optString("titleEn"),
                source = o.optString("source"),
                bullets = (0 until b.length()).map { b.getString(it) }
            )
        }
    }

    private fun loadNotesQuiz(context: Context): List<NotesQuestion> {
        val arr = JSONObject(readAsset(context, "data/quiz_geography.json")).getJSONArray("questions")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NotesQuestion(q = o.getString("q"), a = o.getString("a"), ch = o.optString("ch"))
        }
    }

    private fun loadHistory(context: Context): List<HistoryDataset> {
        val arr = JSONObject(readAsset(context, "data/history.json")).getJSONArray("datasets")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val polyJson = if (o.isNull("poly")) null else o.getJSONArray("poly")
            val poly = polyJson?.let { pj ->
                (0 until pj.length()).map { k -> val p = pj.getJSONArray(k); p.getDouble(0) to p.getDouble(1) }
            }
            val evArr = o.getJSONArray("events")
            val events = (0 until evArr.length()).map { e ->
                val ev = evArr.getJSONObject(e)
                HistoryEvent(
                    en = ev.getString("en"), hi = ev.getString("hi"),
                    lon = ev.getDouble("lon"), lat = ev.getDouble("lat"),
                    yearLabel = ev.optString("year"),
                    infoHi = ev.optString("infoHi"), infoEn = ev.optString("infoEn"),
                    factHi = ev.optString("factHi")
                )
            }
            HistoryDataset(
                id = o.getString("id"), en = o.getString("en"), hi = o.getString("hi"),
                scope = o.getString("scope"), from = o.getInt("from"), to = o.getInt("to"),
                periodHi = o.optString("periodHi"), colorHex = o.optString("color", "#F2994A"),
                poly = poly, summaryHi = o.optString("summaryHi"), summaryEn = o.optString("summaryEn"),
                confidence = o.optString("confidence"), source = o.optString("source"),
                events = events
            )
        }
    }

    private fun loadStatesInfo(context: Context): Map<String, StateInfo> {
        val root = JSONObject(readAsset(context, "data/states_info.json"))
        val out = HashMap<String, StateInfo>()
        root.keys().forEach { k ->
            val a = root.getJSONArray(k)
            out[k] = StateInfo(a.getString(0), a.getString(1), a.getString(2), a.optString(3))
        }
        return out
    }

    private fun loadCountriesHi(context: Context): Map<String, String> {
        val root = JSONObject(readAsset(context, "data/countries_hi.json"))
        val out = HashMap<String, String>()
        root.keys().forEach { k -> out[k] = root.getString(k) }
        return out
    }
}

/** Unified display model for the info bottom-sheet — built from any kind of place. */
data class PlaceInfo(
    val id: String,
    val nameHi: String,
    val nameEn: String,
    val categoryHi: String,
    val categoryEn: String,
    val infoHi: String,
    val infoEn: String,
    val factHi: String,
    val dateLabel: String,
    val source: String,
    val scope: String,
    val lon: Double,
    val lat: Double
)

fun formatYear(year: Int): String = when {
    year < 0 -> "${-year} ईसा पूर्व (BCE)"
    year == 0 -> "0 (BCE/CE सीमा)"
    else -> "$year ईस्वी (CE)"
}

val CATEGORY_LABELS: Map<String, Pair<String, String>> = mapOf(
    "STATE" to ("राज्य / केंद्र शासित प्रदेश" to "State / UT"),
    "COUNTRY" to ("देश" to "Country"),
    "RIVER" to ("नदी" to "River"),
    "MOUNTAIN" to ("पर्वत श्रृंखला" to "Mountain range"),
    "DESERT" to ("मरुस्थल" to "Desert"),
    "LAKE" to ("झील" to "Lake"),
    "WATERFALL" to ("जलप्रपात" to "Waterfall"),
    "PEAK" to ("पर्वत चोटी" to "Peak"),
    "PASS" to ("दर्रा" to "Mountain pass"),
    "DAM" to ("बाँध" to "Dam"),
    "PARK" to ("राष्ट्रीय उद्यान" to "National park"),
    "CITY" to ("नगर / बंदरगाह" to "City / Port"),
    "OCEAN" to ("महासागर / सागर" to "Ocean / Sea"),
    "STRAIT" to ("जलडमरूमध्य / नहर" to "Strait / Canal"),
    "EMPIRE" to ("ऐतिहासिक साम्राज्य / काल" to "Historical empire / period"),
    "EVENT" to ("ऐतिहासिक घटना / स्थल" to "Historical event / site"),
    "VOLCANO" to ("ज्वालामुखी" to "Volcano"),
    "POWER" to ("परमाणु ऊर्जा संयंत्र" to "Nuclear power plant"),
    "ISLAND" to ("द्वीप" to "Island"),
    "SITE" to ("विशेष स्थल" to "Special place / site")
)

fun categoryLabel(cat: String, hindi: Boolean): String {
    val pair = CATEGORY_LABELS[cat] ?: (cat to cat)
    return if (hindi) pair.first else pair.second
}
