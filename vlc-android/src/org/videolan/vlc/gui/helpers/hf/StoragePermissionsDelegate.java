/*
 * *************************************************************************
 *  StoragePermissionsDelegate.java
 * **************************************************************************
 *  Copyright © 2017 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.helpers.hf;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.StartActivity;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.util.Permissions;

import static org.videolan.vlc.util.Permissions.PERMISSION_STORAGE_TAG;
import static org.videolan.vlc.util.Permissions.canReadStorage;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class StoragePermissionsDelegate extends BaseHeadlessFragment {

    public interface CustomActionController {
        void onStorageAccessGranted();
    }

    public final static String TAG = "VLC/StorageHF";

    private boolean mFirstRun, mUpgrade;
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = mActivity == null ? null : mActivity.getIntent();
        if (intent != null && intent.getBooleanExtra(StartActivity.EXTRA_UPGRADE, false)) {
            mUpgrade = true;
            mFirstRun = intent.getBooleanExtra(StartActivity.EXTRA_FIRST_RUN, false);
            intent.removeExtra(StartActivity.EXTRA_UPGRADE);
            intent.removeExtra(StartActivity.EXTRA_FIRST_RUN);
        }
        if (AndroidUtil.isMarshMallowOrLater && !canReadStorage()) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))
                Permissions.showStoragePermissionDialog(mActivity, false);
            else
                requestStorageAccess();
        }
    }

    private void requestStorageAccess() {
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                Permissions.PERMISSION_STORAGE_TAG);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_STORAGE_TAG:
                // If request is cancelled, the result arrays are empty.
                final Context ctx = VLCApplication.getAppContext();
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (mActivity instanceof CustomActionController) {
                        ((CustomActionController) mActivity).onStorageAccessGranted();
                    } else {
                        final Intent serviceIntent = new Intent(MediaParsingService.ACTION_INIT, null, ctx, MediaParsingService.class);
                        serviceIntent.putExtra(StartActivity.EXTRA_FIRST_RUN, mFirstRun);
                        serviceIntent.putExtra(StartActivity.EXTRA_UPGRADE, mUpgrade);
                        ctx.startService(serviceIntent);
                    }
                    exit();
                } else if (mActivity != null) {
                    Permissions.showStoragePermissionDialog(mActivity, false);
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))
                        exit();
                }
                break;
        }
    }

    public static void askStoragePermission(@NonNull FragmentActivity activity) {
        final FragmentManager fm = activity.getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(TAG);
        if (fragment == null)
            fm.beginTransaction().add(new StoragePermissionsDelegate(), TAG).commit();
        else
            ((StoragePermissionsDelegate)fragment).requestStorageAccess();
    }
}
