/*
 * ************************************************************************
 *  PreferenceUtil.kt
 * *************************************************************************
 * Copyright © 2023 VLC authors and VideoLAN
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

package org.videolan.vlc.webserver.utils

import android.content.Context
import android.content.SharedPreferences
import org.videolan.tools.KEY_REMOTE_ACCESS_ML_CONTENT
import org.videolan.vlc.R

fun SharedPreferences.serveVideos(context: Context)  = getStringSet(KEY_REMOTE_ACCESS_ML_CONTENT, context.resources.getStringArray(R.array.remote_access_content_values).toSet())?.contains("0") == true
fun SharedPreferences.serveAudios(context: Context)  = getStringSet(KEY_REMOTE_ACCESS_ML_CONTENT, context.resources.getStringArray(R.array.remote_access_content_values).toSet())?.contains("1") == true
fun SharedPreferences.servePlaylists(context: Context)  = getStringSet(KEY_REMOTE_ACCESS_ML_CONTENT, context.resources.getStringArray(R.array.remote_access_content_values).toSet())?.contains("2") == true
fun SharedPreferences.serveSearch(context: Context)  = getStringSet(KEY_REMOTE_ACCESS_ML_CONTENT, context.resources.getStringArray(R.array.remote_access_content_values).toSet())?.contains("3") == true