package com.fusionmind.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var ai: AiOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ModelStore(this)
        ai = AiOrchestrator(store)
        val recorder = WavRecorder(this)
        setContent { FusionMindApp(store, ai, recorder) }
    }

    override fun onDestroy() {
        if (::ai.isInitialized) ai.release()
        super.onDestroy()
    }
}

data class ChatLine(val role: String, val text: String, val image: Bitmap? = null)
enum class Page { CHAT, MODELS }

private val welcomeMessage = ChatLine(
    "assistant",
    "Olá! Eu sou o FusionMind AI. Posso conversar, analisar fotos, ouvir sua voz, escrever código e gerar imagens sem API paga."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FusionMindApp(store: ModelStore, ai: AiOrchestrator, recorder: WavRecorder) {
    var page by remember { mutableStateOf(Page.CHAT) }
    var dark by remember { mutableStateOf(true) }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        when (page) {
            Page.CHAT -> ChatScreen(
                ai = ai,
                recorder = recorder,
                onModels = { page = Page.MODELS },
                dark = dark,
                onToggleTheme = { dark = !dark }
            )
            Page.MODELS -> ModelsScreen(store = store, onBack = { page = Page.CHAT })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    ai: AiOrchestrator,
    recorder: WavRecorder,
    onModels: () -> Unit,
    dark: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var attachedImage by remember { mutableStateOf<Uri?>(null) }
    var preferQuality by remember { mutableStateOf(loadQualityPreference(context)) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    var messages by remember { mutableStateOf(loadHistory(context).ifEmpty { listOf(welcomeMessage) }) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { attachedImage = it }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) {
            recorder.start(scope)
            recording = true
        }
    }

    fun replaceAssistant(index: Int, text: String, bitmap: Bitmap? = null) {
        if (index !in messages.indices) return
        messages = messages.toMutableList().also { it[index] = ChatLine("assistant", text, bitmap) }
    }

    fun newChat() {
        ai.cancelCurrent()
        currentJob?.cancel()
        currentJob = null
        busy = false
        attachedImage = null
        input = ""
        messages = listOf(welcomeMessage)
        saveHistory(context, messages)
    }

    fun sendMessage() {
        val prompt = input.trim()
        if (prompt.isBlank() || busy) return

        val history = messages.takeLast(12).joinToString("\n") {
            val label = if (it.role == "user") "Usuário" else "Assistente"
            "$label: ${it.text.take(1800)}"
        }
        val imageUri = attachedImage
        messages = messages + ChatLine("user", prompt)
        input = ""
        attachedImage = null
        messages = messages + ChatLine("assistant", "")
        val assistantIndex = messages.lastIndex
        busy = true

        currentJob = scope.launch {
            try {
                if (imageUri != null) {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                            ?: error("Não consegui abrir a imagem")
                    }
                    val final = ai.analyzeImage(bytes, prompt) { full -> replaceAssistant(assistantIndex, full) }
                    replaceAssistant(assistantIndex, final)
                } else if (wantsGeneratedImage(prompt)) {
                    replaceAssistant(assistantIndex, if (preferQuality) "Criando imagem em qualidade alta…" else "Criando imagem rápida…")
                    val bitmap = ai.generateImage(prompt, preferQuality)
                    val path = saveGeneratedImage(context, bitmap)
                    replaceAssistant(assistantIndex, "Imagem criada offline • $path", bitmap)
                } else {
                    val final = ai.chat(prompt, history, preferQuality) { full ->
                        replaceAssistant(assistantIndex, full)
                    }
                    replaceAssistant(assistantIndex, final)
                }
                saveHistory(context, messages)
            } catch (_: CancellationException) {
                replaceAssistant(assistantIndex, "Geração interrompida.")
            } catch (t: Throwable) {
                replaceAssistant(
                    assistantIndex,
                    "Não consegui concluir. ${t.message ?: "Tente novamente."}\n\nDica: use o modo Rápido ou feche outros apps se o aparelho estiver com pouca memória."
                )
            } finally {
                busy = false
                currentJob = null
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) runCatching { listState.animateScrollToItem(messages.lastIndex) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FusionMind AI", fontWeight = FontWeight.Bold)
                        Text(
                            if (preferQuality) "Qualidade • local" else "Rápido • local",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        preferQuality = !preferQuality
                        saveQualityPreference(context, preferQuality)
                    }) { Text(if (preferQuality) "⚡ Rápido" else "🧠 Qualidade") }
                    IconButton(onClick = ::newChat) { Text("＋") }
                    IconButton(onClick = onModels) { Text("⚙") }
                    IconButton(onClick = onToggleTheme) { Text(if (dark) "☀" else "☾") }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                    if (attachedImage != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📷 Foto anexada", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { attachedImage = null }) { Text("Remover") }
                        }
                    }
                    if (recording) Text("● Gravando… toque no microfone para concluir", style = MaterialTheme.typography.labelSmall)
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { picker.launch("image/*") }, enabled = !busy) { Text("＋") }
                        IconButton(
                            onClick = {
                                if (!recording) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        recorder.start(scope)
                                        recording = true
                                    } else {
                                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    currentJob = scope.launch {
                                        try {
                                            val file = recorder.stop()
                                            recording = false
                                            if (file != null) input = ai.transcribe(file)
                                        } catch (t: Throwable) {
                                            input = ""
                                        }
                                    }
                                }
                            },
                            enabled = !busy
                        ) { Text(if (recording) "■" else "🎤") }

                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Mensagem para o FusionMind…") },
                            maxLines = 6,
                            shape = RoundedCornerShape(24.dp)
                        )

                        FilledIconButton(
                            onClick = {
                                if (busy) {
                                    ai.cancelCurrent()
                                    currentJob?.cancel()
                                } else sendMessage()
                            },
                            enabled = busy || input.isNotBlank()
                        ) { Text(if (busy) "■" else "➤") }
                    }
                    Text(
                        if (preferQuality) "Qwen 4B quando instalado • respostas melhores, mais lentas" else "Qwen 1.7B quando instalado • menor RAM e resposta mais rápida",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { MessageBubble(it) }
            if (busy) item {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 48.dp))
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatLine) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (message.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 620.dp)
        ) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (message.text.isBlank()) "Pensando…" else message.text)
                message.image?.let {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = "Imagem gerada",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelsScreen(store: ModelStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var downloading by remember { mutableStateOf<String?>(null) }
    var pct by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    refresh

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modelos offline", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Text("‹") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Instale somente o que usar. O modo Rápido é recomendado para o dia a dia.")
                Text("Se um download cair, toque em Baixar novamente: ele tenta continuar da parte já recebida.", style = MaterialTheme.typography.bodySmall)
            }
            items(ModelCatalog.all) { model ->
                val installed = store.isInstalled(model)
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (model.kind) {
                                    EngineKind.BRAIN -> "🧠"
                                    EngineKind.VISION -> "👁"
                                    EngineKind.IMAGE -> "🎨"
                                    EngineKind.VOICE -> "🎙"
                                },
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(model.title, fontWeight = FontWeight.Bold)
                                Text(model.subtitle, style = MaterialTheme.typography.bodySmall)
                                Text("~${model.sizeMb + model.companionSizeMb} MB", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(if (installed) "✓" else "○")
                        }

                        if (downloading == model.id) {
                            LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                            Text("$pct% • $status")
                        }

                        if (!installed) {
                            Button(
                                onClick = {
                                    downloading = model.id
                                    pct = 0
                                    scope.launch {
                                        try {
                                            store.download(model) { p, text -> pct = p; status = text }
                                        } catch (t: Throwable) {
                                            status = "Falhou: ${t.message}. Toque em Baixar para retomar."
                                        }
                                        downloading = null
                                        refresh++
                                    }
                                },
                                enabled = downloading == null
                            ) { Text("Baixar") }
                        } else {
                            OutlinedButton(onClick = { store.delete(model); refresh++ }) { Text("Remover") }
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Text("Estabilidade: o app mantém apenas um motor pesado carregado por vez para reduzir travamentos por falta de memória.")
            }
        }
    }
}

