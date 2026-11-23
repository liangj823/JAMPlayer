package com.amplayer.portable;
import android.app.Activity;
import android.content.*;
import android.net.*;
import android.os.*;
import org.json.*;
import java.util.concurrent.*;
final class CarResumeController {
 private final Activity activity;private final Handler handler=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private boolean visible,pending=true,external,closed;private int generation=0;private volatile String stage="ready";private volatile boolean enabled=true;
 CarResumeController(Activity a){activity=a;IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);if(Build.VERSION.SDK_INT>=33)a.registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);else a.registerReceiver(screen,filter);}
 private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){generation++;handler.removeCallbacksAndMessages(null);PlaybackService.carCheckpoint();pending=true;if(PlaybackService.carActive())stage="active";}else {pending=true;queue();}}};
 void resume(){visible=true;if(external){external=false;return;}queue();}
 void stop(){visible=false;generation++;handler.removeCallbacksAndMessages(null);PlaybackService.carCheckpoint();if(!external)pending=true;}
 void externalUi(){external=true;generation++;handler.removeCallbacksAndMessages(null);}
 void launch(Intent intent){if(Intent.ACTION_MAIN.equals(intent.getAction())){pending=true;queue();}else manual();}
 void manual(){generation++;handler.removeCallbacksAndMessages(null);pending=false;stage="manual";}
 void settingsChanged(){enabled=Store.load(activity).optBoolean("carAutoResume",true);if(!enabled){manual();stage="off";}}
 private void queue(){if(closed||!visible||!pending||!activity.getSystemService(PowerManager.class).isInteractive())return;JSONObject settings=Store.load(activity);enabled=settings.optBoolean("carAutoResume",true);if(!enabled){stage="off";return;}if(PlaybackService.carActive()){pending=false;stage="active";return;}int token=++generation;handler.removeCallbacksAndMessages(null);stage="delay";long delay=Math.max(0,Math.min(15,settings.optInt("carResumeDelaySec",3)))*1000L;handler.postDelayed(()->check(token),delay);}
 private void check(int token){if(closed||!visible||!pending||token!=generation||!activity.getSystemService(PowerManager.class).isInteractive())return;JSONObject target=CarMemory.target(activity);if(target==null){pending=false;stage="empty";return;}
 worker.execute(()->{String ready=available(target);handler.post(()->{if(closed||!visible||!pending||token!=generation||!activity.getSystemService(PowerManager.class).isInteractive())return;if(!Store.load(activity).optBoolean("carAutoResume",true)){stage="off";return;}if(PlaybackService.carActive()){pending=false;stage="active";return;}if(!ready.isEmpty()){stage=ready;handler.postDelayed(()->check(token),2000);return;}try{stage="resuming";activity.startForegroundService(new Intent(activity,PlaybackService.class).setAction("car-resume"));pending=false;}catch(RuntimeException e){stage="blocked";pending=false;}});});}
 private String available(JSONObject item){try{boolean radio="radio".equals(item.optString("type"));JSONObject settings=Store.load(activity);String scope=item.optString("playbackContext"),key=radio?"url":"path",value=item.optString(key);JSONArray values=settings.optJSONArray(radio?("favorites".equals(RadioQueue.scope(item,settings))?"favorites":"stations"):"favoriteMusic".equals(scope)?"favorites":"localFiles");boolean found=false;if(values!=null)for(int n=0;n<values.length();n++){JSONObject x=values.optJSONObject(n);if(x!=null&&value.equals(x.optString(key)))found=true;}if(!found)return "missing";
 if("playlist".equals(scope)){boolean inPlaylist=false;JSONArray groups=settings.optJSONArray("playlists");if(groups!=null)for(int n=0;n<groups.length();n++){JSONObject group=groups.optJSONObject(n);if(group!=null&&group.optString("id").equals(item.optString("activePlaylistId"))){JSONArray paths=group.optJSONArray("paths");if(paths!=null)for(int k=0;k<paths.length();k++)if(value.equals(paths.optString(k)))inPlaylist=true;}}if(!inPlaylist)return "missing";}
 if(radio){ConnectivityManager cm=activity.getSystemService(ConnectivityManager.class);NetworkCapabilities nc=cm.getNetworkCapabilities(cm.getActiveNetwork());return nc!=null&&nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)?"":"network";}
 Uri uri=Uri.parse(value);if(!MediaLibrary.playable(activity,uri))return "media";try(android.content.res.AssetFileDescriptor fd=activity.getContentResolver().openAssetFileDescriptor(uri,"r")){return fd!=null?"":"media";}
 }catch(Exception e){return "media";}}
 String status(){try{String current=stage;if("resuming".equals(current)&&new JSONObject(PlaybackService.status()).optBoolean("playing"))current="restored";return new JSONObject().put("stage",current).put("position",CarMemory.read(activity).optLong("position",0)).put("enabled",enabled).put("online",online()).toString();}catch(Exception e){return "{}";}}
 private boolean online(){try{ConnectivityManager cm=activity.getSystemService(ConnectivityManager.class);NetworkCapabilities nc=cm.getNetworkCapabilities(cm.getActiveNetwork());return nc!=null&&nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);}catch(Exception e){return false;}}
 void destroy(){closed=true;generation++;handler.removeCallbacksAndMessages(null);worker.shutdownNow();try{activity.unregisterReceiver(screen);}catch(Exception ignored){}}
}
