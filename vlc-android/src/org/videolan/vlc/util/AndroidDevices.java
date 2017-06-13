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
import android.os.Build.VERSION;
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
import java.io.FileNotFoundException;
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
    public static final File SUBTITLES_DIRECTORY = new File(VLCApplication.getAppContext().getExternalFilesDir(null), "subs");

    final static boolean hasNavBar;
    final static boolean hasTsp = VLCApplication.getAppContext().getPackageManager().hasSystemFeature("android.hardware.touchscreen");
    final static boolean isTv = VLCApplication.getAppContext().getPackageManager().hasSystemFeature("android.software.leanback");
    final static boolean showInternalStorage = !TextUtils.equals(Build.BRAND, "Swisscom") && !TextUtils.equals(Build.BOARD, "sprint");
    public final static boolean isChromeBook = VLCApplication.getAppContext().getPackageManager().hasSystemFeature("org.chromium.arc.device_management");
    private final static String[] noMediaStyleManufacturers = {"huawei", "symphony teleca"};
    public final static boolean showMediaStyle = !isManufacturerBannedForMediastyleNotifications();
    public static final boolean hasPiP = AndroidUtil.isOOrLater || AndroidUtil.isNougatOrLater && isTv;


    static {
        HashSet<String> devicesWithoutNavBar = new HashSet<>();
        devicesWithoutNavBar.add("HTC One V");
        devicesWithoutNavBar.add("HTC One S");
        devicesWithoutNavBar.add("HTC One X");
        devicesWithoutNavBar.add("HTC One XL");
        hasNavBar = AndroidUtil.isICSOrLater
                && !devicesWithoutNavBar.contains(android.os.Build.MODEL);
    }

    public static boolean hasExternalStorage() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    public static boolean hasNavBar() {
        return hasNavBar;
    }

    /**
     * hasCombBar test if device has Combined Bar : only for tablet with Honeycomb or ICS
     */
    public static boolean hasCombBar() {
        return (!AndroidDevices.isPhone()
                && ((VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) &&
                (VERSION.SDK_INT <= VERSION_CODES.JELLY_BEAN)));
    }

    public static boolean isPhone() {
        TelephonyManager manager = (TelephonyManager) VLCApplication.getAppContext().getSystemService(Context.TELEPHONY_SERVICE);
        return manager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
    }

    public static boolean hasTsp() {
        return hasTsp;
    }

    public static boolean isAndroidTv() {
        return isTv;
    }

    public static boolean showInternalStorage() {
        return showInternalStorage;
    }

    public static ArrayList<String> getExternalStorageDirectories() {
        BufferedReader bufReader = null;
        ArrayList<String> list = new ArrayList<>();

        List<String> typeWL = Arrays.asList("vfat", "exfat", "sdcardfs", "fuse", "ntfs", "fat32", "ext3", "ext4", "esdfs");
        List<String> typeBL = Arrays.asList("tmpfs");
        String[] mountWL = {"/mnt", "/Removable", "/storage"};
        String[] mountBL = {
                EXTERNAL_PUBLIC_DIRECTORY,
                "/mnt/secure",
                "/mnt/shell",
                "/mnt/asec",
                "/mnt/nand",
                "/mnt/runtime",
                "/mnt/obb",
                "/mnt/media_rw/extSdCard",
                "/mnt/media_rw/sdcard",
                "/storage/emulated"};
        String[] deviceWL = {
                "/dev/block/vold",
                "/dev/fuse",
                "/mnt/media_rw"
        };

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
                    int position = Strings.containsName(list, FileUtils.getFileNameFromPath(mountpoint));
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
        String storages[] = AndroidDevices.getMediaDirectories();
        LinkedList<MediaWrapper> list = new LinkedList<>();
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
        ArrayList<String> list = new ArrayList<String>();
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

    public static boolean hasPlayServices() {
        try {
            VLCApplication.getAppContext().getPackageManager().getPackageInfo("com.google.android.gsf", PackageManager.GET_SERVICES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {}
        return false;
    }

    public static boolean isVPNActive() {
        if (AndroidUtil.isLolliPopOrLater) {
            ConnectivityManager cm = (ConnectivityManager)VLCApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);

            for (Network network : cm.getAllNetworks()) {
                if (cm.getNetworkCapabilities(network).hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                    return true;
            }
            return false;
        } else {
            try {
                Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                while (networkInterfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = networkInterfaces.nextElement();
                    String name = networkInterface.getDisplayName();
                    if (name.startsWith("ppp") || name.startsWith("tun") || name.startsWith("tap"))
                        return true;
                }
            } catch (SocketException ignored) {}
            return false;
        }
    }

    public static boolean hasLANConnection() {
        boolean networkEnabled = false;
        ConnectivityManager connectivity = (ConnectivityManager) (VLCApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE));
        if (connectivity != null) {
            NetworkInfo networkInfo = connectivity.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected() &&
                    (networkInfo.getType() != ConnectivityManager.TYPE_MOBILE)) {
                networkEnabled = true;
            }
        }
        return networkEnabled;
    }

    public static boolean hasConnection() {
        boolean networkEnabled = false;
        ConnectivityManager connectivity = (ConnectivityManager) (VLCApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE));
        if (connectivity != null) {
            NetworkInfo networkInfo = connectivity.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                networkEnabled = true;
            }
        }
        return networkEnabled;
    }

    public static boolean hasMobileConnection() {
        boolean networkEnabled = false;
        ConnectivityManager connectivity = (ConnectivityManager) (VLCApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE));
        if (connectivity != null) {
            NetworkInfo networkInfo = connectivity.getActiveNetworkInfo();
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
}
