package org.videolan.vlc;

import android.app.Application;
import android.content.Context;

public class VLCApplication extends Application {

    private static VLCApplication instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
    
    public static Context getAppContext()
    {
        return instance;
    }

}
