/*****************************************************************************
 * public class AWindowNativeHandler.java
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

import android.view.Surface;

public abstract class AWindowNativeHandler {
    /**
     * Callback called from {@link IVLCVout#sendMouseEvent}.
     *
     * @param nativeHandle handle passed by {@link #setCallback}.
     * @param action see ACTION_* in {@link android.view.MotionEvent}.
     * @param button see BUTTON_* in {@link android.view.MotionEvent}.
     * @param x x coordinate.
     * @param y y coordinate.
     */
    protected abstract void nativeOnMouseEvent(long nativeHandle, int action, int button, int x, int y);

    /**
     * Callback called from {@link IVLCVout#setWindowSize}.
     *
     * @param nativeHandle handle passed by {@link #setCallback}.
     * @param width width of the window.
     * @param height height of the window.
     */
    protected abstract void nativeOnWindowSize(long nativeHandle, int width, int height);

    /**
     * Get the valid Video surface.
     *
     * @return can be null if the surface was destroyed.
     */
    @SuppressWarnings("unused") /* Used by JNI */
    protected abstract Surface getVideoSurface();

    /**
     * Get the valid Subtitles surface.
     *
     * @return can be null if the surface was destroyed.
     */
    @SuppressWarnings("unused") /* Used by JNI */
    protected abstract Surface getSubtitlesSurface();

    /**
     * Set a callback in order to receive {@link #nativeOnMouseEvent} and {@link #nativeOnWindowSize} events.
     *
     * @param nativeHandle native Handle passed by {@link #nativeOnMouseEvent} and {@link #nativeOnWindowSize}
     * @return true if callback was successfully registered
     */
    @SuppressWarnings("unused") /* Used by JNI */
    protected abstract boolean setCallback(long nativeHandle);

    /**
     * This method is only used for ICS and before since ANativeWindow_setBuffersGeometry doesn't work before.
     * It is synchronous.
     *
     * @param surface surface returned by getVideoSurface or getSubtitlesSurface
     * @param width surface width
     * @param height surface height
     * @param format color format (or PixelFormat)
     * @return true if buffersGeometry were set (only before ICS)
     */
    @SuppressWarnings("unused") /* Used by JNI */
    protected abstract boolean setBuffersGeometry(Surface surface, int width, int height, int format);

    /**
     * Set the window Layout.
     * This call will result of {@link IVLCVout.Callback#onNewLayout} being called from the main thread.
     *
     * @param width Frame width
     * @param height Frame height
     * @param visibleWidth Visible frame width
     * @param visibleHeight Visible frame height
     * @param sarNum Surface aspect ratio numerator
     * @param sarDen Surface aspect ratio denominator
     */
    @SuppressWarnings("unused") /* Used by JNI */
    protected abstract void setWindowLayout(int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen);
}