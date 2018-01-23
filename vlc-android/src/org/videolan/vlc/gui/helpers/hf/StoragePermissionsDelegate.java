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
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.ContentActivity;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Permissions;

import static org.videolan.vlc.util.Permissions.canReadStorage;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class StoragePermissionsDelegate extends BaseHeadlessFragment {

    public interface CustomActionController {
        void onStorageAccessGranted();
    }

    public final static String TAG = "VLC/StorageHF";

    private boolean mFirstRun, mUpgrade, mWrite;
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = mActivity == null ? null : mActivity.getIntent();
        if (intent != null && intent.getBooleanExtra(Constants.EXTRA_UPGRADE, false)) {
            mUpgrade = true;
            mFirstRun = intent.getBooleanExtra(Constants.EXTRA_FIRST_RUN, false);
            intent.removeExtra(Constants.EXTRA_UPGRADE);
            intent.removeExtra(Constants.EXTRA_FIRST_RUN);
        }
        mWrite = getArguments().getBoolean("write");
        if (AndroidUtil.isMarshMallowOrLater && !canReadStorage(getActivity())) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))
                Permissions.showStoragePermissionDialog(mActivity, false, false);
            else
                requestStorageAccess(false);
        } else if (mWrite) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))
                Permissions.showStoragePermissionDialog(mActivity, false, true);
            else
                requestStorageAccess(true);
        }
    }

    private void requestStorageAccess(boolean write) {
        requestPermissions(new String[]{write ? Manifest.permission.WRITE_EXTERNAL_STORAGE : Manifest.permission.READ_EXTERNAL_STORAGE},
                write ? Permissions.PERMISSION_WRITE_STORAGE_TAG : Permissions.PERMISSION_STORAGE_TAG);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case Permissions.PERMISSION_STORAGE_TAG:
                // If request is cancelled, the result arrays are empty.
                final Context ctx = VLCApplication.getAppContext();
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (mActivity instanceof CustomActionController) {
                        ((CustomActionController) mActivity).onStorageAccessGranted();
                    } else {
                        final Intent serviceIntent = new Intent(Constants.ACTION_INIT, null, ctx, MediaParsingService.class);
                        serviceIntent.putExtra(Constants.EXTRA_FIRST_RUN, mFirstRun);
                        serviceIntent.putExtra(Constants.EXTRA_UPGRADE, mUpgrade);
                        ctx.startService(serviceIntent);
                    }
                    exit();
                } else if (mActivity != null) {
                    Permissions.showStoragePermissionDialog(mActivity, false, mWrite);
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))
                        exit();
                }
                break;
            case Permissions.PERMISSION_WRITE_STORAGE_TAG:
                if (mActivity instanceof ContentActivity) ((ContentActivity) mActivity).onWriteAccessGranted();
                break;

        }
    }

    public static void askStoragePermission(@NonNull FragmentActivity activity, boolean write) {
        if (activity.isFinishing()) return;
        final FragmentManager fm = activity.getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(TAG);
        if (fragment == null) {
            final Bundle args = new Bundle();
            args.putBoolean("write", write);
            fragment = new StoragePermissionsDelegate();
            fragment.setArguments(args);
            fm.beginTransaction().add(fragment, TAG).commitAllowingStateLoss();
        } else
            ((StoragePermissionsDelegate)fragment).requestStorageAccess(write);
    }
}
