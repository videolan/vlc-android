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

package org.videolan.libvlc.interfaces;

import androidx.annotation.Nullable;

public abstract class AbstractVLCEvent {
    public final int type;
    protected final long arg1;
    protected final long arg2;
    protected final float argf1;
    protected final String args1;

    public AbstractVLCEvent(int type) {
        this.type = type;
        this.arg1 = this.arg2 = 0;
        this.argf1 = 0.0f;
        this.args1 = null;
    }
    public AbstractVLCEvent(int type, long arg1) {
        this.type = type;
        this.arg1 = arg1;
        this.arg2 = 0;
        this.argf1 = 0.0f;
        this.args1 = null;
    }
    public AbstractVLCEvent(int type, long arg1, long arg2) {
        this.type = type;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.argf1 = 0.0f;
        this.args1 = null;
    }
    public AbstractVLCEvent(int type, float argf) {
        this.type = type;
        this.arg1 = this.arg2 = 0;
        this.argf1 = argf;
        this.args1 = null;
    }
    public AbstractVLCEvent(int type, long arg1, @Nullable String args1) {
        this.type = type;
        this.arg1 = arg1;
        this.arg2 = 0;
        this.argf1 = 0.0f;
        this.args1 = args1;
    }

    public void release() {
        /* do nothing */
    }

    /**
     * Listener for libvlc events
     *
     * @see AbstractVLCEvent
     */
    public interface Listener<T extends AbstractVLCEvent> {
        void onEvent(T event);
    }
}
