/*****************************************************************************
 * GLRenderer.java
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 **
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

import android.graphics.Point;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRenderer  {

    private MediaPlayer.SurfaceListener mListener;
    private int mLastTextureId = 0;
    private boolean mFrameUpdated = false;
    private boolean mSurfaceCreated = false;

    GLRenderer(int eglContextClientVersion, MediaPlayer mp, MediaPlayer.SurfaceListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("listener can't be null");
        mListener = listener;
        nativeInit(mp, eglContextClientVersion);
    }

    void release() {
        nativeRelease();
    }

    @SuppressWarnings("unused") /* Used from JNI */
    private long mInstance = 0;

    synchronized boolean isValid() {
        return mSurfaceCreated;
    }

    /**
     * This method need to be called when a new EGL Context is created (from the same thread).
     *
     * Example: call this method from
     * {@link android.opengl.GLSurfaceView.Renderer#onSurfaceCreated(GL10, EGLConfig)}.
     */
    synchronized public void onSurfaceCreated() {
        nativeOnSurfaceCreated();
        if (!mSurfaceCreated) {
            mSurfaceCreated = true;
            mListener.onSurfaceCreated();
        }
    }

    /**
     * This method need to be called when the EGL Context is destroyed (from any thread).
     *
     * Example: call this method from {@link android.opengl.GLSurfaceView.Renderer#onPause()}.
     */
    synchronized public void onSurfaceDestroyed() {
        if (mSurfaceCreated) {
            mSurfaceCreated = false;
            mListener.onSurfaceDestroyed();
            nativeOnSurfaceDestroyed();
        }
    }

    /**
     * Get the current GL texture ID.
     *
     * @param videoSize Used to get the size of the texture (can be null)
     * @return a valid GL_TEXTURE_2D id or 0 if no video is playing.
     */
    public int getVideoTexture(Point videoSize) {
        int newTextureId = nativeGetVideoTexture(videoSize);
        if (newTextureId != mLastTextureId) {
            if (newTextureId != 0)
                mFrameUpdated = true;
        }
        else {
            mLastTextureId = newTextureId;
            mFrameUpdated = false;
        }
        return newTextureId;
    }

    /**
     * Check if the video frame changed
     *
     * This method need to be called after {@link GLRenderer#getVideoTexture}
     *
     * @return true of the video frame changed
     */
    public boolean isVideoFrameUpdated() {
        return mFrameUpdated;
    }

    private native void nativeInit(MediaPlayer mp, int eglContextClientVersion);
    private native void nativeRelease();
    private native void nativeOnSurfaceCreated();
    private native void nativeOnSurfaceDestroyed();
    private native int nativeGetVideoTexture(Point point);
}