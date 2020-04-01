/*****************************************************************************
 * AndroidDevices.java
 *
 * Copyright © 2011-2014 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 */

package org.videolan.resources

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Environment
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.view.InputDevice
import android.view.MotionEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.tools.containsName
import org.videolan.tools.getFileNameFromPath
import org.videolan.tools.startsWith
import java.io.*
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@TargetApi(VERSION_CODES.N)
object AndroidDevices {
    const val TAG = "VLC/UiTools/AndroidDevices"
    val EXTERNAL_PUBLIC_DIRECTORY: String = Environment.getExternalStorageDirectory().path
    val isPhone: Boolean
    val hasNavBar: Boolean
    val hasTsp: Boolean
    val isAndroidTv: Boolean
    val watchDevices: Boolean
    val isTv: Boolean
    val isAmazon = TextUtils.equals(Build.MANUFACTURER, "Amazon")
    val isChromeBook: Boolean
    val hasPiP: Boolean
    val pipAllowed: Boolean
    private val noMediaStyleManufacturers = arrayOf("huawei", "symphony teleca")
    val showMediaStyle = !isManufacturerBannedForMediastyleNotifications
    val hasPlayServices: Boolean

    //Devices mountpoints management
    private val typeWL = Arrays.asList("vfat", "exfat", "sdcardfs", "fuse", "ntfs", "fat32", "ext3", "ext4", "esdfs")
    private val typeBL = listOf("tmpfs")
    private val mountWL = arrayOf("/mnt", "/Removable", "/storage")
    val mountBL = arrayOf(EXTERNAL_PUBLIC_DIRECTORY, "/mnt/secure", "/mnt/shell", "/mnt/asec", "/mnt/nand", "/mnt/runtime", "/mnt/obb", "/mnt/media_rw/extSdCard", "/mnt/media_rw/sdcard", "/storage/emulated", "/var/run/arc")
    private val deviceWL = arrayOf("/dev/block/vold", "/dev/fuse", "/mnt/media_rw", "passthrough")

    /**
     * hasCombBar test if device has Combined Bar : only for tablet with Honeycomb or ICS
     */

    // skip if already in list or if type/mountpoint is blacklisted
    // check that device is in whitelist, and either type or mountpoint is in a whitelist
    val externalStorageDirectories: List<String>
        get() {
            var bufReader: BufferedReader? = null
            val list = ArrayList<String>()
            try {
                bufReader = BufferedReader(FileReader("/proc/mounts"))
                var line = bufReader.readLine()
                while (line != null) {

                    val tokens = StringTokenizer(line, " ")
                    val device = tokens.nextToken()
                    val mountpoint = tokens.nextToken().replace("\\\\040".toRegex(), " ")
                    val type = if (tokens.hasMoreTokens()) tokens.nextToken() else null
                    if (list.contains(mountpoint) || typeBL.contains(type) || startsWith(mountBL, mountpoint)) {
                        line = bufReader.readLine()
                        continue
                    }
                    if (startsWith(deviceWL, device) && (typeWL.contains(type) || startsWith(mountWL, mountpoint))) {
                        val position = containsName(list, mountpoint.getFileNameFromPath())
                        if (position > -1) list.removeAt(position)
                        list.add(mountpoint)
                    }
                    line = bufReader.readLine()
                }
            } catch (ignored: IOException) {
            } finally {
                close(bufReader)
            }
            list.remove(EXTERNAL_PUBLIC_DIRECTORY)
            return list
        }

    private val isManufacturerBannedForMediastyleNotifications: Boolean
        get() {
            if (!AndroidUtil.isMarshMallowOrLater)
                for (manufacturer in noMediaStyleManufacturers)
                    if (Build.MANUFACTURER.toLowerCase(Locale.getDefault()).contains(manufacturer))
                        return true
            return false
        }

