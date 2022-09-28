/*
 * ************************************************************************
 *  CarConnectionQueryHandler.java
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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
package org.videolan.vlc.util

import android.content.AsyncQueryHandler
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.lifecycle.MutableLiveData

private const val CAR_CONNECTION_STATE = "CarConnectionState"
private const val CAR_CONNECTION_AUTHORITY = "androidx.car.app.connection"

internal class CarConnectionHandler(resolver: ContentResolver?) : AsyncQueryHandler(resolver) {
    val connectionType = MutableLiveData<Int?>(null)

    init {
        query()
    }

    fun query() {
        startQuery(42, null, Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(CAR_CONNECTION_AUTHORITY).build(), arrayOf(CAR_CONNECTION_STATE), null, null, null)
    }

    override fun onQueryComplete(token: Int, cookie: Any?, cursor: Cursor?) {
        if (cursor == null) {
            connectionType.postValue(CONNECTION_TYPE_NOT_CONNECTED)
            return
        }
        val carConnectionTypeColumn = cursor.getColumnIndex(CAR_CONNECTION_STATE)
        if (carConnectionTypeColumn < 0) {
            connectionType.postValue(CONNECTION_TYPE_NOT_CONNECTED)
            return
        }
        if (!cursor.moveToNext()) {
            connectionType.postValue(CONNECTION_TYPE_NOT_CONNECTED)
            return
        }
        connectionType.postValue(cursor.getInt(carConnectionTypeColumn))
    }

     companion object {
         const val RECEIVER_ACTION = "androidx.car.app.connection.action.CAR_CONNECTION_UPDATED"

         /**
          * Not connected to any car head unit.z
          */
         const val CONNECTION_TYPE_NOT_CONNECTED = 0

         /**
          * Natively running on a head unit (Android Automotive OS).
          */
         const val CONNECTION_TYPE_NATIVE = 1

         /**
          * Connected to a car head unit by projecting to it.
          */
         const val CONNECTION_TYPE_PROJECTION = 2

         fun preferCarConnectionHandler() = Build.VERSION.SDK_INT >= 23
     }
}