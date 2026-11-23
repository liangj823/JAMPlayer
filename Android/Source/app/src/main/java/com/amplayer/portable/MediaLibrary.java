package com.amplayer.portable;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.os.storage.*;
import android.provider.*;
import android.webkit.MimeTypeMap;
import org.json.*;
import java.io.*;
import java.util.*;

/** Read-only access to shared audio. No file edits or deletion. */
final class MediaLibrary {
 static String permission(){return Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;}
 static boolean musicGranted(Context c){return c.checkSelfPermission(permission())==PackageManager.PERMISSION_GRANTED;}
 static boolean allFiles(){return Build.VERSION.SDK_INT>=30&&Environment.isExternalStorageManager();}
 static boolean canRead(Context c){return musicGranted(c)||allFiles();}
 static JSONObject access(Context c){JSONObject v=new JSONObject();try{v.put("media",musicGranted(c)).put("allFiles",allFiles()).put("canRead",canRead(c)).put("allFilesSupported",Build.VERSION.SDK_INT>=30).put("asked",c.getSharedPreferences("music-access",0).getBoolean("asked",false));}catch(Exception ignored){}return v;}
 static List<File> roots(Context c){LinkedHashSet<String> paths=new LinkedHashSet<>();paths.add(Environment.getExternalStorageDirectory().getAbsolutePath());if(Build.VERSION.SDK_INT>=30){StorageManager manager=(StorageManager)c.getSystemService(Context.STORAGE_SERVICE);for(StorageVolume volume:manager.getStorageVolumes()){File dir=volume.getDirectory();if(dir!=null)paths.add(dir.getAbsolutePath());}}else for(File dir:c.getExternalFilesDirs(null)){if(dir==null)continue;String path=dir.getAbsolutePath();int at=path.indexOf("/Android/data/");if(at>0)paths.add(path.substring(0,at));}List<File> out=new ArrayList<>();for(String path:paths)out.add(new File(path));return out;}
 static boolean sharedFile(Context c,Uri uri){if(!"file".equals(uri.getScheme())||uri.getPath()==null)return false;try{String path=new File(uri.getPath()).getCanonicalPath();for(File root:roots(c)){String prefix=root.getCanonicalPath()+"/";if(path.startsWith(prefix)){String relative=path.substring(prefix.length());if(relative.equals("Android/data")||relative.startsWith("Android/data/")||relative.equals("Android/obb")||relative.startsWith("Android/obb/"))return false;return true;}}}catch(Exception ignored){}return false;}
 static boolean playable(Context c,Uri uri){return "content".equals(uri.getScheme())||"http".equals(uri.getScheme())||"https".equals(uri.getScheme())||sharedFile(c,uri);}
 private static String physicalDocument(Context c,Uri uri){try{String id=DocumentsContract.getDocumentId(uri);if(id.startsWith("raw:"))return id.substring(4);int colon=id.indexOf(':');if(colon<0)return null;String volume=id.substring(0,colon),relative=id.substring(colon+1);if(volume.equals("primary"))return new File(Environment.getExternalStorageDirectory(),relative).getAbsolutePath();for(File root:roots(c))if(root.getName().equalsIgnoreCase(volume))return new File(root,relative).getAbsolutePath();}catch(Exception ignored){}return null;}
 static Uri resolve(Context c,Uri uri){if(!canRead(c))return null;String authority=uri.getAuthority();if("media".equals(authority)&&uri.getPath()!=null&&uri.getPath().contains("/audio/media/"))return uri;
  String path=null;if(sharedFile(c,uri))path=uri.getPath();else if("com.android.externalstorage.documents".equals(authority)||"com.android.providers.downloads.documents".equals(authority))path=physicalDocument(c,uri);
  try{String selection=null;String[] args=null;if("com.android.providers.media.documents".equals(authority)){String id=DocumentsContract.getDocumentId(uri);if(id.startsWith("audio:")){selection="_id=?";args=new String[]{id.substring(6)};}}else if(path!=null){selection=MediaStore.Audio.Media.DATA+"=?";args=new String[]{path};}if(selection!=null)try(Cursor cursor=c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,new String[]{"_id"},selection,args,null)){if(cursor!=null&&cursor.moveToFirst())return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cursor.getLong(0));}}catch(Exception ignored){}
  if(path!=null){Uri file=Uri.fromFile(new File(path));if(sharedFile(c,file)&&(allFiles()||Build.VERSION.SDK_INT<=28&&musicGranted(c)))return file;}return null;
 }
 private static String clean(String value){return value==null||value.equals("<unknown>")?"":value;}
 static boolean excludedScanPath(String path){if(path==null)return false;String normalized=path.replace('\\','/').toLowerCase(Locale.ROOT);String[] parts=normalized.split("/");String previous="";for(String part:parts){if(part.startsWith("."))return true;String compact=part.replaceAll("[\\s_-]","");if(Arrays.asList("callrecord","callrecords","callrecording","callrecordings","callrecorder","callrec","phonerecordings","通話錄音","通话录音","電話錄音","电话录音").contains(compact))return true;if((compact.equals("call")||compact.equals("calls"))&&Arrays.asList("recordings","recording","sounds","soundrecorder","record").contains(previous))return true;previous=compact;}String name=parts.length==0?"":parts[parts.length-1];return name.matches("^(call[ _-]*(recording|record|rec)[ _.-].*|call[ _-][0-9]{4}.*|[通話电话話電話]+[錄录]音[ _.-].*)");}
 static JSONObject scan(Context c)throws Exception{
  if(!canRead(c))return new JSONObject().put("denied",true).put("items",new JSONArray());
  JSONArray values=new JSONArray();Set<String> seen=new HashSet<>();Set<String> seenFiles=new HashSet<>();boolean truncated=false;
  List<String> projection=new ArrayList<>(Arrays.asList(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.DISPLAY_NAME,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.DATA,MediaStore.Audio.Media.SIZE));if(Build.VERSION.SDK_INT>=29)projection.add(MediaStore.MediaColumns.RELATIVE_PATH);String[] columns=projection.toArray(new String[0]);
  try(Cursor cursor=c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,columns,null,null,MediaStore.Audio.Media.DISPLAY_NAME+" COLLATE NOCASE ASC")){
   while(cursor!=null&&cursor.moveToNext()){if(values.length()>=10000){truncated=true;break;}String uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cursor.getLong(0)).toString(),filename=clean(cursor.getString(1)),title=clean(cursor.getString(2)),path=clean(cursor.getString(6));if(!seen.add(uri))continue;String relative=Build.VERSION.SDK_INT>=29?clean(cursor.getString(8)):"";if(excludedScanPath(filename)||excludedScanPath(path.isEmpty()?relative:path))continue;
    JSONObject item=new JSONObject().put("type","local").put("path",uri).put("name",title.isEmpty()?filename.replaceFirst("\\.[^.]+$",""):title).put("title",title).put("originalName",filename).put("artist",clean(cursor.getString(3))).put("album",clean(cursor.getString(4))).put("duration",cursor.getLong(5)/1000.0).put("fileSize",cursor.getLong(7)).put("storagePath",path).put("owned",false);
    try(InputStream input=c.getContentResolver().openInputStream(Uri.parse(uri))){if(input==null||input.read()==-1)continue;}catch(IOException|SecurityException unreadable){continue;}
    values.put(item);if(!path.isEmpty())try{seenFiles.add(new File(path).getCanonicalPath());}catch(IOException ignored){}
   }
  }
  if(allFiles()){
   Deque<File> dirs=new ArrayDeque<>(roots(c));Set<String> visited=new HashSet<>();int checked=0;
   while(!dirs.isEmpty()){if(values.length()>=10000||checked++>=100000){truncated=true;break;}File dir=dirs.removeFirst();String canonical;try{canonical=dir.getCanonicalPath();if(!visited.add(canonical)||!sharedFile(c,Uri.fromFile(new File(dir,".scan"))))continue;}catch(IOException e){continue;}
    File[] children=dir.listFiles();if(children==null)continue;Arrays.sort(children,Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER));
    for(File file:children){if(excludedScanPath(file.getAbsolutePath()))continue;if(file.isDirectory()){if(!file.getName().startsWith(".")&&sharedFile(c,Uri.fromFile(new File(file,".scan"))))dirs.addLast(file);continue;}String filename=file.getName(),lower=filename.toLowerCase(Locale.ROOT);if(!lower.matches(".*\\.(mp3|m4a|aac|flac|wav|ogg|opus|aif|aiff|wma|ape|amr)$"))continue;Uri uri=Uri.fromFile(file);if(!sharedFile(c,uri)||!file.canRead())continue;try{if(!seenFiles.add(file.getCanonicalPath()))continue;}catch(IOException e){continue;}if(values.length()>=10000){truncated=true;break;}
     JSONObject item=AudioTags.read(c,uri,filename).put("storagePath",file.getAbsolutePath()).put("fileSize",file.length()).put("owned",false);values.put(item);
    }
   }
  }
  return new JSONObject().put("items",values).put("denied",false).put("truncated",truncated);
 }
}
