package com.didi.virtualapk;

import android.app.Application;
import android.content.Context;
import android.util.Log;

/**
 * Created by renyugang on 16/8/10.
 */
public class VAApplication extends Application {
//测试
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        long start = System.currentTimeMillis();
        PluginManager.getInstance(base).init();
        //test show dev a
        Log.d("ryg", "use time:" + (System.currentTimeMillis() - start));
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
