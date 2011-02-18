package vlc.android;

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
}
