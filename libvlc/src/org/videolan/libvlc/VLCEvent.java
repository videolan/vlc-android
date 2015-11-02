/*****************************************************************************
 * VLCEvent.java
 *****************************************************************************
 * Copyright © 2015 VLC authors, VideoLAN and VideoLabs
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.libvlc;

abstract class VLCEvent {
    public final int type;
    protected final long arg1;
    protected final float arg2;

    protected VLCEvent(int type) {
        this.type = type;
        this.arg1 = 0;
        this.arg2 = 0;
    }
    protected VLCEvent(int type, long arg1) {
        this.type = type;
        this.arg1 = arg1;
        this.arg2 = 0;
    }
    protected VLCEvent(int type, float arg2) {
        this.type = type;
        this.arg1 = 0;
        this.arg2 = arg2;
    }

    /**
     * Listener for libvlc events
     *
     * @see VLCEvent
     */
    public interface Listener<T extends VLCEvent> {
        void onEvent(T event);
    }
}
