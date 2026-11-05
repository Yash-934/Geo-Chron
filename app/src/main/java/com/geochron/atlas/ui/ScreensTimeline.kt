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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.geochron.atlas.AppViewModel
import com.geochron.atlas.data.GeoFeature
import com.geochron.atlas.data.HistoryDataset
import com.geochron.atlas.data.formatYear
import kotlinx.coroutines.delay

/** Computes dynamic territorial expansion/contraction (0.62f..1.0f) across an empire's lifespan. */
private fun computeEmpireExpansion(ds: HistoryDataset, year: Int): Float {
    val span = (ds.to - ds.from).coerceAtLeast(1)
    val progress = ((year - ds.from).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    return when {
        progress < 0.22f -> 0.62f + (progress / 0.22f) * 0.38f
        progress > 0.78f -> 1.0f - ((progress - 0.78f) / 0.22f) * 0.35f
        else -> 1.0f
    }
}

/** Returns a human-friendly Historical Epoch label for the video HUD. */
private fun epochLabelForYear(year: Int, hindi: Boolean): String = when {
    year < -1500 -> if (hindi) "आद्य-ऐतिहासिक काल • सिंधु घाटी सभ्यता" else "Proto-Historic Era • Indus Valley Civilization"
    year < -600 -> if (hindi) "वैदिक काल • आर्यावर्त एवं जनपद" else "Vedic Period • Aryavarta & Janapadas"
    year < -322 -> if (hindi) "महाजनपद काल • 16 महाजनपद और मगध का उत्कर्ष" else "Mahajanapada Era • Rise of Magadha"
    year <= -185 -> if (hindi) "मौर्य युग • अखिल भारतीय साम्राज्य" else "Mauryan Age • Pan-Indian Empire"
    year < 320 -> if (hindi) "मौर्योत्तर एवं संगम काल • शुंग, सातवाहन, कुषाण, संगम" else "Post-Mauryan & Sangam Era • Kushan, Satavahana, Sangam"
    year <= 550 -> if (hindi) "क्लासिकल स्वर्ण युग • गुप्त एवं वाकाटक साम्राज्य" else "Classical Golden Age • Gupta & Vakataka Empires"
    year < 750 -> if (hindi) "हर्ष, चालुक्य एवं पल्लव काल" else "Era of Harsha, Chalukyas & Pallavas"
    year < 1000 -> if (hindi) "त्रिपक्षीय संघर्ष एवं चोल उत्कर्ष • प्रतिहार, पाल, राष्ट्रकूट, चोल" else "Tripartite Struggle & Chola Rise"
    year < 1206 -> if (hindi) "राजपूत काल एवं चोल साम्राज्य" else "Rajput Kingdoms & Imperial Cholas"
    year < 1526 -> if (hindi) "सल्तनत काल, विजयनगर, बहमनी एवं अहोम" else "Delhi Sultanate, Vijayanagara, Bahmani & Ahom"
    year < 1707 -> if (hindi) "मुगल साम्राज्य एवं मराठा स्वराज्य का उदय" else "Mughal Empire & Rise of Maratha Swarajya"
    year < 1857 -> if (hindi) "मराठा महासंघ, सिख साम्राज्य एवं ब्रिटिश विस्तार" else "Maratha Confederacy, Sikh Empire & British Expansion"
    year < 1947 -> if (hindi) "ब्रिटिश राज और भारतीय स्वतंत्रता संग्राम" else "British Raj & Indian Freedom Struggle"
    else -> if (hindi) "स्वतंत्र भारत गणराज्य • आधुनिक युग" else "Republic of India • Modern Era"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(nav: NavController, vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val hindi by vm.hindi.collectAsState()
    val dark by vm.dark.collectAsState()
    val isFullScreen by vm.isMapFullScreen.collectAsState()

    var year by remember { mutableIntStateOf(-300) }
    var playing by remember { mutableStateOf(false) }
    var speedMultiplier by remember { mutableFloatStateOf(1f) }
    var mapScope by remember { mutableStateOf("india") }
    var selectedDatasetId by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = isFullScreen) {
        vm.setMapFullScreen(false)
    }

    DisposableEffect(Unit) {
        onDispose { vm.setMapFullScreen(false) }
    }

    // Video-style year-by-year animation: faster in ancient BCE millennia, finer year-by-year after 600 BCE
    LaunchedEffect(playing, speedMultiplier) {
        while (playing) {
            val baseStep = when {
                year < -1500 -> 15
                year < -600 -> 8
                year < 500 -> 4
                else -> 2
            }
            val step = (baseStep * speedMultiplier).toInt().coerceAtLeast(1)
            delay(85L)
            year += step
            if (year >= 2026) {
                year = 2026
                playing = false
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = { Text(t(hindi, "इतिहास एटलस • समय रेखा", "History Atlas • Timeline")) },
                    actions = {
                        IconButton(onClick = { vm.setMapFullScreen(true) }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DeepNavy, titleContentColor = Color.White
                    )
                )
            }
        }
    ) { padding ->
        val d = data
        if (d == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        val scopeDatasets = remember(d, mapScope) {
            d.history.filter { it.scope == mapScope }.sortedBy { it.from }
        }
        val activeInScope = remember(year, scopeDatasets) {
            scopeDatasets.filter { it.contains(year) }
        }
        val selectedDataset = activeInScope.firstOrNull { it.id == selectedDatasetId }
            ?: activeInScope.firstOrNull()
            ?: scopeDatasets.firstOrNull { it.id == selectedDatasetId }

        // Map layers: all active empires/kingdoms in this year with dynamic expansion + labels!
        val layers = remember(activeInScope, year, hindi, selectedDataset) {
            activeInScope.filter { it.poly != null }.map { ds ->
                EmpireLayer(
                    datasetId = ds.id,
                    name = if (hindi) ds.hi else ds.en,
                    color = parseHexColor(ds.colorHex),
                    poly = ds.poly!!,
                    periodLabel = ds.periodHi,
                    expansionFactor = computeEmpireExpansion(ds, year),
                    isSelected = ds.id == selectedDataset?.id
                )
            }
        }

        val eventFeatures = remember(activeInScope) {
            activeInScope.flatMap { ds ->
                ds.events.map { ev ->
                    GeoFeature(
                        id = "event_" + ds.id + "_" + ev.en,
                        en = ev.en,
                        hi = ev.hi,
                        cat = "EVENT",
                        scope = ds.scope,
                        geomType = "point",
                        coords = listOf(ev.lon to ev.lat),
                        infoHi = ev.infoHi,
                        infoEn = ev.infoEn,
                        factHi = ev.factHi,
                        source = ds.source
                    )
                }
            }
        }
        val geo = d.boundaries[mapScope]!!

        Column(Modifier.padding(padding).fillMaxSize()) {
            // ---------------- MAP WITH VIDEO-STYLE LIVE TIMELINE HUD ----------------
            Box(
                modifier = if (isFullScreen) Modifier.weight(1f).fillMaxWidth()
                else Modifier.fillMaxWidth().height(360.dp)
            ) {
                AtlasMap(
                    geo = geo,
                    dark = dark,
                    features = eventFeatures,
                    empireLayers = layers,
                    selectedFeatureId = null,
                    hindi = hindi,
                    showFeatureLabels = true,
                    historicalMode = true,
                    isFullScreen = isFullScreen,
                    onToggleFullScreen = { vm.setMapFullScreen(!isFullScreen) },
                    controlsBottomPadding = if (isFullScreen) 80.dp else 14.dp,
                    onFeatureTap = { f ->
                        val ds = d.history.firstOrNull { f.id.startsWith("event_" + it.id + "_") }
                        val ev = ds?.events?.firstOrNull { f.id == "event_" + ds.id + "_" + it.en }
                        if (ds != null && ev != null) vm.select(placeFromEvent(ds, ev))
                    },
                    onEmpireTap = { dsId ->
                        selectedDatasetId = dsId
                        val tapped = d.history.firstOrNull { it.id == dsId }
                        if (tapped != null && isFullScreen) {
                            vm.select(placeFromDataset(tapped))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // TOP-LEFT: Video-style Year Counter & Epoch HUD right on the map (like Ollie Bye video)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = DeepNavy.copy(alpha = 0.88f),
                    shadowElevation = 6.dp
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .background(if (playing) Color(0xFF4CAF50) else AccentOrange, CircleShape)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = formatYear(year),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                        }
                        Text(
                            text = if (mapScope == "india") epochLabelForYear(year, hindi)
                            else (selectedDataset?.let { if (hindi) it.hi else it.en } ?: t(hindi, "विश्व इतिहास", "World History")),
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                // TOP-RIGHT: भारत | विश्व Scope Toggle + Fullscreen exit if in fullscreen
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScopeChip(t(hindi, "भारत", "India"), mapScope == "india") { mapScope = "india" }
                    ScopeChip(t(hindi, "विश्व", "World"), mapScope == "world") { mapScope = "world" }
                    if (isFullScreen) {
                        Surface(
                            shape = CircleShape,
                            color = DeepNavy.copy(alpha = 0.9f),
                            modifier = Modifier.size(34.dp).clickable { vm.setMapFullScreen(false) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.FullscreenExit,
                                    contentDescription = "Exit Fullscreen",
                                    tint = AccentOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // BOTTOM-LEFT ON MAP: Active Kingdoms / Empires Legend in current year
                if (activeInScope.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, end = 64.dp, bottom = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (ds in activeInScope) {
                            val dsColor = parseHexColor(ds.colorHex)
                            val isSel = ds.id == selectedDataset?.id
                            Row(
                                modifier = Modifier
                                    .background(
                                        DeepNavy.copy(alpha = 0.88f),
                                        RoundedCornerShape(50)
                                    )
                                    .border(
                                        width = if (isSel) 1.8.dp else 1.dp,
                                        color = if (isSel) Color.White else dsColor,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable {
                                        selectedDatasetId = ds.id
                                        if (isFullScreen) vm.select(placeFromDataset(ds))
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(dsColor, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (hindi) ds.hi else ds.en,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ---------------- VIDEO PLAYER TRANSPORT & SCRUBBER BAR ----------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(DeepNavy)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Prev Dynasty / -25y / Play-Pause / +25y / Next Dynasty
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Jump to previous dynasty
                        IconButton(
                            onClick = {
                                playing = false
                                val prev = scopeDatasets.lastOrNull { it.midYear < year - 15 }
                                    ?: scopeDatasets.firstOrNull()
                                if (prev != null) {
                                    year = prev.midYear
                                    selectedDatasetId = prev.id
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous Era", tint = Color.White)
                        }
                        // Step back
                        IconButton(
                            onClick = {
                                playing = false
                                year = (year - 25).coerceAtLeast(-3300)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.FastRewind, contentDescription = "-25 years", tint = Color(0xFFBFD9EA))
                        }
                        // Play / Pause main button
                        FilledIconButton(
                            onClick = {
                                if (!playing && year >= 2025) year = -3300
                                playing = !playing
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (playing) AccentOrange else AtlasBlue
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White
                            )
                        }
                        // Step forward
                        IconButton(
                            onClick = {
                                playing = false
                                year = (year + 25).coerceAtMost(2026)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.FastForward, contentDescription = "+25 years", tint = Color(0xFFBFD9EA))
                        }
                        // Jump to next dynasty
                        IconButton(
                            onClick = {
                                playing = false
                                val next = scopeDatasets.firstOrNull { it.midYear > year + 15 }
                                    ?: scopeDatasets.lastOrNull()
                                if (next != null) {
                                    year = next.midYear
                                    selectedDatasetId = next.id
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next Era", tint = Color.White)
                        }
                    }

                    // Speed selector pill (0.5x -> 1x -> 2x -> 4x)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (spd in listOf(0.5f, 1f, 2f, 4f)) {
                            val activeSpd = speedMultiplier == spd
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (activeSpd) AccentOrange else Color.White.copy(alpha = 0.14f),
                                        RoundedCornerShape(50)
                                    )
                                    .clickable { speedMultiplier = spd }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (spd == 0.5f) "0.5x" else "${spd.toInt()}x",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (activeSpd) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Slider(
                    value = year.toFloat(),
                    onValueChange = {
                        year = it.toInt()
                        playing = false
                    },
                    valueRange = -3300f..2026f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentOrange,
                        activeTrackColor = AccentOrange,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.height(28.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("3300 BCE (सिंधु घाटी)", color = Color(0xFF9FB9CC), fontSize = 10.5.sp)
                    Text("322 BCE (मौर्य)", color = Color(0xFF9FB9CC), fontSize = 10.5.sp)
                    Text("320 CE (गुप्त)", color = Color(0xFF9FB9CC), fontSize = 10.5.sp)
                    Text("1526 (मुगल)", color = Color(0xFF9FB9CC), fontSize = 10.5.sp)
                    Text("2026 CE", color = Color(0xFF9FB9CC), fontSize = 10.5.sp)
                }
            }

            if (!isFullScreen) {
                // Chronological Dynasty/Empire chips — tap to jump directly to that era
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (ds in scopeDatasets) {
                        val isActive = ds.contains(year)
                        val isSelected = ds.id == selectedDataset?.id
                        val dsColor = parseHexColor(ds.colorHex)
                        Card(
                            modifier = Modifier.clickable {
                                playing = false
                                selectedDatasetId = ds.id
                                mapScope = ds.scope
                                year = ds.midYear
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isSelected -> dsColor
                                    isActive -> dsColor.copy(alpha = 0.28f)
                                    dark -> Color(0xFF182635)
                                    else -> Color.White
                                }
                            ),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(if (isSelected) Color.White else dsColor, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (hindi) ds.hi else ds.en,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected || isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> Color.White
                                        dark -> Color.White
                                        else -> Color(0xFF1E293B)
                                    }
                                )
                            }
                        }
                    }
                }

                // Context panel showing active empires and events for the selected year
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp)
                ) {
                    val ds = selectedDataset
                    if (ds != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (dark) Color(0xFF182635) else Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .background(parseHexColor(ds.colorHex), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(ds.periodHi, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        if (ds.contains(year)) t(
                                            hindi,
                                            "  •  इस वर्ष सक्रिय (${activeInScope.size} समकालीन राज्य)",
                                            "  •  Active in this year (${activeInScope.size} concurrent powers)"
                                        )
                                        else t(hindi, "  •  चयनित", "  •  Selected"),
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (hindi) ds.hi else ds.en,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (dark) Color.White else DeepNavy
                                )
                                Text(
                                    if (hindi) ds.summaryHi else ds.summaryEn,
                                    fontSize = 13.5.sp,
                                    color = if (dark) Color(0xFFCFD8DC) else Color(0xFF263238)
                                )
                                if (ds.events.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        t(hindi, "प्रमुख राजधानियाँ, युद्ध एवं स्थल (टैप करें):", "Key capitals, battles & sites (tap):"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = AtlasBlue
                                    )
                                    for (ev in ds.events) {
                                        Text(
                                            "📍 ${if (hindi) ev.hi else ev.en} (${ev.yearLabel}) — ${if (hindi) ev.infoHi else ev.infoEn}",
                                            fontSize = 12.8.sp,
                                            color = if (dark) Color(0xFFE2E8F0) else Color(0xFF334155),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { vm.select(placeFromEvent(ds, ev)) }
                                                .padding(vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        PlaceSheet(vm)
    }
}

@Composable
private fun ScopeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (active) AtlasBlue else Color.White.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else Color.DarkGray,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
