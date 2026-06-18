/*
 * ************************************************************************
 *  VlcLoader.kt
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

package org.videolan.television.ui.compose.composable.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.askStoragePermission
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.util.Permissions

@Composable
fun VlcEmptyViewLoader(state: EmptyLoadingState?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    VlcEmptyViewLoader(
        state = state,
        onScanClick = {
            val intent = Intent(context.applicationContext, SecondaryActivity::class.java)
            intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER)
            (context as Activity).startActivityForResult(intent, ACTIVITY_RESULT_PREFERENCES)
        },
        onPermissionClick = { loadingState ->
            when (loadingState) {
                EmptyLoadingState.MISSING_AUDIO_PERMISSION -> ActivityCompat.requestPermissions(
                    context as Activity, arrayOf(
                        Manifest.permission.READ_MEDIA_AUDIO
                    ), Permissions.FINE_STORAGE_PERMISSION_REQUEST_CODE
                )

                EmptyLoadingState.MISSING_VIDEO_PERMISSION -> ActivityCompat.requestPermissions(
                    context as Activity, arrayOf(
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_IMAGES
                    ), Permissions.FINE_STORAGE_PERMISSION_REQUEST_CODE
                )

                else -> (context as? FragmentActivity)?.askStoragePermission(false, null)

            }
        },
        content = content
    )
}

@Composable
private fun VlcEmptyViewLoader(
    state: EmptyLoadingState?,
    onScanClick: () -> Unit,
    onPermissionClick: (EmptyLoadingState) -> Unit,
    content: @Composable () -> Unit
) {
    state?.let {
        when (state) {
            EmptyLoadingState.LOADING -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }

            EmptyLoadingState.EMPTY -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(R.drawable.ic_empty), contentDescription = null, modifier = Modifier.size(96.dp))
                        Text(
                            modifier = Modifier.padding(top = 16.dp),
                            text = stringResource(R.string.nomedia),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        VLCButton(R.drawable.ic_medialibrary_scan, R.string.button_medialibrary_preferences, modifier = Modifier.padding(top = 16.dp)) {
                            onScanClick()
                        }
                    }
                }
            }

            EmptyLoadingState.EMPTY_SEARCH -> TODO("Not implemented as the lists are not searchable yet")
            EmptyLoadingState.MISSING_PERMISSION -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val text = stringResource(R.string.permission_expanation_no_allow) + "\n" + stringResource(R.string.permission_expanation_allow)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(R.drawable.ic_empty_warning), contentDescription = null, modifier = Modifier.size(96.dp))
                        Text(
                            modifier = Modifier.padding(top = 16.dp),
                            text = stringResource(R.string.permission_not_granted),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            modifier = Modifier.padding(top = 8.dp),
                            text = text,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        PermissionButton(modifier = Modifier.padding(top = 16.dp)) {
                            onPermissionClick(state)
                        }
                    }
                }
            }

            EmptyLoadingState.MISSING_VIDEO_PERMISSION, EmptyLoadingState.MISSING_AUDIO_PERMISSION -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(R.drawable.ic_empty_warning), contentDescription = null, modifier = Modifier.size(96.dp))
                        Text(
                            modifier = Modifier.padding(top = 16.dp),
                            text = stringResource(R.string.permission_not_granted),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            modifier = Modifier.padding(top = 8.dp),
                            text = stringResource(if (state == EmptyLoadingState.MISSING_AUDIO_PERMISSION) R.string.permission_audio else R.string.permission_video),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        PermissionButton(modifier = Modifier.padding(top = 16.dp)) {
                            onPermissionClick(state)
                        }
                    }
                }
            }

            EmptyLoadingState.EMPTY_FAVORITES -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Image(painterResource(R.drawable.ic_fav_empty), contentDescription = null, modifier = Modifier.size(96.dp))
                            Text(
                                modifier = Modifier.padding(top = 16.dp),
                                text = stringResource(R.string.nofav),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                        content()
                    }
                }
            }

            EmptyLoadingState.NONE -> content()
        }
    }
}

@Composable
private fun PermissionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    VLCButton(R.drawable.ic_permission_big, R.string.permission_ask_again, modifier) {
        onClick()
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF34434e)
@Composable
private fun VlcEmptyViewLoaderLoadingPreview() {
    VlcPreview {
        VlcEmptyViewLoader(
            state = EmptyLoadingState.LOADING,
            onScanClick = {},
            onPermissionClick = {},
            content = {}
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF34434e)
@Composable
private fun VlcEmptyViewLoaderEmptyPreview() {
    VlcPreview {
        VlcEmptyViewLoader(
            state = EmptyLoadingState.EMPTY,
            onScanClick = {},
            onPermissionClick = {},
            content = {}
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF34434e)
@Composable
private fun VlcEmptyViewLoaderPermissionPreview() {
    VlcPreview {
        VlcEmptyViewLoader(
            state = EmptyLoadingState.MISSING_PERMISSION,
            onScanClick = {},
            onPermissionClick = {},
            content = {}
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF34434e)
@Composable
private fun VlcEmptyViewLoaderFavoritesPreview() {
    VlcPreview {
        VlcEmptyViewLoader(
            state = EmptyLoadingState.EMPTY_FAVORITES,
            onScanClick = {},
            onPermissionClick = {},
            content = {
                Text("Content behind the empty view", color = MaterialTheme.colorScheme.onSurface)
            }
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF34434e)
@Composable
private fun PermissionButtonPreview() {
    VlcPreview {
        PermissionButton(
            onClick = {}
        )
    }
}
