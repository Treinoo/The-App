package no.matvenn.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlin.math.roundToInt
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

data class Recipe(
    val name: String,
    val source: String,
    val url: String,
    val kids: Boolean,
    val quick: Boolean,
    val items: List<String>
)

data class ShoppingItem(
    val name: String,
    val done: Boolean = false
)

private val recipes = listOf(
    Recipe(
        "Kjøttkaker med kålstuing",
        "MatPrat",
        "https://www.matprat.no/oppskrifter/tradisjon/kjottkaker/",
        true, false,
        listOf("Kjøttdeig","Poteter","Hodekål","Melk","Smør","Hvetemel","Kjøttkraft","Tyttebær")
    ),
    Recipe(
        "Fiskegrateng med makaroni",
        "MatPrat",
        "https://www.matprat.no/oppskrifter/familien/fiskegrateng/",
        true, false,
        listOf("Hvit fisk","Makaroni","Melk","Egg","Smør","Purre","Hvitost")
    ),
    Recipe(
        "Pannekaker",
        "MatPrat",
        "https://www.matprat.no/oppskrifter/familien/pannekaker/",
        true, true,
        listOf("Hvetemel","Melk","Egg","Smør")
    ),
    Recipe(
        "Butter chicken",
        "MENY",
        "https://meny.no/oppskrifter/gryte/enkel-butter-chicken",
        false, true,
        listOf("Kylling","Butter chicken-base","Ris","Naan","Koriander")
    ),
    Recipe(
        "Kyllingwok med nudler",
        "MENY",
        "https://meny.no/oppskrifter/wok/kylling-med-nudler",
        false, true,
        listOf("Kylling","Nudler","Wokgrønnsaker","Sweet chilisaus")
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MatvennApp() }
    }
}

@Composable
fun MatvennApp() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var shopping by remember { mutableStateOf(listOf<ShoppingItem>()) }
    var kidsDays by remember { mutableIntStateOf(2) }
    var week by remember { mutableStateOf(listOf<Recipe>()) }
    var listening by remember { mutableStateOf(false) }
    var speechStatus by remember { mutableStateOf("Trykk mikrofonen og si flere varer.") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun addItem(raw: String) {
        val name = raw.trim().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        if (name.isBlank()) return
        if (shopping.none { it.name.equals(name, true) && !it.done }) {
            shopping = listOf(ShoppingItem(name)) + shopping
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        speechStatus = if (granted) "Mikrofontilgang gitt. Trykk mikrofonen igjen." else "Mikrofontilgang ble avslått."
    }

    fun stopSpeech() {
        listening = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        speechStatus = "Stoppet."
    }

    fun startSpeech() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speechStatus = "Talegjenkjenning er ikke tilgjengelig på denne telefonen."
            return
        }

        listening = true
        speechStatus = "Lytter … si flere varer etter hverandre."

        fun begin() {
            if (!listening) return
            recognizer?.destroy()
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = sr

            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(error: Int) {
                    if (listening) {
                        android.os.Handler(context.mainLooper).postDelayed({ begin() }, 350)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()

                    val parts = text
                        .replace(Regex("\\bog så\\b", RegexOption.IGNORE_CASE), ",")
                        .replace(Regex("\\bsamt\\b", RegexOption.IGNORE_CASE), ",")
                        .replace(Regex("\\bog\\b", RegexOption.IGNORE_CASE), ",")
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    parts.forEach(::addItem)
                    if (parts.isNotEmpty()) {
                        speechStatus = "La til: ${parts.joinToString(", ")}"
                    }

                    if (listening) {
                        android.os.Handler(context.mainLooper).postDelayed({ begin() }, 250)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "nb-NO")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            sr.startListening(intent)
        }

        begin()
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF76917A),
            background = androidx.compose.ui.graphics.Color(0xFFF7F2EA),
            surface = androidx.compose.ui.graphics.Color(0xFFFFFDF9)
        )
    ) {
        Scaffold(
            topBar = {
                Column(Modifier.padding(16.dp)) {
                    Text("Matvenn 🥕", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Mindre husking. Mindre skriving. Mer ferdig.")
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Mat & uke") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Handleliste") })
                }

                if (tab == 0) {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("✨ Ordne uka for meg", fontWeight = FontWeight.Bold)
                                    Text("Pizza, taco og rester er faste.")
                                    Spacer(Modifier.height(8.dp))
                                    Text("$kidsDays barnevennlige dager")
                                    Slider(
                                        value = kidsDays.toFloat(),
                                        onValueChange = { kidsDays = it.roundToInt().coerceIn(0,4) },
                                        valueRange = 0f..4f,
                                        steps = 3
                                    )
                                    Button(onClick = {
                                        val kids = recipes.filter { it.kids }.shuffled().take(kidsDays)
                                        val adults = recipes.filter { !it.kids }.shuffled().take((4-kids.size).coerceAtLeast(0))
                                        week = (kids + adults).distinct().take(4)
                                    }) { Text("Lag ukeplan") }
                                }
                            }
                        }

                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("⚡ Kjappe middager", fontWeight = FontWeight.Bold)
                                    recipes.filter { it.quick }.take(3).forEach { r ->
                                        Text("• ${r.name} (${r.source})")
                                    }
                                }
                            }
                        }

                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("🍽️ Ukesmeny", fontWeight = FontWeight.Bold)
                                    Text("🍕 Pizza   🌮 Taco   🥡 Rester")
                                    Spacer(Modifier.height(6.dp))
                                    week.forEach { r ->
                                        Text("• ${r.name} – ${r.source}")
                                        TextButton(onClick = {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(r.url)))
                                        }) {
                                            Text("Åpne oppskrift")
                                        }
                                        TextButton(onClick = {
                                            r.items.forEach(::addItem)
                                        }) {
                                            Text("Legg ingredienser på handlelisten")
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("🔥 Gode tilbud", fontWeight = FontWeight.Bold)
                                    Text("Valgfri liten seksjon for Cola Zero, ost og kjøttdeig. Live tilbud kobles på senere.")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("${shopping.count { !it.done }} varer", fontWeight = FontWeight.Bold)
                                    var manual by remember { mutableStateOf("") }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = manual,
                                            onValueChange = { manual = it },
                                            modifier = Modifier.weight(1f),
                                            placeholder = { Text("Skriv eller snakk …") }
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Button(onClick = {
                                            if (listening) stopSpeech() else startSpeech()
                                        }) {
                                            Text(if (listening) "⏹" else "🎤")
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Button(onClick = {
                                            addItem(manual)
                                            manual = ""
                                        }) {
                                            Text("+")
                                        }
                                    }
                                    Text(speechStatus, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        items(shopping) { item ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.done,
                                        onCheckedChange = { checked ->
                                            shopping = shopping.map {
                                                if (it === item) it.copy(done = checked) else it
                                            }
                                        }
                                    )
                                    Text(item.name, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
