/*****************************************************************************
 * AndroidDevices.java
 *****************************************************************************
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
 *****************************************************************************/

package org.videolan.vlc.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.MotionEvent;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.VLCApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

public class AndroidDevices {
    public final static String TAG = "VLC/UiTools/AndroidDevices";
    public final static String EXTERNAL_PUBLIC_DIRECTORY = Environment.getExternalStorageDirectory().getPath();
    public final static boolean isPhone;
    public final static boolean hasNavBar;
    public final static boolean hasTsp;
    public final static boolean isAndroidTv;
    public final static boolean watchDevices;
    public final static boolean isTv;
    public final static boolean isAmazon = TextUtils.equals(Build.MANUFACTURER,"Amazon");
    public final static boolean isChromeBook;
    public static final boolean hasPiP;
    public final static boolean showInternalStorage = !TextUtils.equals(Build.BRAND, "Swisscom") && !TextUtils.equals(Build.BOARD, "sprint");
    private final static String[] noMediaStyleManufacturers = {"huawei", "symphony teleca"};
    public final static boolean showMediaStyle = !isManufacturerBannedForMediastyleNotifications();
    public static final boolean hasPlayServices;

    //Devices mountpoints management
    private static final List<String> typeWL = Arrays.asList("vfat", "exfat", "sdcardfs", "fuse", "ntfs", "fat32", "ext3", "ext4", "esdfs");
    private static final List<String> typeBL = Arrays.asList("tmpfs");
    private static final String[] mountWL = {"/mnt", "/Removable", "/storage"};
    private static final String[] mountBL = {
            EXTERNAL_PUBLIC_DIRECTORY,
            "/mnt/secure",
            "/mnt/shell",
            "/mnt/asec",
            "/mnt/nand",
            "/mnt/runtime",
            "/mnt/obb",
            "/mnt/media_rw/extSdCard",
            "/mnt/media_rw/sdcard",
            "/storage/emulated",
            "/var/run/arc"
    };
    private static final String[] deviceWL = {
            "/dev/block/vold",
            "/dev/fuse",
            "/mnt/media_rw"
    };

    static {
        final HashSet<String> devicesWithoutNavBar = new HashSet<>();
        devicesWithoutNavBar.add("HTC One V");
        devicesWithoutNavBar.add("HTC One S");
        devicesWithoutNavBar.add("HTC One X");
        devicesWithoutNavBar.add("HTC One XL");
        hasNavBar = !devicesWithoutNavBar.contains(android.os.Build.MODEL);
        final Context ctx = VLCApplication.getAppContext();
        final PackageManager pm = ctx != null ? ctx.getPackageManager() : null;
        hasTsp = pm == null || pm.hasSystemFeature("android.hardware.touchscreen");
        isAndroidTv = pm != null && pm.hasSystemFeature("android.software.leanback");
        watchDevices = isAndroidTv && isBbox();
        isChromeBook = pm != null && pm.hasSystemFeature("org.chromium.arc.device_management");
        isTv = isAndroidTv || (!isChromeBook && !hasTsp);
        hasPlayServices = pm == null || hasPlayServices(pm);
        hasPiP = AndroidUtil.isOOrLater || AndroidUtil.isNougatOrLater && isAndroidTv;
        final TelephonyManager tm = ctx != null ? ((TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE)) : null;
        isPhone = tm == null || tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
        // hasCombBar test if device has Combined Bar : only for tablet with Honeycomb or ICS
    }

    private static boolean isBbox() {
        return Build.MODEL.startsWith("Bouygtel");
    }

    public static boolean hasExternalStorage() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    /**
     * hasCombBar test if device has Combined Bar : only for tablet with Honeycomb or ICS
     */

