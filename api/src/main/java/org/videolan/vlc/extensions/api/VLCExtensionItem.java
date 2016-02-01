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

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * A parcelable, serializable object containing information about medium to be sent to VLC
 * for browsing of playback
 *
 * <p>
 * This class follows the <a href="http://en.wikipedia.org/wiki/Fluent_interface">fluent
 * interface</a> style, using method chaining to provide for more readable code. For example, to set
 * the title and link of this data, use {@link #setTitle(String)} and {@link #setLink(String)}
 * methods like so:
 *
 * <pre class="prettyprint">
 * VLCExtensionItem data = new VLCExtensionItem(id, null)
 *     .setTitle("My Video")
 *     .setLink("http://mysite.net/myvideo.ogv");
 * </pre>
 *
 */
public class VLCExtensionItem implements Parcelable {

    /**
     * Item type to show it as a directory in VLC browser
     */
    public static final int TYPE_DIRECTORY = 0;
    /**
     * Item type to show it as a video medium in VLC browser
     */
    public static final int TYPE_VIDEO = 1;
     /**
     * Item type to show it as an audio medium in VLC browser
     */
    public static final int TYPE_AUDIO = 2;
    /**
     * Item type to show it as a playlist item in VLC browser
     */
    public static final int TYPE_PLAYLIST = 3;
    /**
     * Item type to show it as a subtitle file in VLC browser
     */
    public static final int TYPE_SUBTITLE = 4;
    /**
     * Unknown type, VLC will guess from its {#link link} or title
     */
    public static final int TYPE_OTHER_FILE = 5;

    public String stringId;
    public int intId;

    public String link;
    public String title;
    public String subTitle;

    public Uri imageUri; // for content provider
    public int type; // Using VLC icons. maybe with iconRes?

    /**
     * Simple constructor, with only ids.
     * You have to provide a String or int id for browsable elements (with type #TYPE_DIRECTORY)
     *
     * @param stringId The String to use as an ID, set to #null if you prefer to use the #intId
     * @param intId The int to use as an ID, set to 0 if you prefer to use the #stringId
     */
    public VLCExtensionItem(String stringId, int intId) {
        this.stringId = stringId;
        this.intId = intId;
    }

    public VLCExtensionItem() {}

    private VLCExtensionItem(Parcel in) {
        readFromParcel(in);
    }

    /**
     * Returns the subTitle of this item. e.g. media artist or album
     */
    public String getSubTitle() {
        return subTitle;
    }

    /**
     * Sets the subTitles string of this item.
     *
     * @param subTitle The subTitle string to set.
     */
    public VLCExtensionItem setSubTitle(String subTitle) {
        this.subTitle = subTitle;
        return this;
    }

    /**
     * Returns the uri string of the {#link VLCExtensionItem} for playback or download
     */
    public String getLink() {
        return link;
    }

    /**
     * Sets the uri String of the {#link VLCExtensionItem}
     *
     * @param link The medium link.
     */
    public VLCExtensionItem setLink(String link) {
        this.link = link;
        return this;
    }

    /**
     * returns the {#link VLCExtensionItem} title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the {#link VLCExtensionItem} title
     *
     * @param title The string to set as title.
     */
    public VLCExtensionItem setTitle(String title) {
        this.title = title;
        return this;
    }

    /**
     * Returns the {#link VLCExtensionItem} icon image link
     */
    public Uri getImageUri() {
        return imageUri;
    }

    /**
     * Sets the uri string of the {#link VLCExtensionItem} icon image.
     *
     * @param imageUri The uri string to set.
     */
    public VLCExtensionItem setImageUri(Uri imageUri) {
        this.imageUri = imageUri;
        return this;
    }

    /**
     * Returns the {#link VLCExtensionItem} type
     * @see {#link setType}.
     */
    public int getType() {
        return type;
    }

    /**
     * Sets the type of the {#link VLCExtensionItem}
     *
     * @param type The type among {#link TYPE_DIRECTORY}, {#link TYPE_VIDEO},
     *             {#link TYPE_AUDIO} or {#link TYPE_OTHER_FILE}.
     */
    public VLCExtensionItem setType(int type) {
        this.type = type;
        return this;
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
        dest.writeParcelable(imageUri, 0);
        dest.writeInt(type);
    }

    public void readFromParcel(Parcel in) {
        stringId = in.readString();
        intId = in.readInt();
        link = in.readString();
        title = in.readString();
        subTitle = in.readString();
        imageUri = in.readParcelable(null);
        type = in.readInt();
    }
}
