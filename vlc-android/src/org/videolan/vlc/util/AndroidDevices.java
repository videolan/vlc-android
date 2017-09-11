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
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.MotionEvent;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.RemoteControlClientReceiver;
import org.videolan.vlc.VLCApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

public class AndroidDevices {
    public final static String TAG = "VLC/UiTools/AndroidDevices";
    public final static String EXTERNAL_PUBLIC_DIRECTORY = Environment.getExternalStorageDirectory().getPath();
    public static final File SUBTITLES_DIRECTORY;

    public final static boolean isPhone;
    public final static boolean hasCombBar;
    public final static boolean hasNavBar;
    public final static boolean hasTsp;
    public final static boolean isAndroidTv;
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
        hasNavBar = AndroidUtil.isICSOrLater && !devicesWithoutNavBar.contains(android.os.Build.MODEL);
        final Context ctx = VLCApplication.getAppContext();
        final PackageManager pm = ctx.getPackageManager();
        hasTsp = pm == null || pm.hasSystemFeature("android.hardware.touchscreen");
        isAndroidTv = pm != null && pm.hasSystemFeature("android.software.leanback");
        isChromeBook = pm != null && pm.hasSystemFeature("org.chromium.arc.device_management");
        hasPlayServices = pm == null || hasPlayServices(pm);
        hasPiP = AndroidUtil.isOOrLater || AndroidUtil.isNougatOrLater && isAndroidTv;
        isPhone = ((TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE))
                .getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
        // hasCombBar test if device has Combined Bar : only for tablet with Honeycomb or ICS
        hasCombBar = !AndroidDevices.isPhone && AndroidUtil.isHoneycombOrLater
                && !AndroidUtil.isJellyBeanMR1OrLater;
        SUBTITLES_DIRECTORY = new File(ctx.getExternalFilesDir(null), "subs");
    }

    public static boolean hasExternalStorage() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    /**
     * hasCombBar test if device has Combined Bar : only for tablet with Honeycomb or ICS
     */

    public static ArrayList<String> getExternalStorageDirectories() {
        BufferedReader bufReader = null;
        final ArrayList<String> list = new ArrayList<>();
        try {
            bufReader = new BufferedReader(new FileReader("/proc/mounts"));
            String line;
            while ((line = bufReader.readLine()) != null) {

                StringTokenizer tokens = new StringTokenizer(line, " ");
                String device = tokens.nextToken();
                String mountpoint = tokens.nextToken();
                String type = tokens.nextToken();

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
        return list;
    }

    public static List<MediaWrapper> getMediaDirectoriesList() {
        final String storages[] = AndroidDevices.getMediaDirectories();
        final LinkedList<MediaWrapper> list = new LinkedList<>();
        MediaWrapper directory;
        for (String mediaDirLocation : storages) {
            if (!(new File(mediaDirLocation).exists()))
                continue;
            directory = new MediaWrapper(AndroidUtil.PathToUri(mediaDirLocation));
            directory.setType(MediaWrapper.TYPE_DIR);
            if (TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, mediaDirLocation))
                directory.setDisplayTitle(VLCApplication.getAppResources().getString(R.string.internal_memory));
            list.add(directory);
        }
        return list;
    }

    public static String[] getMediaDirectories() {
        final ArrayList<String> list = new ArrayList<>();
        list.add(EXTERNAL_PUBLIC_DIRECTORY);
        list.addAll(getExternalStorageDirectories());
        list.addAll(Arrays.asList(CustomDirectories.getCustomDirectories()));
        return list.toArray(new String[list.size()]);
    }

    @TargetApi(VERSION_CODES.HONEYCOMB_MR1)
    public static float getCenteredAxis(MotionEvent event,
                                        InputDevice device, int axis) {
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

    public static boolean isVPNActive() {
        if (AndroidUtil.isLolliPopOrLater) {
            final ConnectivityManager cm = (ConnectivityManager)VLCApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);

            for (Network network : cm.getAllNetworks()) {
                if (cm.getNetworkCapabilities(network).hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                    return true;
            }
            return false;
        } else {
            try {
                final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                while (networkInterfaces.hasMoreElements()) {
                    final NetworkInterface networkInterface = networkInterfaces.nextElement();
                    final String name = networkInterface.getDisplayName();
                    if (name.startsWith("ppp") || name.startsWith("tun") || name.startsWith("tap"))
                        return true;
                }
            } catch (SocketException ignored) {}
            return false;
        }
    }

    public static boolean hasLANConnection() {
        boolean networkEnabled = false;
        final ConnectivityManager connectivity = (ConnectivityManager) (VLCApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE));
        if (connectivity != null) {
            final NetworkInfo networkInfo = connectivity.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected() &&
                    (networkInfo.getType() != ConnectivityManager.TYPE_MOBILE)) {
                networkEnabled = true;
            }
        }
        return networkEnabled;
    }

    public static boolean hasConnection() {
        boolean networkEnabled = false;
        final ConnectivityManager connectivity = (ConnectivityManager) (VLCApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE));
        if (connectivity != null) {
            final NetworkInfo networkInfo = connectivity.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                networkEnabled = true;
            }
        }
        return networkEnabled;
    }

    public static boolean hasMobileConnection() {
        boolean networkEnabled = false;
        final ConnectivityManager connectivity = (ConnectivityManager) (VLCApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE));
        if (connectivity != null) {
            final NetworkInfo networkInfo = connectivity.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected() &&
                    (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE)) {
                networkEnabled = true;
            }
        }
        return networkEnabled;
    }

    public static void setRemoteControlReceiverEnabled(boolean enabled) {
        VLCApplication.getAppContext().getPackageManager().setComponentEnabledSetting(
                new ComponentName(VLCApplication.getAppContext(), RemoteControlClientReceiver.class),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private static boolean isManufacturerBannedForMediastyleNotifications() {
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
}
