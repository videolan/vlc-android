package org.videolan.medialibrary;


import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;

public class Tools {


    /*
     * Convert file:// uri from real path to emulated FS path.
     */
    public static Uri convertLocalUri(Uri uri) {
        if (!TextUtils.equals(uri.getScheme(), "file") || !uri.getPath().startsWith("/sdcard"))
            return uri;
        String path = uri.toString();
        return Uri.parse(path.replace("/sdcard", Environment.getExternalStorageDirectory().getPath()));
    }
}
