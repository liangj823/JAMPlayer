package com.amplayer.portable;
import org.json.*;import java.util.*;
final class RadioQueue {
 static String country(JSONObject item){String value=item.optString("country").toUpperCase(Locale.ROOT);if(value.matches("[A-Z]{2}")&&!"XX".equals(value)&&!"ZZ".equals(value))return value;String region=item.optString("region");return Arrays.asList("TW","JP","KR").contains(region)?region:"UNKNOWN";}
 static String scope(JSONObject item,JSONObject state){String value=item.optString("radioQueueScope",state.optString("radioQueueScope","country"));return Arrays.asList("all","country","favorites").contains(value)?value:"all";}
 static String countryScope(JSONObject item,JSONObject state){String value=item.optString("radioQueueCountry",state.optString("radioQueueCountry"));return value.matches("[A-Z]{2}")||"UNKNOWN".equals(value)?value:country(item);}
 static List<JSONObject> items(JSONObject state,String scope,String selected){JSONArray source=state.optJSONArray("favorites".equals(scope)?"favorites":"stations");List<JSONObject> result=new ArrayList<>();if(source==null)return result;Set<String> seen=new HashSet<>();for(int n=0;n<source.length();n++){JSONObject item=source.optJSONObject(n);if(item==null||!"radio".equals(item.optString("type"))||"country".equals(scope)&&!country(item).equals(selected)||!seen.add(item.optString("url")))continue;result.add(item);}return result;}
}
