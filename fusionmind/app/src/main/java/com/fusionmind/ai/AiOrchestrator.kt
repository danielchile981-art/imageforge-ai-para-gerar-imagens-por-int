package com.fusionmind.ai

import android.graphics.Bitmap
import com.llamatik.library.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AiOrchestrator(private val store:ModelStore){
    private var brainReady=false;private var visionReady=false;private var imageReady=false;private var voiceReady=false
    suspend fun ensureBrain()=withContext(Dispatchers.Default){if(brainReady)return@withContext true;val m=ModelCatalog.brain;if(!store.isInstalled(m))return@withContext false;LlamaBridge.updateGenerateParams(.65f,768,.92f,40,1.08f,4096,Runtime.getRuntime().availableProcessors().coerceIn(2,8),true,true,256,0);brainReady=LlamaBridge.initGenerateModel(store.file(m).absolutePath);brainReady}
    suspend fun chat(prompt:String,onDelta:(String)->Unit):String{if(!ensureBrain())error("Baixe o Qwen3 4B na aba Modelos.");return suspendCancellableCoroutine{c->val full=StringBuilder();LlamaBridge.generateWithContextStream("Você é o FusionMind AI. Responda em português quando o usuário escrever em português. Seja útil, claro e direto.","",prompt,{t->full.append(t);onDelta(t)},{if(c.isActive)c.resume(full.toString())},{e->if(c.isActive)c.resumeWithException(IllegalStateException(e))})}}
    suspend fun ensureVision()=withContext(Dispatchers.Default){if(visionReady)return@withContext true;val m=ModelCatalog.vision;if(!store.isInstalled(m))return@withContext false;val mm=store.companionFile(m)?:return@withContext false;visionReady=MultimodalBridge.initModel(store.file(m).absolutePath,mm.absolutePath);visionReady}
    suspend fun analyzeImage(bytes:ByteArray,prompt:String,onDelta:(String)->Unit):String{if(!ensureVision())error("Baixe o Gemma Vision na aba Modelos.");return suspendCancellableCoroutine{c->val full=StringBuilder();MultimodalBridge.analyzeImageBytesStream(bytes,prompt,object:GenStream{override fun onDelta(text:String){full.append(text);onDelta(text)};override fun onComplete(){if(c.isActive)c.resume(full.toString())};override fun onError(message:String){if(c.isActive)c.resumeWithException(IllegalStateException(message))}});c.invokeOnCancellation{MultimodalBridge.cancelAnalysis()}}}
    suspend fun ensureImage()=withContext(Dispatchers.Default){if(imageReady)return@withContext true;val m=ModelCatalog.image;if(!store.isInstalled(m))return@withContext false;imageReady=StableDiffusionBridge.initModel(store.file(m).absolutePath,Runtime.getRuntime().availableProcessors().coerceIn(2,8));imageReady}
    suspend fun generateImage(prompt:String):Bitmap=withContext(Dispatchers.Default){if(!ensureImage())error("Baixe o Stable Diffusion na aba Modelos.");val w=512;val h=512;val rgba=StableDiffusionBridge.txt2img(prompt,"blurry, low quality, distorted",w,h,18,7f,-1L);if(rgba.size!=w*h*4)error("Falha na geração");val pixels=IntArray(w*h);var p=0;for(i in pixels.indices){val r=rgba[p++].toInt()and 255;val g=rgba[p++].toInt()and 255;val b=rgba[p++].toInt()and 255;val a=rgba[p++].toInt()and 255;pixels[i]=(a shl 24)or(r shl 16)or(g shl 8)or b};Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)}
    suspend fun ensureVoice()=withContext(Dispatchers.Default){if(voiceReady)return@withContext true;val m=ModelCatalog.voice;if(!store.isInstalled(m))return@withContext false;voiceReady=WhisperBridge.initModel(store.file(m).absolutePath);voiceReady}
    suspend fun transcribe(wav:File)=withContext(Dispatchers.Default){if(!ensureVoice())error("Baixe o Whisper na aba Modelos.");WhisperBridge.transcribeWav(wav.absolutePath,"pt").trim()}
    fun release(){runCatching{LlamaBridge.shutdown()};runCatching{MultimodalBridge.release()};runCatching{StableDiffusionBridge.release()};runCatching{WhisperBridge.release()}}
}
