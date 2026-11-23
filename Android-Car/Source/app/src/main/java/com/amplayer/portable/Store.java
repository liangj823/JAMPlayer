package com.amplayer.portable;
import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
final class Store {
 static synchronized JSONObject load(Context c) {try{return new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(c.getFilesDir(),"settings.json").toPath()),StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
 static synchronized void save(Context c,String value) throws Exception {if(value.length()>5*1024*1024)throw new IOException("備份超過 5 MB");new JSONObject(value);File file=new File(c.getFilesDir(),"settings.json"),temp=new File(c.getFilesDir(),"settings.tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(value.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(!temp.renameTo(file))throw new IOException("設定儲存失敗");}
}