    public static List<String> getExternalStorageDirectories() {
        BufferedReader bufReader = null;
        final List<String> list = new ArrayList<>();
        try {
            bufReader = new BufferedReader(new FileReader("/proc/mounts"));
            String line;
            while ((line = bufReader.readLine()) != null) {

                final StringTokenizer tokens = new StringTokenizer(line, " ");
                final String device = tokens.nextToken();
                final String mountpoint = tokens.nextToken();
                final String type = tokens.hasMoreTokens() ? tokens.nextToken() : null;

                // skip if already in list or if type/mountpoint is blacklisted
                if (list.contains(mountpoint) || typeBL.contains(type) || Strings.startsWith(mountBL, mountpoint))
                    continue;

                // check that device is in whitelist, and either type or mountpoint is in a whitelist
                if (Strings.startsWith(deviceWL, device) && (typeWL.contains(type) || Strings.startsWith(mountWL, mountpoint))) {
                    final int position = Strings.containsName(list, FileUtils.getFileNameFromPath(mountpoint));
                    if (position > -1)
                        list.remove(position);
                    list.add(mountpoint);
                }
            }
        } catch (IOException ignored) {
        } finally {
            Util.close(bufReader);
        }
        list.remove(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
        return list;
    }


    @TargetApi(VERSION_CODES.HONEYCOMB_MR1)
    public static float getCenteredAxis(MotionEvent event, InputDevice device, int axis) {
        final InputDevice.MotionRange range =
                device.getMotionRange(axis, event.getSource());

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            final float flat = range.getFlat();
            final float value = event.getAxisValue(axis);

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }

    public static boolean showTvUi(Context context) {
        return isTv || (Settings.INSTANCE.getInstance(context.getApplicationContext()).getBoolean("tv_ui", false));
    }

    private static boolean isManufacturerBannedForMediastyleNotifications() {
        if (!AndroidUtil.isMarshMallowOrLater)
            for (String manufacturer : noMediaStyleManufacturers)
                if (Build.MANUFACTURER.toLowerCase(Locale.getDefault()).contains(manufacturer))
                    return true;
        return false;
    }

    private static boolean hasPlayServices(PackageManager pm) {
        try {
            pm.getPackageInfo("com.google.android.gsf", PackageManager.GET_SERVICES);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {}
        return false;
    }

    public static boolean isDex(Context ctx) {
        if (!AndroidUtil.isNougatOrLater) return false;
        try {
            final Configuration config = ctx.getResources().getConfiguration();
            final Class configClass = config.getClass();
            return configClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(configClass)
                    == configClass.getField("semDesktopModeEnabled").getInt(config);
        } catch(Exception ignored) {
            return false;
        }
    }

    public static class MediaFolders {
        public final static File EXTERNAL_PUBLIC_MOVIES_DIRECTORY_FILE = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        public final static File EXTERNAL_PUBLIC_MUSIC_DIRECTORY_FILE = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        public final static File EXTERNAL_PUBLIC_PODCAST_DIRECTORY_FILE = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS);
        public final static File EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_FILE = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        public final static File WHATSAPP_VIDEOS_FILE = new File(EXTERNAL_PUBLIC_DIRECTORY+"/WhatsApp/Media/WhatsApp Video/");

        public final static Uri EXTERNAL_PUBLIC_MOVIES_DIRECTORY_URI = getFolderUri(EXTERNAL_PUBLIC_MOVIES_DIRECTORY_FILE);
        public final static Uri EXTERNAL_PUBLIC_MUSIC_DIRECTORY_URI = getFolderUri(EXTERNAL_PUBLIC_MUSIC_DIRECTORY_FILE);
        public final static Uri EXTERNAL_PUBLIC_PODCAST_DIRECTORY_URI = getFolderUri(EXTERNAL_PUBLIC_PODCAST_DIRECTORY_FILE);
        public final static Uri EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI = getFolderUri(EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_FILE);
        public final static Uri WHATSAPP_VIDEOS_FILE_URI = getFolderUri(WHATSAPP_VIDEOS_FILE);

        private static Uri getFolderUri(File file) {
            try {
                return Uri.parse("file://"+ file.getCanonicalPath());
            } catch (IOException ignored) {
                return Uri.parse("file://"+ file.getPath());
            }
        }
    }
}
