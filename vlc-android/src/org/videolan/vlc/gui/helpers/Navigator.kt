/*
 * *************************************************************************
 *  Navigator.kt
 * **************************************************************************
 *  Copyright © 2018-2019 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.helpers

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.tools.isStarted
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.extensions.ExtensionManagerService
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.extensions.api.VLCExtensionItem
import org.videolan.vlc.gui.HistoryFragment
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.PlaylistFragment
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.browser.BaseBrowserFragment
import org.videolan.vlc.gui.browser.ExtensionBrowser
import org.videolan.vlc.gui.browser.FileBrowserFragment
import org.videolan.vlc.gui.browser.NetworkBrowserFragment
import org.videolan.vlc.gui.folders.FoldersFragment
import org.videolan.vlc.gui.network.MRLPanelFragment
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.gui.video.VideoGridFragment
import org.videolan.vlc.gui.view.HackyDrawerLayout
import org.videolan.vlc.util.*

private const val TAG = "Navigator"
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class Navigator: NavigationView.OnNavigationItemSelectedListener, LifecycleObserver, INavigator {

    private val defaultFragmentId= R.id.nav_video
    override var currentFragmentId : Int = 0
    var currentFragment: Fragment? = null
        private set
    private lateinit var activity: MainActivity
    private lateinit var settings: SharedPreferences
    private var extensionsService: ExtensionManagerService? = null
    override lateinit var navigationView: NavigationView
    override lateinit var drawerLayout: HackyDrawerLayout
    override lateinit var drawerToggle: ActionBarDrawerToggle

    override lateinit var extensionsManager: ExtensionsManager
    override var extensionServiceConnection: ServiceConnection? = null
    override var extensionManagerService: ExtensionManagerService? = null
    private val isExtensionServiceBinded: Boolean
        get() = extensionServiceConnection != null

    override fun MainActivity.setupNavigation(state: Bundle?) {
        activity = this
        this@Navigator.settings = settings
        currentFragmentId = intent.getIntExtra(EXTRA_TARGET, 0)
        if (state !== null) {
            currentFragment = supportFragmentManager.getFragment(state, "current_fragment")
        } else {
            if (intent.getBooleanExtra(EXTRA_UPGRADE, false)) {
                /*
                 * The sliding menu is automatically opened when the user closes
                 * the info dialog. If (for any reason) the dialog is not shown,
                 * open the menu after a short delay.
                 */
                launch {
                    delay(500L)
                    drawerLayout.openDrawer(navigationView)
                }
            }
        }
        lifecycle.addObserver(this@Navigator)
        navigationView = findViewById(R.id.navigation)
        navigationView.menu.findItem(R.id.nav_history).isVisible = settings.getBoolean(PLAYBACK_HISTORY, true)
        drawerLayout = findViewById(R.id.root_container)

        drawerToggle = ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close)
        drawerLayout.addDrawerListener(drawerToggle)
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (currentFragment === null && !currentIdIsExtension()) showFragment(if (currentFragmentId != 0) currentFragmentId else settings.getInt("fragment_id", defaultFragmentId))
        navigationView.setNavigationItemSelectedListener(this)
        if (BuildConfig.DEBUG) createExtensionServiceConnection()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        navigationView.setNavigationItemSelectedListener(null)
        if (isExtensionServiceBinded) {
            activity.unbindService(extensionServiceConnection!!)
            extensionServiceConnection = null
        }
        if (currentIdIsExtension())
            settings.edit()
                    .putString("current_extension_name", extensionsManager.getExtensions(activity.application, false)[currentFragmentId].componentName().packageName)
                    .apply()
    }

    private fun getNewFragment(id: Int): Fragment {
        return when (id) {
            R.id.nav_audio -> AudioBrowserFragment()
            R.id.nav_directories -> FileBrowserFragment()
            R.id.nav_playlists -> PlaylistFragment()
            R.id.nav_history -> HistoryFragment()
            R.id.nav_network -> NetworkBrowserFragment()
            R.id.nav_mrl -> MRLPanelFragment()
            else -> {
                val group = Integer.valueOf(Settings.getInstance(activity.applicationContext).getString("video_min_group_length", "6")!!)
                if (group == 0) FoldersFragment()
                else VideoGridFragment()
            }
        }
    }

    private fun showFragment(id: Int) {
        val tag = getTag(id)
        val fragment = getNewFragment(id)
        showFragment(fragment, id, tag)
    }

    private fun showFragment(fragment: Fragment, id: Int, tag: String = getTag(id)) {
        val fm = activity.supportFragmentManager
        if (currentFragment is BaseBrowserFragment) fm.popBackStackImmediate("root", FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val ft = fm.beginTransaction()
        ft.replace(R.id.fragment_placeholder, fragment, tag)
        if (BuildConfig.DEBUG) ft.commit()
        else ft.commitAllowingStateLoss()
        updateCheckedItem(id)
        currentFragment = fragment
        currentFragmentId = id
    }

    private fun showSecondaryFragment(fragmentTag: String, param: String? = null) {
        val i = Intent(activity, SecondaryActivity::class.java)
        i.putExtra("fragment", fragmentTag)
        param?.let { i.putExtra("param", it) }
        activity.startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
        // Slide down the audio player if needed.
        activity.slideDownAudioPlayer()
    }

    override fun currentIdIsExtension() = idIsExtension(currentFragmentId)

    private fun idIsExtension(id: Int) = id in 1..100

    private fun clearBackstackFromClass(clazz: Class<*>) {
        val fm = activity.supportFragmentManager
        while (clazz.isInstance(currentFragment)) if (!fm.popBackStackImmediate()) break
    }

    override fun reloadPreferences() {
        currentFragmentId = settings.getInt("fragment_id", defaultFragmentId)
    }

    override fun closeDrawer() {
        drawerLayout.closeDrawer(navigationView)
    }

    private fun getTag(id: Int) = when (id) {
        R.id.nav_about -> ID_ABOUT
        R.id.nav_settings -> ID_PREFERENCES
        R.id.nav_audio -> ID_AUDIO
        R.id.nav_playlists -> ID_PLAYLISTS
        R.id.nav_directories -> ID_DIRECTORIES
        R.id.nav_history -> ID_HISTORY
        R.id.nav_mrl -> ID_MRL
        R.id.nav_network -> ID_NETWORK
        else -> ID_VIDEO
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        val current = currentFragment
        if (item.groupId == R.id.extensions_group) {
            if (currentFragmentId == id) {
                clearBackstackFromClass(ExtensionBrowser::class.java)
                activity.closeDrawer()
                return false
            } else
                extensionsService?.openExtension(id)
        } else {
            if (isExtensionServiceBinded) extensionsService?.disconnect()

            if (current == null) {
                activity.closeDrawer()
                return false
            }

            if (currentFragmentId == id) { /* Already selected */
                // Go back at root level of current mProvider
                if ((current as? BaseBrowserFragment)?.isStarted() == false) {
                    activity.supportFragmentManager.popBackStackImmediate("root", FragmentManager.POP_BACK_STACK_INCLUSIVE)
                } else {
                    activity.closeDrawer()
                    return false
                }
            } else when (id) {
                R.id.nav_about -> showSecondaryFragment(SecondaryActivity.ABOUT)
                R.id.nav_settings -> activity.startActivityForResult(Intent(activity, PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
                else -> {
                    activity.slideDownAudioPlayer()
                    showFragment(id)
                }
            }
        }
        activity.closeDrawer()
        return true
    }

    override fun displayExtensionItems(extensionId: Int, title: String, items: List<VLCExtensionItem>, showParams: Boolean, refresh: Boolean) {
        if (refresh && currentFragment is ExtensionBrowser) {
            (currentFragment as ExtensionBrowser).doRefresh(title, items)
        } else {
            val fragment = ExtensionBrowser()
            fragment.arguments =  Bundle().apply {
                putParcelableArrayList(ExtensionBrowser.KEY_ITEMS_LIST, ArrayList(items))
                putBoolean(ExtensionBrowser.KEY_SHOW_FAB, showParams)
                putString(ExtensionBrowser.KEY_TITLE, title)
            }
            extensionsService?.let { fragment.setExtensionService(it) }
            when {
                currentFragment !is ExtensionBrowser -> //case: non-extension to extension root
                    showFragment(fragment, extensionId, title)
                currentFragmentId == extensionId -> //case: extension root to extension sub dir
                    showFragment(fragment, extensionId, title)
                else -> { //case: extension to other extension root
                    clearBackstackFromClass(ExtensionBrowser::class.java)
                    showFragment(fragment, extensionId, title)
                }
            }
        }
        navigationView.menu.findItem(extensionId).isCheckable = true
        updateCheckedItem(extensionId)
    }

    private fun updateCheckedItem(id: Int) {
        when (id) {
            R.id.nav_settings, R.id.nav_about -> return
            else -> {
                val currentId = currentFragmentId
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

    private fun loadPlugins() {
        val plugins = extensionsManager.getExtensions(activity, true)
        if (plugins.isEmpty()) {
            extensionServiceConnection?.let { activity.unbindService(it) }
            extensionServiceConnection = null
            extensionManagerService?.stopSelf()
            return
        }
        val extensionGroup = navigationView.menu.findItem(R.id.extensions_group)
        extensionGroup.subMenu.clear()
        for (id in plugins.indices) {
            val extension = plugins[id]
            val key = "extension_" + extension.componentName().packageName
            if (settings.contains(key)) {
                extensionsManager.displayPlugin(activity, id, extension, settings.getBoolean(key, false))
            } else {
                extensionsManager.showExtensionPermissionDialog(activity, id, extension, key)
            }
        }
        if (extensionGroup.subMenu.size() == 0) extensionGroup.isVisible = false
        onPluginsLoaded()
        navigationView.invalidate()
    }

    private fun onPluginsLoaded() {
        if (currentFragment == null && currentIdIsExtension())
            if (extensionsManager.previousExtensionIsEnabled(activity.application))
                extensionManagerService?.openExtension(currentFragmentId)
            else
                showFragment(R.id.nav_video)
    }

    private fun createExtensionServiceConnection() {
        extensionServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                extensionManagerService = (service as ExtensionManagerService.LocalBinder).service.apply {
                    setExtensionManagerActivity(activity)
                }
                loadPlugins()
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }
        // Bind service which discoverves au connects toplugins
        if (!activity.bindService(Intent(activity,
                        ExtensionManagerService::class.java), extensionServiceConnection!!, Context.BIND_AUTO_CREATE))
            extensionServiceConnection = null
    }
}

interface INavigator {
    var navigationView: NavigationView
    var currentFragmentId : Int

    var drawerLayout: HackyDrawerLayout
    var drawerToggle: ActionBarDrawerToggle

    var extensionsManager: ExtensionsManager
    var extensionServiceConnection: ServiceConnection?
    var extensionManagerService: ExtensionManagerService?

    fun MainActivity.setupNavigation(state: Bundle?)
    fun currentIdIsExtension() : Boolean
    fun displayExtensionItems(extensionId: Int, title: String, items: List<VLCExtensionItem>, showParams: Boolean, refresh: Boolean)
    fun reloadPreferences()
    fun closeDrawer()
}