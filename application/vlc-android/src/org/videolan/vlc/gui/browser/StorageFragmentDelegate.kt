/*
 * ************************************************************************
 *  StorageFragmentDelegate.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.browser

import android.content.Context
import android.os.Handler
import android.view.View
import android.widget.CheckBox
import androidx.collection.SimpleArrayMap
import org.videolan.medialibrary.interfaces.EntryPointsEventsCb
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.tools.*
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox

interface IStorageFragmentDelegate {
    fun checkBoxAction(v: View, mrl: String)
    fun addEntryPointsCallabck()
    fun removeEntryPointsCallabck()
    val processingFolders: SimpleArrayMap<String, CheckBox>

    fun withContext(context: Context)
    fun withAdapters(adapters: Array<StorageBrowserAdapter>)
    fun addBannedFoldersCallback(callback: (folder:String, banned: Boolean)-> Unit)
}

class StorageFragmentDelegate : IStorageFragmentDelegate, EntryPointsEventsCb {
    private lateinit var adapters: Array<StorageBrowserAdapter>
    private lateinit var context:Context
    override val processingFolders = SimpleArrayMap<String, CheckBox>()
    private  val handler = Handler()
    private var bannedFolderCallback: ((folder: String, banned: Boolean) -> Unit)? = null

    override fun withContext(context: Context) {
        this.context = context
    }

    override fun withAdapters(adapters: Array<StorageBrowserAdapter>) {
        this.adapters = adapters
    }

    override fun addBannedFoldersCallback(callback: (folder: String, banned: Boolean) -> Unit) {
        bannedFolderCallback = callback
    }

    override fun addEntryPointsCallabck() {
        Medialibrary.getInstance().addEntryPointsEventsCb(this)
    }

    override fun removeEntryPointsCallabck() {
        Medialibrary.getInstance().removeEntryPointsEventsCb(this)
    }

    override fun checkBoxAction(v: View, mrl: String) {
        val tscb = v as ThreeStatesCheckbox
        val checked = tscb.state == ThreeStatesCheckbox.STATE_CHECKED
        if ((context as? SecondaryActivity)?.isOnboarding == true) {
            val path = mrl.sanitizePath()
            if (checked) {
                MediaParsingService.preselectedStorages.removeAll { it.startsWith(path) }
                MediaParsingService.preselectedStorages.add(path)
            } else {
                MediaParsingService.preselectedStorages.removeAll { it.startsWith(path) }
            }
        } else {
            if (checked) {
                MedialibraryUtils.addDir(mrl, v.context.applicationContext)
                val prefs = Settings.getInstance(v.getContext())
                if (prefs.getInt(KEY_MEDIALIBRARY_SCAN, -1) != ML_SCAN_ON) prefs.putSingle(KEY_MEDIALIBRARY_SCAN, ML_SCAN_ON)
            } else
                MedialibraryUtils.removeDir(mrl)
            processEvent(v as CheckBox, mrl)
        }
    }

    private fun processEvent(cbp: CheckBox, mrl: String) {
        cbp.isEnabled = false
        processingFolders.put(mrl, cbp)
    }

    override fun onEntryPointBanned(entryPoint: String, success: Boolean) {
        handler.post { bannedFolderCallback?.invoke(entryPoint, true) }
    }

    override fun onEntryPointUnbanned(entryPoint: String, success: Boolean) {
        handler.post { bannedFolderCallback?.invoke(entryPoint, false) }
    }

    override fun onEntryPointAdded(entryPoint: String, success: Boolean) {}

    override fun onEntryPointRemoved(entryPoint: String, success: Boolean) {
        var entryPoint = entryPoint
        if (entryPoint.endsWith("/"))
            entryPoint = entryPoint.substring(0, entryPoint.length - 1)
        if (processingFolders.containsKey(entryPoint)) {
            processingFolders.remove(entryPoint)?.let {
                handler.post {
                    it.isEnabled = true
                    if (success) {
                        adapters.forEach {
                            it.updateMediaDirs(context)
                            it.notifyDataSetChanged()
                        }
                    } else
                        it.isChecked = true
                }
            }
        }
    }

    override fun onDiscoveryStarted() {}

    override fun onDiscoveryProgress(entryPoint: String) {}

    override fun onDiscoveryCompleted() {
        handler.post { for (i in 0 until processingFolders.size()) processingFolders.get(processingFolders.keyAt(i))?.isEnabled = true }
        adapters.forEach {
            it.updateMediaDirs(context)
        }
    }

    override fun onDiscoveryFailed(entryPoint: String) {

    }
}