package com.amplayer.portable;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlaybackService extends Service {
 private static PlaybackService current;
 private androidx.media3.exoplayer.ExoPlayer player;
 private long bufferingSince=0;
 private android.graphics.Bitmap artwork;
 private MediaSession mediaSession;
 private AudioManager audioManager;
 private AudioFocusRequest focus;
 private JSONObject item;
 private volatile boolean preparing=false;
 private String error="",errorKey="",liveTitle="",liveArtist="";
 private long sleepDeadline=0,backgroundDeadline=0;
 private static boolean appForeground=false;
 private static final long BACKGROUND_LIMIT=60L*60*1000;
 private long lastCountdown=-1;
 private boolean resumeOnFocus=false;
 private int retryCount=0;
 private String playbackContext="local",activePlaylistId="";
 private String radioQueueScope="all",radioQueueCountry="";private long navigationRevision=0;
 private final Deque<String> shuffleBack=new ArrayDeque<>(),shuffleForward=new ArrayDeque<>();
 private final Handler handler=new Handler(Looper.getMainLooper());
 private Runnable retry;
 private static volatile String snapshot="{}";
 private JSONObject radioMemory;
 private long carCheckpointAt=0,carPendingPosition=0;private boolean carMemoryEnabled=true;
 static boolean carActive(){return current!=null&&(current.isPlaying()||current.preparing);}
 static void carCheckpoint(){if(current!=null)current.checkpointCar(true);}
 static void carSleep(){if(current!=null){current.checkpointCar(true);current.pause();}}
 static void carForget(Context c){if(current!=null)current.carMemoryEnabled=false;CarMemory.clear(c);}
 private void checkpointCar(boolean force){if(!carMemoryEnabled||item==null||(!force&&SystemClock.elapsedRealtime()-carCheckpointAt<5000))return;long position=carPendingPosition;try{if(player!=null&&!preparing)position=player.getCurrentPosition();}catch(Exception ignored){}CarMemory.save(this,item,playbackContext,activePlaylistId,position);carCheckpointAt=SystemClock.elapsedRealtime();}

 private final BroadcastReceiver carPower=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))carCheckpoint();}};
 private final BroadcastReceiver noisy=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){pause();}};
 @Override public void onCreate(){
  super.onCreate();current=this;L10n.refresh(this);radioMemory=Store.load(this).optJSONObject("lastRadioPlayed");
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("playback",L10n.text(this,"playback"),NotificationManager.IMPORTANCE_LOW));
  audioManager=getSystemService(AudioManager.class);
  focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes()).setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS){resumeOnFocus=false;pause();}else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){resumeOnFocus=isPlaying();if(isPlaying())player.pause();updateSnapshot();notifyState();}else if(change==AudioManager.AUDIOFOCUS_GAIN){if(resumeOnFocus){resumeOnFocus=false;resume();}setVolume((float)Store.load(this).optDouble("volume",.8));}else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){setVolume(.15f);}}).build();
  mediaSession=new MediaSession(this,"JAMPlayer");mediaSession.setCallback(new MediaSession.Callback(){@Override public void onPlay(){resume();}@Override public void onPause(){pause();}@Override public void onSkipToNext(){change(1);}@Override public void onSkipToPrevious(){change(-1);}@Override public void onSeekTo(long pos){seek(pos);}@Override public void onStop(){stopSelf();}});mediaSession.setActive(true);
  if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
  if(Build.VERSION.SDK_INT>=33)registerReceiver(carPower,new IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(carPower,new IntentFilter(Intent.ACTION_SCREEN_OFF));
  handler.post(tick);
 }
 private static AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();}
 private final Runnable tick=new Runnable(){public void run(){if(preparing&&bufferingSince>0&&SystemClock.elapsedRealtime()-bufferingSince>25000){fail("電台連線逾時");}
 if(backgroundDeadline>0&&SystemClock.elapsedRealtime()>=backgroundDeadline){stopSelf();return;}if(sleepDeadline>0&&SystemClock.elapsedRealtime()>=sleepDeadline){sleepDeadline=0;pause();}updateSnapshot();checkpointCar(false);handler.postDelayed(this,500);}};
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  String action=intent==null?"":intent.getAction();
  // The foreground notification must exist before requesting focus or preparing.
  if("item".equals(action)||"play".equals(action)||"car-resume".equals(action)){if(Build.VERSION.SDK_INT>=29)startForeground(1,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(1,notification());}
  try{
   if("item".equals(action)){retryCount=0;shuffleBack.clear();shuffleForward.clear();play(new JSONObject(intent.getStringExtra("item")));}
   else if("car-resume".equals(action)){if(!carActive()){JSONObject target=CarMemory.target(this);if(target!=null)play(target);else stopSelf();}}
   else if("play".equals(action))resume();
   else if("pause".equals(action))pause();
   else if("next".equals(action))change(1);
   else if("previous".equals(action))change(-1);
   else if("stop".equals(action))stopSelf();
   else if("sleep".equals(action)){double minutes=intent.getDoubleExtra("sleepMinutes",0);sleepDeadline=minutes>0?SystemClock.elapsedRealtime()+(long)(minutes*60000):0;updateSnapshot();notifyState();}
  }catch(Exception e){fail("無法播放此音訊");}
  return START_NOT_STICKY;
 }
 private void releasePlayer(){if(retry!=null){handler.removeCallbacks(retry);retry=null;}preparing=false;bufferingSince=0;if(player!=null){player.release();player=null;}}
 private void play(JSONObject value){
  checkpointCar(true);long resumeAt=value.optLong("carResumePosition",0);value.remove("carResumePosition");releasePlayer();carPendingPosition=resumeAt;carMemoryEnabled=true;artwork=null;item=value;JSONObject queueSettings=Store.load(this);if("radio".equals(value.optString("type"))){radioQueueScope=RadioQueue.scope(value,queueSettings);radioQueueCountry=RadioQueue.countryScope(value,queueSettings);try{value.put("radioQueueScope",radioQueueScope);value.put("radioQueueCountry",radioQueueCountry);}catch(JSONException ignored){}}playbackContext="radio".equals(value.optString("type"))?"radio":"playlist".equals(value.optString("playbackContext",queueSettings.optString("playbackContext")))?"playlist":"favoriteMusic".equals(value.optString("playbackContext",queueSettings.optString("playbackContext")))?"favoriteMusic":"local";if("playlist".equals(playbackContext))activePlaylistId=value.optString("activePlaylistId",queueSettings.optString("activePlaylistId"));error="";errorKey="";liveTitle="";liveArtist="";if("favoriteMusic".equals(playbackContext)||"playlist".equals(playbackContext)||"radio".equals(playbackContext)&&"favorites".equals(radioQueueScope)){List<JSONObject> allowed=playQueue(queueSettings);if(allowed.isEmpty()){stopSelf();return;}if(allowed.stream().noneMatch(x->x.optString("radio".equals(playbackContext)?"url":"path").equals(item.optString("radio".equals(playbackContext)?"url":"path")))){JSONObject first=allowed.get(0);try{first.put("playbackContext",playbackContext);first.put("activePlaylistId",activePlaylistId);}catch(JSONException ignored){}play(first);return;}}preparing=true;armBackground();updateSnapshot();notifyState();
  if("radio".equals(value.optString("type")))radioMemory=value;JSONObject settings=Store.load(this);try{settings.put("lastPlayed",value);if(radioMemory!=null)settings.put("lastRadioPlayed",radioMemory);if("local".equals(value.optString("type"))){settings.put("lastLocalPlayed",value);settings.put("lastLocalPlaylistId","playlist".equals(playbackContext)?activePlaylistId:"");}if("radio".equals(playbackContext)){settings.put("radioQueueScope",radioQueueScope);settings.put("radioQueueCountry",radioQueueCountry);}settings.put("playbackContext",playbackContext);settings.put("activePlaylistId",activePlaylistId);Store.save(this,settings.toString());}catch(Exception ignored){}
  String source="radio".equals(value.optString("type"))?value.optString("url"):value.optString("path");
  Uri uri=Uri.parse(source);String scheme=uri.getScheme();
  if(!MediaLibrary.playable(this,uri)){fail("請在手機重新加入本機音樂");return;}
  if(audioManager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){fail("其他應用程式正在使用音訊，請稍後重試");return;}
  try{
   androidx.media3.datasource.DefaultHttpDataSource.Factory http=new androidx.media3.datasource.DefaultHttpDataSource.Factory()
    .setUserAgent("JAMPlayer-Car/1.0.0 (Android)").setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(10000).setReadTimeoutMs(10000);
   androidx.media3.datasource.DefaultDataSource.Factory data=new androidx.media3.datasource.DefaultDataSource.Factory(this,http);
   player=new androidx.media3.exoplayer.ExoPlayer.Builder(this).setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(data)).build();
   final androidx.media3.exoplayer.ExoPlayer active=player;
   player.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC).build(),false);
   player.setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK);
   player.addListener(new androidx.media3.common.Player.Listener(){
    @Override public void onPlaybackStateChanged(int state){if(player!=active)return;
     if(state==androidx.media3.common.Player.STATE_READY){preparing=false;bufferingSince=0;retryCount=0;carPendingPosition=0;checkpointCar(true);updateSnapshot();notifyState();}
     else if(state==androidx.media3.common.Player.STATE_BUFFERING){preparing=true;if(bufferingSince==0)bufferingSince=SystemClock.elapsedRealtime();updateSnapshot();notifyState();}
     else if(state==androidx.media3.common.Player.STATE_ENDED){if("local".equals(item.optString("type"))){JSONObject queueState=Store.load(PlaybackService.this);if("one".equals(playMode(queueState))&&playQueue(queueState).stream().anyMatch(x->x.optString("path").equals(item.optString("path")))){active.seekTo(0);active.play();}else change(1);}else fail("電台連線中斷");}
    }
    @Override public void onIsPlayingChanged(boolean value){if(player==active){updateSnapshot();notifyState();}}
    @Override public void onPlayerError(androidx.media3.common.PlaybackException e){if(player==active){if(item!=null&&"local".equals(item.optString("type"))){String code=e.getErrorCodeName();boolean access=false;for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof SecurityException||cause instanceof java.io.FileNotFoundException){access=true;break;}failLocal(access||code.contains("NO_PERMISSION")||code.contains("FILE_NOT_FOUND")?"localAccess":code.contains("DECOD")||code.contains("PARSING")?"localFormat":"localRead");}else fail("無法播放，請確認電台網址、網路或檔案");}}
    @Override public void onMediaMetadataChanged(androidx.media3.common.MediaMetadata tags){if(player!=active)return;if(tags.title!=null)liveTitle=tags.title.toString();if(tags.artist!=null)liveArtist=tags.artist.toString();if(tags.artworkData!=null&&tags.artworkData.length<4*1024*1024){android.graphics.Bitmap cover=decodeNotificationCover(tags.artworkData);if(cover!=null)artwork=cover;}updateSnapshot();notifyState();}
   });
   player.setVolume((float)Store.load(this).optDouble("volume",.8));bufferingSince=SystemClock.elapsedRealtime();
   player.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri));if(!"radio".equals(value.optString("type"))&&resumeAt>0)player.seekTo(resumeAt);checkpointCar(true);player.prepare();player.play();
  }catch(Exception e){fail("無法開啟音訊，請確認網址或重新加入音樂");}
 }
 private void failLocal(String key){releasePlayer();errorKey=key;error=L10n.text(this,key);updateSnapshot();notifyState();}
 private void fail(String message){
  releasePlayer();errorKey=message.startsWith("其他")?"focus":message.startsWith("請在手機")?"local":"connection";error=L10n.text(this,errorKey);updateSnapshot();notifyState();
  if(item!=null&&"radio".equals(item.optString("type"))&&retryCount<3){retryCount++;updateSnapshot();retry=()->{retry=null;if(item!=null)play(item);};handler.postDelayed(retry,2000L*retryCount);}
 }
 private boolean isPlaying(){try{return player!=null&&!preparing&&player.isPlaying();}catch(Exception e){return false;}}
 private void pause(){checkpointCar(true);resumeOnFocus=false;if(retry!=null){handler.removeCallbacks(retry);retry=null;}if(preparing)releasePlayer();else if(isPlaying())player.pause();audioManager.abandonAudioFocusRequest(focus);updateSnapshot();notifyState();}
 private void armBackground(){if(!Store.load(this).optBoolean("backgroundAutoOff",false)){backgroundDeadline=0;return;}if(!appForeground&&backgroundDeadline==0)backgroundDeadline=SystemClock.elapsedRealtime()+BACKGROUND_LIMIT;}
 static void refreshBackgroundPolicy(){if(current==null)return;if(appForeground||!Store.load(current).optBoolean("backgroundAutoOff",false))current.backgroundDeadline=0;else if(current.isPlaying()||current.preparing)current.armBackground();current.updateSnapshot();current.notifyState();}
 static void setForeground(boolean value){appForeground=value;if(current!=null){if(value)current.backgroundDeadline=0;else if(current.isPlaying()||current.preparing)current.armBackground();current.updateSnapshot();current.notifyState();}}
 private void resume(){armBackground();if(item==null){JSONObject last=Store.load(this).optJSONObject("lastPlayed");if(last!=null)play(last);else stopSelf();return;}if(("favoriteMusic".equals(playbackContext)||"playlist".equals(playbackContext))&&playQueue(Store.load(this)).stream().noneMatch(x->x.optString("path").equals(item.optString("path")))){change(1);return;}if(player==null){retryCount=0;play(item);return;}if(!preparing&&audioManager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){player.play();error="";updateSnapshot();notifyState();}}
 private JSONObject activePlaylist(JSONObject state){JSONArray groups=state.optJSONArray("playlists");if(groups!=null)for(int i=0;i<groups.length();i++){JSONObject p=groups.optJSONObject(i);if(p!=null&&activePlaylistId.equals(p.optString("id")))return p;}return new JSONObject();}
 private String playMode(JSONObject state){String value="playlist".equals(playbackContext)?activePlaylist(state).optString("mode","list"):state.optString("favoriteMusic".equals(playbackContext)?"favoriteMusicMode":"localPlayMode",!"favoriteMusic".equals(playbackContext)&&state.optBoolean("loopOne")?"one":"list");return "one".equals(value)?"one":"shuffle".equals(value)&&!"favoriteMusic".equals(playbackContext)?"shuffle":"list";}
 private List<JSONObject> playQueue(JSONObject state){
  boolean radio="radio".equals(playbackContext);if(radio)return RadioQueue.items(state,radioQueueScope,radioQueueCountry);JSONArray all=state.optJSONArray(radio?"stations":"favoriteMusic".equals(playbackContext)?"favorites":"localFiles");List<JSONObject> list=new ArrayList<>();if(all==null)return list;
  if("playlist".equals(playbackContext)){JSONArray paths=activePlaylist(state).optJSONArray("paths");if(paths==null)return list;Map<String,JSONObject> files=new HashMap<>();for(int i=0;i<all.length();i++){JSONObject x=all.optJSONObject(i);if(x!=null&&"local".equals(x.optString("type")))files.put(x.optString("path"),x);}Set<String> seen=new HashSet<>();for(int i=0;i<paths.length();i++){String path=paths.optString(i);if(files.containsKey(path)&&seen.add(path))list.add(files.get(path));}return list;}
  for(int i=0;i<all.length();i++){JSONObject x=all.optJSONObject(i);if(x==null||radio&&(!"radio".equals(x.optString("type"))||item!=null&&!x.optString("region").equals(item.optString("region")))||!radio&&!"local".equals(x.optString("type")))continue;list.add(x);}return list;
 }
 private void change(int direction){
  JSONObject state=Store.load(this);List<JSONObject> list=playQueue(state);if(list.isEmpty()){stopSelf();return;}boolean radio="radio".equals(playbackContext);String key=radio?"url":"path";int index=-1;for(int i=0;i<list.size();i++)if(item!=null&&list.get(i).optString(key).equals(item.optString(key)))index=i;
  int next=index<0?0:(index+direction+list.size())%list.size();if(!radio&&index>=0&&"one".equals(playMode(state)))next=index;
  if(!radio&&"shuffle".equals(playMode(state))&&list.size()>1){
   Deque<String> history=direction<0?shuffleBack:shuffleForward,opposite=direction<0?shuffleForward:shuffleBack;int prior=-1;
   while(!history.isEmpty()&&prior<0){String path=history.removeLast();for(int i=0;i<list.size();i++)if(list.get(i).optString("path").equals(path)&&i!=index){prior=i;break;}}
   if(index>=0){opposite.addLast(list.get(index).optString("path"));while(opposite.size()>1000)opposite.removeFirst();}
   if(prior>=0)next=prior;else{next=new java.util.Random().nextInt(list.size()-(index>=0?1:0));if(next>=index&&index>=0)next++;if(direction>0)shuffleForward.clear();}
  }else{shuffleBack.clear();shuffleForward.clear();}
  navigationRevision++;retryCount=0;JSONObject nextItem=list.get(next);try{nextItem.put("playbackContext",playbackContext);nextItem.put("activePlaylistId",activePlaylistId);if(radio){nextItem.put("radioQueueScope",radioQueueScope);nextItem.put("radioQueueCountry",radioQueueCountry);}}catch(JSONException ignored){}play(nextItem);
 }
 static void setVolume(float value){if(current!=null&&current.player!=null)try{current.player.setVolume(value);}catch(Exception ignored){}}
 static void seek(long position){if(current!=null&&current.player!=null&&!current.preparing)try{current.player.seekTo(position);current.updateSnapshot();}catch(Exception ignored){}}
 static String status(){return snapshot;}
 static void updateTags(JSONObject tags){if(current!=null&&current.item!=null&&current.item.optString("path").equals(tags.optString("path"))){try{for(String key:Arrays.asList("title","artist","album","name"))if(tags.has(key))current.item.put(key,tags.get(key));current.notifyState();current.updateSnapshot();}catch(Exception ignored){}}}
 static void refreshLanguage(){if(current!=null){NotificationManager nm=current.getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("playback",L10n.text(current,"playback"),NotificationManager.IMPORTANCE_LOW));if(!current.errorKey.isEmpty())current.error=L10n.text(current,current.errorKey);current.notifyState();current.updateSnapshot();}}
 private void updateSnapshot(){try{JSONObject s=new JSONObject().put("navigationRevision",navigationRevision).put("radioQueueScope",radioQueueScope).put("radioQueueCountry",radioQueueCountry).put("playbackContext",playbackContext).put("activePlaylistId",activePlaylistId).put("playing",isPlaying()).put("preparing",preparing).put("error",error).put("trackTitle",liveTitle).put("trackArtist",liveArtist).put("errorKey",errorKey).put("retrying",retry!=null).put("retryCount",retryCount).put("sleepDeadline",wallDeadline(sleepDeadline)).put("backgroundDeadline",wallDeadline(backgroundDeadline));if(item!=null)s.put("item",item);if(radioMemory!=null)s.put("lastRadioPlayed",radioMemory);if(player!=null&&!preparing){s.put("position",player.getCurrentPosition());s.put("duration",Math.max(0,player.getDuration()));}snapshot=s.toString();}catch(Exception ignored){}}
 private PendingIntent action(String name,int id){return PendingIntent.getService(this,id,new Intent(this,PlaybackService.class).setAction(name),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 private long timerEnd(){return sleepDeadline>0&&backgroundDeadline>0?Math.min(sleepDeadline,backgroundDeadline):Math.max(sleepDeadline,backgroundDeadline);}
 private String countdown(){long remaining=Math.max(0,(timerEnd()-SystemClock.elapsedRealtime()+999)/1000);return String.format(Locale.ROOT,"%02d:%02d:%02d",remaining/3600,(remaining/60)%60,remaining%60);}
 private static long wallDeadline(long elapsed){return elapsed==0?0:System.currentTimeMillis()+Math.max(0,elapsed-SystemClock.elapsedRealtime());}
 private static android.graphics.Bitmap decodeNotificationCover(byte[] data){
  android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(data,0,data.length,options);if(options.outWidth<=0||options.outHeight<=0)return null;options.inSampleSize=1;while(options.outWidth/options.inSampleSize>512||options.outHeight/options.inSampleSize>512)options.inSampleSize*=2;options.inJustDecodeBounds=false;android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(data,0,data.length,options);if(bitmap==null)return null;return android.graphics.Bitmap.createScaledBitmap(bitmap,256,256,true);
 }
 private int notificationColor(){return android.graphics.Color.rgb(25,29,35);}
 private android.graphics.Bitmap notificationArtwork(){
  if(artwork!=null)return artwork;
  artwork=android.graphics.Bitmap.createBitmap(256,256,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(artwork);android.graphics.Paint paint=new android.graphics.Paint(3);
  paint.setShader(new android.graphics.LinearGradient(0,0,256,256,0xFF31594E,0xFF15232C,android.graphics.Shader.TileMode.CLAMP));canvas.drawRoundRect(0,0,256,256,24,24,paint);paint.setShader(null);
  paint.setColor(0xFF14201F);canvas.drawCircle(128,124,91,paint);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setColor(0xFF365348);for(int radius=48;radius<=82;radius+=9)canvas.drawCircle(128,124,radius,paint);
  paint.setStyle(android.graphics.Paint.Style.FILL);paint.setColor(0xFF73D7C1);canvas.drawCircle(128,124,31,paint);paint.setColor(0xFF172C27);canvas.drawCircle(128,124,7,paint);paint.setColor(0xFFDCECE7);paint.setTextSize(17);paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));paint.setTextAlign(android.graphics.Paint.Align.CENTER);canvas.drawText("AMPLAYER",128,233,paint);return artwork;
 }
 private String notificationTitle(){String title=liveTitle.isEmpty()?(item==null?"":item.optString("title")):liveTitle;return title.isEmpty()?(item==null?"JAMPlayer":item.optString("name","JAMPlayer")):title;}
 private String notificationSubtitle(){
  if(item==null)return "JAMPlayer";String artist=liveArtist.isEmpty()?item.optString("artist"):liveArtist;
  if("radio".equals(item.optString("type"))&&!liveTitle.isEmpty())return item.optString("name","JAMPlayer");
  if(!artist.isEmpty())return artist+(item.optString("album").isEmpty()?"":" · "+item.optString("album"));
  return preparing?L10n.text(this,"connecting"):!error.isEmpty()?error:isPlaying()?L10n.text(this,"playing"):L10n.text(this,"paused");
 }
 private android.widget.RemoteViews notificationView(boolean expanded){
  android.widget.RemoteViews view=new android.widget.RemoteViews(getPackageName(),expanded?R.layout.notification_expanded:R.layout.notification_compact);
  view.setTextViewText(R.id.notification_title,notificationTitle());view.setTextViewText(R.id.notification_subtitle,notificationSubtitle());view.setImageViewBitmap(R.id.notification_cover,notificationArtwork());
  long end=timerEnd();view.setViewVisibility(R.id.notification_timer,end>0?android.view.View.VISIBLE:android.view.View.GONE);view.setChronometer(R.id.notification_timer,end>0?end:SystemClock.elapsedRealtime(),null,end>0);view.setChronometerCountDown(R.id.notification_timer,true);
  if(expanded){view.setViewVisibility(R.id.notification_timer_row,end>0?android.view.View.VISIBLE:android.view.View.GONE);view.setTextViewText(R.id.notification_timer_label,L10n.text(this,end==backgroundDeadline?"backgroundClose":"sleep"));view.setOnClickPendingIntent(R.id.notification_previous,action("previous",1));view.setOnClickPendingIntent(R.id.notification_stop,action("stop",4));view.setContentDescription(R.id.notification_stop,L10n.text(this,"close"));view.setViewVisibility(R.id.notification_dots,isPlaying()||preparing?android.view.View.VISIBLE:android.view.View.INVISIBLE);}
  view.setImageViewResource(R.id.notification_play,isPlaying()?R.drawable.notification_pause:R.drawable.notification_play);view.setOnClickPendingIntent(R.id.notification_play,action(isPlaying()?"pause":"play",2));view.setContentDescription(R.id.notification_play,L10n.text(this,isPlaying()?"pause":"play"));view.setOnClickPendingIntent(R.id.notification_next,action("next",3));view.setContentDescription(R.id.notification_next,L10n.text(this,"next"));return view;
 }
 private Notification notification(){
  long end=timerEnd();PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  String title=liveTitle.isEmpty()?(item==null?"JAMPlayer":item.optString("title",item.optString("name","JAMPlayer"))):liveTitle;if(title.isEmpty())title=item==null?"JAMPlayer":item.optString("name","JAMPlayer");
  return new Notification.Builder(this,"playback").setSmallIcon(R.drawable.ic_notification).setColor(notificationColor()).setColorized(false).setCategory(Notification.CATEGORY_TRANSPORT).setContentTitle(title).setContentText(preparing?L10n.text(this,"connecting"):isPlaying()?L10n.text(this,"playing"):error.isEmpty()?L10n.text(this,"paused"):error).setContentIntent(open).setShowWhen(false).setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setSound(null).setVibrate(null).setDefaults(0).setOngoing(isPlaying()||preparing).setCustomContentView(notificationView(false)).setCustomBigContentView(notificationView(true)).setStyle(new Notification.DecoratedCustomViewStyle()).build();
 }
 private void notifyState(){if(mediaSession==null)return;long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_STOP;long position=0;try{if(player!=null&&!preparing)position=player.getCurrentPosition();}catch(Exception ignored){}mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(preparing?PlaybackState.STATE_BUFFERING:isPlaying()?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,position,1).build());if(item!=null)mediaSession.setMetadata(new MediaMetadata.Builder().putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,notificationArtwork()).putBitmap(MediaMetadata.METADATA_KEY_ART,notificationArtwork()).putString(MediaMetadata.METADATA_KEY_TITLE,liveTitle.isEmpty()?(item.optString("title").isEmpty()?item.optString("name"):item.optString("title")):liveTitle).putString(MediaMetadata.METADATA_KEY_ALBUM,item.optString("album")).putString(MediaMetadata.METADATA_KEY_ARTIST,liveArtist.isEmpty()?item.optString("artist","JAMPlayer"):liveArtist).build());getSystemService(NotificationManager.class).notify(1,notification());}
 @Override public void onDestroy(){checkpointCar(true);handler.removeCallbacksAndMessages(null);releasePlayer();audioManager.abandonAudioFocusRequest(focus);if(mediaSession!=null)mediaSession.release();try{unregisterReceiver(noisy);}catch(Exception ignored){}try{unregisterReceiver(carPower);}catch(Exception ignored){}current=null;snapshot="{}";stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
