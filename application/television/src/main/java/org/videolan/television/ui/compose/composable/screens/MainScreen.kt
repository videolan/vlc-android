/*
 * ************************************************************************
 *  MainScreen.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.television.ui.compose.composable.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import org.videolan.television.R
import org.videolan.television.ui.compose.composable.lists.AudioListScreen
import org.videolan.television.ui.compose.composable.lists.BrowseList
import org.videolan.television.ui.compose.composable.lists.MoreScreen
import org.videolan.television.ui.compose.composable.lists.PlaylistsList
import org.videolan.television.ui.compose.composable.lists.VideoListScreen
import org.videolan.television.ui.compose.theme.Orange800
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.viewmodel.MainActivityViewModel

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    Column {
        Tabs()
    }
}

@Composable
fun Tabs(viewModel: MainActivityViewModel = viewModel()) {
    Tabs(viewModel.tabs)
}

@Composable
private fun Tabs(tabs: List<Pair<Int, Int>>) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val selectedItemFocusRestorer = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier
                .focusRestorer(selectedItemFocusRestorer)
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == selectedTabIndex,
                    onFocus = { selectedTabIndex = index },
                    modifier = Modifier.focusRestorer(if (index == selectedTabIndex) selectedItemFocusRestorer else FocusRequester.Default)
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        Icon(
                            painter = painterResource(tab.second),
                            tint = if (selectedTabIndex == index) Orange800 else White.copy(0.4F),
                            contentDescription = stringResource(id = R.string.reset),
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp)
                                .align(Alignment.CenterVertically)
                        )
                        Text(
                            text = stringResource(tab.first),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selectedTabIndex == index) Orange800 else White.copy(0.4F),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 0.dp)
                                .align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }
        TabPanels(selectedTabIndex = selectedTabIndex)
    }
}

@Composable
private fun TabPanels(selectedTabIndex: Int, viewModel: MainActivityViewModel = viewModel()) {
    var value = 0
    Box(modifier = Modifier.padding(16.dp)) {
        AnimatedContent(targetState = selectedTabIndex, label = "") {
            value = it

            when (viewModel.tabs[selectedTabIndex].first) {
                R.string.video -> VideoListScreen()
                R.string.audio -> AudioListScreen()
                R.string.browse -> BrowseList()
                R.string.playlists -> PlaylistsList()
                else -> MoreScreen()
            }
        }
    }
}