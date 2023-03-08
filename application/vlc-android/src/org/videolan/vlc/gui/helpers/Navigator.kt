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

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.view.MenuItem
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import org.videolan.resources.*
import org.videolan.tools.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.*
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.browser.BaseBrowserFragment
import org.videolan.vlc.gui.browser.MainBrowserFragment
import org.videolan.vlc.gui.helpers.UiTools.isTablet
import org.videolan.vlc.gui.video.VideoBrowserFragment
import org.videolan.vlc.util.getScreenWidth

private const val TAG = "Navigator"

class Navigator : NavigationBarView.OnItemSelectedListener, DefaultLifecycleObserver, INavigator {

    private val defaultFragmentId = R.id.nav_video
    override var currentFragmentId: Int = 0
    private var currentFragment: Fragment? = null
        private set
    private lateinit var activity: MainActivity
    private lateinit var settings: SharedPreferences
    override lateinit var navigationView: List<NavigationBarView>
    override lateinit var appbarLayout: AppBarLayout


    override fun MainActivity.setupNavigation(state: Bundle?) {
        activity = this
        this@Navigator.settings = settings
        currentFragmentId = intent.getIntExtra(EXTRA_TARGET, 0)
        if (state !== null) {
            currentFragment = supportFragmentManager.getFragment(state, "current_fragment")
        }
        lifecycle.addObserver(this@Navigator)
        navigationView = listOf(findViewById(R.id.navigation), findViewById(R.id.navigation_rail))
        appbarLayout = findViewById(R.id.appbar)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (currentFragment === null && !currentIdIsExtension()) showFragment(if (currentFragmentId != 0) currentFragmentId else settings.getInt("fragment_id", defaultFragmentId))
        navigationView.forEach { it.setOnItemSelectedListener(this) }
    }

    override fun onStop(owner: LifecycleOwner) {
        navigationView.forEach { it.setOnItemSelectedListener(null) }
    }

    private fun getNewFragment(id: Int): Fragment {
        return when (id) {
            R.id.nav_audio -> AudioBrowserFragment()
            R.id.nav_directories -> MainBrowserFragment()
            R.id.nav_playlists -> PlaylistFragment()
            R.id.nav_more -> MoreFragment()
            else -> VideoBrowserFragment()
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

    override fun currentIdIsExtension() = idIsExtension(currentFragmentId)

    private fun idIsExtension(id: Int) = id in 1..100

    private fun clearBackstackFromClass(clazz: Class<*>) {
        val fm = activity.supportFragmentManager
        while (clazz.isInstance(currentFragment)) if (!fm.popBackStackImmediate()) break
    }

    override fun reloadPreferences() {
        currentFragmentId = settings.getInt("fragment_id", defaultFragmentId)
    }

    override fun configurationChanged(size: Int) {
        navigationView.forEach {
            when (it) {
                is BottomNavigationView -> if (activity.isTablet()) it.setGone() else it.setVisible()
                else -> if (!activity.isTablet()) it.setGone() else it.setVisible()
            }
        }
    }

    override fun getFragmentWidth(activity: Activity): Int {
        val screenWidth = activity.getScreenWidth()
        return screenWidth - activity.resources.getDimension(R.dimen.navigation_margin).toInt()
    }

    private fun getTag(id: Int) = when (id) {
        R.id.nav_audio -> ID_AUDIO
        R.id.nav_directories -> ID_DIRECTORIES
        else -> ID_VIDEO
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        val current = currentFragment

        appbarLayout.setExpanded(true, true)

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
        } else {
            activity.slideDownAudioPlayer()
            showFragment(id)
        }
        return true
    }


    private fun updateCheckedItem(id: Int) {
        val currentId = currentFragmentId
        navigationView.forEach {
            val target = it.menu.findItem(id)
            if (id != it.selectedItemId && target != null) {
                val current = it.menu.findItem(currentId)
                if (current != null) current.isChecked = false
                target.isChecked = true
                /* Save the tab status in pref */
                settings.edit { putInt("fragment_id", id) }
            }
        }
    }

}

interface INavigator {
    var navigationView: List<NavigationBarView>
    var appbarLayout: AppBarLayout
    var currentFragmentId: Int

    fun MainActivity.setupNavigation(state: Bundle?)
    fun currentIdIsExtension(): Boolean
    fun reloadPreferences()
    fun configurationChanged(size: Int)
    fun getFragmentWidth(activity: Activity): Int
}