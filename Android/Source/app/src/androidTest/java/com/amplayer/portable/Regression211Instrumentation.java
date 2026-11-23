package com.amplayer.portable;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.webkit.*;import android.graphics.*;import org.json.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class Regression211Instrumentation extends Instrumentation {
 private Activity activity;private WebView web;
 private WebView find(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView found=find(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}}return null;}
 private String js(String code)throws Exception{CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();runOnMainSync(()->web.evaluateJavascript(code,result->{value.set(result);latch.countDown();}));if(!latch.await(20,TimeUnit.SECONDS))throw new Exception("JS timeout");return value.get();}
 private void report(String key,String value){Bundle b=new Bundle();b.putString(key,value);sendStatus(0,b);}
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  JSONObject settings=Store.load(getTargetContext());settings.put("autoUpdate",false);settings.put("language","zh-Hant");settings.put("volume",0);Store.save(getTargetContext(),settings.toString());
  activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();runOnMainSync(()->web=find(activity.getWindow().getDecorView()));
  for(int n=0;n<50;n++){if("true".equals(js("typeof state!=='undefined'&&!!state")))break;Thread.sleep(300);}
  // Verify actual native WebView bounds, not merely a browser screenshot.
  runOnMainSync(()->{int[] location=new int[2];web.getLocationOnScreen(location);android.graphics.Insets bars=activity.getWindow().getDecorView().getRootWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());if(location[1]<bars.top)throw new AssertionError("WebView overlaps status bar: "+location[1]+" < "+bars.top);Bundle b=new Bundle();b.putString("nativeInsets","PASS webTop="+location[1]+", statusBar="+bars.top);sendStatus(0,b);});
  js("(()=>{state.motion=true;applyAppearance();state.lastPlayed={type:'radio',name:'長電台名稱測試：中廣音樂網及完整歌曲資訊 ABCDEFGHIJKLMNOPQRSTUVWXYZ',url:'https://example.org/test',region:'TW'};observed={};refreshPlayer();return true;})()");Thread.sleep(300);String marquee=js("(()=>{const el=$('track-title'),span=el.firstChild;const a=getComputedStyle(span).animationName;refreshPlayer();if(span!==el.firstChild||a!=='overflow-scroll')return {error:'marquee reset or missing',a};const b=el.getBoundingClientRect(),lcd=document.querySelector('.lcd').getBoundingClientRect();if(b.right>lcd.right||b.left<lcd.left||b.bottom>lcd.bottom)return {error:'LCD overflow'};return {result:'PASS',animation:a,distance:el.style.getPropertyValue('--scroll-distance')};})()");if(marquee.contains("error")||marquee.equals("null"))throw new Exception(marquee);report("marquee",marquee);
  String[] ids={"78fm9wyy2tzuv","ndk05tyy2tzuv","aw9uqyxy2tzuv","p2e3rfg3qtzuv"};String[] names={"中廣新聞網","中廣音樂網","中廣流行網","中廣鄉親網"};
  for(int i=0;i<ids.length;i++){
   JSONObject item=new JSONObject().put("type","radio").put("name",names[i]).put("url","https://stream.rcs.revma.com/"+ids[i]).put("region","TW");
   runOnMainSync(()->getTargetContext().startForegroundService(new Intent(getTargetContext(),PlaybackService.class).setAction("item").putExtra("item",item.toString())));JSONObject status=new JSONObject();boolean playing=false;long start=SystemClock.elapsedRealtime();
   for(int n=0;n<100;n++){Thread.sleep(600);status=new JSONObject(PlaybackService.status());if(status.optJSONObject("item")!=null&&status.optJSONObject("item").optString("url").equals(item.optString("url"))&&status.optBoolean("playing")&&status.optLong("position")>1500){playing=true;break;}}
   report("station"+i,names[i]+" "+(playing?"PASS":"FAIL")+" waitMs="+(SystemClock.elapsedRealtime()-start)+" "+status);if(!playing)throw new Exception("Real AAC stream did not play: "+names[i]);
  }
  runOnMainSync(()->activity.moveTaskToBack(true));Thread.sleep(1500);
  android.service.notification.StatusBarNotification[] list=getTargetContext().getSystemService(NotificationManager.class).getActiveNotifications();boolean found=false;
  for(android.service.notification.StatusBarNotification entry:list)if(entry.getId()==1){Notification n=entry.getNotification();if(n.color!=Color.parseColor("#191D23")||n.bigContentView==null)throw new Exception("Notification contrast/art/custom view missing");View view=n.bigContentView.apply(getTargetContext(),null);android.widget.TextView title=view.findViewById(R.id.notification_title),timer=view.findViewById(R.id.notification_timer);float sp=title.getTextSize()/getTargetContext().getResources().getDisplayMetrics().scaledDensity;if(sp<17||title.getCurrentTextColor()!=Color.parseColor("#F7F9FB")||timer.getVisibility()!=View.VISIBLE)throw new Exception("Notification font/countdown incorrect");report("notification","PASS explicit dark palette, white 22sp title, 24sp native timer and controls");found=true;}
  if(!found)throw new Exception("Missing notification");result.putString("result","PASS");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("failure",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
