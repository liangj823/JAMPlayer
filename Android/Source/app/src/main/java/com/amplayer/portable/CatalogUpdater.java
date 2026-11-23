package com.amplayer.portable;
import android.app.job.*;
import android.content.*;
import android.net.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class CatalogUpdater {
 private static final int JOB=21001,MAX_BYTES=8*1024*1024;
 private static final long DAY=24L*60*60*1000;
 private static final ExecutorService executor=Executors.newSingleThreadExecutor();
 private static final AtomicBoolean running=new AtomicBoolean(false);
 interface Callback {void done(JSONObject result);}
 static JSONObject sources(Context c){try(InputStream in=c.getAssets().open("sources.json")){return new JSONObject(read(in,MAX_BYTES));}catch(Exception e){return new JSONObject();}}
 private static String read(InputStream in,int limit)throws IOException{try(ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){out.write(bytes,0,n);if(out.size()>limit)throw new IOException("size_limit");}return out.toString("UTF-8");}}
 private static JSONObject readFile(Context c,String file){try{return new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(c.getFilesDir(),file).toPath()),StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
 private static synchronized void writeFile(Context c,String file,JSONObject value)throws IOException{File temp=new File(c.getFilesDir(),file+".tmp"),target=new File(c.getFilesDir(),file);try(FileOutputStream out=new FileOutputStream(temp)){out.write(value.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(!temp.renameTo(target))throw new IOException("write_failed");}
 static JSONObject cache(Context c){JSONObject cached=readFile(c,"catalog-cache.json");if(cached.has("stations")&&cached.optInt("schemaVersion")>=2)return cached;try(InputStream in=c.getAssets().open("github-snapshot.json")){return new JSONObject(read(in,MAX_BYTES));}catch(Exception e){return new JSONObject();}}
 static JSONObject status(Context c){JSONObject value=readFile(c,"update-status.json");try{value.put("running",running.get());}catch(JSONException ignored){}return value;}
 static void configure(Context c){
  JSONObject s=Store.load(c);JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler==null)return;
  if(!s.optBoolean("autoUpdate",true)){scheduler.cancel(JOB);return;}
  boolean wifi=s.optBoolean("wifiOnly",true);String signature="daily-v2:"+wifi;
  android.content.SharedPreferences prefs=c.getSharedPreferences("update-schedule",Context.MODE_PRIVATE);
  if(signature.equals(prefs.getString("signature",""))&&scheduler.getPendingJob(JOB)!=null)return;
  JobInfo job=new JobInfo.Builder(JOB,new ComponentName(c,CatalogJobService.class)).setRequiredNetworkType(wifi?JobInfo.NETWORK_TYPE_UNMETERED:JobInfo.NETWORK_TYPE_ANY).setPeriodic(DAY,2*60*60*1000L).build();
  if(scheduler.schedule(job)==JobScheduler.RESULT_SUCCESS)prefs.edit().putString("signature",signature).apply();
 }
 static boolean allowedNetwork(Context c){JSONObject s=Store.load(c);if(!s.optBoolean("wifiOnly",true))return true;ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);return cm!=null&&cm.getActiveNetwork()!=null&&!cm.isActiveNetworkMetered();}
 static JSONObject check(Context c){JSONObject s=Store.load(c),status=status(c);if(s.optBoolean("autoUpdate",true)&&allowedNetwork(c)&&System.currentTimeMillis()-status.optLong("lastAttempt")>DAY)update(c,null);return status(c);}
 static void update(Context context,Callback callback){
  Context c=context.getApplicationContext();if(!running.compareAndSet(false,true)){if(callback!=null)callback.done(status(c));return;}
  executor.execute(()->{
   JSONObject status=readFile(c,"update-status.json");try{status.put("lastAttempt",System.currentTimeMillis());status.put("running",true);writeFile(c,"update-status.json",status);}catch(Exception ignored){}
   boolean success=false;String used="";JSONArray failures=new JSONArray();
   try{
    JSONObject prefs=Store.load(c);JSONArray providers=sources(c).getJSONArray("providers");List<JSONObject> order=new ArrayList<>();String primary=prefs.optString("primarySource","alon");
    for(int pass=0;pass<2;pass++)for(int i=0;i<providers.length();i++){JSONObject p=providers.getJSONObject(i);if(p.getString("id").equals(primary)==(pass==0)&&(pass==0||prefs.optBoolean("fallback",true)))order.add(p);}
    for(JSONObject provider:order){
     try{
      JSONArray feeds=provider.getJSONArray("feeds");List<Future<JSONArray>> jobs=new ArrayList<>();ExecutorService downloads=Executors.newFixedThreadPool(8);List<JSONObject> incoming=new ArrayList<>();int successfulFeeds=0,failedFeeds=0;long deadline=System.currentTimeMillis()+120000;
      try{for(int i=0;i<feeds.length();i++){final JSONObject feed=feeds.getJSONObject(i);jobs.add(downloads.submit(()->parseFeed(c,download(feed.getString("url")),feed,provider.getString("id"))));}
       for(Future<JSONArray> job:jobs){try{long remaining=deadline-System.currentTimeMillis();if(remaining<=0)throw new TimeoutException();JSONArray parsed=job.get(remaining,TimeUnit.MILLISECONDS);if(parsed.length()>0)successfulFeeds++;else failedFeeds++;for(int j=0;j<parsed.length();j++)incoming.add(parsed.getJSONObject(j));}catch(Exception feedFailure){failedFeeds++;job.cancel(true);}}
      }finally{for(Future<JSONArray> job:jobs)if(!job.isDone())job.cancel(true);downloads.shutdownNow();}
      JSONArray values=balanced(incoming,6000);if(successfulFeeds<Math.min(3,feeds.length())||values.length()<10)throw new IOException("insufficient_entries");
      Set<String> countries=new HashSet<>();for(int i=0;i<values.length();i++){String country=values.getJSONObject(i).optString("country");if(!country.isEmpty())countries.add(country);}
      long now=System.currentTimeMillis();JSONObject cache=new JSONObject().put("schemaVersion",2).put("revision",provider.getString("id")+":"+now).put("provider",provider.getString("id")).put("downloadedAt",now).put("complete",false).put("partial",failedFeeds>0).put("countryCount",countries.size()).put("stations",values);
      status.put("countryCount",countries.size()).put("failedFeeds",failedFeeds);
      writeFile(c,"catalog-cache.json",cache);status.put("lastSuccess",now).put("provider",provider.getString("id")).put("count",values.length()).put("ok",true).put("error","");used=provider.getString("id");success=true;break;
     }catch(Exception e){failures.put(provider.optString("id")+":"+e.getClass().getSimpleName());}
    }
    if(!success)status.put("ok",false).put("error","all_sources_failed");
   }catch(Exception e){try{status.put("ok",false).put("error","update_failed");}catch(Exception ignored){}}
   finally{running.set(false);try{status.put("running",false).put("attempts",failures);writeFile(c,"update-status.json",status);}catch(Exception ignored){}if(callback!=null)callback.done(status);}
  });
 }
 private static String download(String address)throws Exception{
  URL url=new URL(address);if(!url.getProtocol().equals("https")||!url.getHost().equals("raw.githubusercontent.com"))throw new IOException("untrusted_source");
  HttpURLConnection conn=(HttpURLConnection)url.openConnection();conn.setConnectTimeout(12000);conn.setReadTimeout(12000);conn.setRequestProperty("User-Agent","JAMPlayer/1.0.4 (personal radio player)");conn.setInstanceFollowRedirects(false);
  try{if(conn.getResponseCode()!=200)throw new IOException("http_"+conn.getResponseCode());if(conn.getContentLengthLong()>MAX_BYTES)throw new IOException("size_limit");try(InputStream in=conn.getInputStream()){return read(in,MAX_BYTES);}}finally{conn.disconnect();}
 }
 static boolean publicStream(String text){
  try{URI u=new URI(text);String scheme=u.getScheme(),host=u.getHost();if(!Arrays.asList("http","https").contains(scheme)||host==null||u.getUserInfo()!=null)return false;host=host.toLowerCase(Locale.ROOT);if(host.equals("localhost")||host.endsWith(".local")||host.startsWith("127.")||host.startsWith("10.")||host.startsWith("192.168.")||host.startsWith("169.254.")||host.equals("0.0.0.0")||host.equals("[::1]")||host.startsWith("[fc")||host.startsWith("[fd")||host.startsWith("[fe80"))return false;if(host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*"))return false;return true;}catch(Exception e){return false;}
 }
 private static String clean(String value){return value.replaceAll("[\\p{Cc}\\p{Cf}]","").trim();}
 static String regionFor(String country){return Arrays.asList("TW","JP","KR").contains(country)?country:"INT";}
 private static volatile Map<String,String> countryNames;
 static String countryCode(Context c,String value){String code=value.trim().toUpperCase(Locale.ROOT);if(code.matches("[A-Z]{2}"))return Arrays.asList("ZZ","XX").contains(code)?"":code;try{if(countryNames==null)synchronized(CatalogUpdater.class){if(countryNames==null){Map<String,String> map=new HashMap<>();try(InputStream in=c.getAssets().open("countries.json")){JSONArray values=new JSONObject(read(in,MAX_BYTES)).getJSONArray("countries");for(int i=0;i<values.length();i++){JSONObject item=values.getJSONObject(i);map.put(item.getJSONObject("names").getString("en").toLowerCase(Locale.ROOT),item.getString("code"));}}map.put("united states of america","US");map.put("usa","US");map.put("uk","GB");map.put("great britain","GB");map.put("south korea","KR");map.put("czech republic","CZ");map.put("russia","RU");map.put("turkey","TR");map.put("hong kong","HK");map.put("hong kong sar china","HK");map.put("macao","MO");map.put("macau","MO");map.put("macao sar china","MO");countryNames=map;}}return countryNames.getOrDefault(value.trim().toLowerCase(Locale.ROOT),"");}catch(Exception e){return "";}}
 private static JSONObject station(String name,String url,String country,String provider,String genre,String language)throws Exception{country=country==null?"":country.trim().toUpperCase(Locale.ROOT);if(!country.matches("[A-Z]{2}")||country.equals("ZZ")||country.equals("XX"))country="";String normalized=Normalizer.normalize(name,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");byte[] hash=MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(int i=0;i<10;i++)hex.append(String.format(Locale.ROOT,"%02x",hash[i]));return new JSONObject().put("id","remote:"+(country.isEmpty()?"INT":country)+":"+hex).put("name",name.substring(0,Math.min(200,name.length()))).put("url",url).put("country",country).put("region",regionFor(country)).put("type","radio").put("origin",provider).put("genre",genre.isEmpty()?"GitHub":genre.substring(0,Math.min(60,genre.length()))).put("language",language).put("check","source-list");}
 static JSONArray parseFeed(Context c,String body,JSONObject feed,String provider)throws Exception{String format=feed.optString("format","m3u"),country=feed.optString("country");if(format.equals("m3u"))return parseM3U(body,feed.optString("region","INT"),provider,Arrays.asList("TW","JP","KR").contains(country)?120:60,country);JSONArray values=new JSONArray();JSONObject root=new JSONObject(body);
  if(format.equals("openstations")){JSONArray items=root.getJSONArray("stations");for(int i=0;i<items.length();i++){JSONObject x=items.optJSONObject(i);if(x==null)continue;String name=clean(x.optString("name")),url=x.optString("streamURL");if(!name.isEmpty()&&publicStream(url))values.put(station(name,url,countryCode(c,x.optString("country")),provider,x.optString("genre"),x.optString("language")));}}
  else if(format.equals("webradiodb")){Iterator<String> keys=root.keys();while(keys.hasNext()){JSONObject x=root.optJSONObject(keys.next());if(x==null)continue;String name=clean(x.optString("Name")),url=x.optString("StreamUri");JSONArray genres=x.optJSONArray("Genre"),languages=x.optJSONArray("Languages");if(!name.isEmpty()&&publicStream(url))values.put(station(name,url,countryCode(c,x.optString("Country")),provider,genres==null?"":genres.optString(0),languages==null?"":languages.optString(0)));}}
  else throw new IOException("unsupported_format");return balanced(toList(values),6000);
 }
 private static List<JSONObject> toList(JSONArray values)throws Exception{List<JSONObject> items=new ArrayList<>();for(int i=0;i<values.length();i++)items.add(values.getJSONObject(i));return items;}
 static JSONArray balanced(List<JSONObject> incoming,int limit)throws Exception{Map<String,List<JSONObject>> groups=new LinkedHashMap<>();Set<String> seen=new HashSet<>(),ids=new HashSet<>();for(JSONObject x:incoming){String url=x.optString("url"),country=x.optString("country"),id=x.optString("id");List<JSONObject> group=groups.computeIfAbsent(country,k->new ArrayList<>());int cap=Arrays.asList("TW","JP","KR").contains(country)?120:60;if(group.size()<cap&&!seen.contains(url)&&(id.isEmpty()||!ids.contains(id))){group.add(x);seen.add(url);if(!id.isEmpty())ids.add(id);}}JSONArray out=new JSONArray();for(String code:Arrays.asList("TW","JP","KR"))for(JSONObject item:groups.getOrDefault(code,Collections.emptyList()))if(out.length()<limit)out.put(item);for(int round=0;round<60&&out.length()<limit;round++)for(Map.Entry<String,List<JSONObject>> entry:groups.entrySet())if(!Arrays.asList("TW","JP","KR").contains(entry.getKey())&&round<entry.getValue().size()&&out.length()<limit)out.put(entry.getValue().get(round));return out;}
 static JSONArray parseM3U(String body,String region,String provider,int limit)throws Exception{return parseM3U(body,region,provider,limit,Arrays.asList("TW","JP","KR").contains(region)?region:"");}
 static JSONArray parseM3U(String body,String region,String provider,int limit,String country)throws Exception{
  if(!body.replace("\uFEFF","").trim().startsWith("#EXTM3U"))throw new IOException("invalid_m3u");JSONArray result=new JSONArray();Set<String> names=new HashSet<>(),urls=new HashSet<>();String name=null;
  for(String raw:body.split("\\r?\\n")){String line=raw.trim();if(line.startsWith("#EXTINF:")){boolean quoted=false;int comma=-1;for(int i=0;i<line.length();i++){char ch=line.charAt(i);if(ch=='\"')quoted=!quoted;else if(ch==','&&!quoted){comma=i;break;}}name=comma>=0?clean(line.substring(comma+1)):null;}
   else if(!line.isEmpty()&&!line.startsWith("#")){if(name!=null&&!name.isEmpty()&&publicStream(line)&&urls.add(line)){JSONObject item=station(name,line,country,provider,"GitHub","");if(names.add(item.getString("id")))result.put(item);}name=null;}if(result.length()>=limit)break;
  }return result;
 }
}
