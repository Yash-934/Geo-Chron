package com.geochron.atlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.geochron.atlas.data.AtlasData
import com.geochron.atlas.data.GeoFeature
import com.geochron.atlas.data.HistoryDataset
import kotlin.random.Random

data class QuizQuestion(
    val promptHi: String,
    val promptEn: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanationHi: String,
    val placeId: String,
    val mapScope: String? = null,
    val highlightRegion: String? = null,
    val feature: GeoFeature? = null,
    val empire: HistoryDataset? = null
)

data class QuizMode(val id: String, val titleHi: String, val titleEn: String, val descHi: String, val emoji: String)

val QUIZ_MODES = listOf(
    QuizMode("state", "राज्य पहचानें", "Identify the State", "मानचित्र में लाल राज्य पहचानें", "🗺️"),
    QuizMode("country", "देश पहचानें", "Identify the Country", "विश्व मानचित्र पर देश पहचानें", "🌍"),
    QuizMode("feature", "नदी और पर्वत", "Rivers & Mountains", "नारंगी रंग से दिखाई गई आकृति पहचानें", "🏔️"),
    QuizMode("empire", "साम्राज्य पहचानें", "Identify the Empire", "सीमा देखकर साम्राज्य पहचानें", "👑"),
    QuizMode("site", "ऐतिहासिक स्थल", "Historical Sites", "वर्णन पढ़कर स्थल/घटना पहचानें", "🏛️"),
    QuizMode("timeline", "समय रेखा प्रश्न", "Timeline Questions", "तिथियों और घटनाओं के प्रश्न", "🕰️"),
    QuizMode("notes", "भूगोल नोट्स क्विज़", "Geography Notes Quiz", "भूगोल अध्ययन नोट्स से बने 400+ प्रश्न", "📚")
)

private fun optionsFor(correct: String, pool: List<String>, rnd: Random): Pair<List<String>, Int> {
    val others = pool.filter { it != correct }.shuffled(rnd).take(3)
    val opts = (others + correct).shuffled(rnd)
    return opts to opts.indexOf(correct)
}

