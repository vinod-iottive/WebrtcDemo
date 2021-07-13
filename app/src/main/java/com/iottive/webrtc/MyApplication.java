package com.iottive.webrtc;

import android.app.Application;
import android.content.Context;

public class MyApplication extends Application {

    public static Context getContext;

    @Override
    public void onCreate() {
        super.onCreate();
        getContext = this;
    }
}
