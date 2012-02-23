/*****************************************************************************
 * LibVlcException.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

/**
 * LibVlcException: exceptions thrown by the native LibVLC interface
 */
package org.videolan.vlc;

/**
 * @author jpeg
 *
 */
public class LibVlcException extends Exception {
    private static final long serialVersionUID = -1909522348226924189L;

    /**
     * Create an empty error
     */
    public LibVlcException() {
        super();
    }

    /**
     * @param detailMessage
     */
    public LibVlcException(String detailMessage) {
        super(detailMessage);
    }

    /**
     * @param throwable
     */
    public LibVlcException(Throwable throwable) {
        super(throwable);
    }

    /**
     * @param detailMessage
     * @param throwable
     */
    public LibVlcException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

}
