package org.videolan.vlc.util

import android.content.Context
import android.os.Build
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication

object AppUtils {

    fun getVersionName(context: Context): String {
        return context.packageManager.getPackageInfo(VLCApplication.appContext.packageName, 0).versionName
    }

    fun getVersionCode(context: Context): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        else context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
    }

    fun isBeta(context: Context): Boolean {
        return context.resources.getBoolean(R.bool.is_beta)
    }
}