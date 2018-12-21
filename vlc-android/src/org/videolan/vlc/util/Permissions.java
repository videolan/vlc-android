/*
 * *************************************************************************
 *  Permissions.java
 * **************************************************************************
 *  Copyright © 2015-2017 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

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
import android.net.Uri;
import android.os.Build;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

public class Permissions {

    public static final int PERMISSION_STORAGE_TAG = 255;
    public static final int PERMISSION_SETTINGS_TAG = 254;
    public static final int PERMISSION_WRITE_STORAGE_TAG = 253;


    public static final int PERMISSION_SYSTEM_RINGTONE = 42;
    public static final int PERMISSION_SYSTEM_BRIGHTNESS = 43;
    public static final int PERMISSION_SYSTEM_DRAW_OVRLAYS = 44;

    /*
     * Marshmallow permission system management
     */

    @TargetApi(Build.VERSION_CODES.M)
    public static boolean canDrawOverlays(Context context) {
        return !AndroidUtil.isMarshMallowOrLater || android.provider.Settings.canDrawOverlays(context);
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static boolean canWriteSettings(Context context) {
        return !AndroidUtil.isMarshMallowOrLater || android.provider.Settings.System.canWrite(context);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static boolean canReadStorage(Context context) {
        return !AndroidUtil.isMarshMallowOrLater || ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean canWriteStorage() {
        return canWriteStorage(VLCApplication.getAppContext());
    }

    public static boolean canWriteStorage(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean checkReadStoragePermission(FragmentActivity activity, boolean exit) {
        if (AndroidUtil.isMarshMallowOrLater && !canReadStorage(activity)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                showStoragePermissionDialog(activity, exit);
            } else
                requestStoragePermission(activity, false, null);
            return false;
        }
        return true;
    }

    public static void askWriteStoragePermission(FragmentActivity activity, boolean exit, Runnable callback) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            showStoragePermissionDialog(activity, exit);
        } else
            requestStoragePermission(activity, true, callback);
    }

    public static void checkDrawOverlaysPermission(FragmentActivity activity) {
        if (AndroidUtil.isMarshMallowOrLater && !canDrawOverlays(activity)) {
            showSettingsPermissionDialog(activity, PERMISSION_SYSTEM_DRAW_OVRLAYS);
        }
    }

    public static void checkWriteSettingsPermission(FragmentActivity activity, int mode) {
        if (!canWriteSettings(activity)) showSettingsPermissionDialog(activity, mode);
    }

    private static Dialog sAlertDialog;

    private static void showSettingsPermissionDialog(final FragmentActivity activity, int mode) {
        if (activity.isFinishing() || (sAlertDialog != null && sAlertDialog.isShowing())) return;
        sAlertDialog = createSettingsDialogCompat(activity, mode);
    }

    public static void showStoragePermissionDialog(final FragmentActivity activity, boolean exit) {
        if (activity.isFinishing() || (sAlertDialog != null && sAlertDialog.isShowing())) return;
        if (activity instanceof AppCompatActivity) sAlertDialog = createDialogCompat(activity, exit);
        else sAlertDialog = createDialog(activity, exit);
    }

    private static Dialog createDialog(final FragmentActivity activity, boolean exit) {
        android.app.AlertDialog.Builder dialogBuilder = new  android.app.AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.allow_storage_access_title))
                .setMessage(activity.getString(R.string.allow_storage_access_description))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(activity.getString(R.string.permission_ask_again), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        final SharedPreferences settings = Settings.INSTANCE.getInstance(activity);
                        if (!settings.getBoolean("user_declined_storage_access", false))
                            requestStoragePermission(activity, false, null);
                        else {
                            final Intent i = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
                            i.addCategory(Intent.CATEGORY_DEFAULT);
                            i.setData(Uri.parse("package:" + VLCApplication.getAppContext().getPackageName()));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try {
                                activity.startActivity(i);
                            } catch (Exception ignored) {}
                        }
                        settings.edit()
                            .putBoolean("user_declined_storage_access", true)
                            .apply();
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

    private static Dialog createDialogCompat(final FragmentActivity activity, boolean exit) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.allow_storage_access_title))
                .setMessage(activity.getString(R.string.allow_storage_access_description))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(activity.getString(R.string.permission_ask_again), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        final SharedPreferences settings = Settings.INSTANCE.getInstance(activity);
                        if (!settings.getBoolean("user_declined_storage_access", false))
                            requestStoragePermission(activity, false, null);
                        else {
                            final Intent i = new Intent();
                            i.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                            i.addCategory(Intent.CATEGORY_DEFAULT);
                            i.setData(Uri.parse("package:" + VLCApplication.getAppContext().getPackageName()));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try {
                                activity.startActivity(i);
                            } catch (Exception ignored) {}
                        }
                        settings.edit()
                            .putBoolean("user_declined_storage_access", true)
                            .apply();
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

    private static Dialog createSettingsDialogCompat(final Activity activity, int mode) {
        int titleId = 0, textId = 0;
        String action = android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS;
        switch (mode) {
            case PERMISSION_SYSTEM_RINGTONE:
                titleId = R.string.allow_settings_access_ringtone_title;
                textId = R.string.allow_settings_access_ringtone_description;
                break;
            case PERMISSION_SYSTEM_BRIGHTNESS:
                titleId = R.string.allow_settings_access_brightness_title;
                textId = R.string.allow_settings_access_brightness_description;
                break;
            case PERMISSION_SYSTEM_DRAW_OVRLAYS:
                titleId = R.string.allow_draw_overlays_title;
                textId = R.string.allow_sdraw_overlays_description;
                action = android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION;
                break;
        }
        final String finalAction = action;
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(titleId))
                .setMessage(activity.getString(textId))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(activity.getString(R.string.permission_ask_again), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        final SharedPreferences settings = Settings.INSTANCE.getInstance(activity);
                        final Intent i = new Intent(finalAction);
                        i.setData(Uri.parse("package:" + activity.getPackageName()));
                        try {
                            activity.startActivity(i);
                        } catch (Exception ignored) {}
                        final SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("user_declined_settings_access", true);
                        editor.apply();
                    }
                });
        return dialogBuilder.show();
    }

    private static void requestStoragePermission(FragmentActivity activity, boolean write, Runnable callback) {
        if (activity != null) StoragePermissionsDelegate.Companion.askStoragePermission(activity, write, callback);
    }
}
