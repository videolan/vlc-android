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

package org.videolan.vlc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.resources.*
import org.videolan.resources.util.startMedialibrary
import org.videolan.tools.*
import org.videolan.vlc.gui.BetaWelcomeActivity
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getStoragePermission
import org.videolan.vlc.gui.onboarding.ONBOARDING_DONE_KEY
import org.videolan.vlc.gui.onboarding.startOnboarding
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Permissions
import videolan.org.commontools.TV_CHANNEL_PATH_APP
import videolan.org.commontools.TV_CHANNEL_PATH_VIDEO
import videolan.org.commontools.TV_CHANNEL_QUERY_VIDEO_ID
import videolan.org.commontools.TV_CHANNEL_SCHEME

private const val SEND_CRASH_RESULT = 0
private const val TAG = "VLC/StartActivity"
@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class StartActivity : FragmentActivity() {

    private val idFromShortcut: Int
        get() {
            if (!AndroidUtil.isNougatMR1OrLater) return 0
            val intent = intent
            val action = intent?.action
            if (!TextUtils.isEmpty(action)) {
                return when (action) {
                    "vlc.shortcut.video" -> R.id.nav_video
                    "vlc.shortcut.audio" -> R.id.nav_audio
                    "vlc.shortcut.browser" -> R.id.nav_directories
                    "vlc.shortcut.network" -> R.id.nav_network
                    "vlc.shortcut.playlists" -> R.id.nav_playlists
                    "vlc.shortcut.resume" -> R.id.ml_menu_last_playlist
                    else -> 0
                }
            }
            return 0
        }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.getContextWithLocale(AppContextProvider.locale))
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale(AppContextProvider.locale)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            if (!Settings.showTvUi && BuildConfig.BETA && !Settings.getInstance(this).getBoolean(BETA_WELCOME, false)) {
                val intent = Intent(this, BetaWelcomeActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                startActivityForResult(intent, SEND_CRASH_RESULT)
                Settings.getInstance(this).putSingle(BETA_WELCOME, true)
                return
            }
        } catch (ignored: Exception) {}
        resume()
    }

    private fun resume() {
        val intent = intent
        val action = intent?.action

        if ((Intent.ACTION_VIEW == action || ACTION_VIEW_ARC == action)
                && TV_CHANNEL_SCHEME != intent.data?.scheme) {
            startPlaybackFromApp(intent)
            return
        } else if (Intent.ACTION_SEND == action) {
            val cd = intent.clipData
            val item = if (cd != null && cd.itemCount > 0) cd.getItemAt(0) else null
            if (item != null) {
                var uri: Uri? = item.uri
                if (uri == null && item.text != null) uri = Uri.parse(item.text.toString())
                if (uri != null) {
                    MediaUtils.openMediaNoUi(uri)
                    finish()
                    return
                }
            }
        }

        // Setting test mode with stubbed media library if required
        if (intent.hasExtra(MLServiceLocator.EXTRA_TEST_STUBS)
                && intent.getBooleanExtra(MLServiceLocator.EXTRA_TEST_STUBS, false)) {
            MLServiceLocator.setLocatorMode(MLServiceLocator.LocatorMode.TESTS)
            Log.i(TAG, "onCreate: Setting test mode`")
        }

        // Start application
        /* Get the current version from package */
        val settings = Settings.getInstance(this)
        val currentVersionNumber = BuildConfig.VERSION_CODE
        val savedVersionNumber = settings.getInt(PREF_FIRST_RUN, -1)
        /* Check if it's the first run */
        val firstRun = savedVersionNumber == -1
        val upgrade = firstRun || savedVersionNumber != currentVersionNumber
        val tv = showTvUi()
        if (upgrade && (tv || !firstRun)) settings.putSingle(PREF_FIRST_RUN, currentVersionNumber)
        val removeOldDevices = savedVersionNumber in 3028201..3028399
        // Route search query
        if (Intent.ACTION_SEARCH == action || ACTION_SEARCH_GMS == action) {
            intent.setClassName(applicationContext, if (tv) TV_SEARCH_ACTIVITY else MOBILE_SEARCH_ACTIVITY)
            startActivity(intent)
            finish()
            return
        } else if (MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH == action) {
            val serviceInent = Intent(ACTION_PLAY_FROM_SEARCH, null, this, PlaybackService::class.java)
                    .putExtra(EXTRA_SEARCH_BUNDLE, intent.extras)
            ContextCompat.startForegroundService(this, serviceInent)
        } else if (Intent.ACTION_VIEW == action && intent.data != null) { //launch from TV Channel
            val data = intent.data
            val path = data!!.path
            if (TextUtils.equals(path, "/$TV_CHANNEL_PATH_APP"))
                startApplication(tv, firstRun, upgrade, 0, removeOldDevices)
            else if (TextUtils.equals(path, "/$TV_CHANNEL_PATH_VIDEO")) {
                val id = java.lang.Long.valueOf(data.getQueryParameter(TV_CHANNEL_QUERY_VIDEO_ID)!!)
                MediaUtils.openMediaNoUi(this, id)
            }
        } else {
            val target = idFromShortcut
            if (target == R.id.ml_menu_last_playlist)
                PlaybackService.loadLastAudio(this)
            else
                startApplication(tv, firstRun, upgrade, target, removeOldDevices)
        }
        FileUtils.copyLua(applicationContext, upgrade)
        FileUtils.copyHrtfs(applicationContext, upgrade)
        if (AndroidDevices.watchDevices) this.enableStorageMonitoring()
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SEND_CRASH_RESULT) {
            resume()
        }
    }

    private fun startApplication(tv: Boolean, firstRun: Boolean, upgrade: Boolean, target: Int, removeDevices:Boolean = false) {
        val settings = Settings.getInstance(this@StartActivity)
        val onboarding = !tv && !settings.getBoolean(ONBOARDING_DONE_KEY, false)
        // Start Medialibrary from background to workaround Dispatchers.Main causing ANR
        // cf https://github.com/Kotlin/kotlinx.coroutines/issues/878
        if (!onboarding || !firstRun) {
            Thread {
                AppScope.launch {
                    // workaround for a Android 9 bug
                    // https://issuetracker.google.com/issues/113122354
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P && !awaitAppIsForegroung()) {
                        return@launch
                    }
                    this@StartActivity.startMedialibrary(firstRun, upgrade, true, removeDevices)
                    if (onboarding) settings.putSingle(ONBOARDING_DONE_KEY, true)
                }
            }.start()
            val mainIntent = Intent(Intent.ACTION_VIEW)
                    .setClassName(applicationContext, if (tv) TV_MAIN_ACTIVITY else MOBILE_MAIN_ACTIVITY)
                    .putExtra(EXTRA_FIRST_RUN, firstRun)
                    .putExtra(EXTRA_UPGRADE, upgrade)
            if (tv && intent.hasExtra(EXTRA_PATH)) mainIntent.putExtra(EXTRA_PATH, intent.getStringExtra(EXTRA_PATH))
            if (target != 0) mainIntent.putExtra(EXTRA_TARGET, target)
            startActivity(mainIntent)
        } else {
            startOnboarding()
        }
    }

    private fun startPlaybackFromApp(intent: Intent) = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
        // workaround for a Android 9 bug
        // https://issuetracker.google.com/issues/113122354
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P && !awaitAppIsForegroung()) {
            finish()
            return@launch
        }
        if (Permissions.canReadStorage(applicationContext) || getStoragePermission()) when {
            intent.type?.startsWith("video") == true -> try {
                startActivity(intent.setClass(this@StartActivity, VideoPlayerActivity::class.java))
            } catch (ex: SecurityException) {
                intent.data?.let { MediaUtils.openMediaNoUi(it) }
            }
            intent.data?.authority == getString(R.string.tv_provider_authority) -> MediaUtils.openMediaNoUiFromTvContent(this@StartActivity, intent.data)
            else -> intent.data?.let { MediaUtils.openMediaNoUi(it) }
        }
        finish()
    }

    private fun showTvUi(): Boolean {
        return AndroidDevices.isAndroidTv || !AndroidDevices.isChromeBook && !AndroidDevices.hasTsp ||
                Settings.getInstance(this).getBoolean("tv_ui", false)
    }
}
