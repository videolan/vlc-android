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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.resources.*
import org.videolan.tools.Settings
import org.videolan.tools.isStarted
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.extensions.ExtensionManagerService
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.extensions.api.VLCExtensionItem
import org.videolan.vlc.gui.*
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.browser.BaseBrowserFragment
import org.videolan.vlc.gui.browser.ExtensionBrowser
import org.videolan.vlc.gui.browser.MainBrowserFragment
import org.videolan.vlc.gui.browser.NetworkBrowserFragment
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.gui.video.VideoGridFragment
import org.videolan.vlc.viewmodels.mobile.VideoGroupingType

private const val TAG = "Navigator"
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class Navigator : BottomNavigationView.OnNavigationItemSelectedListener, LifecycleObserver, INavigator {

    private val defaultFragmentId= R.id.nav_video
    override var currentFragmentId : Int = 0
    private var currentFragment: Fragment? = null
        private set
    private lateinit var activity: MainActivity
    private lateinit var settings: SharedPreferences
    private var extensionsService: ExtensionManagerService? = null
    override lateinit var navigationView: BottomNavigationView

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
        }
        lifecycle.addObserver(this@Navigator)
        navigationView = findViewById(R.id.navigation)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (currentFragment === null && !currentIdIsExtension()) showFragment(if (currentFragmentId != 0) currentFragmentId else settings.getInt("fragment_id", defaultFragmentId))
        navigationView.setOnNavigationItemSelectedListener(this)
        if (BuildConfig.DEBUG) createExtensionServiceConnection()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        navigationView.setOnNavigationItemSelectedListener(null)
        if (isExtensionServiceBinded) {
            activity.unbindService(extensionServiceConnection!!)
            extensionServiceConnection = null
        }
        if (currentIdIsExtension()) {
            val name = extensionsManager.getExtensions(activity.application, false)[currentFragmentId].componentName().packageName
            settings.putSingle("current_extension_name", name)
        }
    }

    private fun getNewFragment(id: Int): Fragment {
        return when (id) {
            R.id.nav_audio -> AudioBrowserFragment()
            R.id.nav_directories -> MainBrowserFragment()
            R.id.nav_playlists -> PlaylistFragment()
            R.id.nav_network -> NetworkBrowserFragment()
            R.id.nav_more -> MoreFragment()
            else -> VideoGridFragment()
        }
    }

    private fun showFragment(id: Int) {
        val tag = getTag(id)
        val fragment = getNewFragment(id)
        showFragment(fragment, id, tag)
    }

    override fun forceLoadVideoFragment() {
        showFragment(R.id.nav_video)
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

    private fun getTag(id: Int) = when (id) {
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
                return false
            } else
                extensionsService?.openExtension(id)
        } else {
            if (isExtensionServiceBinded) extensionsService?.disconnect()

            if (current == null) {
                return false
            }
            if (current is BaseFragment && current.actionMode != null) current.stopActionMode()

            if (currentFragmentId == id) { /* Already selected */
                // Go back at root level of current mProvider
                if ((current as? BaseBrowserFragment)?.isStarted() == false) {
                    activity.supportFragmentManager.popBackStackImmediate("root", FragmentManager.POP_BACK_STACK_INCLUSIVE)
                } else {
                    return false
                }
            } else when (id) {
                R.id.nav_settings -> activity.startActivityForResult(Intent(activity, PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
                else -> {
                    activity.slideDownAudioPlayer()
                    showFragment(id)
                }
            }
        }
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
            R.id.nav_settings -> return
            else -> {
                val currentId = currentFragmentId
                val target = navigationView.menu.findItem(id)
                if (id != navigationView.selectedItemId && target != null) {
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
       navigationView.menu.findItem(R.id.extensions_group)?.let { extensionGroup->
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
    var navigationView: BottomNavigationView
    var currentFragmentId : Int


    var extensionsManager: ExtensionsManager
    var extensionServiceConnection: ServiceConnection?
    var extensionManagerService: ExtensionManagerService?

    fun MainActivity.setupNavigation(state: Bundle?)
    fun currentIdIsExtension() : Boolean
    fun displayExtensionItems(extensionId: Int, title: String, items: List<VLCExtensionItem>, showParams: Boolean, refresh: Boolean)
    fun reloadPreferences()
    fun forceLoadVideoFragment()
}