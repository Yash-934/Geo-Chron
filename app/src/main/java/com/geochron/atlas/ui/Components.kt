package com.geochron.atlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geochron.atlas.AppViewModel
import com.geochron.atlas.data.AtlasData
import com.geochron.atlas.data.GeoFeature
import com.geochron.atlas.data.HistoryDataset
import com.geochron.atlas.data.HistoryEvent
import com.geochron.atlas.data.PlaceInfo
import com.geochron.atlas.data.RegionGeom
import com.geochron.atlas.data.categoryLabel

// ---------------- Markdown Support ----------------

private val INLINE_MD_REGEX = Regex(
    """(\*\*(.+?)\*\*|__(.+?)__|~~(.+?)~~|`([^`]+)`|\*([^*]+)\*|_([^_]+)_)"""
)

/**
 * Parses inline markdown (**bold**, __bold__, *italic*, _italic_, ~~strike~~, `code`)
 * and optional search query highlighting into a Compose AnnotatedString.
 */
fun parseInlineMarkdown(
    text: String,
    boldColor: Color = Color(0xFF0B2A4A),
    codeBg: Color = Color(0xFFE0F2F1),
    codeColor: Color = Color(0xFF00695C),
    highlightQuery: String = ""
): AnnotatedString = buildAnnotatedString {
    val normalized = text.replace("->", "→").replace("=>", "⇒")
    var lastIndex = 0
    for (match in INLINE_MD_REGEX.findAll(normalized)) {
        if (match.range.first > lastIndex) {
            append(normalized.substring(lastIndex, match.range.first))
        }
        val bold1 = match.groups[2]?.value
        val bold2 = match.groups[3]?.value
        val strike = match.groups[4]?.value
        val code = match.groups[5]?.value
        val italic1 = match.groups[6]?.value
        val italic2 = match.groups[7]?.value

        val start = length
        when {
            bold1 != null || bold2 != null -> {
                val content = bold1 ?: bold2 ?: ""
                append(content)
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, color = boldColor),
                    start,
                    length
                )
            }
            strike != null -> {
                append(strike)
                addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color.Gray),
                    start,
                    length
                )
            }
            code != null -> {
                append(" $code ")
                addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        background = codeBg,
                        color = codeColor
                    ),
                    start,
                    length
                )
            }
            italic1 != null || italic2 != null -> {
                val content = italic1 ?: italic2 ?: ""
                append(content)
                addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic),
                    start,
                    length
                )
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < normalized.length) {
        append(normalized.substring(lastIndex))
    }

    val q = highlightQuery.trim()
    if (q.length >= 2) {
        val full = toString()
        var idx = full.indexOf(q, ignoreCase = true)
        while (idx >= 0) {
            addStyle(
                SpanStyle(
                    background = Color(0xFFFFF59D),
                    color = Color(0xFF212121),
                    fontWeight = FontWeight.Bold
                ),
                idx,
                idx + q.length
            )
            idx = full.indexOf(q, startIndex = idx + q.length, ignoreCase = true)
        }
    }
}

