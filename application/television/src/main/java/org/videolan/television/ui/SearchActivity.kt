/*****************************************************************************
 * SearchActivity.kt
 *
 * Copyright © 2014-2025 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
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
package org.videolan.television.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import org.videolan.television.ui.compose.composable.screens.SearchScreen
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.viewmodel.SearchViewModel

@AndroidEntryPoint
class SearchActivity : DefaultTvActivity() {

    private val viewModel: SearchViewModel by viewModels()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VlcTVTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SearchScreen(
                        viewModel = viewModel,
                        onVoiceSearchClick = {}
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "VLC/SearchActivity"
    }
}
