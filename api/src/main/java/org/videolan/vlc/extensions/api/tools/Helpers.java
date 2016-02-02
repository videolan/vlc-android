package org.videolan.vlc.extensions.api.tools;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.videolan.vlc.extensions.api.WarningActivity;

public class Helpers {

    /**
     * Helper method to check if VLC is installed on device. If not, shows an AlertDialog and offers
     * the user to install it from the Play Store.
     * @param context A simple context reference
     * @return true is VLC is installed, false if not.
     */
    public static boolean checkVlc(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo("org.videolan.vlc", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            context.startActivity(new Intent(context, WarningActivity.class));
            return false;
        }
    }
}