/**
 * Multi-line & block Markdown renderer supporting:
 * - # / ## / ### Headings
 * - **bold**, *italic*, `code`, ~~strikethrough~~
 * - - / * bullet lists and 1. numbered lists
 * - > blockquotes
 * - --- horizontal dividers
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.5.sp,
    lineHeight: TextUnit = 21.sp,
    color: Color = Color.Unspecified,
    boldColor: Color = Color(0xFF0B2A4A),
    highlightQuery: String = ""
) {
    val lines = remember(markdown) { markdown.lines() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                Spacer(Modifier.height(2.dp))
                continue
            }
            when {
                line == "---" || line == "***" -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFCFD8DC))
                }
                line.startsWith("### ") -> {
                    val content = line.removePrefix("### ").trim()
                    Text(
                        text = parseInlineMarkdown(content, boldColor = boldColor, highlightQuery = highlightQuery),
                        fontSize = (fontSize.value + 1.5f).sp,
                        fontWeight = FontWeight.Bold,
                        color = AtlasBlue,
                        lineHeight = lineHeight
                    )
                }
                line.startsWith("## ") -> {
                    val content = line.removePrefix("## ").trim()
                    Text(
                        text = parseInlineMarkdown(content, boldColor = boldColor, highlightQuery = highlightQuery),
                        fontSize = (fontSize.value + 2.5f).sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepNavy,
                        lineHeight = (lineHeight.value + 2f).sp
                    )
                }
                line.startsWith("# ") -> {
                    val content = line.removePrefix("# ").trim()
                    Text(
                        text = parseInlineMarkdown(content, boldColor = boldColor, highlightQuery = highlightQuery),
                        fontSize = (fontSize.value + 4f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DeepNavy,
                        lineHeight = (lineHeight.value + 4f).sp
                    )
                }
                line.startsWith("> ") -> {
                    val content = line.removePrefix("> ").trim()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F8E9), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(AtlasTeal, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(content, boldColor = boldColor, highlightQuery = highlightQuery),
                            fontSize = fontSize,
                            fontStyle = FontStyle.Italic,
                            lineHeight = lineHeight,
                            color = color
                        )
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val content = line.substring(2).trim()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            "•",
                            color = AtlasTeal,
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSize,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(content, boldColor = boldColor, highlightQuery = highlightQuery),
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            color = color
                        )
                    }
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(line, boldColor = boldColor, highlightQuery = highlightQuery),
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        color = color
                    )
                }
            }
        }
    }
}

// ---------------- PlaceInfo builders ----------------

fun placeFromFeature(f: GeoFeature): PlaceInfo {
    val (lon, lat) = f.anchor
    return PlaceInfo(
        id = f.id, nameHi = f.hi, nameEn = f.en,
        categoryHi = categoryLabel(f.cat, true), categoryEn = categoryLabel(f.cat, false),
        infoHi = f.infoHi, infoEn = f.infoEn, factHi = f.factHi,
        dateLabel = "", source = f.source, scope = f.scope, lon = lon, lat = lat
    )
}

fun placeFromRegion(data: AtlasData, scope: String, region: RegionGeom): PlaceInfo {
    if (scope == "india") {
        val info = data.statesInfo[region.name]
        return PlaceInfo(
            id = "region_india_" + region.name,
            nameHi = info?.hi ?: region.name, nameEn = region.name,
            categoryHi = categoryLabel("STATE", true), categoryEn = categoryLabel("STATE", false),
            infoHi = if (info != null) "राजधानी: ${info.capitalHi} (${info.capitalEn})" else "",
            infoEn = if (info != null) "Capital: ${info.capitalEn}" else "",
            factHi = info?.factHi ?: "",
            dateLabel = "", source = "Natural Earth / सार्वजनिक GeoJSON डेटा",
            scope = "india", lon = region.centroid.first, lat = region.centroid.second
        )
    }
    return PlaceInfo(
        id = "region_world_" + region.name,
        nameHi = data.countriesHi[region.name] ?: region.name, nameEn = region.name,
        categoryHi = categoryLabel("COUNTRY", true), categoryEn = categoryLabel("COUNTRY", false),
        infoHi = "", infoEn = "", factHi = "",
        dateLabel = "", source = "Natural Earth (public domain)",
        scope = "world", lon = region.centroid.first, lat = region.centroid.second
    )
}

fun placeFromEvent(ds: HistoryDataset, ev: HistoryEvent): PlaceInfo = PlaceInfo(
    id = "event_" + ds.id + "_" + ev.en,
    nameHi = ev.hi, nameEn = ev.en,
    categoryHi = categoryLabel("EVENT", true), categoryEn = categoryLabel("EVENT", false),
    infoHi = ev.infoHi, infoEn = ev.infoEn, factHi = ev.factHi,
    dateLabel = ev.yearLabel, source = ds.source, scope = ds.scope, lon = ev.lon, lat = ev.lat
)

fun placeFromDataset(ds: HistoryDataset): PlaceInfo {
    val (lon, lat) = ds.poly?.firstOrNull() ?: (0.0 to 0.0)
    return PlaceInfo(
        id = "empire_" + ds.id, nameHi = ds.hi, nameEn = ds.en,
        categoryHi = categoryLabel("EMPIRE", true), categoryEn = categoryLabel("EMPIRE", false),
        infoHi = ds.summaryHi + "\n\n⚠ सीमा की सटीकता: " + ds.confidence,
        infoEn = ds.summaryEn,
        factHi = "", dateLabel = ds.periodHi, source = ds.source,
        scope = ds.scope, lon = lon, lat = lat
    )
}

fun parseHexColor(hex: String): Color = runCatching {
    val v = hex.removePrefix("#").toLong(16)
    Color(((v shr 16) and 0xFF).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt())
}.getOrDefault(Color(0xFFF2994A))

// ---------------- Info bottom sheet ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSheet(vm: AppViewModel) {
    val place by vm.selected.collectAsState()
    val hindi by vm.hindi.collectAsState()
    val bookmarkIds by vm.bookmarkIds.collectAsState()
    val current = place ?: return
    val sheetState = rememberModalBottomSheetState()
    var showNoteDialog by remember(current.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = { vm.select(null) }, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(current.nameHi, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text(current.nameEn, color = Color.Gray, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                if (hindi) current.categoryHi else current.categoryEn,
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(AtlasBlue, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            if (current.dateLabel.isNotBlank()) InfoRow(Icons.Filled.Schedule, t(hindi, "समय / काल", "Period"), current.dateLabel)
            val info = if (hindi) current.infoHi else current.infoEn
            if (info.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                MarkdownText(markdown = info, fontSize = 14.5.sp, lineHeight = 21.sp)
            }
            if (current.factHi.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFFFF4D6), RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFE0A526))
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(t(hindi, "महत्वपूर्ण तथ्य", "Important fact"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        MarkdownText(markdown = current.factHi, fontSize = 13.5.sp, lineHeight = 19.sp)
                    }
                }
            }
            if (current.source.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                InfoRow(Icons.Filled.Place, t(hindi, "स्रोत", "Source"), current.source)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val isBookmarked = bookmarkIds.contains(current.id)
                Button(onClick = { vm.toggleBookmark(current) }, modifier = Modifier.weight(1f)) {
                    Icon(if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = null)
                    Text(
                        if (isBookmarked) t(hindi, "सेव है", "Saved") else t(hindi, "बुकमार्क करें", "Add to Bookmarks"),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                OutlinedButton(onClick = { showNoteDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.EditNote, contentDescription = null)
                    Text(t(hindi, "नोट लिखें", "Add note"), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }

    if (showNoteDialog) {
        var title by remember { mutableStateOf(current.nameHi) }
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text(t(hindi, "मेरा नोट", "My note")) },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text(t(hindi, "शीर्षक", "Title")) }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        text,
                        { text = it },
                        label = { Text(t(hindi, "नोट लिखें (Markdown समर्थित)…", "Write note (Markdown supported)…")) },
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
                    if (text.isNotBlank()) vm.saveNote(title, text, current.id)
                    showNoteDialog = false
                }) { Text(t(hindi, "सेव", "Save")) }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text(t(hindi, "रद्द", "Cancel")) } }
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
        Icon(icon, contentDescription = null, tint = AtlasBlue)
        Column(Modifier.padding(start = 10.dp)) {
            Text(label, color = Color.Gray, fontSize = 12.sp)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
