package com.amplayer.portable;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
 private WebView web;
 private CarResumeController car;
 private String pendingId,pendingExport,pendingMusicId;
 private static final int PICK=10,IMPORT=11,EXPORT=12;
 private android.window.OnBackInvokedCallback backCallback;
 // JavaScript is limited to bundled assets; navigation and external requests are blocked.
 @android.annotation.SuppressLint("SetJavaScriptEnabled")
 @Override public void onCreate(Bundle state){
  super.onCreate(state);car=new CarResumeController(this);if(!Intent.ACTION_MAIN.equals(getIntent().getAction()))car.manual();
  getWindow().setStatusBarColor(Color.rgb(34,34,34));getWindow().setNavigationBarColor(Color.rgb(34,34,34));
  android.widget.FrameLayout root=new android.widget.FrameLayout(this);root.setBackgroundColor(Color.rgb(16,20,19));
  web=new WebView(this);web.setBackgroundColor(Color.rgb(16,20,19));root.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));setContentView(root);
  // WebView padding does not constrain its document viewport. Insets belong to the native parent.
  if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
   root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());android.widget.FrameLayout.LayoutParams lp=(android.widget.FrameLayout.LayoutParams)web.getLayoutParams();if(lp.leftMargin!=bars.left||lp.topMargin!=bars.top||lp.rightMargin!=bars.right||lp.bottomMargin!=bars.bottom){lp.setMargins(bars.left,bars.top,bars.right,bars.bottom);web.setLayoutParams(lp);}return WindowInsets.CONSUMED;});root.requestApplyInsets();
  }else {root.setFitsSystemWindows(true);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);
  web.addJavascriptInterface(new Bridge(),"AMNative");
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){if(request.getUrl().toString().startsWith("file:///android_asset/"))return null;return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onJsConfirm(WebView v,String url,String message,JsResult result){new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton(L10n.text(MainActivity.this,"confirm"),(d,w)->result.confirm()).setNegativeButton(L10n.text(MainActivity.this,"cancel"),(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;}
   @Override public boolean onJsAlert(WebView v,String url,String message,JsResult result){new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton(L10n.text(MainActivity.this,"confirm"),(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true;}
  });
  web.loadUrl("file:///android_asset/index.html");
  if(state==null)IncomingMusic.accept(this,getIntent());
  if(Build.VERSION.SDK_INT>=33){backCallback=this::handleBackAction;getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);}
 }
 private void reply(String id,Object value,String error){if(web==null)return;final String payload=value==null?"null":value.toString();runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.nativeReply("+JSONObject.quote(id)+","+payload+","+(error==null?"null":JSONObject.quote(error))+")",null);});}
 private void select(int kind,String id,String value){runOnUiThread(()->{car.externalUi();if(pendingId!=null){reply(id,null,"請先完成目前的檔案選擇");return;}pendingId=id;pendingExport=value;try{Intent intent=new Intent(kind==EXPORT?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(kind==PICK?"audio/*":"application/json");intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);if(kind==PICK)intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);if(kind==EXPORT)intent.putExtra(Intent.EXTRA_TITLE,"JAMPlayer-Car-backup.json");startActivityForResult(intent,kind);}catch(Exception e){pendingId=null;reply(id,null,"無法開啟系統檔案選取器");}});}
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);String id=pendingId;pendingId=null;if(id==null)return;
  if(result!=RESULT_OK||data==null){reply(id,request==PICK?new JSONArray():null,null);return;}
  try{
   if(request==PICK){new Thread(()->{try{JSONArray files=new JSONArray();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)files.put(audioItem(data.getClipData().getItemAt(i).getUri(),data.getFlags()));}else if(data.getData()!=null)files.put(audioItem(data.getData(),data.getFlags()));reply(id,files,null);}catch(Exception e){reply(id,null,"Audio file read failed");}},"AudioPicker").start();}
   else if(request==IMPORT){try(InputStream input=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream buffer=new ByteArrayOutputStream()){byte[] chunk=new byte[8192];int n;while((n=input.read(chunk))!=-1){buffer.write(chunk,0,n);if(buffer.size()>5*1024*1024)throw new IOException("備份超過 5 MB");}reply(id,new JSONObject(buffer.toString("UTF-8")),null);}}
   else if(request==EXPORT){try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException();out.write(pendingExport.getBytes(StandardCharsets.UTF_8));}reply(id,Boolean.TRUE,null);}
  }catch(Exception e){reply(id,null,"檔案讀寫失敗："+e.getMessage());}finally{pendingExport=null;}
 }
 private JSONObject audioItem(Uri uri,int flags)throws Exception{return IncomingMusic.picked(this,uri,flags);}
 private void send(String action,JSONObject value){runOnUiThread(()->{car.manual();if((action.equals("item")||action.equals("play"))&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getPreferences(MODE_PRIVATE).getBoolean("notificationAsked",false)){getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},100);}Intent i=new Intent(MainActivity.this,PlaybackService.class).setAction(action);if(value!=null)i.putExtra("item",value.toString());if(action.equals("item")||action.equals("play"))startForegroundService(i);else startService(i);});}
 private void requestMusicAccess(String id){runOnUiThread(()->{car.externalUi();if(MediaLibrary.musicGranted(this)){reply(id,MediaLibrary.access(this),null);return;}if(getSharedPreferences("music-access",0).getBoolean("asked",false)&&!shouldShowRequestPermissionRationale(MediaLibrary.permission())){try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception ignored){}reply(id,MediaLibrary.access(this),null);return;}if(pendingMusicId!=null){reply(id,null,"permission_request_pending");return;}pendingMusicId=id;getSharedPreferences("music-access",0).edit().putBoolean("asked",true).apply();try{requestPermissions(new String[]{MediaLibrary.permission()},101);}catch(Exception e){pendingMusicId=null;reply(id,MediaLibrary.access(this),null);}});}
 private void openAllFilesSettings(){runOnUiThread(()->{car.externalUi();try{Intent intent;if(Build.VERSION.SDK_INT>=30)intent=new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName()));else intent=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));startActivity(intent);}catch(Exception first){try{startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}catch(Exception ignored){}}});}
 final class Bridge {
  @JavascriptInterface public String carStatus(){return car.status();}
  @JavascriptInterface public void clearCarMemory(){runOnUiThread(()->{car.manual();PlaybackService.carForget(MainActivity.this);});}
  @JavascriptInterface public String musicAccess(){return MediaLibrary.access(MainActivity.this).toString();}
  @JavascriptInterface public void requestMusicAccess(String id){MainActivity.this.requestMusicAccess(id);}
  @JavascriptInterface public void openAllFiles(){openAllFilesSettings();}
  @JavascriptInterface public void scanMusic(String id){new Thread(()->{try{reply(id,MediaLibrary.scan(MainActivity.this),null);}catch(SecurityException e){try{reply(id,new JSONObject().put("denied",true).put("items",new JSONArray()),null);}catch(Exception ignored){reply(id,null,"permission_denied");}}catch(Exception e){reply(id,null,"scan_failed");}},"music-scan").start();}
  @JavascriptInterface public String load(){JSONObject data=Store.load(MainActivity.this);return data.has("stations")?data.toString():"null";}
  @JavascriptInterface public boolean save(String value){try{Store.save(MainActivity.this,value);runOnUiThread(()->{PlaybackService.refreshBackgroundPolicy();car.settingsChanged();});CatalogUpdater.configure(MainActivity.this);return true;}catch(Exception e){return false;}}
  @JavascriptInterface public String locale(){return L10n.system();}
  @JavascriptInterface public String shared(){return IncomingMusic.drain(MainActivity.this).toString();}
  @JavascriptInterface public void metadata(String id,String data){new Thread(()->{JSONArray values=new JSONArray();try{JSONArray files=new JSONArray(data);for(int n=0;n<Math.min(200,files.length());n++){JSONObject file=files.getJSONObject(n);Uri uri=Uri.parse(file.getString("path"));if("content".equals(uri.getScheme())||MediaLibrary.sharedFile(MainActivity.this,uri)){JSONObject tags=AudioTags.read(MainActivity.this,uri,file.optString("originalName",file.optString("name","Music")));values.put(tags);runOnUiThread(()->PlaybackService.updateTags(tags));}}reply(id,values,null);}catch(Exception e){reply(id,null,"metadata_failed");}},"audio-tags").start();}
  @JavascriptInterface public void languageChanged(){L10n.refresh(MainActivity.this);runOnUiThread(()->PlaybackService.refreshLanguage());}
  @JavascriptInterface public String catalog(){return CatalogUpdater.cache(MainActivity.this).toString();}
  @JavascriptInterface public String sources(){return CatalogUpdater.sources(MainActivity.this).toString();}
  @JavascriptInterface public String updateStatus(){return CatalogUpdater.status(MainActivity.this).toString();}
  @JavascriptInterface public String checkUpdates(){return CatalogUpdater.check(MainActivity.this).toString();}
  @JavascriptInterface public void updateCatalog(String id){CatalogUpdater.update(MainActivity.this,value->reply(id,value,null));}
  @JavascriptInterface public void openSource(String id){try{JSONArray providers=CatalogUpdater.sources(MainActivity.this).getJSONArray("providers");for(int n=0;n<providers.length();n++){JSONObject p=providers.getJSONObject(n);if(p.getString("id").equals(id)){String url="https://github.com/"+p.getString("repo");runOnUiThread(()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){}});return;}}}catch(Exception ignored){}}
  @JavascriptInterface public void pickAudio(String id){select(PICK,id,null);}
  @JavascriptInterface public void importSettings(String id){select(IMPORT,id,null);}
  @JavascriptInterface public void exportSettings(String id,String value){select(EXPORT,id,value);}
  @JavascriptInterface public void play(String value){try{JSONObject item=new JSONObject(value);String source=item.optString("type").equals("radio")?item.optString("url"):item.optString("path");String scheme=Uri.parse(source).getScheme();if(!MediaLibrary.playable(MainActivity.this,Uri.parse(source)))return;send("item",item);}catch(Exception ignored){}}
  @JavascriptInterface public void command(String command){if(command.equals("play")||command.equals("pause")||command.equals("next")||command.equals("previous")||command.equals("stop"))send(command,null);}
  @JavascriptInterface public void sleep(double minutes){if(!Double.isFinite(minutes)||minutes<0||minutes>240)return;runOnUiThread(()->startService(new Intent(MainActivity.this,PlaybackService.class).setAction("sleep").putExtra("sleepMinutes",minutes)));}
  @JavascriptInterface public void volume(double value){runOnUiThread(()->PlaybackService.setVolume((float)Math.max(0,Math.min(1,value))));}
  @JavascriptInterface public void seek(double seconds){runOnUiThread(()->PlaybackService.seek((long)Math.max(0,seconds*1000)));}
  @JavascriptInterface public String status(){return PlaybackService.status();}
  @JavascriptInterface public void close(){runOnUiThread(()->{stopService(new Intent(MainActivity.this,PlaybackService.class));finishAndRemoveTask();});}
  @JavascriptInterface public void reset(){runOnUiThread(()->{car.manual();PlaybackService.carForget(MainActivity.this);stopService(new Intent(MainActivity.this,PlaybackService.class));});}
 }
 private void handleBackAction(){if(web!=null)web.evaluateJavascript("window.handleBack?window.handleBack():false",result->{if(!"true".equals(result))moveTaskToBack(true);});else moveTaskToBack(true);}
 @Override protected void onResume(){super.onResume();if(car!=null)car.resume();L10n.refresh(this);PlaybackService.setForeground(true);if(web!=null)web.evaluateJavascript("window.refreshMusicAccess&&window.refreshMusicAccess()",null);}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);car.launch(intent);IncomingMusic.accept(this,intent);}
 @Override protected void onStop(){if(car!=null)car.stop();PlaybackService.setForeground(false);super.onStop();}
 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==100)PlaybackService.refreshLanguage();if(requestCode==101&&pendingMusicId!=null){String id=pendingMusicId;pendingMusicId=null;reply(id,MediaLibrary.access(this),null);}}
 // Android 13+ uses the registered predictive-back callback; this override is for API 26–32.
 @android.annotation.SuppressLint("GestureBackNavigation")
 @Override public void onBackPressed(){handleBackAction();}
 @Override protected void onDestroy(){if(car!=null)car.destroy();if(Build.VERSION.SDK_INT>=33&&backCallback!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);if(web!=null){web.removeJavascriptInterface("AMNative");web.destroy();web=null;}super.onDestroy();}
}
