/*
 * *************************************************************************
 *  Navigator.kt
 * **************************************************************************
 *  Copyright © 2018 VLC authors and VideoLAN
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

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import org.videolan.vlc.R
import org.videolan.vlc.extensions.ExtensionManagerService
import org.videolan.vlc.extensions.api.VLCExtensionItem
import org.videolan.vlc.gui.HistoryFragment
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.PlaylistFragment
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.browser.*
import org.videolan.vlc.gui.network.MRLPanelFragment
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.gui.video.VideoGridFragment
import org.videolan.vlc.util.*
import java.lang.ref.WeakReference

private const val TAG = "Navigator"
class Navigator(private val activity: MainActivity,
        private val settings: SharedPreferences,
        private val extensionsService: ExtensionManagerService?,
        state: Bundle?,
        target: Int
): com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener, LifecycleObserver {

    private val fragmentsStack = androidx.collection.SimpleArrayMap<String, WeakReference<androidx.fragment.app.Fragment>>()
    private val defaultFragmentId inline get() = if (settings.getInt(KEY_MEDIALIBRARY_SCAN, ML_SCAN_OFF) == ML_SCAN_ON) R.id.nav_video else R.id.nav_directories
    var currentFragmentId = target
    var currentFragment: androidx.fragment.app.Fragment? = null
        private set

    init {
        activity.lifecycle.addObserver(this)
        state?.let {
            val fm = activity.supportFragmentManager
            currentFragment = fm.getFragment(it, "current_fragment")
            //Restore fragments stack
            restoreFragmentsStack(fm)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (currentFragment === null && !currentIdIsExtension()) showFragment(if (currentFragmentId != 0) currentFragmentId else settings.getInt("fragment_id", defaultFragmentId))
    }

    private fun getNewFragment(id: Int): androidx.fragment.app.Fragment {
        return when (id) {
            R.id.nav_audio -> AudioBrowserFragment()
            R.id.nav_directories -> FileBrowserFragment()
            R.id.nav_playlists -> PlaylistFragment()
            R.id.nav_history -> HistoryFragment()
            R.id.nav_network -> NetworkBrowserFragment()
            else -> VideoGridFragment()
        }
    }

    fun showFragment(id: Int) {
        val tag = getTag(id)
        //Get new fragment
        val wr = fragmentsStack.get(tag)
        var fragment = wr?.get()
        if (fragment === null) {
            fragment = getNewFragment(id)
            fragmentsStack.put(tag, WeakReference(fragment))
        }
        showFragment(fragment, id, tag)
    }

    private fun showFragment(fragment: androidx.fragment.app.Fragment, id: Int, tag: String = getTag(id), backTag: String? = null) {
        val fm = activity.supportFragmentManager
        if (currentFragment is BaseBrowserFragment && !(currentFragment as BaseBrowserFragment).isRootDirectory)
            fm.popBackStackImmediate("root", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val ft = fm.beginTransaction()
        ft.replace(R.id.fragment_placeholder, fragment, tag)
        if (backTag !== null) ft.addToBackStack(backTag)
        ft.commit()
        activity.updateCheckedItem(id)
        currentFragment = fragment
        currentFragmentId = id
    }

    private fun restoreFragmentsStack(fm: androidx.fragment.app.FragmentManager) {
        val fragments = fm.fragments
        if (fragments != null) {
            val ft = fm.beginTransaction()
            for (fragment in fragments) {
                if (fragment is ExtensionBrowser) ft.remove(fragment)
                else if (fragment is MediaBrowserFragment<*>) fragmentsStack.put(fragment.tag, WeakReference(fragment))
            }
            ft.commit()
        }
    }

    /**
     * Show a secondary fragment.
     */

    fun showSecondaryFragment(fragmentTag: String, param: String? = null) {
        val i = Intent(activity, SecondaryActivity::class.java)
        i.putExtra("fragment", fragmentTag)
        param?.let { i.putExtra("param", it) }
        activity.startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
        // Slide down the audio player if needed.
        activity.slideDownAudioPlayer()
    }

    fun currentIdIsExtension() = idIsExtension(currentFragmentId)

    private fun idIsExtension(id: Int) = id in 1..100

    private fun clearBackstackFromClass(clazz: Class<*>) {
        val fm = activity.supportFragmentManager
        while (clazz.isInstance(currentFragment)) {
            if (!fm.popBackStackImmediate())
                break
        }
    }

    fun reloadPreferences() {
        currentFragmentId = settings.getInt("fragment_id", defaultFragmentId)
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
        R.id.nav_video -> ID_VIDEO
        else -> if (defaultFragmentId == R.id.nav_video) ID_VIDEO else ID_DIRECTORIES
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
            if (activity.isExtensionServiceBinded) extensionsService?.disconnect()

            if (current == null) {
                activity.closeDrawer()
                return false
            }

            if (currentFragmentId == id) { /* Already selected */
                // Go back at root level of current mProvider
                if (current is BaseBrowserFragment && !current.isRootDirectory) {
                    activity.supportFragmentManager.popBackStackImmediate(getTag(id), androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                } else {
                    activity.closeDrawer()
                    return false
                }
            } else when (id) {
                    R.id.nav_about -> showSecondaryFragment(SecondaryActivity.ABOUT)
                    R.id.nav_settings -> activity.startActivityForResult(Intent(activity, PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
                    R.id.nav_mrl -> MRLPanelFragment().show(activity.supportFragmentManager, "fragment_mrl")
                    else -> {
                        activity.slideDownAudioPlayer()
                        showFragment(id)
                    }
                }
        }
        activity.closeDrawer()
        return true
    }

    fun displayExtensionItems(extensionId: Int, title: String, items: List<VLCExtensionItem>, showParams: Boolean, refresh: Boolean) {
        if (refresh && currentFragment is ExtensionBrowser) {
            (currentFragment as ExtensionBrowser).doRefresh(title, items)
        } else {
            val fragment = ExtensionBrowser()
            fragment.arguments =  Bundle().apply {
                putParcelableArrayList(ExtensionBrowser.KEY_ITEMS_LIST, ArrayList(items))
                putBoolean(ExtensionBrowser.KEY_SHOW_FAB, showParams)
                putString(ExtensionBrowser.KEY_TITLE, title)
            }
            fragment.setExtensionService(extensionsService)
            when {
                currentFragment !is ExtensionBrowser -> //case: non-extension to extension root
                    showFragment(fragment, extensionId, title, null)
                currentFragmentId == extensionId -> //case: extension root to extension sub dir
                    showFragment(fragment, extensionId, title, getTag(currentFragmentId))
                else -> { //case: extension to other extension root
                    clearBackstackFromClass(ExtensionBrowser::class.java)
                    showFragment(fragment, extensionId, title, null)
                }
            }
        }
    }
}