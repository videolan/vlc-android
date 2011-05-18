package org.videolan.vlc.android;

import java.lang.reflect.Field;

import android.content.Context;
import android.widget.Toast;

public class Util {
    /** A set of utility functions for the VLC application */

    @Deprecated
    public static void toaster(String message, int duration) {
        Toast.makeText(VLC.getActivityContext(),
                       message, duration)
             .show();
    }

    @Deprecated
    public static void toaster(String message) {
        toaster(message, 1500);
    }

    /** Print an on-screen message to alert the user */
    public static void toaster(int stringId, int duration) {
        Toast.makeText(VLC.getActivityContext(),
                       stringId, duration)
             .show();
    }

    public static void toaster(int stringId) {
        toaster(stringId, 1500);
    }
    
    private static int apiLevel = 0;
    
	/**
	 * Returns the current Android SDK version
	 * This function is called by the native code.
	 * This is used to know if we should use the native audio output,
	 * or the amem as a fallback.
	 */
    public static int getApiLevel() 
    {
        if(apiLevel > 0)
            return apiLevel;
        if( android.os.Build.VERSION.SDK.equalsIgnoreCase("3") ) 
        {
            apiLevel = 3;
        } 
        else
        {
            try
            {
                final Field f = android.os.Build.VERSION.class.getDeclaredField( "SDK_INT" );
                apiLevel = (Integer)f.get(null);
            } 
            catch (final Exception e) 
            {
                return 0;
            }
        }
        return apiLevel;
    }
}
