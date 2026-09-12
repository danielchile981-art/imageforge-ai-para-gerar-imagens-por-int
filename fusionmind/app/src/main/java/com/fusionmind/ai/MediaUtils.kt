package com.fusionmind.ai
import android.content.*
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import java.io.*
fun saveGeneratedImage(context:Context,bitmap:Bitmap):String{val name="FusionMind-${System.currentTimeMillis()}.png";if(Build.VERSION.SDK_INT>=29){val v=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/FusionMind");put(MediaStore.Images.Media.IS_PENDING,1)};val u=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v)?:error("Falha ao salvar");context.contentResolver.openOutputStream(u)?.use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);context.contentResolver.update(u,v,null,null);return u.toString()};val d=File(context.getExternalFilesDir(null),"FusionMind").apply{mkdirs()};val f=File(d,name);FileOutputStream(f).use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};return f.absolutePath}
