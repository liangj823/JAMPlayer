package com.amplayer.portable;
import android.content.*;import android.content.res.AssetFileDescriptor;import android.net.Uri;import android.media.MediaMetadataRetriever;import org.json.*;import java.io.*;import java.nio.*;import java.nio.charset.*;import java.util.*;
final class AudioTags {
 static JSONObject read(Context c,Uri uri,String filename)throws Exception{
  JSONObject value=new JSONObject().put("name",filename.replaceFirst("\\.[^.]+$","")).put("originalName",filename).put("path",uri.toString()).put("type","local");MediaMetadataRetriever retriever=new MediaMetadataRetriever();String encoding=Store.load(c).optString("textEncoding","auto");long duration=0;boolean rawFound=false;
  try{retriever.setDataSource(c,uri);put(value,"title",retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));put(value,"artist",retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));put(value,"album",retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));String d=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(d!=null)duration=Long.parseLong(d);}catch(Exception ignored){}finally{try{retriever.release();}catch(Exception ignored){}}
  try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in!=null){byte[] header=readN(in,10);if(header.length==10&&header[0]=='I'&&header[1]=='D'&&header[2]=='3'){int size=synchsafe(header,6);if(size>0&&size<1024*1024){byte[] body=readN(in,size),tag=new byte[10+body.length];System.arraycopy(header,0,tag,0,10);System.arraycopy(body,0,tag,10,body.length);JSONObject parsed=parseID3(tag,encoding,L10n.system());rawFound=parsed.has("title");for(String key:Arrays.asList("title","artist","album"))if(parsed.has(key))value.put(key,parsed.getString(key));}}}}
  catch(Exception ignored){}
  if(!rawFound)try(AssetFileDescriptor descriptor=c.getContentResolver().openAssetFileDescriptor(uri,"r")){if(descriptor!=null)try(FileInputStream input=new FileInputStream(descriptor.getFileDescriptor())){java.nio.channels.FileChannel channel=input.getChannel();long size=descriptor.getLength()>=0?descriptor.getLength():channel.size();if(size>=128){channel.position(descriptor.getStartOffset()+size-128);byte[] tag=readN(input,128);if(tag.length==128&&tag[0]=='T'&&tag[1]=='A'&&tag[2]=='G'){put(value,"title",decode(Arrays.copyOfRange(tag,3,33),0,encoding,L10n.system()));put(value,"artist",decode(Arrays.copyOfRange(tag,33,63),0,encoding,L10n.system()));put(value,"album",decode(Arrays.copyOfRange(tag,63,93),0,encoding,L10n.system()));}}}}catch(Exception ignored){}
  if(value.has("title"))value.put("name",value.getString("title"));value.put("duration",duration/1000.0);return value;
 }
 private static void put(JSONObject json,String key,String value)throws JSONException{if(value!=null){String clean=value.replace("\u0000","").replaceAll("[\\p{Cc}\\p{Cf}]","").trim();if(!clean.isEmpty())json.put(key,clean.substring(0,Math.min(500,clean.length())));}}
 private static byte[] readN(InputStream in,int count)throws IOException{byte[] bytes=new byte[count];int offset=0,n;while(offset<count&&(n=in.read(bytes,offset,count-offset))>0)offset+=n;return offset==count?bytes:Arrays.copyOf(bytes,offset);}
 private static int synchsafe(byte[] b,int o){return (b[o]&127)<<21|(b[o+1]&127)<<14|(b[o+2]&127)<<7|(b[o+3]&127);}
 static JSONObject parseID3(byte[] bytes,String encoding,String language)throws Exception{
  JSONObject value=new JSONObject();if(bytes.length<10||bytes[0]!='I'||bytes[1]!='D'||bytes[2]!='3')return value;int version=bytes[3],end=Math.min(bytes.length,10+synchsafe(bytes,6)),offset=10;
  if((bytes[5]&64)!=0&&end>=14){int extended=version==4?synchsafe(bytes,10):ByteBuffer.wrap(bytes,10,4).getInt()+4;offset+=extended;}
  while(offset+(version==2?6:10)<=end){int header=version==2?6:10;String id=new String(bytes,offset,version==2?3:4,StandardCharsets.ISO_8859_1);int size=version==2?((bytes[offset+3]&255)<<16|(bytes[offset+4]&255)<<8|(bytes[offset+5]&255)):version==4?synchsafe(bytes,offset+4):ByteBuffer.wrap(bytes,offset+4,4).getInt();if(size<=0||offset+header+size>end)break;String key=id.equals("TIT2")||id.equals("TT2")?"title":id.equals("TPE1")||id.equals("TP1")?"artist":id.equals("TALB")||id.equals("TAL")?"album":null;if(key!=null&&size>1){byte[] data=Arrays.copyOfRange(bytes,offset+header+1,offset+header+size);put(value,key,decode(data,bytes[offset+header]&255,encoding,language));}offset+=header+size;
  }return value;
 }
 private static String strict(byte[] data,String charset){try{return Charset.forName(charset).newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();}catch(Exception e){return null;}}
 static String decode(byte[] input,int flag,String requested,String language){
  byte[] data=input;int end=data.length;while(end>0&&data[end-1]==0&&(flag!=1&&flag!=2))end--;data=Arrays.copyOf(data,end);if(data.length==0)return "";
  if(flag==1||flag==2){String value=strict(data,flag==1?"UTF-16":"UTF-16BE");if(value!=null)return value;}
  if(data.length>=2&&((data[0]&255)==255&&(data[1]&255)==254||(data[0]&255)==254&&(data[1]&255)==255)){String value=strict(data,"UTF-16");if(value!=null)return value;}
  if(!"auto".equals(requested)){String forced=strict(data,requested);if(forced!=null)return forced;}
  String utf=strict(data,"UTF-8");if(utf!=null)return utf;
  int high=0;for(byte b:data)if((b&255)>127)high++;if(high*4<data.length)return new String(data,StandardCharsets.ISO_8859_1);
  String l=language.toLowerCase(Locale.ROOT);String preferred=l.startsWith("ja")?"Shift_JIS":l.startsWith("ko")?"EUC-KR":l.contains("tw")||l.contains("hant")||l.contains("hk")?"Big5":"GB18030";
  List<String> candidates=new ArrayList<>(Arrays.asList(preferred,"Big5","GB18030","Shift_JIS","EUC-KR"));String best=null;double bestScore=-1;
  for(String charset:candidates){String decoded=strict(data,charset);if(decoded==null)continue;int cjk=0;for(int i=0;i<decoded.length();i++){char ch=decoded.charAt(i);if(ch>='\u3400'&&ch<='\u9fff'||ch>='\u3040'&&ch<='\u30ff'||ch>='\uac00'&&ch<='\ud7af')cjk++;}double score=cjk/(double)Math.max(1,decoded.length())+(charset.equals(preferred)?.12:0);if(cjk>0&&score>bestScore){best=decoded;bestScore=score;}}
  return best!=null?best:new String(data,StandardCharsets.ISO_8859_1);
 }
}
