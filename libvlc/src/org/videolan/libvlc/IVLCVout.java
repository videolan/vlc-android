/*****************************************************************************
 * public class IVLCVout.java
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

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.os.Build;
import androidx.annotation.MainThread;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

@SuppressWarnings("unused")
public interface IVLCVout {
    interface OnNewVideoLayoutListener {
        /**
         * This listener is called when the "android-display" "vout display" module request a new
         * video layout. The implementation should take care of changing the surface
         * LayoutsParams accordingly. If width and height are 0, LayoutParams should be reset to the
         * initial state (MATCH_PARENT).
         *
         * By default, "android-display" is used when doing HW decoding and if Video and Subtitles
         * surfaces are correctly attached. You could force "--vout=android-display" from LibVLC
         * arguments if you want to use this module without subtitles. Otherwise, the "opengles2"
         * module will be used (for SW and HW decoding) and this callback will always send a size of
         * 0.
         *
         * @param vlcVout vlcVout
         * @param width Frame width
         * @param height Frame height
         * @param visibleWidth Visible frame width
         * @param visibleHeight Visible frame height
         * @param sarNum Surface aspect ratio numerator
         * @param sarDen Surface aspect ratio denominator
         */
        @MainThread
        void onNewVideoLayout(IVLCVout vlcVout, int width, int height,
                              int visibleWidth, int visibleHeight, int sarNum, int sarDen);
    }

    interface Callback {
        /**
         * This callback is called when surfaces are created.
         */
        @MainThread
        void onSurfacesCreated(IVLCVout vlcVout);

        /**
         * This callback is called when surfaces are destroyed.
         */
        @MainThread
        void onSurfacesDestroyed(IVLCVout vlcVout);
    }

    /**
     * Set a surfaceView used for video out.
     * @see #attachViews()
     */
    @MainThread
    void setVideoView(SurfaceView videoSurfaceView);

    /**
     * Set a TextureView used for video out.
     * @see #attachViews()
     */
    @MainThread
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    void setVideoView(TextureView videoTextureView);

    /**
     * Set a surface used for video out.
     * @param videoSurface if surfaceHolder is null, this surface must be valid and attached.
     * @param surfaceHolder optional, used to configure buffers geometry before Android ICS
     * and to get notified when surface is destroyed.
     * @see #attachViews()
     */
    @MainThread
    void setVideoSurface(Surface videoSurface, SurfaceHolder surfaceHolder);

    /**
     * Set a SurfaceTexture used for video out.
     * @param videoSurfaceTexture this surface must be valid and attached.
     * @see #attachViews()
     */
    @MainThread
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    void setVideoSurface(SurfaceTexture videoSurfaceTexture);

    /**
     * Set a surfaceView used for subtitles out.
     * @see #attachViews()
     */
    @MainThread
    void setSubtitlesView(SurfaceView subtitlesSurfaceView);

    /**
     * Set a TextureView used for subtitles out.
     * @see #attachViews()
     */
    @MainThread
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    void setSubtitlesView(TextureView subtitlesTextureView);

    /**
     * Set a surface used for subtitles out.
     * @param subtitlesSurface if surfaceHolder is null, this surface must be valid and attached.
     * @param surfaceHolder optional, used to configure buffers geometry before Android ICS
     * and to get notified when surface is destroyed.
     * @see #attachViews()
     */
    @MainThread
    void setSubtitlesSurface(Surface subtitlesSurface, SurfaceHolder surfaceHolder);

    /**
     * Set a SurfaceTexture used for subtitles out.
     * @param subtitlesSurfaceTexture this surface must be valid and attached.
     * @see #attachViews()
     */
    @MainThread
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    void setSubtitlesSurface(SurfaceTexture subtitlesSurfaceTexture);

    /**
     * Attach views with an OnNewVideoLayoutListener
     *
     * This must be called afters views are set and before the MediaPlayer is first started.
     *
     * If onNewVideoLayoutListener is not null, the caller will handle the video layout that is
     * needed by the "android-display" "vout display" module. Even if that case, the OpenGL ES2
     * could still be used.
     *
     * If onNewVideoLayoutListener is null, the caller won't handle the video layout that is
     * needed by the "android-display" "vout display" module. Therefore, only the OpenGL ES2
     * "vout display" module will be used (for hardware and software decoding).
     *
     * @see OnNewVideoLayoutListener
     * @see #setVideoView(SurfaceView)
     * @see #setVideoView(TextureView)
     * @see #setVideoSurface(Surface, SurfaceHolder)
     * @see #setSubtitlesView(SurfaceView)
     * @see #setSubtitlesView(TextureView)
     * @see #setSubtitlesSurface(Surface, SurfaceHolder)
     */
    @MainThread
    void attachViews(OnNewVideoLayoutListener onNewVideoLayoutListener);

    /**
     * Attach views without an OnNewVideoLayoutListener
     *
     * @see #attachViews(OnNewVideoLayoutListener)
     */
    @MainThread
    void attachViews();

    /**
     * Detach views previously attached.
     * This will be called automatically when surfaces are destroyed.
     */
    @MainThread
    void detachViews();

    /**
     * Return true if views are attached. If surfaces were destroyed, this will return false.
     */
    @MainThread
    boolean areViewsAttached();

    /**
     * Add a callback to receive {@link Callback#onSurfacesCreated} and
     * {@link Callback#onSurfacesDestroyed(IVLCVout)} events.
     */
    @MainThread
    void addCallback(Callback callback);

    /**
     * Remove a callback.
     */
    @MainThread
    void removeCallback(Callback callback);

    /**
     * Send a mouse event to the native vout.
     * @param action see ACTION_* in {@link android.view.MotionEvent}.
     * @param button see BUTTON_* in {@link android.view.MotionEvent}.
     * @param x x coordinate.
     * @param y y coordinate.
     */
    @MainThread
    void sendMouseEvent(int action, int button, int x, int y);

    /**
     * Send the the window size to the native vout.
     * @param width width of the window.
     * @param height height of the window.
     */
    @MainThread
    void setWindowSize(int width, int height);
}