fun generateQuiz(data: AtlasData, mode: String, hindi: Boolean, seed: Int): List<QuizQuestion> {
    val rnd = Random(seed)
    val questions = ArrayList<QuizQuestion>()
    when (mode) {
        "notes" -> {
            val bank = data.notesQuiz
            val byChapter = bank.groupBy { it.ch }
            for (item in bank.shuffled(rnd).take(10)) {
                val pool = (byChapter[item.ch] ?: bank).map { it.a }.distinct()
                val (opts, ci) = optionsFor(item.a, pool, rnd)
                questions.add(
                    QuizQuestion(
                        promptHi = item.q,
                        promptEn = item.q,
                        options = opts, correctIndex = ci,
                        explanationHi = "सही उत्तर: ${item.a} — (अध्याय: ${item.ch} • भूगोल अध्ययन नोट्स)",
                        placeId = "notes_" + item.q.hashCode()
                    )
                )
            }
        }
        "state" -> {
            val regions = data.boundaries["india"]!!.regions
            val pool = regions.map { r -> if (hindi) (data.statesInfo[r.name]?.hi ?: r.name) else r.name }
            for (region in regions.shuffled(rnd).take(10)) {
                val correct = if (hindi) (data.statesInfo[region.name]?.hi ?: region.name) else region.name
                val (opts, ci) = optionsFor(correct, pool, rnd)
                val info = data.statesInfo[region.name]
                questions.add(
                    QuizQuestion(
                        promptHi = "मानचित्र में लाल रंग से दिखाया गया राज्य/केंद्र शासित प्रदेश कौन सा है?",
                        promptEn = "Which state/UT is highlighted in red on the map?",
                        options = opts, correctIndex = ci,
                        explanationHi = if (info != null) "सही उत्तर: ${info.hi} — राजधानी ${info.capitalHi}। ${info.factHi}" else "सही उत्तर: $correct",
                        placeId = "region_india_" + region.name,
                        mapScope = "india", highlightRegion = region.name
                    )
                )
            }
        }
        "country" -> {
            val regions = data.boundaries["world"]!!.regions.filter { data.countriesHi.containsKey(it.name) }
            val pool = regions.map { r -> if (hindi) data.countriesHi[r.name]!! else r.name }
            for (region in regions.shuffled(rnd).take(10)) {
                val correct = if (hindi) data.countriesHi[region.name]!! else region.name
                val (opts, ci) = optionsFor(correct, pool, rnd)
                questions.add(
                    QuizQuestion(
                        promptHi = "विश्व मानचित्र में लाल रंग से दिखाया गया देश कौन सा है?",
                        promptEn = "Which country is highlighted in red on the world map?",
                        options = opts, correctIndex = ci,
                        explanationHi = "सही उत्तर: $correct (${region.name})",
                        placeId = "region_world_" + region.name,
                        mapScope = "world", highlightRegion = region.name
                    )
                )
            }
        }
        "feature" -> {
            val feats = data.allFeatures().filter { (it.cat == "RIVER" || it.cat == "MOUNTAIN") && it.geomType == "line" }
            val byCat = feats.groupBy { it.cat }
            for (f in feats.shuffled(rnd).take(10)) {
                val pool = byCat[f.cat]!!.map { if (hindi) it.hi else it.en }
                val correct = if (hindi) f.hi else f.en
                val (opts, ci) = optionsFor(correct, pool, rnd)
                questions.add(
                    QuizQuestion(
                        promptHi = if (f.cat == "RIVER") "मानचित्र में नारंगी रंग से दिखाई गई नदी कौन सी है?" else "मानचित्र में नारंगी रंग से दिखाई गई पर्वत श्रृंखला कौन सी है?",
                        promptEn = if (f.cat == "RIVER") "Which river is shown in orange on the map?" else "Which mountain range is shown in orange on the map?",
                        options = opts, correctIndex = ci,
                        explanationHi = "सही उत्तर: ${f.hi}। ${f.factHi.ifBlank { f.infoHi }}",
                        placeId = f.id, mapScope = f.scope, feature = f
                    )
                )
            }
        }
        "empire" -> {
            val sets = data.history.filter { it.poly != null }
            val pool = sets.map { if (hindi) it.hi else it.en }
            for (ds in sets.shuffled(rnd).take(minOf(10, sets.size))) {
                val correct = if (hindi) ds.hi else ds.en
                val (opts, ci) = optionsFor(correct, pool, rnd)
                questions.add(
                    QuizQuestion(
                        promptHi = "मानचित्र में दिखाई गई सीमा (रंगीन क्षेत्र) किस साम्राज्य/काल की है?",
                        promptEn = "Which empire/period does the coloured boundary on the map belong to?",
                        options = opts, correctIndex = ci,
                        explanationHi = "सही उत्तर: ${ds.hi} (${ds.periodHi})। ${ds.summaryHi}",
                        placeId = "empire_" + ds.id,
                        mapScope = ds.scope, empire = ds
                    )
                )
            }
        }
        "site" -> {
            val allEvents = data.history.flatMap { ds -> ds.events.map { ds to it } }
            val pool = allEvents.map { if (hindi) it.second.hi else it.second.en }
            for ((ds, ev) in allEvents.shuffled(rnd).take(10)) {
                val correct = if (hindi) ev.hi else ev.en
                val (opts, ci) = optionsFor(correct, pool, rnd)
                questions.add(
                    QuizQuestion(
                        promptHi = "यह वर्णन किस ऐतिहासिक स्थल/घटना का है?\n\n\"${ev.infoHi}\"",
                        promptEn = "Which historical site/event is described?\n\n\"${ev.infoEn}\"",
                        options = opts, correctIndex = ci,
                        explanationHi = "सही उत्तर: ${ev.hi} (${ev.yearLabel})। ${ev.factHi}",
                        placeId = "event_" + ds.id + "_" + ev.en
                    )
                )
            }
        }
        "timeline" -> {
            val authored = listOf(
                Triple("कलिंग का युद्ध किस वर्ष हुआ था?", listOf("261 ईसा पूर्व", "321 ईसा पूर्व", "185 ईसा पूर्व", "326 ईसा पूर्व"), "अशोक ने 261 ईसा पूर्व में कलिंग जीता; इसके बाद उन्होंने धम्म की नीति अपनाई।"),
                Triple("पानीपत का प्रथम युद्ध कब हुआ?", listOf("1526 ई.", "1556 ई.", "1576 ई.", "1761 ई."), "1526 में बाबर ने इब्राहिम लोदी को हराया और मुगल साम्राज्य की नींव रखी।"),
                Triple("प्लासी का युद्ध कब हुआ?", listOf("1757 ई.", "1764 ई.", "1772 ई.", "1782 ई."), "1757 में क्लाइव की जीत से बंगाल में ब्रिटिश शासन की शुरुआत हुई।"),
                Triple("गांधी जी का दांडी मार्च किस वर्ष हुआ?", listOf("1930 ई.", "1920 ई.", "1942 ई.", "1917 ई."), "1930 में नमक सत्याग्रह के तहत साबरमती से दांडी तक मार्च हुआ।"),
                Triple("सिंधु घाटी सभ्यता का प्रसिद्ध बंदरगाह नगर कौन सा था?", listOf("लोथल", "हड़प्पा", "धोलावीरा", "कालीबंगा"), "लोथल (गुजरात) में गोदी (dockyard) के साक्ष्य मिले हैं।"),
                Triple("नालंदा महाविहार किस काल से विशेष रूप से जुड़ा है?", listOf("गुप्त काल", "मौर्य काल", "मुगल काल", "चोल काल"), "कुमारगुप्त प्रथम (गुप्त काल) के समय नालंदा महाविहार की स्थापना मानी जाती है।"),
                Triple("विजयनगर साम्राज्य की स्थापना कब हुई?", listOf("1336 ई.", "1206 ई.", "1526 ई.", "1674 ई."), "1336 में हरिहर और बुक्का ने विजयनगर की स्थापना की।"),
                Triple("छत्रपति शिवाजी महाराज का राज्याभिषेक कब हुआ?", listOf("1674 ई.", "1761 ई.", "1680 ई.", "1659 ई."), "1674 में रायगढ़ किले में शिवाजी महाराज का राज्याभिषेक हुआ।"),
                Triple("कुस्तुंतुनिया (Constantinople) पर उस्मानियों का अधिकार कब हुआ?", listOf("1453 ई.", "1299 ई.", "1683 ई.", "1526 ई."), "1453 में मेहमद द्वितीय ने कुस्तुंतुनिया जीता।"),
                Triple("मंगोल साम्राज्य के संस्थापक कौन थे?", listOf("चंगेज़ ख़ान", "कुबलई ख़ान", "तैमूर", "हुलागु ख़ान"), "चंगेज़ ख़ान (तेमुजिन) ने 1206 में मंगोल जनजातियों को एकजुट किया।")
            )
            for ((q, optsRaw, expl) in authored.shuffled(rnd)) {
                val correct = optsRaw.first()
                val opts = optsRaw.shuffled(rnd)
                questions.add(
                    QuizQuestion(
                        promptHi = q, promptEn = q,
                        options = opts, correctIndex = opts.indexOf(correct),
                        explanationHi = expl, placeId = "timeline_q"
                    )
                )
            }
        }
    }
    return questions
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(nav: NavController, vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val hindi by vm.hindi.collectAsState()
    val dark by vm.dark.collectAsState()
    val progress by vm.quizProgress.collectAsState()

    var mode by remember { mutableStateOf<String?>(null) }
    var seed by remember { mutableIntStateOf(1) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var finished by remember { mutableStateOf(false) }
    val wrongIds = remember { mutableListOf<String>() }

    val d = data
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t(hindi, "मैप क्विज़", "Map Quiz") + if (mode != null && !finished) "   •   Score: $score" else "") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        if (d == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
            val currentMode = mode
            if (currentMode == null) {
                Text(t(hindi, "क्विज़ का प्रकार चुनें", "Choose a quiz type"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                for (m in QUIZ_MODES) {
                    val p = progress.firstOrNull { it.mode == m.id }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            mode = m.id; seed = (System.currentTimeMillis() % 100000).toInt()
                            index = 0; score = 0; picked = null; finished = false; wrongIds.clear()
                        },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(m.emoji, fontSize = 26.sp)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(if (hindi) m.titleHi else m.titleEn, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(m.descHi, color = Color.Gray, fontSize = 12.5.sp)
                                if (p != null && p.gamesPlayed > 0) {
                                    Text(
                                        t(hindi, "सर्वश्रेष्ठ स्कोर: ${p.bestScore} • खेले: ${p.gamesPlayed} बार", "Best: ${p.bestScore} • Played: ${p.gamesPlayed}"),
                                        color = AtlasBlue, fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
                return@Column
            }

            val questions = remember(currentMode, seed, d, hindi) { generateQuiz(d, currentMode, hindi, seed) }
            if (questions.isEmpty()) {
                Text(t(hindi, "इस मोड के लिए प्रश्न उपलब्ध नहीं हैं।", "No questions available for this mode."))
                Button(onClick = { mode = null }) { Text(t(hindi, "वापस", "Back")) }
                return@Column
            }
            if (finished) {
                LaunchedEffect(finished) {
                    vm.recordQuizResult(currentMode, score, questions.size, wrongIds.toList())
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏆", fontSize = 40.sp)
                        Text(t(hindi, "क्विज़ पूर्ण!", "Quiz complete!"), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("${t(hindi, "आपका स्कोर", "Your score")}: $score / ${questions.size}", fontSize = 16.sp)
                        val best = progress.firstOrNull { it.mode == currentMode }?.bestScore ?: 0
                        Text(t(hindi, "सर्वश्रेष्ठ: ${maxOf(best, score)}", "Best: ${maxOf(best, score)}"), color = Color.Gray)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                seed = (System.currentTimeMillis() % 100000).toInt()
                                index = 0; score = 0; picked = null; finished = false; wrongIds.clear()
                            }) { Text(t(hindi, "फिर से खेलें", "Play again")) }
                            Button(onClick = { mode = null }) { Text(t(hindi, "मोड बदलें", "Change mode")) }
                        }
                        if (wrongIds.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                t(hindi, "जिन प्रश्नों में गलती हुई, वे आपकी Revision सूची में जुड़ गए हैं (बुकमार्क टैब में देखें)।", "Questions you missed were added to your Revision list (see the Bookmarks tab)."),
                                color = Color(0xFF33691E), fontSize = 12.5.sp
                            )
                        }
                    }
                }
                return@Column
            }

            val q = questions[index]
            Text("${t(hindi, "प्रश्न", "Question")} ${index + 1} / ${questions.size}", color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(if (hindi) q.promptHi else q.promptEn, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
            Spacer(Modifier.height(10.dp))

            if (q.mapScope != null) {
                val geo = d.boundaries[q.mapScope]!!
                val layers = q.empire?.poly?.let {
                    listOf(EmpireLayer(q.empire.id, if (hindi) q.empire.hi else q.empire.en, parseHexColor(q.empire.colorHex), it))
                } ?: emptyList()
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(250.dp)) {
                    AtlasMap(
                        geo = geo, dark = dark,
                        features = q.feature?.let { listOf(it) } ?: emptyList(),
                        empireLayers = layers,
                        highlightRegion = q.highlightRegion,
                        selectedFeatureId = q.feature?.id,
                        interactive = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            q.options.forEachIndexed { i, opt ->
                val isCorrect = i == q.correctIndex
                val isPicked = picked == i
                val bg = when {
                    picked == null -> Color.White
                    isCorrect -> Color(0xFFC8E6C9)
                    isPicked -> Color(0xFFFFCDD2)
                    else -> Color.White
                }
                val borderColor = when {
                    picked != null && isCorrect -> Color(0xFF2E7D32)
                    picked != null && isPicked -> Color(0xFFC62828)
                    else -> Color(0xFFCFD8DC)
                }
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .background(bg, RoundedCornerShape(12.dp))
                        .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable(enabled = picked == null) {
                            picked = i
                            if (i == q.correctIndex) score++ else wrongIds.add(q.placeId)
                        }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(opt, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (picked != null && isCorrect) Text("✓", color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        if (picked != null && isPicked && !isCorrect) Text("✗", color = Color(0xFFC62828), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                }
            }

            if (picked != null) {
                Spacer(Modifier.height(10.dp))
                val wasCorrect = picked == q.correctIndex
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (wasCorrect) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (wasCorrect) t(hindi, "✅ सही उत्तर!", "✅ Correct!") else t(hindi, "❌ गलत उत्तर", "❌ Not quite"),
                            fontWeight = FontWeight.Bold, color = if (wasCorrect) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                        Text(q.explanationHi, fontSize = 13.5.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (index + 1 >= questions.size) finished = true else { index++; picked = null }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (index + 1 >= questions.size) t(hindi, "परिणाम देखें", "See result") else t(hindi, "अगला प्रश्न →", "Next question →"))
                }
            }
        }
    }
}
