/*
 * ************************************************************************
 *  BrowserActivity.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.television.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.resources.AndroidDevices
import org.videolan.resources.util.parcelable
import org.videolan.television.ui.compose.composable.screens.BrowserScreen
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.viewmodel.FileBrowserViewModel
import org.videolan.vlc.R
import org.videolan.vlc.gui.browser.PathAdapterListener
import org.videolan.vlc.viewmodels.browser.IPathOperationDelegate
import org.videolan.vlc.viewmodels.browser.PathOperationDelegate


class BrowserActivity : DefaultTvActivity(), PathAdapterListener, IPathOperationDelegate by PathOperationDelegate() {
    lateinit var otgDevice:String
    lateinit var browserTitle:String
    private val networkSharesResult = ArrayList<MediaLibraryItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        otgDevice = getString(R.string.otg_device_title)
        browserTitle = getString(R.string.browser)
        PathOperationDelegate.storages.put(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, makePathSafe(getString(R.string.internal_memory)))
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }
        val storage = intent?.parcelable<MediaLibraryItem>(EXTRA_ITEM) ?: throw IllegalStateException("No storage provided")

        setContent {
            val viewModel: FileBrowserViewModel by viewModels()
            viewModel.setCurrentPathEntry(storage)
            viewModel.addPrepareSegmentsListener { item, list ->
                val uri = when {
                    item is MediaWrapper -> item.uri
                    item is Storage -> item.uri
                    else -> throw IllegalStateException("No uri provided")
                }
                list.addAll(prepareSegments(uri))
            }
            VlcTVTheme {
                BackHandler {
                    if (!viewModel.popBackStack()) {
                        finish()
                    }
                }
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize()) {
                    BrowserScreen()
                }
            }
        }

    }

    private fun prepareSegments(uri: Uri): MutableList<String> {
        val path = Uri.decode(uri.path)
        val isOtg = path.startsWith("/tree/")
        val string = when {
            isOtg -> if (path.endsWith(':')) "" else path.substringAfterLast(':')
            else -> replaceStoragePath(path)
        }
        val list: MutableList<String> = mutableListOf()
        if (isOtg) list.add(otgDevice)

        //list of all the path chunks
        val pathParts = string.split('/').filter { it.isNotEmpty() }
        for (index in pathParts.indices) {
            //start creating the Uri
            val currentPathUri = Uri.Builder().scheme(uri.scheme).encodedAuthority(uri.authority)
            //append all the previous paths and the current one
            for (i in 0..index) appendPathToUri(pathParts[i], currentPathUri)
            list.add(currentPathUri.toString())
        }
        if (showRoot()) list.add(0, browserTitle)
        return list
    }

    override fun backTo(tag: String) { }

    override fun currentContext(): Context = this

    override fun showRoot() = true

    override fun getPathOperationDelegate(): IPathOperationDelegate = this
}
