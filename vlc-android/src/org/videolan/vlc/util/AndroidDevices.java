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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.view.InputDevice;
import android.view.MotionEvent;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

public class AndroidDevices {
    public final static String TAG = "VLC/UiTools/AndroidDevices";
    public final static String EXTERNAL_PUBLIC_DIRECTORY = Environment.getExternalStorageDirectory().getPath();

    public static final int PERMISSION_STORAGE_TAG = 255;
    public static final int PERMISSION_SETTINGS_TAG = 254;

    final static boolean hasNavBar;
    final static boolean hasTsp;

    static {
        HashSet<String> devicesWithoutNavBar = new HashSet<String>();
        devicesWithoutNavBar.add("HTC One V");
        devicesWithoutNavBar.add("HTC One S");
        devicesWithoutNavBar.add("HTC One X");
        devicesWithoutNavBar.add("HTC One XL");
        hasNavBar = AndroidUtil.isICSOrLater()
                && !devicesWithoutNavBar.contains(android.os.Build.MODEL);
        hasTsp = VLCApplication.getAppContext().getPackageManager().hasSystemFeature("android.hardware.touchscreen");
    }

    public static boolean hasExternalStorage() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    public static boolean hasNavBar()
    {
        return hasNavBar;
    }

    /** hasCombBar test if device has Combined Bar : only for tablet with Honeycomb or ICS */
    public static boolean hasCombBar() {
        return (!AndroidDevices.isPhone()
                && ((VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) &&
                (VERSION.SDK_INT <= VERSION_CODES.JELLY_BEAN)));
    }

    public static boolean isPhone(){
        TelephonyManager manager = (TelephonyManager)VLCApplication.getAppContext().getSystemService(Context.TELEPHONY_SERVICE);
        return manager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
    }

    public static boolean hasTsp(){
        return hasTsp;
    }

    public static ArrayList<String> getStorageDirectories() {
        BufferedReader bufReader = null;
        ArrayList<String> list = new ArrayList<String>();
        list.add(EXTERNAL_PUBLIC_DIRECTORY);

        List<String> typeWL = Arrays.asList("vfat", "exfat", "sdcardfs", "fuse", "ntfs", "fat32", "ext3", "ext4", "esdfs");
        List<String> typeBL = Arrays.asList("tmpfs");
        String[] mountWL = { "/mnt", "/Removable", "/storage" };
        String[] mountBL = {
                "/mnt/secure",
                "/mnt/shell",
                "/mnt/asec",
                "/mnt/obb",
                "/mnt/media_rw/extSdCard",
                "/mnt/media_rw/sdcard",
                "/storage/emulated" };
        String[] deviceWL = {
                "/dev/block/vold",
                "/dev/fuse",
                "/mnt/media_rw" };

        try {
            bufReader = new BufferedReader(new FileReader("/proc/mounts"));
            String line;
            while((line = bufReader.readLine()) != null) {

                StringTokenizer tokens = new StringTokenizer(line, " ");
                String device = tokens.nextToken();
                String mountpoint = tokens.nextToken();
                String type = tokens.nextToken();

                // skip if already in list or if type/mountpoint is blacklisted
                if (list.contains(mountpoint) || typeBL.contains(type) || Strings.startsWith(mountBL, mountpoint))
                    continue;

                // check that device is in whitelist, and either type or mountpoint is in a whitelist
                if (Strings.startsWith(deviceWL, device) && (typeWL.contains(type) || Strings.startsWith(mountWL, mountpoint))) {
                    int position = Strings.containsName(list, Strings.getName(mountpoint));
                    if (position > -1)
                        list.remove(position);
                    list.add(mountpoint);
                }
            }
        }
        catch (FileNotFoundException e) {}
        catch (IOException e) {}
        finally {
            Util.close(bufReader);
        }
        return list;
    }