private fun wantsGeneratedImage(prompt: String): Boolean {
    val p = prompt.lowercase()
    return listOf(
        "gere uma imagem",
        "gerar uma imagem",
        "crie uma imagem",
        "criar uma imagem",
        "faça uma imagem",
        "desenhe",
        "gere uma foto",
        "crie uma foto"
    ).any { it in p }
}

private fun historyPrefs(context: Context) = context.getSharedPreferences("fusionmind_chat", Context.MODE_PRIVATE)

private fun loadHistory(context: Context): List<ChatLine> = runCatching {
    val raw = historyPrefs(context).getString("messages", null) ?: return@runCatching emptyList()
    val array = JSONArray(raw)
    buildList {
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            add(ChatLine(obj.optString("role", "assistant"), obj.optString("text", "")))
        }
    }
}.getOrDefault(emptyList())

private fun saveHistory(context: Context, messages: List<ChatLine>) {
    runCatching {
        val array = JSONArray()
        messages.takeLast(50).filter { it.text.isNotBlank() }.forEach {
            array.put(JSONObject().put("role", it.role).put("text", it.text))
        }
        historyPrefs(context).edit().putString("messages", array.toString()).apply()
    }
}

private fun loadQualityPreference(context: Context) =
    historyPrefs(context).getBoolean("quality_mode", false)

private fun saveQualityPreference(context: Context, value: Boolean) {
    historyPrefs(context).edit().putBoolean("quality_mode", value).apply()
}
