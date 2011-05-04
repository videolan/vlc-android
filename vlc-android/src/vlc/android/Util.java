package vlc.android;

import android.widget.Toast;

public class Util {
    /** A set of utility functions for the VLC application */

	@Deprecated
    public static void toaster(String message, int duration) {
        Toast.makeText(MediaLibraryActivity.getInstance(),
                       message, duration)
             .show();
    }

    @Deprecated
    public static void toaster(String message) {
        toaster(message, Toast.LENGTH_SHORT);
    }

    /** Print an on-screen message to alert the user */
    public static void toaster(int stringId, int duration) {
        Toast.makeText(MediaLibraryActivity.getInstance(),
                       stringId, duration)
             .show();
    }

    public static void toaster(int stringId) {
        toaster(stringId, Toast.LENGTH_SHORT);
    }
}
