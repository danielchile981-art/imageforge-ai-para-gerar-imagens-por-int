package com.fusionmind.ai
import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import kotlinx.coroutines.*
import java.io.*
class WavRecorder(private val context:Context){private var recorder:AudioRecord?=null;private var job:Job?=null;private var target:File?=null
@SuppressLint("MissingPermission") fun start(scope:CoroutineScope){if(job!=null)return;val rate=16000;val size=maxOf(AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT),4096);val out=File(context.cacheDir,"fusionmind-${System.currentTimeMillis()}.wav");target=out;header(out,rate);recorder=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size).also{it.startRecording()};job=scope.launch(Dispatchers.IO){FileOutputStream(out,true).use{f->val b=ByteArray(size);while(isActive){val n=recorder?.read(b,0,b.size)?:break;if(n>0)f.write(b,0,n)}}}}
suspend fun stop():File?{val j=job?:return target;runCatching{recorder?.stop()};j.cancel();j.join();recorder?.release();recorder=null;job=null;target?.let{finish(it)};return target}
private fun header(f:File,rate:Int){FileOutputStream(f).use{o->val h=ByteArray(44);"RIFF".toByteArray().copyInto(h,0);"WAVE".toByteArray().copyInto(h,8);"fmt ".toByteArray().copyInto(h,12);i(h,16,16);s(h,20,1);s(h,22,1);i(h,24,rate);i(h,28,rate*2);s(h,32,2);s(h,34,16);"data".toByteArray().copyInto(h,36);o.write(h)}}
private fun finish(f:File){val d=(f.length()-44).coerceAtLeast(0);RandomAccessFile(f,"rw").use{r->r.seek(4);r.write(le((36+d).toInt()));r.seek(40);r.write(le(d.toInt()))}}
private fun i(a:ByteArray,o:Int,v:Int)=le(v).copyInto(a,o);private fun s(a:ByteArray,o:Int,v:Int){a[o]=(v and 255).toByte();a[o+1]=((v ushr 8)and 255).toByte()};private fun le(v:Int)=byteArrayOf(v.toByte(),(v ushr 8).toByte(),(v ushr 16).toByte(),(v ushr 24).toByte())}
