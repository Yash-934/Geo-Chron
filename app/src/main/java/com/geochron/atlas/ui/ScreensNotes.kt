package com.geochron.atlas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.geochron.atlas.AppViewModel
import com.geochron.atlas.data.NotesChapter

private val NUMBERED_ITEM_REGEX = Regex("""^(\d+)\.\s+(.*)$""")

/** Geography study notes: chapter list + Markdown-enabled chapter reader. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(nav: NavController, vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val hindi by vm.hindi.collectAsState()
    val dark by vm.dark.collectAsState()
    var selected by remember { mutableStateOf<NotesChapter?>(null) }
    var query by remember { mutableStateOf("") }
    var chapterQuery by remember { mutableStateOf("") }

    BackHandler(enabled = selected != null) {
        selected = null
        chapterQuery = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        selected?.let { if (hindi) it.titleHi else it.titleEn }
                            ?: t(hindi, "भूगोल अध्ययन नोट्स", "Geography Study Notes")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selected != null) {
                            selected = null
                            chapterQuery = ""
                        } else {
                            nav.popBackStack()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepNavy, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        val d = data
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (d == null) {
                Text(t(hindi, "लोड हो रहा है…", "Loading…"), Modifier.padding(16.dp))
                return@Column
            }
            val chapter = selected
            if (chapter == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    placeholder = { Text(t(hindi, "अध्याय या तथ्य खोजें…", "Search chapters or facts…")) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true
                )
                Text(
                    t(
                        hindi,
                        "कुल ${d.notesChapters.size} अध्याय • ${d.notesChapters.sumOf { it.bullets.size }} परीक्षा-उपयोगी तथ्य • हस्तलिखित भूगोल नोट्स से संकलित",
                        "${d.notesChapters.size} chapters • ${d.notesChapters.sumOf { it.bullets.size }} exam-focused facts • Compiled from handwritten geography notes"
                    ),
                    Modifier.padding(horizontal = 14.dp), fontSize = 12.sp, color = Color(0xFF607D8B)
                )
                Spacer(Modifier.height(8.dp))
                val q = query.trim()
                val chapters = if (q.isEmpty()) d.notesChapters
                else d.notesChapters.filter { ch ->
                    ch.titleHi.contains(q, true) || ch.titleEn.contains(q, true) ||
                        ch.bullets.any { it.contains(q, true) }
                }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(chapters) { ch ->
                        val matchingCount = if (q.isEmpty()) ch.bullets.size else ch.bullets.count { it.contains(q, true) }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (dark) Color(0xFF182635) else Color(0xFFE0F2F1)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                .clickable {
                                    selected = ch
                                    chapterQuery = q
                                }
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📚", fontSize = 24.sp)
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(
                                        if (hindi) ch.titleHi else ch.titleEn,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp,
                                        color = if (dark) Color.White else DeepNavy
                                    )
                                    Text(
                                        if (q.isEmpty()) "${ch.bullets.size} " + t(hindi, "तथ्य", "facts") + " • ${ch.source}"
                                        else "$matchingCount / ${ch.bullets.size} " + t(hindi, "मिलते तथ्य", "matching facts"),
                                        fontSize = 11.5.sp,
                                        color = if (dark) Color(0xFF90A4AE) else Color(0xFF546E7A)
                                    )
                                }
                                Text("›", fontSize = 24.sp, color = Color(0xFF00897B))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            } else {
                OutlinedTextField(
                    value = chapterQuery,
                    onValueChange = { chapterQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    placeholder = { Text(t(hindi, "इस अध्याय में खोजें…", "Search within this chapter…")) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (chapterQuery.isNotEmpty()) {
                            IconButton(onClick = { chapterQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true
                )
                val cq = chapterQuery.trim()
                val filteredBullets = remember(chapter, cq) {
                    if (cq.isEmpty()) chapter.bullets
                    else chapter.bullets.filter { it.contains(cq, ignoreCase = true) }
                }

                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Text(
                            chapter.source + " • " + filteredBullets.size + " " + t(hindi, "तथ्य", "facts"),
                            fontSize = 12.sp,
                            color = Color(0xFF607D8B),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(filteredBullets) { bullet ->
                        NoteMarkdownRow(bullet = bullet, dark = dark, highlightQuery = cq)
                    }
                    item {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            t(
                                hindi,
                                "💡 टिप: इस अध्याय के प्रश्न 'क्विज़' टैब में 'भूगोल नोट्स क्विज़' मोड में मिलेंगे।",
                                "Tip: Questions from this chapter appear in the Quiz tab under 'Geography Notes Quiz'."
                            ),
                            fontSize = 12.5.sp, color = Color(0xFF546E7A)
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteMarkdownRow(bullet: String, dark: Boolean, highlightQuery: String) {
    val trimmed = bullet.trim()
    val boldColor = if (dark) Color(0xFF80CBC4) else Color(0xFF0B2A4A)
    val textColor = if (dark) Color(0xFFECEFF1) else Color(0xFF263238)

    // Check if the entire bullet is a standalone section header like "**कोर (Core):**" or "# Heading"
    val isPureSectionHeader = (trimmed.startsWith("**") && (trimmed.endsWith("**") || trimmed.endsWith("**:")) && trimmed.count { it == '*' } == 4) ||
        trimmed.startsWith("#")

    if (isPureSectionHeader) {
        val cleanHeader = trimmed.removePrefix("###").removePrefix("##").removePrefix("#").trim()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp)
                .background(
                    if (dark) Color(0xFF1E3545) else Color(0xFFE0F2F1),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = parseInlineMarkdown(cleanHeader, boldColor = if (dark) Color(0xFF4DB6AC) else Color(0xFF00695C), highlightQuery = highlightQuery),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (dark) Color(0xFF80CBC4) else Color(0xFF00695C)
            )
        }
        return
    }

    val numMatch = NUMBERED_ITEM_REGEX.matchEntire(trimmed)
    if (numMatch != null) {
        val num = numMatch.groupValues[1]
        val body = numMatch.groupValues[2]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (dark) Color(0xFF14202E) else Color.White,
                    RoundedCornerShape(10.dp)
                )
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(Color(0xFF00897B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = num,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = parseInlineMarkdown(body, boldColor = boldColor, highlightQuery = highlightQuery),
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = textColor,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
            )
        }
        return
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (dark) Color(0xFF152232) else Color.White
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                "•",
                color = Color(0xFF00897B),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = parseInlineMarkdown(trimmed, boldColor = boldColor, highlightQuery = highlightQuery),
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
