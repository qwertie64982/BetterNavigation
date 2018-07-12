package com.qwertie64982.betternavigation;

import android.app.Application;
import android.content.res.Configuration;
import android.util.Log;

import com.squareup.leakcanary.LeakCanary;

public class BNApplication extends Application {
    private String TAG = "BNApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // Leak canary
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        LeakCanary.install(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        Log.e(TAG, "onLowMemory: " + "Low memory!");
        super.onLowMemory();
    }
}