    init {
        val devicesWithoutNavBar = HashSet<String>()
        devicesWithoutNavBar.add("HTC One V")
        devicesWithoutNavBar.add("HTC One S")
        devicesWithoutNavBar.add("HTC One X")
        devicesWithoutNavBar.add("HTC One XL")
        hasNavBar = !devicesWithoutNavBar.contains(Build.MODEL)
        val ctx = AppContextProvider.appContext
        val pm = ctx.packageManager
        hasTsp = pm == null || pm.hasSystemFeature("android.hardware.touchscreen")
        isAndroidTv = pm != null && pm.hasSystemFeature("android.software.leanback")
        watchDevices = isAndroidTv && Build.MODEL.startsWith("Bouygtel")
        isChromeBook = pm != null && pm.hasSystemFeature("org.chromium.arc.device_management")
        isTv = isAndroidTv || !isChromeBook && !hasTsp
        hasPlayServices = pm == null || hasPlayServices(pm)
        hasPiP = AndroidUtil.isOOrLater && pm != null && pm.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) || AndroidUtil.isNougatOrLater && isAndroidTv
        pipAllowed = hasPiP || hasTsp && !AndroidUtil.isOOrLater
        val tm = if (ctx != null) ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager else null
        isPhone = tm == null || tm.phoneType != TelephonyManager.PHONE_TYPE_NONE
    }

    fun hasExternalStorage(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    fun showInternalStorage(): Boolean {
        return (!TextUtils.equals(Build.BRAND, "Swisscom") && !TextUtils.equals(Build.BOARD, "sprint")
                && !TextUtils.equals(Build.BRAND, "BouyguesTelecom"))
    }


    @TargetApi(VERSION_CODES.HONEYCOMB_MR1)
    fun getCenteredAxis(event: MotionEvent, device: InputDevice, axis: Int): Float {
        val range = device.getMotionRange(axis, event.source)

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            val flat = range.flat
            val value = event.getAxisValue(axis)

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                return value
            }
        }
        return 0f
    }

    fun canUseSystemNightMode(): Boolean {
        return Build.VERSION.SDK_INT > VERSION_CODES.P || Build.VERSION.SDK_INT == VERSION_CODES.P && "samsung" == Build.MANUFACTURER.toLowerCase(Locale.US)
    }

    private fun hasPlayServices(pm: PackageManager): Boolean {
        try {
            pm.getPackageInfo("com.google.android.gsf", PackageManager.GET_SERVICES)
            return true
        } catch (ignored: PackageManager.NameNotFoundException) {
        }

        return false
    }

    fun isDex(ctx: Context): Boolean {
        if (!AndroidUtil.isNougatOrLater) return false
        try {
            val config = ctx.resources.configuration
            val configClass = config.javaClass
            return configClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(configClass) == configClass.getField("semDesktopModeEnabled").getInt(config)
        } catch (ignored: Exception) {
            return false
        }

    }

    object MediaFolders {
        private val EXTERNAL_PUBLIC_MOVIES_DIRECTORY_FILE: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        private val EXTERNAL_PUBLIC_MUSIC_DIRECTORY_FILE: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        private val EXTERNAL_PUBLIC_PODCAST_DIRECTORY_FILE: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS)
        private val EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_FILE: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        private val EXTERNAL_PUBLIC_DCIM_DIRECTORY_FILE: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        private val WHATSAPP_VIDEOS_FILE: File = File("$EXTERNAL_PUBLIC_DIRECTORY/WhatsApp/Media/WhatsApp Video/")

        val EXTERNAL_PUBLIC_MOVIES_DIRECTORY_URI = getFolderUri(EXTERNAL_PUBLIC_MOVIES_DIRECTORY_FILE)
        val EXTERNAL_PUBLIC_MUSIC_DIRECTORY_URI = getFolderUri(EXTERNAL_PUBLIC_MUSIC_DIRECTORY_FILE)
        val EXTERNAL_PUBLIC_PODCAST_DIRECTORY_URI = getFolderUri(EXTERNAL_PUBLIC_PODCAST_DIRECTORY_FILE)
        val EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI = getFolderUri(EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_FILE)
        val EXTERNAL_PUBLIC_DCIM_DIRECTORY_URI = getFolderUri(EXTERNAL_PUBLIC_DCIM_DIRECTORY_FILE)
        val WHATSAPP_VIDEOS_FILE_URI = getFolderUri(WHATSAPP_VIDEOS_FILE)

        private fun getFolderUri(file: File): Uri {
            try {
                return Uri.parse("file://" + file.canonicalPath)
            } catch (ignored: IOException) {
                return Uri.parse("file://" + file.path)
            }

        }

        fun isOneOfMediaFolders(uri: Uri) = EXTERNAL_PUBLIC_MOVIES_DIRECTORY_URI == uri || EXTERNAL_PUBLIC_MUSIC_DIRECTORY_URI == uri || EXTERNAL_PUBLIC_PODCAST_DIRECTORY_URI == uri || EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI == uri || EXTERNAL_PUBLIC_DCIM_DIRECTORY_URI == uri || WHATSAPP_VIDEOS_FILE == uri
    }

    fun close(closeable: Closeable?): Boolean {
        if (closeable != null)
            try {
                closeable.close()
                return true
            } catch (e: IOException) {
            }

        return false
    }
}
