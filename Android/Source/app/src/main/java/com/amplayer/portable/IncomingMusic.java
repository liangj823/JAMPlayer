package com.amplayer.portable;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.provider.OpenableColumns;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import java.util.concurrent.*;
final class IncomingMusic {
 private static final ExecutorService worker=Executors.newSingleThreadExecutor();
 private static final java.util.concurrent.atomic.AtomicInteger processing=new java.util.concurrent.atomic.AtomicInteger();
 private static final long MAX_FILE=1024L*1024*1024;
 static void accept(Context context,Intent intent){
  String action=intent.getAction();if(!Arrays.asList(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE,Intent.ACTION_VIEW).contains(action))return;
  LinkedHashSet<Uri> uris=new LinkedHashSet<>();if(Intent.ACTION_VIEW.equals(action)&&intent.getData()!=null)uris.add(intent.getData());Object single=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(single instanceof Uri)uris.add((Uri)single);ArrayList<Uri> multiple=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(multiple!=null)uris.addAll(multiple);if(intent.getClipData()!=null)for(int i=0;i<intent.getClipData().getItemCount();i++){Uri u=intent.getClipData().getItemAt(i).getUri();if(u!=null)uris.add(u);}if(uris.isEmpty())return;
  final String batch=UUID.randomUUID().toString();Context c=context.getApplicationContext();boolean play=Intent.ACTION_VIEW.equals(action);processing.addAndGet(uris.size());final int flags=intent.getFlags();
  worker.execute(()->{for(Uri uri:uris){try{JSONObject item=reference(c,uri,flags);if(item==null)item=copy(c,uri);item.put("shareBatch",batch).put("sharePlay",play);enqueue(c,item,play,false);}catch(Exception e){enqueue(c,null,false,true);}finally{processing.decrementAndGet();}}});
 }
 static JSONObject picked(Context c,Uri uri,int flags)throws Exception{
  if(!"content".equals(uri.getScheme()))throw new IOException("content URI required");
  if((c.getPackageName()+".shared").equals(uri.getAuthority()))return AudioTags.read(c,uri,"Music").put("owned",true);
  Uri stable=MediaLibrary.resolve(c,uri);if(stable!=null){try(InputStream input=c.getContentResolver().openInputStream(stable)){if(input==null||input.read()==-1)throw new IOException("empty audio");}String name="Music";try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}return AudioTags.read(c,stable,name).put("owned",false);}
  boolean persistent=false;
  try{c.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);for(android.content.UriPermission grant:c.getContentResolver().getPersistedUriPermissions())if(grant.isReadPermission()&&uri.equals(grant.getUri())){persistent=true;break;}}catch(SecurityException ignored){}
  if(!persistent)return copy(c,uri);
  String name="Music";try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}
  // A successful metadata query alone does not prove that the audio bytes are readable.
  try(InputStream input=c.getContentResolver().openInputStream(uri)){if(input==null||input.read()==-1)throw new IOException("empty or unreadable audio");}
  return AudioTags.read(c,uri,name).put("owned",false);
 }
 private static JSONObject reference(Context c,Uri uri,int flags){
  try{String authority=uri.getAuthority();if("media".equals(authority)||"com.android.externalstorage.documents".equals(authority)||"com.android.providers.media.documents".equals(authority)||"com.android.providers.downloads.documents".equals(authority)){Uri stable=MediaLibrary.resolve(c,uri);if(stable!=null){try(InputStream input=c.getContentResolver().openInputStream(stable)){if(input==null||input.read()==-1)return null;}String name="Music";try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}return AudioTags.read(c,stable,name).put("owned",false).put("shared",true);}}}catch(Exception ignored){}

  if(!"content".equals(uri.getScheme())||(flags&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)==0)return null;String authority=uri.getAuthority()==null?"":uri.getAuthority().toLowerCase(Locale.ROOT);boolean local=authority.equals("com.android.externalstorage.documents")||authority.equals("com.android.providers.media.documents")||authority.equals("com.android.providers.downloads.documents");if(!local)return null;
  try{c.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);String name="Music";try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}return AudioTags.read(c,uri,name).put("shared",true).put("owned",false);}catch(Exception e){return null;}
 }
 private static JSONObject copy(Context c,Uri uri)throws Exception{
  if(!"content".equals(uri.getScheme()))throw new IOException("content URI required");String name="Shared music",mime=c.getContentResolver().getType(uri);long length=-1;
  try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){if(cursor!=null&&cursor.moveToFirst()){name=cursor.getString(0);length=cursor.getLong(1);}}
  if(name==null)name="Shared music";String lower=name.toLowerCase(Locale.ROOT);if((mime==null||!mime.startsWith("audio/"))&&!"application/ogg".equals(mime)&&!lower.matches(".*\\.(mp3|m4a|aac|flac|wav|ogg|opus|aif|aiff|wma)$"))throw new IOException("not audio");
  File dir=new File(c.getFilesDir(),"Music");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("storage");if(length>MAX_FILE||length>0&&length>dir.getUsableSpace()-20*1024*1024)throw new IOException("size_limit");File temp=File.createTempFile("incoming-",".part",dir);MessageDigest digest=MessageDigest.getInstance("SHA-256");
  try{
   try(InputStream in=c.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException();byte[] buffer=new byte[65536];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>MAX_FILE)throw new IOException("size_limit");digest.update(buffer,0,n);out.write(buffer,0,n);}out.getFD().sync();if(total==0)throw new IOException("empty");}
   StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b));String id=hash.toString();File target=new File(dir,id);if(target.exists())temp.delete();else if(!temp.renameTo(target))throw new IOException("save_failed");
   name=name.replaceAll("[\\p{Cc}\\p{Cf}]","");JSONObject metadata=new JSONObject().put("name",name).put("mime",mime==null?"audio/mpeg":mime);try(FileOutputStream out=new FileOutputStream(new File(dir,id+".json"))){out.write(metadata.toString().getBytes(StandardCharsets.UTF_8));}
   Uri local=Uri.parse("content://"+c.getPackageName()+".shared/"+id);
   return AudioTags.read(c,local,name).put("shared",true).put("owned",true);
  }finally{if(temp.exists())temp.delete();}
 }
 private static JSONObject read(Context c){try{return new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(c.getFilesDir(),"incoming.json").toPath()),StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
 private static synchronized void enqueue(Context c,JSONObject item,boolean play,boolean error){try{JSONObject state=read(c);JSONArray items=state.optJSONArray("items");if(items==null)items=new JSONArray();if(item!=null)items.put(item);state.put("items",items).put("play",state.optBoolean("play")||play).put("errors",state.optInt("errors")+(error?1:0));try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),"incoming.json"))){out.write(state.toString().getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}}
 static synchronized JSONObject drain(Context c){if(processing.get()>0){try{return new JSONObject().put("processing",processing.get());}catch(Exception ignored){return new JSONObject();}}JSONObject value=read(c);try{value.put("processing",processing.get());new File(c.getFilesDir(),"incoming.json").delete();}catch(Exception ignored){}return value;}
}
