package com.fusionmind.ai

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.llamatik.library.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AiOrchestrator(private val store: ModelStore) {
    private var loadedBrainId: String? = null
    private var visionReady = false
    private var imageReady = false
    private var voiceReady = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun unloadBrain() {
        if (loadedBrainId != null) runCatching { LlamaBridge.shutdown() }
        loadedBrainId = null
    }

    private fun unloadVision() {
        if (visionReady) runCatching { MultimodalBridge.release() }
        visionReady = false
    }

    private fun unloadImage() {
        if (imageReady) runCatching { StableDiffusionBridge.release() }
        imageReady = false
    }

    private fun configureBrain(m: AiModel) {
        val quality = m.id == ModelCatalog.brainQuality.id
        LlamaBridge.updateGenerateParams(
            if (quality) 0.65f else 0.55f,
            if (quality) 512 else 384,
            0.92f,
            40,
            1.08f,
            if (quality) 3072 else 2048,
            4,
            true,
            false,
            256,
            0
        )
    }

    suspend fun ensureBrain(preferQuality: Boolean = false) = withContext(Dispatchers.Default) {
        val candidates = if (preferQuality) {
            listOf(ModelCatalog.brainQuality, ModelCatalog.brainFast)
        } else {
            listOf(ModelCatalog.brainFast, ModelCatalog.brainQuality)
        }.filter { store.isInstalled(it) }

        if (candidates.isEmpty()) return@withContext false
        val desired = candidates.first()
        if (loadedBrainId == desired.id) return@withContext true

        unloadVision()
        unloadImage()
        unloadBrain()

        for (m in candidates) {
            configureBrain(m)
            val ok = runCatching { LlamaBridge.initGenerateModel(store.file(m).absolutePath) }.getOrDefault(false)
            if (ok) {
                loadedBrainId = m.id
                return@withContext true
            }
            runCatching { LlamaBridge.shutdown() }
        }
        false
    }

    suspend fun chat(
        prompt: String,
        history: String = "",
        preferQuality: Boolean = false,
        onText: (String) -> Unit
    ): String {
        if (!ensureBrain(preferQuality)) {
            error("Baixe o Qwen3 1.7B Rápido ou o Qwen3 4B na tela Modelos.")
        }

        val effectivePrompt = if (preferQuality) "/think\n$prompt" else "/no_think\n$prompt"
        return suspendCancellableCoroutine { continuation ->
            val full = StringBuilder()
            var lastUi = 0L

            LlamaBridge.generateWithContextStream(
                "Você é o FusionMind AI, um assistente local parecido com um chat moderno. Responda em português quando o usuário escrever em português. Seja útil, claro e direto. Não invente fatos quando não souber. Não exponha raciocínio interno.",
                history,
                effectivePrompt,
                { token ->
                    full.append(token)
                    val now = SystemClock.uptimeMillis()
                    if (now - lastUi >= 45L) {
                        lastUi = now
                        val snapshot = full.toString()
                        mainHandler.post { onText(snapshot) }
                    }
                },
                {
                    val result = full.toString()
                    mainHandler.post { onText(result) }
                    if (continuation.isActive) continuation.resume(result)
                },
                { message ->
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
                }
            )

            continuation.invokeOnCancellation {
                runCatching { LlamaBridge.nativeCancelGenerate() }
            }
        }
    }

    suspend fun ensureVision() = withContext(Dispatchers.Default) {
        if (visionReady) return@withContext true
        val m = ModelCatalog.vision
        if (!store.isInstalled(m)) return@withContext false
        val mm = store.companionFile(m) ?: return@withContext false

        // Evita manter dois modelos de vários GB na RAM ao mesmo tempo.
        unloadBrain()
        unloadImage()

        repeat(2) {
            val ok = runCatching {
                MultimodalBridge.initModel(store.file(m).absolutePath, mm.absolutePath)
            }.getOrDefault(false)
            if (ok) {
                visionReady = true
                return@withContext true
            }
            runCatching { MultimodalBridge.release() }
        }
        false
    }

    suspend fun analyzeImage(
        bytes: ByteArray,
        prompt: String,
        onText: (String) -> Unit
    ): String {
        if (!ensureVision()) error("Baixe o Gemma Vision na tela Modelos.")
        return suspendCancellableCoroutine { continuation ->
            val full = StringBuilder()
            var lastUi = 0L
            MultimodalBridge.analyzeImageBytesStream(bytes, prompt, object : GenStream {
                override fun onDelta(text: String) {
                    full.append(text)
                    val now = SystemClock.uptimeMillis()
                    if (now - lastUi >= 55L) {
                        lastUi = now
                        val snapshot = full.toString()
                        mainHandler.post { onText(snapshot) }
                    }
                }

                override fun onComplete() {
                    val result = full.toString()
                    mainHandler.post { onText(result) }
                    if (continuation.isActive) continuation.resume(result)
                }

                override fun onError(message: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
                }
            })
            continuation.invokeOnCancellation { runCatching { MultimodalBridge.cancelAnalysis() } }
        }
    }

    suspend fun ensureImage() = withContext(Dispatchers.Default) {
        if (imageReady) return@withContext true
        val m = ModelCatalog.image
        if (!store.isInstalled(m)) return@withContext false

        unloadBrain()
        unloadVision()

        repeat(2) {
            val ok = runCatching {
                StableDiffusionBridge.initModel(store.file(m).absolutePath, 4)
            }.getOrDefault(false)
            if (ok) {
                imageReady = true
                return@withContext true
            }
            runCatching { StableDiffusionBridge.release() }
        }
        false
    }

    suspend fun generateImage(prompt: String, quality: Boolean = false): Bitmap = withContext(Dispatchers.Default) {
        if (!ensureImage()) error("Baixe o Stable Diffusion na tela Modelos.")
        val w = if (quality) 512 else 384
        val h = w
        val steps = if (quality) 16 else 10
        val rgba = StableDiffusionBridge.txt2img(
            prompt,
            "blurry, low quality, distorted, deformed",
            w,
            h,
            steps,
            7f,
            -1L
        )
        if (rgba.size != w * h * 4) error("Falha na geração de imagem")
        val pixels = IntArray(w * h)
        var p = 0
        for (i in pixels.indices) {
            val r = rgba[p++].toInt() and 255
            val g = rgba[p++].toInt() and 255
            val b = rgba[p++].toInt() and 255
            val a = rgba[p++].toInt() and 255
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    suspend fun ensureVoice() = withContext(Dispatchers.Default) {
        if (voiceReady) return@withContext true
        val m = ModelCatalog.voice
        if (!store.isInstalled(m)) return@withContext false
        voiceReady = runCatching { WhisperBridge.initModel(store.file(m).absolutePath) }.getOrDefault(false)
        voiceReady
    }

    suspend fun transcribe(wav: File) = withContext(Dispatchers.Default) {
        if (!ensureVoice()) error("Baixe o Whisper na tela Modelos.")
        WhisperBridge.transcribeWav(wav.absolutePath, "pt").trim()
    }

    fun cancelCurrent() {
        runCatching { LlamaBridge.nativeCancelGenerate() }
        runCatching { MultimodalBridge.cancelAnalysis() }
    }

    fun release() {
        unloadBrain()
        unloadVision()
        unloadImage()
        if (voiceReady) runCatching { WhisperBridge.release() }
        voiceReady = false
    }
}
