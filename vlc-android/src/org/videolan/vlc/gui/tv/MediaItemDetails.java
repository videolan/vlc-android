/*****************************************************************************
 * MediaItemDetails.java
 *****************************************************************************
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
 *****************************************************************************/
package org.videolan.vlc.gui.tv;

import android.os.Parcel;
import android.os.Parcelable;

public class MediaItemDetails implements Parcelable {

    private String title, subTitle, body, location, artworkUrl;

    public MediaItemDetails(String title, String subTitle, String body, String location, String artworkUrl) {
        this.title = title;
        this.subTitle = subTitle;
        this.body = body;
        this.location = location;
        this.artworkUrl = artworkUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getSubTitle() {
        return subTitle;
    }

    public String getBody() {
        return body;
    }

    public String getLocation(){
        return location;
    }

    public String getArtworkUrl() {
        return artworkUrl;
    }

    @Override
    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeString(subTitle);
        dest.writeString(body);
        dest.writeString(location);
        dest.writeString(artworkUrl);
    }

    public static final Parcelable.Creator<MediaItemDetails> CREATOR
            = new Parcelable.Creator<MediaItemDetails>() {
        public MediaItemDetails createFromParcel(Parcel in) {
            return new MediaItemDetails(in);
        }

        public MediaItemDetails[] newArray(int size) {
            return new MediaItemDetails[size];
        }
    };

    private MediaItemDetails(Parcel in) {
        title = in.readString();
        subTitle = in.readString();
        body = in.readString();
        location = in.readString();
        artworkUrl = in.readString();
    }
}
