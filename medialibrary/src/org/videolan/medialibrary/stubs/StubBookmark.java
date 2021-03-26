/*
 * ************************************************************************
 *  StubBookmark.java
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

package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.Bookmark;

public class StubBookmark extends Bookmark {

    private StubDataSource dt = StubDataSource.getInstance();

    public StubBookmark(long id, String name, String description, long mediaId, long time) {
        super(id, name, description, mediaId, time);
    }

    @Override
    public boolean setName(String name) {
        return false;
    }

    @Override
    public boolean updateDescription(String description) {
        return false;
    }

    @Override
    public boolean setNameAndDescription(String name, String description) {
        return false;
    }

    @Override
    public boolean move(long time) {
        return false;
    }

    public StubBookmark(Parcel in) {
        super(in);
    }

}
