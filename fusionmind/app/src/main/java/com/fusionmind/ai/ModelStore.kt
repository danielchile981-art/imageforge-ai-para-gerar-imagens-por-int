package com.fusionmind.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ModelStore(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    val modelDir = File(context.filesDir, "models").apply { mkdirs() }

    fun file(m: AiModel) = File(modelDir, m.fileName)
    fun companionFile(m: AiModel) = m.companionFileName?.let { File(modelDir, it) }

    fun isInstalled(m: AiModel): Boolean {
        val a = file(m).let { it.exists() && it.length() > 1024L * 1024L }
        val b = m.companionFileName == null ||
            (companionFile(m)?.let { it.exists() && it.length() > 1024L * 1024L } == true)
        return a && b
    }

    fun delete(m: AiModel) {
        runCatching { file(m).delete() }
        runCatching { File(file(m).absolutePath + ".part").delete() }
        runCatching { companionFile(m)?.delete() }
        runCatching { companionFile(m)?.let { File(it.absolutePath + ".part").delete() } }
    }

    suspend fun download(m: AiModel, onProgress: (Int, String) -> Unit) = withContext(Dispatchers.IO) {
        if (m.companionUrl == null || m.companionFileName == null) {
            downloadOne(m.url, file(m), m.sizeMb.toLong() * 1024L * 1024L) {
                onProgress(it, "Baixando ${m.title}")
            }
        } else {
            downloadOne(m.url, file(m), m.sizeMb.toLong() * 1024L * 1024L) {
                onProgress((it * 0.75f).toInt(), "Baixando ${m.title}")
            }
            downloadOne(
                m.companionUrl,
                companionFile(m)!!,
                m.companionSizeMb.toLong() * 1024L * 1024L
            ) {
                onProgress(75 + (it * 0.25f).toInt(), "Baixando visão auxiliar")
            }
        }
        onProgress(100, "Instalado")
    }

    private suspend fun downloadOne(
        url: String,
        target: File,
        fallback: Long,
        onProgress: (Int) -> Unit
    ) {
        val part = File(target.absolutePath + ".part")
        var attempt = 0

        while (true) {
            try {
                val existing = if (part.exists()) part.length() else 0L
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "FusionMindAI/1.1")
                    .apply { if (existing > 0L) header("Range", "bytes=$existing-") }
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 416) {
                        part.delete()
                        error("RESTART_DOWNLOAD")
                    }
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Resposta vazia")
                    val append = existing > 0L && response.code == 206
                    val base = if (append) existing else 0L
                    if (!append && part.exists()) part.delete()
                    val responseLength = body.contentLength()
                    val total = if (responseLength > 0L) base + responseLength else fallback
                    var done = base
                    var last = -1

                    body.byteStream().use { input ->
                        FileOutputStream(part, append).use { output ->
                            val buffer = ByteArray(512 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                done += n
                                val pct = ((done * 100L) / total.coerceAtLeast(1L)).toInt().coerceIn(0, 100)
                                if (pct != last) {
                                    last = pct
                                    onProgress(pct)
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (target.exists()) target.delete()
                check(part.renameTo(target)) { "Falha ao finalizar ${target.name}" }
                return
            } catch (t: Throwable) {
                attempt++
                if (attempt >= 3) throw t
                if (t.message == "RESTART_DOWNLOAD") part.delete()
                delay(700L * attempt)
            }
        }
    }
}
