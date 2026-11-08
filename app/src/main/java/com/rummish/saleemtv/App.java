package com.rummish.saleemtv;

import android.app.Application;

public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashGuard.install(this);
    }
}
