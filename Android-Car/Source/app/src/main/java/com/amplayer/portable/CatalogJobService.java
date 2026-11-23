package com.amplayer.portable;
import android.app.job.*;
public class CatalogJobService extends JobService {
 @Override public boolean onStartJob(JobParameters params){CatalogUpdater.update(this,result->jobFinished(params,false));return true;}
 @Override public boolean onStopJob(JobParameters params){return true;}
}
