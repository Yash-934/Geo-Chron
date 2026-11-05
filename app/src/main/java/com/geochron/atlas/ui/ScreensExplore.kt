package com.geochron.atlas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.geochron.atlas.AppViewModel
import com.geochron.atlas.data.AtlasData
import com.geochron.atlas.data.GeoFeature
import com.geochron.atlas.data.PlaceInfo
import kotlinx.coroutines.delay

/** Find a full PlaceInfo for any stored id (feature / region / event / empire). */
fun resolvePlace(data: AtlasData, id: String): PlaceInfo? {
    data.allFeatures().firstOrNull { it.id == id }?.let { return placeFromFeature(it) }
    for (ds in data.history) {
        if (id == "empire_" + ds.id) return placeFromDataset(ds)
        for (ev in ds.events) if (id == "event_" + ds.id + "_" + ev.en) return placeFromEvent(ds, ev)
    }
    if (id.startsWith("region_india_")) {
        val name = id.removePrefix("region_india_")
        data.boundaries["india"]?.regions?.firstOrNull { it.name == name }?.let { return placeFromRegion(data, "india", it) }
    }
    if (id.startsWith("region_world_")) {
        val name = id.removePrefix("region_world_")
        data.boundaries["world"]?.regions?.firstOrNull { it.name == name }?.let { return placeFromRegion(data, "world", it) }
    }
    return null
}

// River system groupings for quick filtering in India
private val GANGA_SYSTEM_IDS = setOf(
    "river_ganga", "river_yamuna", "river_chambal", "river_son",
    "river_ghaghara", "river_gandak", "river_kosi", "river_betwa", "river_damodar"
)
private val INDUS_SYSTEM_IDS = setOf(
    "river_indus", "river_jhelum", "river_chenab", "river_ravi", "river_beas", "river_sutlej"
)
private val BRAHMAPUTRA_SYSTEM_IDS = setOf(
    "river_brahmaputra"
)
private val PENINSULAR_RIVER_IDS = setOf(
    "river_godavari", "river_krishna", "river_tungabhadra", "river_kaveri",
    "river_mahanadi", "river_narmada", "river_tapi", "river_luni",
    "river_sabarmati", "river_mahi", "river_periyar"
)

