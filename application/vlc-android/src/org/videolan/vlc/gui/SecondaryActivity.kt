/*
 * *************************************************************************
 *  SecondaryActivity.java
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

package org.videolan.vlc.gui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.Dialog
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.resources.KEY_FOLDER
import org.videolan.resources.KEY_GROUP
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.tools.RESULT_RESCAN
import org.videolan.tools.RESULT_RESTART
import org.videolan.vlc.R
import org.videolan.vlc.gui.audio.AudioAlbumsSongsFragment
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.browser.FileBrowserFragment
import org.videolan.vlc.gui.browser.KEY_MEDIA
import org.videolan.vlc.gui.browser.NetworkBrowserFragment
import org.videolan.vlc.gui.browser.StorageBrowserFragment
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.network.MRLPanelFragment
import org.videolan.vlc.gui.video.VideoGridFragment
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.IDialogManager
import org.videolan.vlc.util.isSchemeNetwork

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class SecondaryActivity : ContentActivity(), IDialogManager {

    private var fragment: Fragment? = null
    override val displayTitle = true
    private val dialogsDelegate = DialogDelegate()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.secondary)
        initAudioPlayerContainerActivity()

        val fph = findViewById<View>(R.id.fragment_placeholder)
        val params = fph.layoutParams as CoordinatorLayout.LayoutParams

        if (AndroidDevices.isTv) {
            applyOverscanMargin(this)
            params.topMargin = resources.getDimensionPixelSize(UiTools.getResourceFromAttribute(this, R.attr.actionBarSize))
        } else
            params.behavior = AppBarLayout.ScrollingViewBehavior()
        fph.requestLayout()

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        if (supportFragmentManager.findFragmentById(R.id.fragment_placeholder) == null) {
            val fragmentId = intent.getStringExtra(KEY_FRAGMENT)
            fetchSecondaryFragment(fragmentId)
            if (fragment == null) {
                finish()
                return
            }
            supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_placeholder, fragment!!)
                    .commit()
        }
        dialogsDelegate.observeDialogs(this, this)
    }

    override fun fireDialog(dialog: Dialog) {
        DialogActivity.dialog = dialog
        startActivity(Intent(DialogActivity.KEY_DIALOG, null, this, DialogActivity::class.java))
    }

    override fun dialogCanceled(dialog: Dialog?) {}

    override fun forceLoadVideoFragment() {
        val fragmentId = intent.getStringExtra(KEY_FRAGMENT)
        fetchSecondaryFragment(fragmentId)
        if (fragment == null) {
            finish()
            return
        }
        supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_placeholder, fragment!!)
                .commit()
    }

    override fun onResume() {
        overridePendingTransition(0, 0)
        super.onResume()
    }

    override fun onPause() {
        if (isFinishing)
            overridePendingTransition(0, 0)
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ACTIVITY_RESULT_SECONDARY) {
            if (resultCode == RESULT_RESCAN) this.reloadLibrary()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        when (item.itemId) {
            R.id.ml_menu_refresh -> {
                val ml = Medialibrary.getInstance()
                if (!ml.isWorking) reloadLibrary()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun fetchSecondaryFragment(id: String) {
        when (id) {
            ALBUMS_SONGS -> {
                fragment = AudioAlbumsSongsFragment().apply {
                    arguments = bundleOf(AudioBrowserFragment.TAG_ITEM to
                            intent.getParcelableExtra(AudioBrowserFragment.TAG_ITEM))
                }
            }
            ABOUT -> fragment = AboutFragment()
            STREAMS -> fragment = MRLPanelFragment()
            HISTORY -> fragment = HistoryFragment()
            VIDEO_GROUP_LIST -> {
                fragment = VideoGridFragment().apply {
                    arguments = bundleOf(KEY_FOLDER to intent.getParcelableExtra(KEY_FOLDER),
                        KEY_GROUP to intent.getParcelableExtra(KEY_GROUP))
                }
            }
            STORAGE_BROWSER -> {
                fragment = StorageBrowserFragment()
                setResult(RESULT_RESTART)
            }
            FILE_BROWSER -> {
                val media = intent.getParcelableExtra(KEY_MEDIA) as MediaWrapper
                fragment = if(media.uri.scheme.isSchemeNetwork()) NetworkBrowserFragment()
                else FileBrowserFragment()
                fragment?.apply { arguments = bundleOf(KEY_MEDIA to media) }
            }
            else -> throw IllegalArgumentException("Wrong fragment id.")
        }
    }

    companion object {
        const val TAG = "VLC/SecondaryActivity"

        const val ACTIVITY_RESULT_SECONDARY = 3

        const val KEY_FRAGMENT = "fragment"

        const val ALBUMS_SONGS = "albumsSongs"
        const val ABOUT = "about"
        const val STREAMS = "streams"
        const val HISTORY = "history"
        const val VIDEO_GROUP_LIST = "videoGroupList"
        const val STORAGE_BROWSER = "storage_browser"
        const val FILE_BROWSER = "file_browser"
    }
}
