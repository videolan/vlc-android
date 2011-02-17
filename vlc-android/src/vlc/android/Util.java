package vlc.android;

import android.content.Context;
import android.widget.Toast;

public class Util {
    /** A set of utility functions for the VLC application */
    
    public static void toaster(String message, int duration) {
        Toast toast = Toast.makeText(VLC.getActivityContext(), 
                                     message, duration);
        // toast.setGravity();
        toast.show();
    }
}
