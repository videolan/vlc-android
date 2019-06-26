/*****************************************************************************
 * MainActivity.java
 *
 * Copyright © 2011-2018 VLC authors and VideoLAN
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

import android.annotation.TargetApi
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.view.ActionMode
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import kotlinx.android.synthetic.main.toolbar.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.*
import org.videolan.vlc.extensions.ExtensionManagerService
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.extensions.api.VLCExtensionItem
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.browser.BaseBrowserFragment
import org.videolan.vlc.gui.browser.ExtensionBrowser
import org.videolan.vlc.gui.helpers.Navigator
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.video.VideoGridFragment
import org.videolan.vlc.gui.view.HackyDrawerLayout
import org.videolan.vlc.interfaces.Filterable
import org.videolan.vlc.interfaces.IRefreshable
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.*


@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class MainActivity : ContentActivity(), ExtensionManagerService.ExtensionManagerActivity {

    var refreshing: Boolean = false
        set(value) {
            mainLoading.visibility = if (value) View.VISIBLE else View.GONE
            field = value
        }
    private lateinit var mediaLibrary: AbstractMedialibrary
    private lateinit var extensionsManager: ExtensionsManager
    private lateinit var drawerLayout: HackyDrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var drawerToggle: ActionBarDrawerToggle
    lateinit var navigator: Navigator
        private set

    private var scanNeeded = false

    // Extensions management
    private var extensionServiceConnection: ServiceConnection? = null
    private var extensionManagerService: ExtensionManagerService? = null

    val isExtensionServiceBinded: Boolean
        get() = extensionServiceConnection != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.checkCpuCompatibility(this)
        /*** Start initializing the UI  */
        setContentView(R.layout.main)
        drawerLayout = findViewById(R.id.root_container)
        setupNavigationView()
        initAudioPlayerContainerActivity()
        navigator = Navigator(this, settings, extensionManagerService, savedInstanceState, intent.getIntExtra(EXTRA_TARGET, 0))
        if (savedInstanceState == null) {
            if (intent.getBooleanExtra(EXTRA_UPGRADE, false)) {
                /*
                 * The sliding menu is automatically opened when the user closes
                 * the info dialog. If (for any reason) the dialog is not shown,
                 * open the menu after a short delay.
                 */
                activityHandler.postDelayed({ drawerLayout.openDrawer(navigationView) }, 500)
            }
            Permissions.checkReadStoragePermission(this@MainActivity, false)
        }

        /* Set up the action bar */
        prepareActionBar()

        drawerToggle = ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close)
        drawerLayout.addDrawerListener(drawerToggle)
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)

        /* Reload the latest preferences */
        scanNeeded = savedInstanceState == null && settings.getBoolean("auto_rescan", true)
        if (BuildConfig.DEBUG) extensionsManager = ExtensionsManager.getInstance()
        mediaLibrary = VLCApplication.mlInstance


        val typedValue = TypedValue()
        theme.resolveAttribute(R.attr.progress_indeterminate_tint, typedValue, true)
        val color = typedValue.data

        mainLoadingProgress.indeterminateDrawable.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)

    }

    private fun setupNavigationView() {
        navigationView = findViewById(R.id.navigation)
        navigationView.menu.findItem(R.id.nav_history).isVisible = settings.getBoolean(PLAYBACK_HISTORY, true)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState()
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private fun prepareActionBar() {
        val actionBar = supportActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeButtonEnabled(true)
    }

    override fun onStart() {
        super.onStart()
        if (mediaLibrary.isInitiated) {
            /* Load media items from database and storage */
            if (scanNeeded && Permissions.canReadStorage(this)) this.reloadLibrary()
        }
        if (BuildConfig.DEBUG) createExtensionServiceConnection()
    }

    override fun onResume() {
        super.onResume()
        updateNavMenu()
    }

    override fun onStop() {
        super.onStop()
        navigationView.setNavigationItemSelectedListener(null)
        if (changingConfigurations == 0) {
            /* Check for an ongoing scan that needs to be resumed during onResume */
            scanNeeded = mediaLibrary.isWorking
        }
        if (isExtensionServiceBinded) {
            unbindService(extensionServiceConnection)
            extensionServiceConnection = null
        }
        if (navigator.currentIdIsExtension())
            settings.edit()
                    .putString("current_extension_name", extensionsManager.getExtensions(application, false)[navigator.currentFragmentId].componentName().packageName)
                    .apply()
    }

    private fun updateNavMenu() {
        navigationView.setNavigationItemSelectedListener(navigator)
    }

    private fun loadPlugins() {
        val plugins = extensionsManager.getExtensions(this, true)
        if (plugins.isEmpty()) {
            unbindService(extensionServiceConnection)
            extensionServiceConnection = null
            extensionManagerService!!.stopSelf()
            return
        }
        val extensionGroup = navigationView.menu.findItem(R.id.extensions_group)
        extensionGroup.subMenu.clear()
        for (id in plugins.indices) {
            val extension = plugins[id]
            val key = "extension_" + extension.componentName().packageName
            if (settings.contains(key)) {
                extensionsManager.displayPlugin(this, id, extension, settings.getBoolean(key, false))
            } else {
                extensionsManager.showExtensionPermissionDialog(this, id, extension, key)
            }
        }
        if (extensionGroup.subMenu.size() == 0) extensionGroup.isVisible = false
        onPluginsLoaded()
        navigationView.invalidate()
    }

    private fun onPluginsLoaded() {
        if (navigator.currentFragment == null && navigator.currentIdIsExtension())
            if (extensionsManager.previousExtensionIsEnabled(application))
                extensionManagerService!!.openExtension(navigator.currentFragmentId)
            else
                navigator.showFragment(R.id.nav_video)
    }

    private fun createExtensionServiceConnection() {
        extensionServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                extensionManagerService = (service as ExtensionManagerService.LocalBinder).service
                extensionManagerService!!.setExtensionManagerActivity(this@MainActivity)
                loadPlugins()
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }
        // Bind service which discoverves au connects toplugins
        if (!bindService(Intent(this@MainActivity,
                        ExtensionManagerService::class.java), extensionServiceConnection, Context.BIND_AUTO_CREATE))
            extensionServiceConnection = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val current = navigator.currentFragment
        if (current !is ExtensionBrowser) supportFragmentManager.putFragment(outState, "current_fragment", current!!)
        super.onSaveInstanceState(outState)
        outState.putInt("current", navigator.currentFragmentId)
    }

    override fun onRestart() {
        super.onRestart()
        /* Reload the latest preferences */
        navigator.reloadPreferences()
    }

    @TargetApi(Build.VERSION_CODES.N)
    override fun onBackPressed() {
        /* Close the menu first */
        if (drawerLayout.isDrawerOpen(navigationView)) {
            closeDrawer()
            return
        }

        /* Close playlist search if open or Slide down the audio player if it is shown entirely. */
        if (isAudioPlayerReady && (audioPlayer?.backPressed() == true || slideDownAudioPlayer()))
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

    override fun displayExtensionItems(extensionId: Int, title: String, items: List<VLCExtensionItem>, showParams: Boolean, refresh: Boolean) {
        navigator.displayExtensionItems(extensionId, title, items, showParams, refresh)
        navigationView.menu.findItem(extensionId).isCheckable = true
        updateCheckedItem(extensionId)
    }

    override fun startSupportActionMode(callback: ActionMode.Callback): ActionMode? {
        appBarLayout.setExpanded(true)
        return super.startSupportActionMode(callback)
    }

    /**
     * Handle onClick form menu buttons
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        closeDrawer()
        if (item.itemId != R.id.ml_menu_filter) UiTools.setKeyboardVisibility(drawerLayout, false)

        // Handle item selection
        return when (item.itemId) {
            // Refresh
            R.id.ml_menu_refresh -> {
                forceRefresh()
                true
            }
            android.R.id.home ->
                // Slide down the audio player or toggle the sidebar
                slideDownAudioPlayer() || drawerToggle.onOptionsItemSelected(item)
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
                this.rescan()
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
        //Filter for LG devices, see https://code.google.com/p/android/issues/detail?id=78154
        if (keyCode == KeyEvent.KEYCODE_MENU &&
                Build.VERSION.SDK_INT <= 16 &&
                Build.MANUFACTURER.compareTo("LGE") == 0) {
            return true
        } else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            toolbar.menu.findItem(R.id.ml_menu_filter).expandActionView()
        }
        return super.onKeyDown(keyCode, event)
    }

    // Note. onKeyDown will not occur while moving within a list
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        //Filter for LG devices, see https://code.google.com/p/android/issues/detail?id=78154
        if (keyCode == KeyEvent.KEYCODE_MENU &&
                Build.VERSION.SDK_INT <= 16 &&
                Build.MANUFACTURER.compareTo("LGE") == 0) {
            openOptionsMenu()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    fun closeDrawer() {
        drawerLayout.closeDrawer(navigationView)
    }

    fun updateCheckedItem(id: Int) {
        when (id) {
            R.id.nav_settings, R.id.nav_about -> return
            else -> {
                val currentId = navigator.currentFragmentId
                val target = navigationView.menu.findItem(id)
                if (id != currentId && target != null) {
                    val current = navigationView.menu.findItem(currentId)
                    if (current != null) current.isChecked = false
                    target.isChecked = true
                    /* Save the tab status in pref */
                    settings.edit().putInt("fragment_id", id).apply()
                }
            }
        }
    }

    companion object {
        const val TAG = "VLC/MainActivity"
    }
}
