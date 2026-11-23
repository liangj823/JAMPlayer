package com.amplayer.portable;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.*;import android.provider.OpenableColumns;import org.json.*;import java.io.*;
public class SharedMusicProvider extends ContentProvider {
 @Override public boolean onCreate(){return true;}
 private File file(Uri uri)throws FileNotFoundException{String id=uri.getLastPathSegment();if(id==null||!id.matches("[a-f0-9]{64}"))throw new FileNotFoundException("invalid music id");File file=new File(new File(getContext().getFilesDir(),"Music"),id);if(!file.isFile())throw new FileNotFoundException();return file;}
 private JSONObject meta(Uri uri){try{return new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(file(uri).getPath()+".json").toPath()),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
 @Override public String getType(Uri uri){return meta(uri).optString("mime","audio/mpeg");}
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);try{JSONObject m=meta(uri);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?m.optString("name","Shared music"):OpenableColumns.SIZE.equals(columns[i])?file(uri).length():null;cursor.addRow(row);}catch(Exception ignored){}return cursor;}
 @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
 @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
 @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