    public static String[] getMediaDirectories() {
        ArrayList<String> list = new ArrayList<String>();
        list.addAll(getStorageDirectories());
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

    public static boolean hasLANConnection(){
        boolean networkEnabled = false;
        ConnectivityManager connectivity = (ConnectivityManager)(VLCApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE));
        if (connectivity != null) {
            NetworkInfo networkInfo = connectivity.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected() &&
                    (networkInfo.getType() != ConnectivityManager.TYPE_MOBILE)) {
                networkEnabled = true;
            }
        }
        return networkEnabled;
    }

    /*
     * Marshmallow permission system management
     */

    @TargetApi(VERSION_CODES.M)
    public static boolean canWriteSettings(Context context) {
        return !AndroidUtil.isMarshMallowOrLater() || Settings.System.canWrite(context);
    }

    public static boolean canReadStorage() {
        return ContextCompat.checkSelfPermission(VLCApplication.getAppContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static void checkReadStoragePermission(Activity activity, boolean exit) {
        if (AndroidUtil.isMarshMallowOrLater() && !canReadStorage()) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {

                showStoragePermissionDialog(activity, exit);

            } else {
                requestStoragePermission(activity);
            }
        }
    }

    public static void checkWriteSettingsPermission(Activity activity) {
        if (AndroidUtil.isMarshMallowOrLater() && !canWriteSettings(activity)) {
            showSettingsPermissionDialog(activity);
        }
    }

    private static Dialog sAlertDialog;

    public static void showSettingsPermissionDialog(final Activity activity) {
        if (sAlertDialog != null && sAlertDialog.isShowing())
            return;
        sAlertDialog = createSettingsDialogCompat(activity);
    }

    public static void showStoragePermissionDialog(final Activity activity, boolean exit) {
        if (sAlertDialog != null && sAlertDialog.isShowing())
            return;
        if (activity instanceof AppCompatActivity)
            sAlertDialog = createDialogCompat(activity, exit);
        else
            sAlertDialog = createDialog(activity, exit);
    }

    private static Dialog createDialog(final Activity activity, boolean exit) {
        android.app.AlertDialog.Builder dialogBuilder = new  android.app.AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.allow_storage_access_title))
                .setMessage(activity.getString(R.string.allow_storage_access_description))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(activity.getString(R.string.permission_ask_again), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
                        if (!settings.getBoolean("user_declined_storage_access", false))
                            requestStoragePermission(activity);
                        else {
                            Intent i = new Intent();
                            i.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                            i.addCategory(Intent.CATEGORY_DEFAULT);
                            i.setData(Uri.parse("package:" + VLCApplication.getAppContext().getPackageName()));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try {
                                activity.startActivity(i);
                            } catch (Exception ex) {}
                        }
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("user_declined_storage_access", true);
                        Util.commitPreferences(editor);
                    }
                });
        if (exit) {
            dialogBuilder.setNegativeButton(activity.getString(R.string.exit_app), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    activity.finish();
                }
            })
                    .setCancelable(false);
        }
        return dialogBuilder.show();
    }

    private static Dialog createDialogCompat(final Activity activity, boolean exit) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.allow_storage_access_title))
                .setMessage(activity.getString(R.string.allow_storage_access_description))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(activity.getString(R.string.permission_ask_again), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
                        if (!settings.getBoolean("user_declined_storage_access", false))
                            requestStoragePermission(activity);
                        else {
                            Intent i = new Intent();
                            i.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                            i.addCategory(Intent.CATEGORY_DEFAULT);
                            i.setData(Uri.parse("package:" + VLCApplication.getAppContext().getPackageName()));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try {
                                activity.startActivity(i);
                            } catch (Exception ex) {}
                        }
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("user_declined_storage_access", true);
                        Util.commitPreferences(editor);
                    }
                });
        if (exit) {
            dialogBuilder.setNegativeButton(activity.getString(R.string.exit_app), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    activity.finish();
                }
            })
                    .setCancelable(false);
        }
        return dialogBuilder.show();
    }

    private static Dialog createSettingsDialogCompat(final Activity activity) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.allow_settings_access_title))
                .setMessage(activity.getString(R.string.allow_settings_access_description))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(activity.getString(R.string.permission_ask_again), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
                        Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        i.setData(Uri.parse("package:" + activity.getPackageName()));
                        try {
                            activity.startActivity(i);
                        } catch (Exception ex) {}
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("user_declined_settings_access", true);
                        Util.commitPreferences(editor);
                    }
                });
        return dialogBuilder.show();
    }

    private static void requestStoragePermission(Activity activity){
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_STORAGE_TAG);
    }
}
