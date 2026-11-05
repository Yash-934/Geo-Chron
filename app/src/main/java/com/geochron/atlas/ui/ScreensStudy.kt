package com.geochron.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.geochron.atlas.data.BookmarkEntity
import com.geochron.atlas.data.PlaceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(nav: NavController, vm: AppViewModel) {
    val hindi by vm.hindi.collectAsState()
    val data by vm.data.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val notes by vm.notes.collectAsState()
    val progress by vm.quizProgress.collectAsState()
    val searchHistory by vm.searchHistory.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var showAddNote by remember { mutableStateOf(false) }

    val tabs = listOf(
        t(hindi, "बुकमार्क", "Bookmarks"),
        t(hindi, "मेरे नोट्स", "My Notes"),
        t(hindi, "Revision", "Revision"),
        t(hindi, "प्रगति", "Progress")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t(hindi, "अध्ययन साधन (Study Tools)", "Study Tools")) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, fontSize = 13.sp) })
                }
            }
            when (tab) {
                0 -> {
                    if (bookmarks.isEmpty()) {
                        EmptyNote(t(hindi, "अभी कोई बुकमार्क नहीं है।", "No bookmarks yet."),
                            t(hindi, "एटलस में किसी स्थान पर टैप करके 'बुकमार्क करें' दबाएँ — वह यहाँ सेव होगा।", "Tap any place in the Atlas and press 'Add to Bookmarks' — it will appear here."))
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(bookmarks, key = { it.id }) { b ->
                                BookmarkCard(b) {
                                    val p = PlaceInfo(
                                        id = b.id, nameHi = b.nameHi, nameEn = b.nameEn,
                                        categoryHi = "", categoryEn = b.category,
                                        infoHi = b.infoHi, infoEn = b.infoEn, factHi = b.factHi,
                                        dateLabel = b.dateLabel, source = b.source,
                                        scope = b.scope, lon = b.lon, lat = b.lat
                                    )
                                    vm.focusPlace = p
                                    vm.exploreScope = p.scope
                                    nav.navigate("explore")
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Button(onClick = { showAddNote = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(t(hindi, "＋ नया नोट लिखें", "＋ Write a new note"))
                        }
                        Spacer(Modifier.height(10.dp))
                        if (notes.isEmpty()) {
                            EmptyNote(t(hindi, "कोई नोट नहीं है।", "No notes yet."),
                                t(hindi, "पढ़ाई के अपने नोट्स यहाँ लिखें — ये फोन में ही सेव रहते हैं।", "Write your own study notes — they stay on this device."))
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(notes, key = { it.id }) { n ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(12.dp)) {
                                            Column(Modifier.weight(1f)) {
                                                Text(n.title, fontWeight = FontWeight.Bold, color = DeepNavy, fontSize = 15.5.sp)
                                                Spacer(Modifier.height(4.dp))
                                                MarkdownText(
                                                    markdown = n.text,
                                                    fontSize = 13.5.sp,
                                                    lineHeight = 20.sp,
                                                    color = Color(0xFF263238)
                                                )
                                            }
                                            IconButton(onClick = { vm.deleteNote(n) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFC62828))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    val wrongIds = progress.flatMap { it.wrongIds.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    val d = data
                    if (d == null || wrongIds.isEmpty()) {
                        EmptyNote(t(hindi, "Revision सूची खाली है।", "Revision list is empty."),
                            t(hindi, "क्विज़ में जिन प्रश्नों में गलती होगी, वे स्थान यहाँ जुड़ते जाएँगे ताकि आप उन्हें दोबारा पढ़ सकें।", "Places from quiz questions you miss will collect here for revision."))
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(wrongIds) { id ->
                                val p = resolvePlace(d, id)
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        if (p != null) {
                                            vm.focusPlace = p; vm.exploreScope = p.scope; nav.navigate("explore")
                                        }
                                    },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(if (p != null) (if (hindi) p.nameHi else p.nameEn) else id, fontWeight = FontWeight.Bold)
                                        Text(t(hindi, "दोबारा पढ़ें — मानचित्र पर देखने हेतु टैप करें", "Revise — tap to see on the map"), color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(t(hindi, "क्विज़ प्रगति", "Quiz progress"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (progress.isEmpty() || progress.all { it.gamesPlayed == 0 }) {
                            Text(t(hindi, "अभी तक कोई क्विज़ नहीं खेला गया।", "No quizzes played yet."), color = Color.Gray)
                        } else {
                            for (p in progress) {
                                val m = QUIZ_MODES.firstOrNull { it.id == p.mode }
                                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(if (m != null) (if (hindi) m.titleHi else m.titleEn) else p.mode, fontWeight = FontWeight.Bold)
                                        val pct = if (p.totalAsked > 0) (p.totalCorrect * 100 / p.totalAsked) else 0
                                        Text(
                                            t(hindi, "खेले: ${p.gamesPlayed} • सर्वश्रेष्ठ: ${p.bestScore} • सही: ${p.totalCorrect}/${p.totalAsked} ($pct%)",
                                                "Played: ${p.gamesPlayed} • Best: ${p.bestScore} • Correct: ${p.totalCorrect}/${p.totalAsked} ($pct%)"),
                                            fontSize = 13.sp, color = Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(t(hindi, "खोज इतिहास (Search history)", "Search history"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (searchHistory.isEmpty()) {
                            Text(t(hindi, "कोई खोज नहीं हुई।", "No searches yet."), color = Color.Gray)
                        } else {
                            for (s in searchHistory) Text("• " + s.query, fontSize = 13.5.sp)
                            TextButton(onClick = { vm.clearSearchHistory() }) { Text(t(hindi, "इतिहास साफ़ करें", "Clear history")) }
                        }
                    }
                }
            }
        }
    }

    if (showAddNote) {
        var title by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddNote = false },
            title = { Text(t(hindi, "नया नोट", "New note")) },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text(t(hindi, "शीर्षक", "Title")) }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        text,
                        { text = it },
                        label = { Text(t(hindi, "नोट (Markdown समर्थित)…", "Note (Markdown supported)…")) },
                        minLines = 3
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t(hindi, "टिप: **बोल्ड**, *इटैलिक*, # हेडिंग, - लिस्ट का उपयोग करें", "Tip: Use **bold**, *italic*, # heading, - list"),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) vm.saveNote(title.ifBlank { t(hindi, "मेरा नोट", "My note") }, text, null)
                    showAddNote = false
                }) { Text(t(hindi, "सेव", "Save")) }
            },
            dismissButton = { TextButton(onClick = { showAddNote = false }) { Text(t(hindi, "रद्द", "Cancel")) } }
        )
    }
}

@Composable
private fun BookmarkCard(b: BookmarkEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(b.nameHi, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
            Text(b.nameEn, color = Color.Gray, fontSize = 12.5.sp)
            if (b.factHi.isNotBlank()) Text("⭐ " + b.factHi, fontSize = 12.5.sp, color = Color(0xFF8D6E00))
        }
    }
}

@Composable
private fun EmptyNote(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Color.Gray, fontSize = 13.5.sp)
        }
    }
}

// ---------------- SETTINGS / ABOUT ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController, vm: AppViewModel) {
    val hindi by vm.hindi.collectAsState()
    val dark by vm.dark.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t(hindi, "सेटिंग्स और परिचय", "Settings & About")) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepNavy, titleContentColor = Color.White, navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = DeepNavy), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("🌍 GeoChron", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text(t(hindi, "इंटरएक्टिव भूगोल और इतिहास एटलस", "Interactive Geography & History Atlas"), color = Color(0xFFCFE7F7))
                    Text(t(hindi, "सीखें • खोजें • समझें", "Learn • Explore • Understand"), color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t(hindi, "भाषा / Language", "Language / भाषा"), fontWeight = FontWeight.Bold)
                    Text(if (hindi) "वर्तमान: हिंदी" else "Current: English", color = Color.Gray, fontSize = 12.5.sp)
                }
                TextButton(onClick = { vm.setHindi(true) }) { Text("हिंदी", fontWeight = if (hindi) FontWeight.ExtraBold else FontWeight.Normal) }
                TextButton(onClick = { vm.setHindi(false) }) { Text("English", fontWeight = if (!hindi) FontWeight.ExtraBold else FontWeight.Normal) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t(hindi, "डार्क मोड", "Dark mode"), fontWeight = FontWeight.Bold)
                    Text(if (dark) t(hindi, "चालू", "On") else t(hindi, "बंद", "Off"), color = Color.Gray, fontSize = 12.5.sp)
                }
                androidx.compose.material3.Switch(checked = dark, onCheckedChange = { vm.setDark(it) })
            }
            Spacer(Modifier.height(16.dp))
            Text(t(hindi, "विशेषताएँ", "Highlights"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "✓ " + t(hindi, "पूरी तरह ऑफ़लाइन — कोई लॉगिन, कोई विज्ञापन नहीं", "Fully offline — no login, no ads") + "\n" +
                    "✓ " + t(hindi, "सटीक मानचित्र (Natural Earth सार्वजनिक डेटा)", "Accurate maps (Natural Earth public-domain data)") + "\n" +
                    "✓ " + t(hindi, "छात्रों के लिए बनाया गया — परीक्षा उपयोगी तथ्य हर स्थान के साथ", "Made for students — exam facts with every place") + "\n" +
                    "✓ " + t(hindi, "ऐतिहासिक सीमाएँ 'सरलीकृत' चेतावनी के साथ", "Historical borders with 'simplified' honesty labels"),
                fontSize = 13.5.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(t(hindi, "डेटा स्रोत", "Data sources"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                t(
                    hindi,
                    "सीमाएँ: Natural Earth (public domain) से सरलीकृत GeoJSON। भारत की राज्य सीमाएँ सार्वजनिक GeoJSON डेटासेट से। स्थानों के तथ्य NCERT पाठ्यपुस्तकों और सार्वजनिक संदर्भों पर आधारित हैं। ऐतिहासिक साम्राज्य सीमाएँ पाठ्यपुस्तक एटलस की सरलीकृत रूपरेखा हैं — सटीक सर्वेक्षण नहीं।",
                    "Boundaries: simplified GeoJSON from Natural Earth (public domain); India state boundaries from public GeoJSON datasets. Place facts are based on NCERT textbooks and public references. Historical empire borders are simplified textbook-atlas outlines — not precise surveys."
                ),
                fontSize = 13.sp, color = Color.DarkGray
            )
            Spacer(Modifier.height(16.dp))
            Text("Version 1.0.0", color = Color.Gray, fontSize = 12.sp)
        }
    }
}
