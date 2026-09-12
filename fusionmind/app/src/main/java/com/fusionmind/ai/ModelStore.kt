package com.fusionmind.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ModelStore(private val context: Context) {
    private val client=OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).build()
    val modelDir=File(context.filesDir,"models").apply{mkdirs()}
    fun file(m:AiModel)=File(modelDir,m.fileName)
    fun companionFile(m:AiModel)=m.companionFileName?.let{File(modelDir,it)}
    fun isInstalled(m:AiModel):Boolean { val a=file(m).let{it.exists()&&it.length()>1024*1024}; val b=m.companionFileName==null||(companionFile(m)?.let{it.exists()&&it.length()>1024*1024}==true); return a&&b }
    fun delete(m:AiModel){runCatching{file(m).delete()};runCatching{companionFile(m)?.delete()}}
    suspend fun download(m:AiModel,onProgress:(Int,String)->Unit)=withContext(Dispatchers.IO){
        downloadOne(m.url,file(m),m.sizeMb.toLong()*1024*1024){onProgress((it*.78f).toInt(),"Baixando ${m.title}")}
        if(m.companionUrl!=null&&m.companionFileName!=null) downloadOne(m.companionUrl,companionFile(m)!!,m.companionSizeMb.toLong()*1024*1024){onProgress((78+it*.22f).toInt(),"Baixando visão auxiliar")}
        onProgress(100,"Instalado")
    }
    private fun downloadOne(url:String,target:File,fallback:Long,onProgress:(Int)->Unit){
        val part=File(target.absolutePath+".part"); if(part.exists())part.delete()
        client.newCall(Request.Builder().url(url).header("User-Agent","FusionMindAI/1.0").build()).execute().use{r->
            if(!r.isSuccessful) error("HTTP ${r.code}"); val body=r.body?:error("Resposta vazia"); val total=if(body.contentLength()>0)body.contentLength() else fallback
            body.byteStream().use{input->FileOutputStream(part).use{out->val buf=ByteArray(256*1024);var n:Int;var done=0L;var last=-1;while(input.read(buf).also{n=it}!=-1){out.write(buf,0,n);done+=n;val pct=((done*100)/total).toInt().coerceIn(0,100);if(pct!=last){last=pct;onProgress(pct)}}}}
        }
        if(target.exists())target.delete(); check(part.renameTo(target)){"Falha ao finalizar ${target.name}"}
    }
}
