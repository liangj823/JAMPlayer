package com.amplayer.portable;
import android.app.*;import android.content.*;import android.content.res.*;import android.os.*;import android.graphics.*;import android.view.*;import android.widget.*;import org.json.*;import java.lang.reflect.*;import java.io.*;
public class Notification212Instrumentation extends Instrumentation {
 private View attachedPreview;
 private void report(String key,String value){Bundle b=new Bundle();b.putString(key,value);sendStatus(0,b);}
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  File folder=new File(getTargetContext().getExternalFilesDir(null),"notification212");folder.mkdirs();
  int[] widths={240,280,340,600};float[] fonts={1f,1.3f};
  for(float font:fonts)for(int width:widths)for(boolean expanded:new boolean[]{false,true}){
   final String name=(expanded?"expanded":"compact")+"-"+width+"-font"+(font==1?100:130);final int[] height={0};
   runOnMainSync(()->{try{
    Configuration config=new Configuration(getTargetContext().getResources().getConfiguration());config.fontScale=font;Context context=getTargetContext().createConfigurationContext(config);
    PlaybackService service=new PlaybackService();Method attach=ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);attach.invoke(service,context);
    Field item=PlaybackService.class.getDeclaredField("item");item.setAccessible(true);item.set(service,new JSONObject().put("type","radio").put("name","中廣音樂網 i Radio"));Field title=PlaybackService.class.getDeclaredField("liveTitle");title.setAccessible(true);title.set(service,"告五人－披星戴月的想你 / Jimin Park - April Fools (0401)");
    Field preparing=PlaybackService.class.getDeclaredField("preparing");preparing.setAccessible(true);preparing.setBoolean(service,true);Field deadline=PlaybackService.class.getDeclaredField("backgroundDeadline");deadline.setAccessible(true);deadline.setLong(service,SystemClock.elapsedRealtime()+3599000);
    Method make=PlaybackService.class.getDeclaredMethod("notificationView",boolean.class);make.setAccessible(true);RemoteViews remote=(RemoteViews)make.invoke(service,expanded);View view=remote.apply(context,null);float density=context.getResources().getDisplayMetrics().density;int px=Math.round(width*density);view.measure(View.MeasureSpec.makeMeasureSpec(px,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(expanded?0:Math.round(48*density),expanded?View.MeasureSpec.UNSPECIFIED:View.MeasureSpec.EXACTLY));view.layout(0,0,px,view.getMeasuredHeight());height[0]=Math.round(view.getMeasuredHeight()/density);
    if(height[0]>(expanded?210:48)||height[0]<=0)throw new Exception("Height exceeds notification budget: "+height[0]);
    TextView text=view.findViewById(R.id.notification_title);View copy=view.findViewById(R.id.notification_copy),play=view.findViewById(R.id.notification_play);if(text.getHeight()<=0||copy.getWidth()<=0||text.getLayout()==null)throw new Exception("Missing title layout");
    if(!expanded&&copy.getRight()>play.getLeft())throw new Exception("Text and controls overlap");
    for(int id:new int[]{R.id.notification_cover,R.id.notification_title,R.id.notification_subtitle,R.id.notification_timer,R.id.notification_play,R.id.notification_next}){View child=view.findViewById(id);Rect bounds=new Rect();child.getDrawingRect(bounds);((android.view.ViewGroup)view).offsetDescendantRectToMyCoords(child,bounds);if(bounds.left<0||bounds.right>px+1||bounds.top<0||bounds.bottom>view.getHeight()+1)throw new Exception("Content outside card: "+id+" "+bounds);}
    Chronometer clock=view.findViewById(R.id.notification_timer);if(!clock.isCountDown()||clock.getBase()<=SystemClock.elapsedRealtime())throw new Exception("Countdown missing");
    if(expanded){ViewFlipper dots=view.findViewById(R.id.notification_dots);if(dots.getChildCount()!=18||dots.getFlipInterval()!=1800)throw new Exception("Dot animation missing");dots.setDisplayedChild(9);view.measure(View.MeasureSpec.makeMeasureSpec(px,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,px,view.getMeasuredHeight());}
    if(expanded&&width==340&&font==1f)attachedPreview=view;Bitmap bitmap=Bitmap.createBitmap(px,view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    // No timer means no empty reserved countdown row.
    if(expanded){deadline.setLong(service,0);RemoteViews noTimer=(RemoteViews)make.invoke(service,true);View other=noTimer.apply(context,null);if(other.findViewById(R.id.notification_timer_row).getVisibility()!=View.GONE)throw new Exception("Empty timer row remains");}
   }catch(Exception e){throw new RuntimeException(e);}});report(name,"PASS height="+height[0]+"dp");
  }
  Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  runOnMainSync(()->activity.setContentView(attachedPreview,new ViewGroup.LayoutParams(Math.round(340*getTargetContext().getResources().getDisplayMetrics().density),ViewGroup.LayoutParams.WRAP_CONTENT)));Thread.sleep(4200);
  runOnMainSync(()->{try{ViewFlipper dots=attachedPreview.findViewById(R.id.notification_dots);TextView frame=(TextView)dots.getCurrentView();if(!dots.isFlipping()||dots.getDisplayedChild()==0||frame.getHeight()<=0||frame.getWidth()<=0)throw new Exception("Attached dots did not advance: "+dots.getDisplayedChild()+" "+frame.getWidth()+"x"+frame.getHeight());report("dotCycle","PASS actual attached ViewFlipper advanced to frame "+dots.getDisplayedChild());Bitmap bitmap=Bitmap.createBitmap(attachedPreview.getWidth(),attachedPreview.getHeight(),Bitmap.Config.ARGB_8888);attachedPreview.draw(new Canvas(bitmap));try(FileOutputStream out=new FileOutputStream(new File(folder,"expanded-attached-340.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}catch(Exception e){throw new RuntimeException(e);}});
  result.putString("result","PASS: 16 actual Android RemoteViews renders, compact/expanded, phone/tablet widths and 130% fonts");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("failure",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
