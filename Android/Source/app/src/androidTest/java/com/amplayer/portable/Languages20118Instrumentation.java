package com.amplayer.portable;
import android.app.*;import android.os.*;import android.content.res.*;import org.json.*;import java.io.*;import java.util.*;
public class Languages20118Instrumentation extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();File file=new File(getTargetContext().getFilesDir(),"settings.json");byte[] original=null;Configuration config=new Configuration(Resources.getSystem().getConfiguration());try{
  if(file.exists())original=java.nio.file.Files.readAllBytes(file.toPath());
  String[] languages={"zh-Hant","zh-Hans","en","ja","ko","fr","es","pt-BR","de","vi","th","id"},playing={"播放中","播放中","Playing","再生中","재생 중","Lecture en cours","Reproduciendo","Reproduzindo","Wiedergabe läuft","Đang phát","กำลังเล่น","Sedang diputar"};String[] keys={"confirm","cancel","playback","playing","paused","connecting","play","pause","previous","next","close","backgroundClose","sleep","focus","local","localAccess","localFormat","localRead","connection"};
  for(int i=0;i<languages.length;i++){Store.save(getTargetContext(),new JSONObject().put("language",languages[i]).toString());L10n.refresh(getTargetContext());if(L10n.language(getTargetContext())!=i||!playing[i].equals(L10n.text(getTargetContext(),"playing")))throw new Exception("Manual locale failed: "+languages[i]);for(String key:keys)if(L10n.text(getTargetContext(),key).isEmpty())throw new Exception("Missing "+key+languages[i]);}
  for(int i=0;i<languages.length;i++){Configuration local=new Configuration(config);local.setLocales(new LocaleList(Locale.forLanguageTag(languages[i])));Resources.getSystem().updateConfiguration(local,Resources.getSystem().getDisplayMetrics());Store.save(getTargetContext(),new JSONObject().put("language","auto").toString());L10n.refresh(getTargetContext());if(L10n.language(getTargetContext())!=i||!playing[i].equals(L10n.text(getTargetContext(),"playing")))throw new Exception("System locale failed: "+languages[i]+" resolved "+L10n.system());}
  if(L10n.resolve("pt-PT")!=7||L10n.resolve("in-ID")!=11||L10n.resolve("fr-CA")!=5||L10n.resolve("ar-SA")!=2)throw new Exception("Region/fallback mapping failed");
  result.putString("result","PASS: 12 manual languages, 12 simulated system languages, 228 native texts, region aliases and English fallback");
 }catch(Throwable e){result.putString("failure",e.toString());}finally{try{Resources.getSystem().updateConfiguration(config,Resources.getSystem().getDisplayMetrics());if(original==null)file.delete();else java.nio.file.Files.write(file.toPath(),original);L10n.refresh(getTargetContext());}catch(Exception e){result.putString("restoreFailure",e.toString());}}
 finish(result.containsKey("failure")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
 }
}