// ---------------- HOME ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController, vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val hindi by vm.hindi.collectAsState()
    val recents by vm.recents.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GeoChron") },
                actions = {
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepNavy, titleContentColor = Color.White, actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (data == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(t(hindi, "एटलस लोड हो रहा है…", "Loading the atlas…"))
                }
            }
            return@Scaffold
        }
        val d = data!!
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header banner
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(DeepNavy, AtlasBlue)), RoundedCornerShape(18.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text("🌍 GeoChron", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                    Text(
                        t(hindi, "इंटरएक्टिव भूगोल और इतिहास एटलस", "Interactive Geography & History Atlas"),
                        color = Color(0xFFCFE7F7), fontSize = 14.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t(hindi, "सीखें • खोजें • समझें", "Learn • Explore • Understand"),
                        color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeCard(Icons.Filled.Public, Color(0xFF1565C0), t(hindi, "भूगोल", "Geography"), t(hindi, "भूगोल का संसार", "World of geography"), Modifier.weight(1f)) {
                    vm.exploreScope = "india"; vm.startInRiverMode = false; nav.navigate("explore")
                }
                HomeCard(Icons.Filled.History, Color(0xFFEF6C00), t(hindi, "इतिहास", "History"), t(hindi, "इतिहास की यात्रा", "Journey through time"), Modifier.weight(1f)) {
                    nav.navigate("timeline")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeCard(Icons.Filled.EmojiEvents, Color(0xFF2E7D32), t(hindi, "मैप क्विज़", "Map Quiz"), t(hindi, "मैप पर आधारित प्रश्न", "Map-based questions"), Modifier.weight(1f)) {
                    nav.navigate("quiz")
                }
                HomeCard(Icons.Filled.Bookmark, Color(0xFF6A1B9A), t(hindi, "बुकमार्क", "Bookmarks"), t(hindi, "मेरी सेवाएँ (${bookmarks.size})", "My saves (${bookmarks.size})"), Modifier.weight(1f)) {
                    nav.navigate("bookmarks")
                }
            }

            if (d.notesChapters.isNotEmpty()) {
                HomeCard(
                    Icons.Filled.MenuBook,
                    Color(0xFF00897B),
                    t(hindi, "भूगोल अध्ययन नोट्स", "Geography Study Notes"),
                    t(
                        hindi,
                        "${d.notesChapters.size} अध्याय • ${d.notesChapters.sumOf { it.bullets.size }} परीक्षा-उपयोगी तथ्य",
                        "${d.notesChapters.size} chapters • ${d.notesChapters.sumOf { it.bullets.size }} exam-focused facts"
                    ),
                    Modifier.fillMaxWidth()
                ) {
                    nav.navigate("notes")
                }
            }

            Text(t(hindi, "त्वरित पहुँच (Quick Access)", "Quick Access"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAccess("🗺️", t(hindi, "भारत मानचित्र", "India Map"), Modifier.weight(1f)) {
                    vm.exploreScope = "india"; vm.startInRiverMode = false; nav.navigate("explore")
                }
                QuickAccess("🌊", t(hindi, "नदी प्रवाह (Play)", "River Flow"), Modifier.weight(1f)) {
                    vm.exploreScope = "india"; vm.startInRiverMode = true; nav.navigate("explore")
                }
                QuickAccess("🌐", t(hindi, "विश्व मानचित्र", "World Map"), Modifier.weight(1f)) {
                    vm.exploreScope = "world"; vm.startInRiverMode = false; nav.navigate("explore")
                }
                QuickAccess("🕰️", t(hindi, "समय रेखा", "Timeline"), Modifier.weight(1f)) {
                    nav.navigate("timeline")
                }
            }

            // Did you know — fact of the day, deterministic by date
            val facts = remember(d) {
                (d.allFeatures().map { it.factHi }.filter { it.isNotBlank() } +
                    d.statesInfo.values.map { it.factHi }.filter { it.isNotBlank() })
            }
            if (facts.isNotEmpty()) {
                val dayIndex = (System.currentTimeMillis() / 86_400_000L % facts.size).toInt()
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Color(0xFFE0A526))
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(t(hindi, "क्या आप जानते हैं?", "Did you know?"), fontWeight = FontWeight.Bold, color = Color(0xFF8D6E00))
                            Text(facts[dayIndex], fontSize = 13.5.sp, color = Color(0xFF5D4A12))
                        }
                    }
                }
            }

            if (recents.isNotEmpty()) {
                Text(t(hindi, "हाल ही में देखे गए", "Recently viewed"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recents.take(5).forEach { r ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                resolvePlace(d, r.id)?.let { p ->
                                    vm.focusPlace = p; vm.exploreScope = p.scope; nav.navigate("explore")
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(Modifier.padding(12.dp)) {
                                Icon(Icons.Filled.Map, contentDescription = null, tint = AtlasBlue)
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(if (hindi) r.nameHi else r.nameEn, fontWeight = FontWeight.Medium)
                                    Text(if (r.scope == "india") t(hindi, "भारत एटलस", "India atlas") else t(hindi, "विश्व एटलस", "World atlas"), color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun HomeCard(icon: ImageVector, color: Color, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(118.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun QuickAccess(emoji: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 20.sp)
            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        }
    }
}

// ---------------- EXPLORE ----------------

private val LAYER_GROUPS: List<Pair<String, List<String>>> = listOf(
    "भौगोलिक (Geographic)" to listOf("RIVER", "MOUNTAIN", "DESERT", "OCEAN", "ISLAND"),
    "प्राकृतिक (Natural)" to listOf("LAKE", "WATERFALL", "PEAK", "PASS", "PARK", "VOLCANO"),
    "मानव निर्मित व विशेष (Human-made & Special)" to listOf("DAM", "POWER", "CITY", "STRAIT", "SITE")
)

private val CATEGORY_DISPLAY_ORDER = listOf(
    "RIVER", "MOUNTAIN", "DAM", "PARK", "PASS", "PEAK", "LAKE", "WATERFALL",
    "DESERT", "POWER", "ISLAND", "CITY", "STRAIT", "VOLCANO", "SITE", "OCEAN"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(nav: NavController, vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val hindi by vm.hindi.collectAsState()
    val dark by vm.dark.collectAsState()
    val selected by vm.selected.collectAsState()
    val isFullScreen by vm.isMapFullScreen.collectAsState()

    var scope by remember { mutableStateOf(vm.exploreScope) }
    val layers = remember { mutableStateMapOf<String, Boolean>() }
    /** When non-null, ONLY features of this category are shown on the map (e.g. "DAM", "RIVER"). */
    var selectedCategory by remember(scope) {
        mutableStateOf<String?>(if (vm.startInRiverMode) "RIVER" else null)
    }
    var showLayers by remember { mutableStateOf(false) }
    var showRiverSelectorSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var focus by remember { mutableStateOf<MapFocus?>(null) }
    var focusSignal by remember { mutableIntStateOf(0) }

    // ---------------- INDIVIDUAL RIVER VISIBILITY & FLOW ANIMATION STATE ----------------
    /** Map of riverId -> visible (true by default). Allows hiding/showing any specific river! */
    val riverVisibility = remember(scope) { mutableStateMapOf<String, Boolean>() }
    /** Whether river flow animation is currently playing. */
    var riverPlaying by remember(scope) { mutableStateOf(false) }
    /** Overall river animation progress (0f..1f). */
    var riverProgress by remember(scope) { mutableFloatStateOf(1f) }
    /** Sequential mode: true = draws rivers one-by-one like the YouTube video; false = draws all visible rivers simultaneously. */
    var riverSequentialMode by remember { mutableStateOf(true) }
    /** Playback speed multiplier for river animation (0.5x, 1x, 2x). */
    var riverSpeed by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        if (vm.startInRiverMode) {
            selectedCategory = "RIVER"
            riverProgress = 0f
            riverPlaying = true
            vm.startInRiverMode = false
        }
    }

    BackHandler(enabled = isFullScreen) {
        vm.setMapFullScreen(false)
    }

    DisposableEffect(Unit) {
        onDispose { vm.setMapFullScreen(false) }
    }

    val d = data
    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = { Text(t(hindi, "भूगोल एटलस", "Geography Atlas")) },
                    actions = {
                        // Quick River Flow Video Mode toggle button in top bar
                        IconButton(
                            onClick = {
                                if (selectedCategory == "RIVER") {
                                    showRiverSelectorSheet = true
                                } else {
                                    selectedCategory = "RIVER"
                                    riverProgress = 0f
                                    riverPlaying = true
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.Water,
                                contentDescription = "River Flow Mode",
                                tint = if (selectedCategory == "RIVER") AccentOrange else Color.White
                            )
                        }
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showLayers = true }) {
                            Icon(Icons.Filled.Layers, contentDescription = "Layers")
                        }
                        IconButton(onClick = { vm.setMapFullScreen(true) }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "Fullscreen")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DeepNavy, titleContentColor = Color.White, actionIconContentColor = Color.White
                    )
                )
            }
        }
    ) { padding ->
        if (d == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val geo = d.boundaries[scope]!!
        val allScopeFeatures = d.featuresFor(scope)
        val allScopeRivers = remember(allScopeFeatures) {
            allScopeFeatures.filter { it.cat == "RIVER" }
        }
        val visibleRivers = remember(allScopeRivers, riverVisibility.toMap()) {
            allScopeRivers.filter { riverVisibility[it.id] ?: true }
        }

        // Animate river flow progress (0f -> 1f) when riverPlaying is true
        LaunchedEffect(riverPlaying, riverSpeed, riverSequentialMode, visibleRivers.size) {
            while (riverPlaying) {
                delay(35L)
                // In sequential mode with N rivers, adjust speed so each river gets ~1.4 seconds to draw smoothly
                val countFactor = if (riverSequentialMode) visibleRivers.size.coerceAtLeast(1).toFloat() else 1.4f
                val delta = (0.032f * riverSpeed) / countFactor.coerceIn(1f, 10f)
                riverProgress += delta
                if (riverProgress >= 1f) {
                    riverProgress = 1f
                    riverPlaying = false
                }
            }
        }

        // Compute per-river progress map (0f..1f) and which river is currently being drawn
        val (riverProgressMap, activeAnimatingRiver) = remember(
            visibleRivers,
            riverProgress,
            riverSequentialMode,
            selectedCategory
        ) {
            if (visibleRivers.isEmpty() || (riverProgress >= 0.999f && selectedCategory != "RIVER")) {
                emptyMap<String, Float>() to null
            } else if (!riverSequentialMode || visibleRivers.size == 1) {
                val map = visibleRivers.associate { it.id to riverProgress.coerceIn(0f, 1f) }
                val active = if (riverProgress < 0.999f) visibleRivers.firstOrNull() else null
                map to active
            } else {
                val n = visibleRivers.size
                val scaled = riverProgress.coerceIn(0f, 1f) * n
                val currentIdx = scaled.toInt().coerceIn(0, n - 1)
                val map = HashMap<String, Float>(n)
                for (i in 0 until n) {
                    val p = when {
                        riverProgress >= 0.999f -> 1f
                        i < currentIdx -> 1f
                        i == currentIdx -> (scaled - currentIdx).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    map[visibleRivers[i].id] = p
                }
                val active = if (riverProgress < 0.999f) visibleRivers[currentIdx] else null
                map to active
            }
        }

        // All categories present in this scope, sorted in a logical study order
        val scopeCategories = remember(allScopeFeatures) {
            val present = allScopeFeatures.map { it.cat }.distinct()
            CATEGORY_DISPLAY_ORDER.filter { it in present } + present.filter { it !in CATEGORY_DISPLAY_ORDER }
        }

        // Filter features:
        // 1) Individual rivers respect `riverVisibility` (so any river can be hidden/shown!)
        // 2) If a single category chip is selected (e.g. "DAM" or "RIVER"), show ONLY that category!
        val visibleFeatures = remember(allScopeFeatures, selectedCategory, layers.toMap(), riverVisibility.toMap()) {
            val activeCat = selectedCategory
            if (activeCat != null) {
                allScopeFeatures.filter { f ->
                    f.cat == activeCat && (f.cat != "RIVER" || (riverVisibility[f.id] ?: true))
                }
            } else {
                allScopeFeatures.filter { f ->
                    if (f.cat == "RIVER" && !(riverVisibility[f.id] ?: true)) {
                        false
                    } else {
                        when (f.cat) {
                            "OCEAN" -> layers["OCEAN"] ?: true
                            else -> layers[f.cat] ?: true
                        }
                    }
                }
            }
        }

        val regionLabels = remember(d, scope, hindi) {
            if (scope == "india") d.statesInfo.mapValues { (k, v) -> if (hindi) v.hi else k }.mapKeys { it.key }
                .let { m -> geo.regions.associate { r -> r.name to (m[r.name] ?: r.name) } }
            else geo.regions.associate { r -> r.name to (if (hindi) (d.countriesHi[r.name] ?: r.name) else r.name) }
        }

        // Focus request coming from Home / Bookmarks / Recents
        LaunchedEffect(d, vm.focusPlace) {
            val p = vm.focusPlace ?: return@LaunchedEffect
            scope = p.scope
            selectedCategory = null
            focusSignal++
            focus = MapFocus(p.lon, p.lat, 2.4f, focusSignal)
            vm.select(p)
            vm.focusPlace = null
        }

        val isRiverMode = selectedCategory == "RIVER"

        Column(Modifier.padding(padding).fillMaxSize()) {
            // Scope switch — भारत | विश्व (hidden in Full Screen mode since floating controls are shown)
            if (!isFullScreen) {
                Row(
                    Modifier.fillMaxWidth().background(DeepNavy).padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScopeButton(t(hindi, "भारत", "India"), scope == "india", Modifier.weight(1f)) { scope = "india" }
                    ScopeButton(t(hindi, "विश्व", "World"), scope == "world", Modifier.weight(1f)) { scope = "world" }
                    IconButton(onClick = {
                        focusSignal++
                        val c = geoCenter(geo)
                        focus = MapFocus(c.first, c.second, 1f, focusSignal)
                    }) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Reset view", tint = Color.White)
                    }
                }
            }

            if (showSearch) {
                Column(Modifier.fillMaxWidth().background(if (dark) Color(0xFF182635) else Color.White).padding(10.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text(t(hindi, "स्थान या नदी खोजें — हिंदी या English में…", "Search places or rivers — Hindi or English…")) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showSearch = false; query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close search")
                            }
                        },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    if (query.length >= 2) {
                        val results = remember(query, d, scope) { searchPlaces(d, query).take(8) }
                        LazyColumn(Modifier.fillMaxWidth().height(220.dp)) {
                            items(results) { p ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        vm.recordSearch(query)
                                        scope = p.scope
                                        selectedCategory = null
                                        focusSignal++
                                        focus = MapFocus(p.lon, p.lat, 2.4f, focusSignal)
                                        vm.select(p)
                                        showSearch = false
                                    }.padding(10.dp)
                                ) {
                                    Icon(Icons.Filled.SwitchAccount, contentDescription = null, tint = AtlasBlue)
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Text(if (hindi) p.nameHi else p.nameEn, fontWeight = FontWeight.Medium)
                                        Text(if (hindi) p.categoryHi else p.categoryEn, color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AtlasMap(
                    geo = geo,
                    dark = dark,
                    features = visibleFeatures,
                    highlightRegion = null,
                    selectedFeatureId = selected?.id,
                    regionLabels = regionLabels,
                    focus = focus,
                    hindi = hindi,
                    showFeatureLabels = selectedCategory != null,
                    riverProgressMap = riverProgressMap,
                    activeAnimatingRiverId = activeAnimatingRiver?.id,
                    isFullScreen = isFullScreen,
                    onToggleFullScreen = { vm.setMapFullScreen(!isFullScreen) },
                    controlsBottomPadding = if (isRiverMode) 156.dp else 54.dp,
                    onRegionTap = { region -> vm.select(placeFromRegion(d, scope, region)) },
                    onFeatureTap = { f -> vm.select(placeFromFeature(f)) },
                    modifier = Modifier.fillMaxSize()
                )

                // Top overlay in Full Screen mode OR active single-category filter badge + Live River Flow Banner
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isFullScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Compact scope switcher pill
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = DeepNavy.copy(alpha = 0.92f),
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    SmallScopeChip(t(hindi, "भारत", "India"), scope == "india") { scope = "india" }
                                    SmallScopeChip(t(hindi, "विश्व", "World"), scope == "world") { scope = "world" }
                                }
                            }

                            // Action buttons in full screen
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = DeepNavy.copy(alpha = 0.92f),
                                shadowElevation = 4.dp
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { showSearch = !showSearch }) {
                                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White)
                                    }
                                    IconButton(onClick = { showLayers = true }) {
                                        Icon(Icons.Filled.Layers, contentDescription = "Layers", tint = Color.White)
                                    }
                                    IconButton(onClick = { vm.setMapFullScreen(false) }) {
                                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit Fullscreen", tint = AccentOrange)
                                    }
                                }
                            }
                        }
                    }

                    // Active single-category filter indicator banner
                    val activeCat = selectedCategory
                    if (activeCat != null && activeAnimatingRiver == null) {
                        val catLabel = com.geochron.atlas.data.categoryLabel(activeCat, hindi)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = categoryColor(activeCat),
                                shadowElevation = 4.dp,
                                modifier = Modifier.clickable {
                                    riverPlaying = false
                                    riverProgress = 1f
                                    selectedCategory = null
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$catLabel (${visibleFeatures.size})",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "✕ " + t(hindi, "सभी दिखाएँ", "Show all"),
                                        color = Color.White.copy(alpha = 0.92f),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Live Video-Style Banner while a river is actively flowing on the map!
                    if (activeAnimatingRiver != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = DeepNavy.copy(alpha = 0.92f),
                            shadowElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                vm.select(placeFromFeature(activeAnimatingRiver))
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🌊", fontSize = 22.sp)
                                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                    Text(
                                        text = if (hindi) "${activeAnimatingRiver.hi} (${activeAnimatingRiver.en})" else activeAnimatingRiver.en,
                                        color = Color(0xFF4FC3F7),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.5.sp
                                    )
                                    val desc = if (hindi) activeAnimatingRiver.infoHi else activeAnimatingRiver.infoEn
                                    if (desc.isNotBlank()) {
                                        Text(
                                            text = desc,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Overlay: River Flow Video Player (when RIVER is selected) + Category Filter Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    // ---------------- RIVER FLOW VIDEO PLAYER & INDIVIDUAL RIVER SELECTOR ----------------
                    if (isRiverMode && allScopeRivers.isNotEmpty()) {
                        Surface(
                            color = DeepNavy.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                // Top Row: Play/Pause, Scrubber Slider, Speed, One-by-One/Simultaneous, & River Filter Sheet button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Play / Pause River Flow button
                                    FilledIconButton(
                                        onClick = {
                                            if (visibleRivers.isEmpty()) {
                                                allScopeRivers.forEach { riverVisibility[it.id] = true }
                                            }
                                            if (!riverPlaying && riverProgress >= 0.98f) {
                                                riverProgress = 0f
                                            }
                                            riverPlaying = !riverPlaying
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = if (riverPlaying) AccentOrange else Color(0xFF0288D1)
                                        ),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            if (riverPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = "Play River Flow",
                                            tint = Color.White
                                        )
                                    }

                                    // Replay from 0%
                                    IconButton(
                                        onClick = {
                                            if (visibleRivers.isEmpty()) {
                                                allScopeRivers.forEach { riverVisibility[it.id] = true }
                                            }
                                            riverProgress = 0f
                                            riverPlaying = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Replay, contentDescription = "Replay", tint = Color(0xFF81D4FA))
                                    }

                                    // Flow progress slider
                                    Slider(
                                        value = riverProgress,
                                        onValueChange = {
                                            riverPlaying = false
                                            riverProgress = it
                                        },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF29B6F6),
                                            activeTrackColor = Color(0xFF29B6F6),
                                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                        ),
                                        modifier = Modifier.weight(1f).height(26.dp)
                                    )

                                    // Speed toggle pill (0.5x / 1x / 2x)
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                                            .clickable {
                                                riverSpeed = when (riverSpeed) {
                                                    0.5f -> 1f
                                                    1f -> 2f
                                                    else -> 0.5f
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (riverSpeed == 0.5f) "0.5x" else "${riverSpeed.toInt()}x",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // One-by-one vs All-together toggle
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (riverSequentialMode) Color(0xFF00897B) else Color(0xFF3949AB),
                                                RoundedCornerShape(50)
                                            )
                                            .clickable {
                                                riverSequentialMode = !riverSequentialMode
                                                riverProgress = 0f
                                                riverPlaying = true
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (riverSequentialMode) t(hindi, "एक-एक", "1-by-1") else t(hindi, "एक साथ", "All"),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Open full River Selector sheet
                                    IconButton(
                                        onClick = { showRiverSelectorSheet = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Tune, contentDescription = "Select Rivers", tint = AccentOrange)
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                // Bottom Row inside River Panel: Individual River Show/Hide & Solo-Play Chips!
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Show All Rivers chip
                                    val allRiversOn = visibleRivers.size == allScopeRivers.size
                                    RiverQuickChip(
                                        label = t(hindi, "सभी (${allScopeRivers.size})", "All (${allScopeRivers.size})"),
                                        active = allRiversOn,
                                        accentColor = Color(0xFF0288D1)
                                    ) {
                                        allScopeRivers.forEach { riverVisibility[it.id] = true }
                                        riverProgress = 0f
                                        riverPlaying = true
                                    }

                                    // Hide All chip (so user can tap just the 1 or 2 rivers they want!)
                                    RiverQuickChip(
                                        label = t(hindi, "सभी छुपाएँ", "Hide All"),
                                        active = visibleRivers.isEmpty(),
                                        accentColor = Color(0xFFE53935)
                                    ) {
                                        riverPlaying = false
                                        allScopeRivers.forEach { riverVisibility[it.id] = false }
                                    }

                                    // River System presets for India
                                    if (scope == "india") {
                                        RiverQuickChip(
                                            label = t(hindi, "गंगा तंत्र", "Ganga System"),
                                            active = visibleRivers.map { it.id }.toSet() == GANGA_SYSTEM_IDS,
                                            accentColor = Color(0xFF00897B)
                                        ) {
                                            allScopeRivers.forEach { riverVisibility[it.id] = it.id in GANGA_SYSTEM_IDS }
                                            riverProgress = 0f
                                            riverPlaying = true
                                        }
                                        RiverQuickChip(
                                            label = t(hindi, "सिंधु तंत्र", "Indus System"),
                                            active = visibleRivers.map { it.id }.toSet() == INDUS_SYSTEM_IDS,
                                            accentColor = Color(0xFF5C6BC0)
                                        ) {
                                            allScopeRivers.forEach { riverVisibility[it.id] = it.id in INDUS_SYSTEM_IDS }
                                            riverProgress = 0f
                                            riverPlaying = true
                                        }
                                        RiverQuickChip(
                                            label = t(hindi, "प्रायद्वीपीय", "Peninsular"),
                                            active = visibleRivers.map { it.id }.toSet() == PENINSULAR_RIVER_IDS,
                                            accentColor = Color(0xFFF57C00)
                                        ) {
                                            allScopeRivers.forEach { riverVisibility[it.id] = it.id in PENINSULAR_RIVER_IDS }
                                            riverProgress = 0f
                                            riverPlaying = true
                                        }
                                    }

                                    // Individual River Toggle + Play Chips
                                    for (river in allScopeRivers) {
                                        val isVis = riverVisibility[river.id] ?: true
                                        val isFlowingThis = activeAnimatingRiver?.id == river.id
                                        Row(
                                            modifier = Modifier
                                                .background(
                                                    when {
                                                        isFlowingThis -> AccentOrange
                                                        isVis -> Color(0xFF0277BD)
                                                        else -> Color.White.copy(alpha = 0.12f)
                                                    },
                                                    RoundedCornerShape(50)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isVis) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.25f),
                                                    RoundedCornerShape(50)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Tap eye / name to toggle THIS river ON/OFF (Show / Hide)
                                            Row(
                                                modifier = Modifier.clickable {
                                                    val next = !isVis
                                                    riverVisibility[river.id] = next
                                                    if (next && riverProgress >= 0.99f) {
                                                        riverProgress = 1f
                                                    }
                                                },
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isVis) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                    contentDescription = null,
                                                    tint = if (isVis) Color.White else Color.Gray,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = if (hindi) river.hi else river.en,
                                                    color = if (isVis) Color.White else Color.LightGray,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isVis) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            // Tap ▶ to show ONLY this river and play its flow animation from origin to mouth!
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .background(Color.White.copy(alpha = 0.24f), CircleShape)
                                                    .clickable {
                                                        allScopeRivers.forEach { r ->
                                                            riverVisibility[r.id] = (r.id == river.id)
                                                        }
                                                        riverProgress = 0f
                                                        riverPlaying = true
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Play only this river",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Interactive Category Filter Chips at bottom of map
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background((if (dark) Color(0xFF101D2C) else Color.White).copy(alpha = 0.95f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isAllSelected = selectedCategory == null
                        CategoryFilterChip(
                            label = t(hindi, "सभी (${allScopeFeatures.size})", "All (${allScopeFeatures.size})"),
                            dotColor = AtlasBlue,
                            selected = isAllSelected,
                            dark = dark,
                            onClick = {
                                riverPlaying = false
                                riverProgress = 1f
                                selectedCategory = null
                            }
                        )

                        for (cat in scopeCategories) {
                            val count = allScopeFeatures.count { it.cat == cat }
                            val isSelected = selectedCategory == cat
                            CategoryFilterChip(
                                label = "${com.geochron.atlas.data.categoryLabel(cat, hindi)} ($count)",
                                dotColor = categoryColor(cat),
                                selected = isSelected,
                                dark = dark,
                                onClick = {
                                    if (selectedCategory == cat) {
                                        riverPlaying = false
                                        riverProgress = 1f
                                        selectedCategory = null
                                    } else {
                                        selectedCategory = cat
                                        if (cat == "RIVER") {
                                            if (visibleRivers.isEmpty()) {
                                                allScopeRivers.forEach { riverVisibility[it.id] = true }
                                            }
                                            riverProgress = 0f
                                            riverPlaying = true
                                        } else {
                                            riverPlaying = false
                                            riverProgress = 1f
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (!isFullScreen) {
                Text(
                    if (isRiverMode) t(
                        hindi,
                        "🌊 किसी नदी के नाम पर टैप करके उसे छुपाएँ/दिखाएँ • ▶ दबाकर केवल उस नदी का उद्गम से मुहाने तक प्रवाह देखें",
                        "🌊 Tap any river name to hide/show it • Tap ▶ to animate only that river from origin to mouth"
                    ) else t(
                        hindi,
                        "किसी श्रेणी (जैसे नदी, बाँध) पर टैप करके केवल उसे देखें • पिंच से ज़ूम करें",
                        "Tap any category (e.g. River, Dam) to show only that • Pinch to zoom"
                    ),
                    color = Color.Gray, fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }

        // ---------------- INDIVIDUAL RIVER SELECTOR & FLOW SHEET ----------------
        if (showRiverSelectorSheet) {
            ModalBottomSheet(onDismissRequest = { showRiverSelectorSheet = false }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 30.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🌊 " + t(hindi, "नदियाँ चुनें (दिखाएँ / छुपाएँ)", "Select Rivers (Show / Hide)"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Row {
                            TextButton(onClick = {
                                allScopeRivers.forEach { riverVisibility[it.id] = true }
                            }) {
                                Text(t(hindi, "सभी दिखाएँ", "Show All"), fontSize = 12.5.sp)
                            }
                            TextButton(onClick = {
                                allScopeRivers.forEach { riverVisibility[it.id] = false }
                            }) {
                                Text(t(hindi, "सभी छुपाएँ", "Hide All"), fontSize = 12.5.sp)
                            }
                        }
                    }
                    Text(
                        t(
                            hindi,
                            "जिस नदी को देखना हो उसे चालू (✓) करें और बाकी को बंद कर दें, या '▶ चलाएँ' दबाकर केवल उसी नदी को बनते हुए देखें।",
                            "Check the rivers you want to see and uncheck others, or tap '▶ Play' to animate only that river."
                        ),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(10.dp))

                    for (river in allScopeRivers) {
                        val isVis = riverVisibility[river.id] ?: true
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { riverVisibility[river.id] = !isVis }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isVis,
                                onCheckedChange = { riverVisibility[river.id] = it }
                            )
                            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                Text(
                                    text = if (hindi) "${river.hi} (${river.en})" else river.en,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                                if (river.infoHi.isNotBlank()) {
                                    Text(
                                        text = if (hindi) river.infoHi else river.infoEn,
                                        fontSize = 11.5.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    selectedCategory = "RIVER"
                                    allScopeRivers.forEach { r ->
                                        riverVisibility[r.id] = (r.id == river.id)
                                    }
                                    riverProgress = 0f
                                    riverPlaying = true
                                    showRiverSelectorSheet = false
                                }
                            ) {
                                Text(
                                    "▶ " + t(hindi, "केवल यही चलाएँ", "Play Only"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0288D1)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showLayers) {
            ModalBottomSheet(onDismissRequest = { showLayers = false }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 30.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🗺️ " + t(hindi, "मैप लेयर्स", "Map Layers"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                selectedCategory = null
                                scopeCategories.forEach { layers[it] = true }
                            }) {
                                Text(t(hindi, "सभी दिखाएँ", "Show All"), fontSize = 12.5.sp)
                            }
                            TextButton(onClick = {
                                selectedCategory = null
                                scopeCategories.forEach { layers[it] = false }
                            }) {
                                Text(t(hindi, "सभी छुपाएँ", "Hide All"), fontSize = 12.5.sp)
                            }
                        }
                    }
                    Text(
                        t(
                            hindi,
                            "किसी एक श्रेणी को अकेले देखने के लिए 'केवल यही' दबाएँ, या स्विच से कई लेयर्स चालू/बंद करें।",
                            "Tap 'Only' to view a single category on the map, or toggle multiple layers with switches."
                        ),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    for ((group, cats) in LAYER_GROUPS) {
                        val present = cats.filter { cat -> allScopeFeatures.any { it.cat == cat } }
                        if (present.isEmpty()) continue
                        Text(group, fontWeight = FontWeight.Bold, color = AtlasBlue, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        for (cat in present) {
                            val on = if (selectedCategory != null) selectedCategory == cat else (layers[cat] ?: true)
                            val count = allScopeFeatures.count { it.cat == cat }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCategory = if (selectedCategory == cat) null else cat
                                        showLayers = false
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(12.dp).background(categoryColor(cat), RoundedCornerShape(3.dp)))
                                Text(
                                    "${com.geochron.atlas.data.categoryLabel(cat, hindi)} ($count)",
                                    modifier = Modifier.padding(start = 10.dp).weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        selectedCategory = cat
                                        if (cat == "RIVER") {
                                            riverProgress = 0f
                                            riverPlaying = true
                                        }
                                        showLayers = false
                                    }
                                ) {
                                    Text(
                                        t(hindi, "केवल यही", "Only"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = categoryColor(cat)
                                    )
                                }
                                Switch(
                                    checked = on,
                                    onCheckedChange = { checked ->
                                        if (selectedCategory != null) {
                                            selectedCategory = null
                                        }
                                        layers[cat] = checked
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        PlaceSheet(vm)
    }
}

@Composable
private fun RiverQuickChip(
    label: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (active) accentColor else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(50)
            )
            .border(
                1.dp,
                if (active) Color.White else accentColor.copy(alpha = 0.6f),
                RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun CategoryFilterChip(
    label: String,
    dotColor: Color,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        selected -> dotColor
        dark -> Color(0xFF1B2B3C)
        else -> Color(0xFFF1F5F9)
    }
    val contentColor = when {
        selected -> Color.White
        dark -> Color(0xFFE2E8F0)
        else -> Color(0xFF1E293B)
    }
    val borderColor = if (selected) dotColor else dotColor.copy(alpha = 0.45f)

    Row(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(50))
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .background(if (selected) Color.White else dotColor, CircleShape)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun SmallScopeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (active) AtlasBlue else Color.Transparent, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ScopeButton(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (active) {
        Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = AtlasBlue)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text(label) }
    }
}

private fun geoCenter(geo: com.geochron.atlas.data.MapGeometry): Pair<Double, Double> =
    ((geo.minLon + geo.maxLon) / 2) to ((geo.minLat + geo.maxLat) / 2)

fun searchPlaces(data: AtlasData, query: String): List<PlaceInfo> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val out = ArrayList<PlaceInfo>()
    for (f in data.allFeatures()) {
        if (f.en.lowercase().contains(q) || f.hi.contains(query.trim())) out.add(placeFromFeature(f))
    }
    for ((name, info) in data.statesInfo) {
        if (name.lowercase().contains(q) || info.hi.contains(query.trim()) || info.capitalEn.lowercase().contains(q)) {
            data.boundaries["india"]?.regions?.firstOrNull { it.name == name }?.let { out.add(placeFromRegion(data, "india", it)) }
        }
    }
    for (region in data.boundaries["world"]?.regions ?: emptyList()) {
        val hi = data.countriesHi[region.name] ?: ""
        if (region.name.lowercase().contains(q) || hi.contains(query.trim())) out.add(placeFromRegion(data, "world", region))
    }
    for (ds in data.history) {
        if (ds.en.lowercase().contains(q) || ds.hi.contains(query.trim())) out.add(placeFromDataset(ds))
        for (ev in ds.events) {
            if (ev.en.lowercase().contains(q) || ev.hi.contains(query.trim())) out.add(placeFromEvent(ds, ev))
        }
    }
    return out.distinctBy { it.id }
}
