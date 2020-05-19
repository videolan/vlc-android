/*****************************************************************************
 * MainActivity.java
 *
 * Copyright © 2011-2019 VLC authors and VideoLAN
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
 */

package org.videolan.vlc.gui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.toolbar.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.*
import org.videolan.tools.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.StartActivity
import org.videolan.vlc.extensions.ExtensionManagerService
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.browser.BaseBrowserFragment
import org.videolan.vlc.gui.browser.ExtensionBrowser
import org.videolan.vlc.gui.helpers.INavigator
import org.videolan.vlc.gui.helpers.Navigator
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.video.VideoGridFragment
import org.videolan.vlc.interfaces.Filterable
import org.videolan.vlc.interfaces.IRefreshable
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.Util

private const val TAG = "VLC/MainActivity"

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class MainActivity : ContentActivity(),
        ExtensionManagerService.ExtensionManagerActivity,
        INavigator by Navigator()
{
    var refreshing: Boolean = false
        set(value) {
            mainLoading.visibility = if (value) View.VISIBLE else View.GONE
            field = value
        }
    private lateinit var mediaLibrary: Medialibrary
    private var scanNeeded = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.checkCpuCompatibility(this)
        /*** Start initializing the UI  */
        setContentView(R.layout.main)
        initAudioPlayerContainerActivity()
        setupNavigation(savedInstanceState)
        if (savedInstanceState == null) Permissions.checkReadStoragePermission(this)

        /* Set up the action bar */
        prepareActionBar()
        /* Reload the latest preferences */
        scanNeeded = savedInstanceState == null && settings.getBoolean(KEY_MEDIALIBRARY_AUTO_RESCAN, true)
        if (BuildConfig.DEBUG) extensionsManager = ExtensionsManager.getInstance()
        mediaLibrary = Medialibrary.getInstance()

        val color = TypedValue().run {
            theme.resolveAttribute(R.attr.progress_indeterminate_tint, this, true)
            data
        }
        mainLoadingProgress.indeterminateDrawable.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
    }


    private fun prepareActionBar() {
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(false)
            setHomeButtonEnabled(false)
            setDisplayShowTitleEnabled(false)
        }
    }

    override fun onStart() {
        super.onStart()
        if (mediaLibrary.isInitiated) {
            /* Load media items from database and storage */
            if (scanNeeded && Permissions.canReadStorage(this)) this.reloadLibrary()
        }
    }

    override fun onStop() {
        super.onStop()
        if (changingConfigurations == 0) {
            /* Check for an ongoing scan that needs to be resumed during onResume */
            scanNeeded = mediaLibrary.isWorking
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val current = currentFragment
        if (current !is ExtensionBrowser) supportFragmentManager.putFragment(outState, "current_fragment", current!!)
        outState.putInt(EXTRA_TARGET, currentFragmentId)
        super.onSaveInstanceState(outState)
    }

    override fun onRestart() {
        super.onRestart()
        /* Reload the latest preferences */
        reloadPreferences()
    }

    @TargetApi(Build.VERSION_CODES.N)
    override fun onBackPressed() {


        /* Close playlist search if open or Slide down the audio player if it is shown entirely. */
        if (isAudioPlayerReady && (audioPlayer.backPressed() || slideDownAudioPlayer()))
            return

        // If it's the directory view, a "backpressed" action shows a parent.
        val fragment = currentFragment
        if (fragment is BaseBrowserFragment && fragment.goBack()) {
            return
        } else if (fragment is ExtensionBrowser) {
            fragment.goBack()
            return
        }
        if (AndroidUtil.isNougatOrLater && isInMultiWindowMode) {
            UiTools.confirmExit(this)
            return
        }
        finish()
    }

    override fun startSupportActionMode(callback: ActionMode.Callback): ActionMode? {
        appBarLayout.setExpanded(true)
        return super.startSupportActionMode(callback)
    }

    /**
     * Handle onClick form menu buttons
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.ml_menu_filter) UiTools.setKeyboardVisibility(appBarLayout, false)

        // Handle item selection
        return when (item.itemId) {
            // Refresh
            R.id.ml_menu_refresh -> {
                forceRefresh()
                true
            }
            android.R.id.home ->
                // Slide down the audio player or toggle the sidebar
                slideDownAudioPlayer()
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onMenuItemActionExpand(item: MenuItem): Boolean {
        return if (currentFragment is Filterable) {
            (currentFragment as Filterable).allowedToExpand()
        } else false
    }

    fun forceRefresh() {
        forceRefresh(currentFragment)
    }

    private fun forceRefresh(current: Fragment?) {
        if (!mediaLibrary.isWorking) {
            if (current != null && current is IRefreshable)
                (current as IRefreshable).refresh()
            else
                reloadLibrary()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ACTIVITY_RESULT_PREFERENCES) {
            when (resultCode) {
                RESULT_RESCAN -> this.reloadLibrary()
                RESULT_RESTART, RESULT_RESTART_APP -> {
                    val intent = Intent(this@MainActivity, if (resultCode == RESULT_RESTART_APP) StartActivity::class.java else MainActivity::class.java)
                    finish()
                    startActivity(intent)
                }
                RESULT_UPDATE_SEEN_MEDIA -> for (fragment in supportFragmentManager.fragments)
                    if (fragment is VideoGridFragment)
                        fragment.updateSeenMediaMarker()
                RESULT_UPDATE_ARTISTS -> {
                    val fragment = currentFragment
                    if (fragment is AudioBrowserFragment) fragment.viewModel.refresh()
                }
            }
        } else if (requestCode == ACTIVITY_RESULT_OPEN && resultCode == Activity.RESULT_OK) {
            MediaUtils.openUri(this, data!!.data)
        } else if (requestCode == ACTIVITY_RESULT_SECONDARY) {
            if (resultCode == RESULT_RESCAN) {
                forceRefresh(currentFragment)
            }
        }
    }

    // Note. onKeyDown will not occur while moving within a list
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            toolbar.menu.findItem(R.id.ml_menu_filter).expandActionView()
        }
        return super.onKeyDown(keyCode, event)
    }
}
