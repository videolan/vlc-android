/*
 * ************************************************************************
 *  BookmarkImpl.java
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
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

package org.videolan.medialibrary.media;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.Bookmark;

@SuppressWarnings("JniMissingFunction")
public class BookmarkImpl extends Bookmark {

    public BookmarkImpl(long id, String name, String description, long mediaId, long time) {
        super(id, name,description, mediaId, time);
    }

    public BookmarkImpl(Parcel in) {
        super(in);
    }

    @Override
    public boolean setName(String name) {
        this.setTitle(name);
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeSetName(ml, mId, name);
    }

    @Override
    public boolean updateDescription(String description) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeSetDescription(ml, mId, description);
    }

    @Override
    public boolean setNameAndDescription(String name, String description) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeSetNameAndDescription(ml, mId, name, description);
    }

    @Override
    public boolean move(long time) {
        final Medialibrary ml = Medialibrary.getInstance();
        return ml.isInitiated() && nativeMove(ml, mId, time);
    }

    private native boolean nativeSetName(Medialibrary ml, long mId, String name);
    private native boolean nativeSetDescription(Medialibrary ml, long mId, String description);
    private native boolean nativeSetNameAndDescription(Medialibrary ml, long mId, String name, String description);
    private native boolean nativeMove(Medialibrary ml, long mId, long time);
}
