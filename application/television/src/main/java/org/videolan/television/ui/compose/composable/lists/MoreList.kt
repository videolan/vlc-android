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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.videolan.television.R
import org.videolan.television.ui.MainTvActivity

@Composable
fun MoreScreen() {
    val activity = LocalActivity.current
    Column {
        Row() {
            Button(
                onClick = {

                },
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
//                    tint = if (selectedTabIndex == index) Orange800 else White.copy(0.4F),
                    contentDescription = stringResource(id = R.string.preferences),
                    modifier = Modifier
                        .width(24.dp)
                        .height(24.dp)
                        .align(Alignment.CenterVertically)
                )
                Text(
                    text = stringResource(R.string.preferences),
                    modifier = Modifier
                        .padding(8.dp)
                )
            }
            Box {

                Button(
                    onClick = {
                        activity?.startActivity(Intent(activity.applicationContext, MainTvActivity::class.java))
                    },
                    modifier = Modifier
                        .padding(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_about),
                        contentDescription = stringResource(id = R.string.preferences),
                        modifier = Modifier
                            .width(24.dp)
                            .height(24.dp)
                            .align(Alignment.CenterVertically)
                    )
                    Text(
                        text = stringResource(R.string.about),
                        modifier = Modifier
                            .padding(8.dp)
                    )
                }
            }
        }
    }

}