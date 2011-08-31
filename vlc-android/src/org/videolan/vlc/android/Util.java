package org.videolan.vlc.android;

import java.lang.reflect.Field;
import java.text.DecimalFormat;

import android.widget.Toast;

public class Util {
    /** A set of utility functions for the VLC application */

	@Deprecated
    public static void toaster(String message, int duration) {
        Toast.makeText(MainActivity.getInstance(),
                       message, duration)
             .show();
    }

    @Deprecated
    public static void toaster(String message) {
        toaster(message, Toast.LENGTH_SHORT);
    }

    /** Print an on-screen message to alert the user */
    public static void toaster(int stringId, int duration) {
        Toast.makeText(MainActivity.getInstance(),
                       stringId, duration)
             .show();
    }

    public static void toaster(int stringId) {
        toaster(stringId, Toast.LENGTH_SHORT);
    }

	/**
	 * Convert time to a string
	 * @param millis e.g.time/length from file
	 * @return formated string (hh:)mm:ss
	 */
	public static String millisToString(long millis) {
		millis /= 1000;
		int sec = (int) (millis % 60);
		millis /= 60;
		int min = (int) (millis % 60);
		millis /= 60;
		int hours = (int) millis;

		String time;
		DecimalFormat format = new DecimalFormat("00");
		if (millis > 0) {
			time = hours + ":" + format.format(min) + ":" + format.format(sec);
		} else {
			time = min + ":" + format.format(sec);
		}
		return time;
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
