/*
 * *************************************************************************
 *  Permissions.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;

public class Permissions {

    public static final int PERMISSION_STORAGE_TAG = 255;
    public static final int PERMISSION_SETTINGS_TAG = 254;

    /*
     * Marshmallow permission system management
     */

    @TargetApi(Build.VERSION_CODES.M)
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
        if (activity.isFinishing() || (sAlertDialog != null && sAlertDialog.isShowing()))
            return;
        sAlertDialog = createSettingsDialogCompat(activity);
    }

    public static void showStoragePermissionDialog(final Activity activity, boolean exit) {
        if (activity.isFinishing() || (sAlertDialog != null && sAlertDialog.isShowing()))
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
