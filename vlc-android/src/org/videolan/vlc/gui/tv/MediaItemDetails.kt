/*****************************************************************************
 * MediaItemDetails.java
 *
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
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
package org.videolan.vlc.gui.tv

import android.os.Parcel
import android.os.Parcelable

data class MediaItemDetails(var title: String? = null, var subTitle: String? = null,
                            var body: String? = null,
                            var location: String? = null,
                            var artworkUrl: String? = null) : Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title)
        parcel.writeString(subTitle)
        parcel.writeString(body)
        parcel.writeString(location)
        parcel.writeString(artworkUrl)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<MediaItemDetails> {
        override fun createFromParcel(parcel: Parcel): MediaItemDetails {
            return MediaItemDetails(parcel)
        }

        override fun newArray(size: Int): Array<MediaItemDetails?> {
            return arrayOfNulls(size)
        }
    }


}
