/*
 * ************************************************************************
 *  MoreList.kt
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

package org.videolan.television.ui.compose.composable.lists

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.television.R
import org.videolan.television.ui.MainTvActivity
import org.videolan.television.ui.compose.composable.components.ContentLine
import org.videolan.television.ui.compose.composable.components.VLCButton
import org.videolan.television.ui.preferences.PreferencesActivity
import org.videolan.television.viewmodel.MoreViewModel

@Composable
fun MoreScreen(onFocusExit: () -> Unit, onFocusEnter: () -> Unit, viewModel: MoreViewModel = viewModel()) {
    viewModel.updateHistory()
    viewModel.updateStreams()
    val history by viewModel.history.observeAsState()
    val streams by viewModel.streams.observeAsState()
    val historyLoading by viewModel.historyLoading.observeAsState()
    val streamsLoading by viewModel.streamsLoading.observeAsState()
    val activity = LocalActivity.current
    Column(
        modifier = Modifier
            .focusProperties {
                onExit = {
                    onFocusExit()
                }
                onEnter = {
                    onFocusEnter()
                }
            }
            .verticalScroll(rememberScrollState())
            .focusGroup()
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .focusGroup()
        ) {
            VLCButton(R.drawable.ic_settings, R.string.preferences) {
                activity?.startActivityForResult(Intent(activity, PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
            }
            VLCButton(R.drawable.ic_more_about, R.string.about) {
                activity?.startActivity(Intent(activity.applicationContext, MainTvActivity::class.java))
            }

        }
        if (!history.isNullOrEmpty())
            ContentLine(history, historyLoading, R.string.history)
        ContentLine(streams, streamsLoading, R.string.streams)
    }
}