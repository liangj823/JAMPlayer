package com.amplayer.portable;
import android.content.Context;
import org.json.*;
final class CarMemory {
 static JSONObject read(Context c){try{return new JSONObject(c.getSharedPreferences("car-playback",0).getString("record","{}"));}catch(Exception e){return new JSONObject();}}
 static void save(Context c,JSONObject item,String scope,String playlist,long position){try{JSONObject record=new JSONObject().put("item",new JSONObject(item.toString())).put("playbackContext",scope).put("activePlaylistId",playlist).put("position","radio".equals(item.optString("type"))?0:Math.max(0,position)).put("savedAt",System.currentTimeMillis());c.getSharedPreferences("car-playback",0).edit().putString("record",record.toString()).commit();}catch(Exception ignored){}}
 static void clear(Context c){c.getSharedPreferences("car-playback",0).edit().clear().commit();}
 static JSONObject target(Context c){JSONObject record=read(c),settings=Store.load(c);try{JSONObject source=record.optJSONObject("item");if(source==null)source=settings.optJSONObject("lastPlayed");if(source==null)return null;JSONObject item=new JSONObject(source.toString());item.put("playbackContext",record.optString("playbackContext",settings.optString("playbackContext","local")));item.put("activePlaylistId",record.optString("activePlaylistId",settings.optString("activePlaylistId")));item.put("carResumePosition",record.optLong("position",0));return item;}catch(Exception e){return null;}}
}
