/*
 * *************************************************************************
 *  VLCExtensionItem.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.extensions.api;

import android.os.Parcel;
import android.os.Parcelable;

public class VLCExtensionItem implements Parcelable {

    public static final int TYPE_DIRECTORY = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_AUDIO = 2;
    public static final int TYPE_PLAYLIST = 3;
    public static final int TYPE_SUBTITLE = 4;
    public static final int TYPE_OTHER_FILE = 5;

    public String stringId;
    public int intId;

    public String link;
    public String title;
    public String subTitle;

    //TODO choose how to deal with icons
    public String imageLink; // for content provider
    public int type; // Using VLC icons. maybe with iconRes?

    public VLCExtensionItem(String stringId, int intId, String link, String title, String subTitle, String imageLink, int type) {
        this.stringId = stringId;
        this.intId = intId;
        this.link = link;
        this.title = title;
        this.subTitle = subTitle;
        this.imageLink = imageLink;
        this.type = type;
    }

    public VLCExtensionItem() {
    }

    private VLCExtensionItem(Parcel in) {
        readFromParcel(in);
    }
    public static final Parcelable.Creator<VLCExtensionItem> CREATOR = new
            Parcelable.Creator<VLCExtensionItem>() {
                public VLCExtensionItem createFromParcel(Parcel in) {
                    return new VLCExtensionItem(in);
                }

                public VLCExtensionItem[] newArray(int size) {
                    return new VLCExtensionItem[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(stringId);
        dest.writeInt(intId);
        dest.writeString(link);
        dest.writeString(title);
        dest.writeString(subTitle);
        dest.writeString(imageLink);
        dest.writeInt(type);
    }

    public void readFromParcel(Parcel in) {
        stringId = in.readString();
        intId = in.readInt();
        link = in.readString();
        title = in.readString();
        subTitle = in.readString();
        imageLink = in.readString();
        type = in.readInt();
    }
}
