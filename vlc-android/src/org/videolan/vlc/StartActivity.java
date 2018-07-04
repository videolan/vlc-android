/*
 * *************************************************************************
 *  StartActivity.java
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

package org.videolan.vlc;

import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SearchActivity;
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate;
import org.videolan.vlc.gui.tv.MainTvActivity;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Util;

import videolan.org.commontools.TvChannelUtilsKt;

public class StartActivity extends FragmentActivity implements StoragePermissionsDelegate.CustomActionController {

    public final static String TAG = "VLC/StartActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        final boolean tv =  showTvUi();
        final String action = intent != null ? intent.getAction(): null;

        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null
                && !TvChannelUtilsKt.TV_CHANNEL_SCHEME.equals(intent.getData().getScheme())) {
            startPlaybackFromApp(intent);
            return;
        } else if (Intent.ACTION_SEND.equals(action)) {
            final ClipData cd = intent.getClipData();
            final ClipData.Item item = cd != null && cd.getItemCount() > 0 ? cd.getItemAt(0) : null;
            final String mrl = item != null ? item.getText().toString() : null;
            if (mrl != null) {
                MediaUtils.INSTANCE.openMediaNoUi(Uri.parse(mrl));
                finish();
                return;
            }
        }

        // Start application
        /* Get the current version from package */
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        final int currentVersionNumber = BuildConfig.VERSION_CODE;
        final int savedVersionNumber = settings.getInt(Constants.PREF_FIRST_RUN, -1);
        /* Check if it's the first run */
        final boolean firstRun = savedVersionNumber == -1;
        final boolean upgrade = firstRun || savedVersionNumber != currentVersionNumber;
        if (upgrade) settings.edit().putInt(Constants.PREF_FIRST_RUN, currentVersionNumber).apply();
        // Route search query
        if (Intent.ACTION_SEARCH.equals(action) || "com.google.android.gms.actions.SEARCH_ACTION".equals(action)) {
            startActivity(intent.setClass(this, tv ? org.videolan.vlc.gui.tv.SearchActivity.class : SearchActivity.class));
            finish();
            return;
        } else if (MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(action)) {
            final Intent serviceInent = new Intent(Constants.ACTION_PLAY_FROM_SEARCH, null, this, PlaybackService.class)
                    .putExtra(Constants.EXTRA_SEARCH_BUNDLE, intent.getExtras());
            Util.startService(this, serviceInent);
        } else if(Intent.ACTION_VIEW.equals(action) && intent.getData() != null) { //launch from TV Channel
            final Uri data = intent.getData();
            final String path = data.getPath();
            if (TextUtils.equals(path,"/"+TvChannelUtilsKt.TV_CHANNEL_PATH_APP)) startApplication(tv, firstRun, upgrade);
            else if (TextUtils.equals(path,"/"+TvChannelUtilsKt.TV_CHANNEL_PATH_VIDEO)) {
                final long id = Long.valueOf(data.getQueryParameter(TvChannelUtilsKt.TV_CHANNEL_QUERY_VIDEO_ID));
                MediaUtils.INSTANCE.openMediaNoUi(id);
            }
        } else startApplication(tv, firstRun, upgrade);
        finish();
    }

    private void startApplication(boolean tv, boolean firstRun, boolean upgrade) {
        MediaParsingServiceKt.startMedialibrary(this, firstRun, upgrade, true);
        startActivity(new Intent(this, tv ? MainTvActivity.class : MainActivity.class)
                .putExtra(Constants.EXTRA_FIRST_RUN, firstRun)
                .putExtra(Constants.EXTRA_UPGRADE, upgrade));
    }

    private void startPlaybackFromApp(Intent intent) {
        if (intent.getType() != null && intent.getType().startsWith("video"))
            startActivity(intent.setClass(this, VideoPlayerActivity.class));
        else
            MediaUtils.INSTANCE.openMediaNoUi(intent.getData());
        finish();
    }

    private boolean showTvUi() {
        return AndroidUtil.isJellyBeanMR1OrLater && (AndroidDevices.isAndroidTv || (!AndroidDevices.isChromeBook && !AndroidDevices.hasTsp) ||
                PreferenceManager.getDefaultSharedPreferences(this).getBoolean("tv_ui", false));
    }

    @Override
    public void onStorageAccessGranted() {
        startPlaybackFromApp(getIntent());
    }
}
