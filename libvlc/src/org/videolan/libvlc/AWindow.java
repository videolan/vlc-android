/*****************************************************************************
 * class AWindow.java
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
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.MainThread;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import org.videolan.libvlc.interfaces.IVLCVout;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("WeakerAccess")
public class AWindow implements IVLCVout {
    private static final String TAG = "AWindow";

    private static final int ID_VIDEO = 0;
    private static final int ID_SUBTITLES = 1;
    private static final int ID_MAX = 2;

    public interface SurfaceCallback {
        @MainThread
        void onSurfacesCreated(AWindow vout);
        @MainThread
        void onSurfacesDestroyed(AWindow vout);
    }

    private class SurfaceHelper {
        private final int mId;
        private final SurfaceView mSurfaceView;
        private final TextureView mTextureView;
        private final SurfaceHolder mSurfaceHolder;
        private Surface mSurface;

        private SurfaceHelper(int id, SurfaceView surfaceView) {
            mId = id;
            mTextureView = null;
            mSurfaceView = surfaceView;
            mSurfaceHolder = mSurfaceView.getHolder();
        }

        private SurfaceHelper(int id, TextureView textureView) {
            mId = id;
            mSurfaceView = null;
            mSurfaceHolder = null;
            mTextureView = textureView;
        }

        private SurfaceHelper(int id, Surface surface, SurfaceHolder surfaceHolder) {
            mId = id;
            mSurfaceView = null;
            mTextureView = null;
            mSurfaceHolder = surfaceHolder;
            mSurface = surface;
        }

        private void setSurface(Surface surface) {
            if (surface.isValid() && getNativeSurface(mId) == null) {
                mSurface = surface;
                setNativeSurface(mId, mSurface);
                onSurfaceCreated();
            }
        }

        private void attachSurfaceView() {
            mSurfaceHolder.addCallback(mSurfaceHolderCallback);
            setSurface(mSurfaceHolder.getSurface());
        }

        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        private void attachTextureView() {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

            /* The SurfaceTexture might be already available, in which case
             * the listener won't be signalled. Check the existence right after
             * attaching the listener and call it manually in this case. */
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            if (surfaceTexture != null)
                mSurfaceTextureListener.onSurfaceTextureAvailable(surfaceTexture,
                        mTextureView.getWidth(), mTextureView.getHeight());
        }

        private void attachSurface() {
            if (mSurfaceHolder != null)
                mSurfaceHolder.addCallback(mSurfaceHolderCallback);
            setSurface(mSurface);
        }

        public void attach() {
            if (mSurfaceView != null) {
                attachSurfaceView();
            } else if (mTextureView != null) {
                attachTextureView();
            } else if (mSurface != null) {
                attachSurface();
            } else
                throw new IllegalStateException();
        }

        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        private void releaseTextureView() {
            if (mTextureView != null)
                mTextureView.setSurfaceTextureListener(null);
        }

        public void release() {
            mSurface = null;
            setNativeSurface(mId, null);
            if (mSurfaceHolder != null)
                mSurfaceHolder.removeCallback(mSurfaceHolderCallback);
            releaseTextureView();
        }

        public boolean isReady() {
            return mSurfaceView == null || mSurface != null;
        }

        public Surface getSurface() {
            return mSurface;
        }

        SurfaceHolder getSurfaceHolder() {
            return mSurfaceHolder;
        }

        private final SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (holder != mSurfaceHolder)
                    throw new IllegalStateException("holders are different");
                setSurface(holder.getSurface());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                onSurfaceDestroyed();
            }
        };

        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        private TextureView.SurfaceTextureListener createSurfaceTextureListener() {
            return new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                    setSurface(new Surface(surfaceTexture));
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    onSurfaceDestroyed();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            };
        }

        private final TextureView.SurfaceTextureListener mSurfaceTextureListener = createSurfaceTextureListener();
    }

    private final static int SURFACE_STATE_INIT = 0;
    private final static int SURFACE_STATE_ATTACHED = 1;
    private final static int SURFACE_STATE_READY = 2;

    private final SurfaceHelper[] mSurfaceHelpers;
    private final SurfaceCallback mSurfaceCallback;
    private final AtomicInteger mSurfacesState = new AtomicInteger(SURFACE_STATE_INIT);
    private OnNewVideoLayoutListener mOnNewVideoLayoutListener = null;
    private ArrayList<IVLCVout.Callback> mIVLCVoutCallbacks = new ArrayList<IVLCVout.Callback>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    /* synchronized Surfaces accessed by an other thread from JNI */
    private final Surface[] mSurfaces;
    private long mCallbackNativeHandle = 0;
    private int mMouseAction = -1, mMouseButton = -1, mMouseX = -1, mMouseY = -1;
    private int mWindowWidth = -1, mWindowHeight = -1;

    private SurfaceTextureThread mSurfaceTextureThread = new SurfaceTextureThread();

    /**
     * Create an AWindow
     *
     * You call this directly only if you use the libvlc_media_player native API (and not the Java
     * MediaPlayer class).
     * @param surfaceCallback
     */
    public AWindow(SurfaceCallback surfaceCallback) {
        mSurfaceCallback = surfaceCallback;
        mSurfaceHelpers = new SurfaceHelper[ID_MAX];
        mSurfaceHelpers[ID_VIDEO] = null;
        mSurfaceHelpers[ID_SUBTITLES] = null;
        mSurfaces = new Surface[ID_MAX];
        mSurfaces[ID_VIDEO] = null;
        mSurfaces[ID_SUBTITLES] = null;
    }

    private void ensureInitState() throws IllegalStateException {
        if (mSurfacesState.get() != SURFACE_STATE_INIT)
            throw new IllegalStateException("Can't set view when already attached. " +
                    "Current state: " + mSurfacesState.get() + ", " +
                    "mSurfaces[ID_VIDEO]: " + mSurfaceHelpers[ID_VIDEO] + " / " + mSurfaces[ID_VIDEO] + ", " +
                    "mSurfaces[ID_SUBTITLES]: " + mSurfaceHelpers[ID_SUBTITLES] + " / " + mSurfaces[ID_SUBTITLES]);
    }

    private void setView(int id, SurfaceView view) {
        ensureInitState();
        if (view == null)
            throw new NullPointerException("view is null");
        final SurfaceHelper surfaceHelper = mSurfaceHelpers[id];
        if (surfaceHelper != null)
            surfaceHelper.release();

        mSurfaceHelpers[id] = new SurfaceHelper(id, view);
    }

    private void setView(int id, TextureView view) {
        ensureInitState();
        if (view == null)
            throw new NullPointerException("view is null");
        final SurfaceHelper surfaceHelper = mSurfaceHelpers[id];
        if (surfaceHelper != null)
            surfaceHelper.release();

        mSurfaceHelpers[id] = new SurfaceHelper(id, view);
    }

    private void setSurface(int id, Surface surface, SurfaceHolder surfaceHolder) {
        ensureInitState();
        if (!surface.isValid() && surfaceHolder == null)
            throw new IllegalStateException("surface is not attached and holder is null");
        final SurfaceHelper surfaceHelper = mSurfaceHelpers[id];
        if (surfaceHelper != null)
            surfaceHelper.release();

        mSurfaceHelpers[id] = new SurfaceHelper(id, surface, surfaceHolder);
    }

    @Override
    @MainThread
    public void setVideoView(SurfaceView videoSurfaceView) {
        setView(ID_VIDEO, videoSurfaceView);
    }

    @Override
    @MainThread
    public void setVideoView(TextureView videoTextureView) {
        setView(ID_VIDEO, videoTextureView);
    }

    @Override
    public void setVideoSurface(Surface videoSurface, SurfaceHolder surfaceHolder) {
        setSurface(ID_VIDEO, videoSurface, surfaceHolder);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void setVideoSurface(SurfaceTexture videoSurfaceTexture) {
        setSurface(ID_VIDEO, new Surface(videoSurfaceTexture), null);
    }

    @Override
    @MainThread
    public void setSubtitlesView(SurfaceView subtitlesSurfaceView) {
        setView(ID_SUBTITLES, subtitlesSurfaceView);
    }

    @Override
    @MainThread
    public void setSubtitlesView(TextureView subtitlesTextureView) {
        setView(ID_SUBTITLES, subtitlesTextureView);
    }

    @Override
    public void setSubtitlesSurface(Surface subtitlesSurface, SurfaceHolder surfaceHolder) {
        setSurface(ID_SUBTITLES, subtitlesSurface, surfaceHolder);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void setSubtitlesSurface(SurfaceTexture subtitlesSurfaceTexture) {
        setSurface(ID_SUBTITLES, new Surface(subtitlesSurfaceTexture), null);
    }

    @Override
    @MainThread
    public void attachViews(OnNewVideoLayoutListener onNewVideoLayoutListener) {
        if (mSurfacesState.get() != SURFACE_STATE_INIT || mSurfaceHelpers[ID_VIDEO] == null)
            throw new IllegalStateException("already attached or video view not configured");
        mSurfacesState.set(SURFACE_STATE_ATTACHED);
        synchronized (mNativeLock) {
            mOnNewVideoLayoutListener = onNewVideoLayoutListener;
            mNativeLock.buffersGeometryConfigured = false;
            mNativeLock.buffersGeometryAbort = false;
        }
        for (int id = 0; id < ID_MAX; ++id) {
            final SurfaceHelper surfaceHelper = mSurfaceHelpers[id];
            if (surfaceHelper != null)
                surfaceHelper.attach();
        }
    }

    @Override
    @MainThread
    public void attachViews() {
        attachViews(null);
    }

    @Override
    @MainThread
    public void detachViews() {
        if (mSurfacesState.get() == SURFACE_STATE_INIT)
            return;

        mSurfacesState.set(SURFACE_STATE_INIT);
        mHandler.removeCallbacksAndMessages(null);
        synchronized (mNativeLock) {
            mOnNewVideoLayoutListener = null;
            mNativeLock.buffersGeometryAbort = true;
            mNativeLock.notifyAll();
        }
        for (int id = 0; id < ID_MAX; ++id) {
            final SurfaceHelper surfaceHelper = mSurfaceHelpers[id];
            if (surfaceHelper != null)
                surfaceHelper.release();
            mSurfaceHelpers[id] = null;
        }
        for (IVLCVout.Callback cb : mIVLCVoutCallbacks)
            cb.onSurfacesDestroyed(this);
        if (mSurfaceCallback != null)
            mSurfaceCallback.onSurfacesDestroyed(this);
        mSurfaceTextureThread.release();
    }

    @Override
    @MainThread
    public boolean areViewsAttached() {
        return mSurfacesState.get() != SURFACE_STATE_INIT;
    }

    @MainThread
    private void onSurfaceCreated() {
        if (mSurfacesState.get() != SURFACE_STATE_ATTACHED)
            throw new IllegalArgumentException("invalid state");

        final SurfaceHelper videoHelper = mSurfaceHelpers[ID_VIDEO];
        final SurfaceHelper subtitlesHelper = mSurfaceHelpers[ID_SUBTITLES];
        if (videoHelper == null)
            throw new NullPointerException("videoHelper shouldn't be null here");

        if (videoHelper.isReady() && (subtitlesHelper == null || subtitlesHelper.isReady())) {
            mSurfacesState.set(SURFACE_STATE_READY);
            for (IVLCVout.Callback cb : mIVLCVoutCallbacks)
                cb.onSurfacesCreated(this);
            if (mSurfaceCallback != null)
                mSurfaceCallback.onSurfacesCreated(this);
        }
    }

    @MainThread
    private void onSurfaceDestroyed() {
        detachViews();
    }

    boolean areSurfacesWaiting() {
        return mSurfacesState.get() == SURFACE_STATE_ATTACHED;
    }

    @Override
    public void sendMouseEvent(int action, int button, int x, int y) {
        synchronized (mNativeLock) {
            if (mCallbackNativeHandle != 0 && (mMouseAction != action || mMouseButton != button
                    || mMouseX != x || mMouseY != y))
                nativeOnMouseEvent(mCallbackNativeHandle, action, button, x, y);
            mMouseAction = action;
            mMouseButton = button;
            mMouseX = x;
            mMouseY = y;
        }
    }

    @Override
    public void setWindowSize(int width, int height) {
        synchronized (mNativeLock) {
            if (mCallbackNativeHandle != 0 && (mWindowWidth != width || mWindowHeight != height))
                nativeOnWindowSize(mCallbackNativeHandle, width, height);
            mWindowWidth = width;
            mWindowHeight = height;
        }
    }

    private void setNativeSurface(int id, Surface surface) {
        synchronized (mNativeLock) {
            mSurfaces[id] = surface;
        }
    }

    private Surface getNativeSurface(int id) {
        synchronized (mNativeLock) {
            return mSurfaces[id];
        }
    }

    private static class NativeLock {
        private boolean buffersGeometryConfigured = false;
        private boolean buffersGeometryAbort = false;
    }
    private final NativeLock mNativeLock = new NativeLock();

    @Override
    public void addCallback(IVLCVout.Callback callback) {
        if (!mIVLCVoutCallbacks.contains(callback))
            mIVLCVoutCallbacks.add(callback);
    }

    @Override
    public void removeCallback(IVLCVout.Callback callback) {
        mIVLCVoutCallbacks.remove(callback);
    }

    /**
     * Callback called from {@link IVLCVout#sendMouseEvent}.
     *
     * @param nativeHandle handle passed by {@link #registerNative(long)}.
     * @param action see ACTION_* in {@link android.view.MotionEvent}.
     * @param button see BUTTON_* in {@link android.view.MotionEvent}.
     * @param x x coordinate.
     * @param y y coordinate.
     */
    @SuppressWarnings("JniMissingFunction")
    private static native void nativeOnMouseEvent(long nativeHandle, int action, int button, int x, int y);

    /**
     * Callback called from {@link IVLCVout#setWindowSize}.
     *
     * @param nativeHandle handle passed by {@link #registerNative(long)}.
     * @param width width of the window.
     * @param height height of the window.
     */
    @SuppressWarnings("JniMissingFunction")
    private static native void nativeOnWindowSize(long nativeHandle, int width, int height);

    /**
     * Get the valid Video surface.
     *
     * @return can be null if the surface was destroyed.
     */
    @SuppressWarnings("unused") /* used by JNI */
    private Surface getVideoSurface() {
        return getNativeSurface(ID_VIDEO);
    }

    /**
     * Get the valid Subtitles surface.
     *
     * @return can be null if the surface was destroyed.
     */
    @SuppressWarnings("unused") /* used by JNI */
    private Surface getSubtitlesSurface() {
        return getNativeSurface(ID_SUBTITLES);

    }

    private final static int AWINDOW_REGISTER_ERROR = 0;
    private final static int AWINDOW_REGISTER_FLAGS_SUCCESS = 0x1;
    private final static int AWINDOW_REGISTER_FLAGS_HAS_VIDEO_LAYOUT_LISTENER = 0x2;

    /**
     * Set a callback in order to receive {@link #nativeOnMouseEvent} and {@link #nativeOnWindowSize} events.
     *
     * @param nativeHandle native Handle passed by {@link #nativeOnMouseEvent} and {@link #nativeOnWindowSize}, cannot be NULL
     * @return true if callback was successfully registered
     */
    @SuppressWarnings("unused") /* used by JNI */
    private int registerNative(long nativeHandle) {
        if (nativeHandle == 0)
            throw new IllegalArgumentException("nativeHandle is null");
        synchronized (mNativeLock) {
            if (mCallbackNativeHandle != 0)
                return AWINDOW_REGISTER_ERROR;
            mCallbackNativeHandle = nativeHandle;
            if (mMouseAction != -1)
                nativeOnMouseEvent(mCallbackNativeHandle, mMouseAction, mMouseButton, mMouseX, mMouseY);
            if (mWindowWidth != -1 && mWindowHeight != -1)
                nativeOnWindowSize(mCallbackNativeHandle, mWindowWidth, mWindowHeight);
            int flags = AWINDOW_REGISTER_FLAGS_SUCCESS;

            if (mOnNewVideoLayoutListener != null)
                flags |= AWINDOW_REGISTER_FLAGS_HAS_VIDEO_LAYOUT_LISTENER;
            return flags;
        }
    }

    @SuppressWarnings("unused") /* used by JNI */
    private void unregisterNative() {
        synchronized (mNativeLock) {
            if (mCallbackNativeHandle == 0)
                throw new IllegalArgumentException("unregister called when not registered");
            mCallbackNativeHandle = 0;
        }
    }

    /**
     * This method is only used for HoneyComb and before since ANativeWindow_setBuffersGeometry doesn't work before.
     * It is synchronous.
     *
     * @param surface surface returned by getVideoSurface or getSubtitlesSurface
     * @param width surface width
     * @param height surface height
     * @param format color format (or PixelFormat)
     * @return true if buffersGeometry were set (only before ICS)
     */
    @SuppressWarnings("unused") /* used by JNI */
    private boolean setBuffersGeometry(final Surface surface, final int width, final int height, final int format) {
        return false;
    }

    /**
     * Set the video Layout.
     * This call will result of{@link IVLCVout.OnNewVideoLayoutListener#onNewVideoLayout(IVLCVout, int, int, int, int, int, int)}
     * being called from the main thread.
     *
     * @param width Frame width
     * @param height Frame height
     * @param visibleWidth Visible frame width
     * @param visibleHeight Visible frame height
     * @param sarNum Surface aspect ratio numerator
     * @param sarDen Surface aspect ratio denominator
     */
    @SuppressWarnings("unused") /* used by JNI */
    private void setVideoLayout(final int width, final int height, final int visibleWidth,
                                final int visibleHeight, final int sarNum, final int sarDen) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                /* No need to synchronize here, mOnNewVideoLayoutListener is only set from MainThread */
                if (mOnNewVideoLayoutListener != null)
                    mOnNewVideoLayoutListener.onNewVideoLayout(AWindow.this, width, height,
                            visibleWidth, visibleHeight, sarNum, sarDen);
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static class SurfaceTextureThread
            implements Runnable, SurfaceTexture.OnFrameAvailableListener {
        private SurfaceTexture mSurfaceTexture = null;
        private Surface mSurface = null;

        private boolean mFrameAvailable = false;
        private Looper mLooper = null;
        private Thread mThread = null;
        private boolean mIsAttached = false;
        private boolean mDoRelease = false;

        private SurfaceTextureThread() {
        }

        private synchronized boolean createSurface() {
            /* Try to re-use the same SurfaceTexture until views are detached. By reusing the same
             * SurfaceTexture, we don't have to reconfigure MediaCodec when it signals a video size
             * change (and when a new VLC vout is created) */
            if (mSurfaceTexture == null) {
                /* Yes, a new Thread, see comments in the run method */
                mThread = new Thread(this);
                mThread.start();
                while (mSurfaceTexture == null) {
                    try {
                        wait();
                    } catch (InterruptedException ignored) {
                        return false;
                    }
                }
                mSurface = new Surface(mSurfaceTexture);
            }
            return true;
        }

        private synchronized boolean attachToGLContext(int texName) {
            if (!createSurface())
                return false;
            mSurfaceTexture.attachToGLContext(texName);
            mFrameAvailable = false;
            mIsAttached = true;
            return true;
        }

        @Override
        public synchronized void onFrameAvailable(SurfaceTexture surfaceTexture) {
            if (surfaceTexture == mSurfaceTexture) {
                if (mFrameAvailable)
                    throw new IllegalStateException("An available frame was not updated");
                mFrameAvailable = true;
                notify();
            }
        }

        @Override
        public void run() {
            Looper.prepare();

            synchronized (this) {
                /* Ideally, all devices are running Android O, and we can create a SurfaceTexture
                 * without an OpenGL context and we can specify the thread (Handler) where to run
                 * SurfaceTexture callbacks. But this is not the case. The SurfaceTexture has to be
                 * created from a new thread with a prepared looper in order to don't use the
                 * MainLooper one (and have deadlock when we stop VLC from the mainloop).
                 */
                mLooper = Looper.myLooper();
                mSurfaceTexture = new SurfaceTexture(0);
                /* The OpenGL texture will be attached from the OpenGL Thread */
                mSurfaceTexture.detachFromGLContext();
                mSurfaceTexture.setOnFrameAvailableListener(this);
                notify();
            }

            Looper.loop();
        }

        private synchronized void detachFromGLContext() {
            if (mDoRelease) {
                mLooper.quit();
                mLooper = null;

                try {
                    mThread.join();
                } catch (InterruptedException ignored) {
                }
                mThread = null;

                mSurface.release();
                mSurface = null;
                mSurfaceTexture.release();
                mSurfaceTexture = null;
                mDoRelease = false;
            } else {
                mSurfaceTexture.detachFromGLContext();
            }
            mIsAttached = false;
        }

        private boolean waitAndUpdateTexImage(float[] transformMatrix) {
            synchronized (this) {
                while (!mFrameAvailable) {
                    try {
                        wait(500);
                        if (!mFrameAvailable)
                            return false;
                    } catch (InterruptedException ignored) {
                    }
                }
                mFrameAvailable = false;
            }
            mSurfaceTexture.updateTexImage();
            mSurfaceTexture.getTransformMatrix(transformMatrix);
            return true;
        }

        private synchronized Surface getSurface() {
            if (!createSurface())
                return null;
            return mSurface;
        }

        private synchronized void release() {
            if (mSurfaceTexture != null) {
                if (mIsAttached) {
                    /* Release from detachFromGLContext */
                    mDoRelease = true;
                } else {
                    mSurface.release();
                    mSurface = null;
                    mSurfaceTexture.release();
                    mSurfaceTexture = null;
                }
            }
        }
    }

    /**
     * Attach the SurfaceTexture to the OpenGL ES context that is current on the calling thread.
     *
     * @param texName the OpenGL texture object name (e.g. generated via glGenTextures)
     * @return true in case of success
     */
    @SuppressWarnings("unused") /* used by JNI */
    boolean SurfaceTexture_attachToGLContext(int texName) {
        return mSurfaceTextureThread.attachToGLContext(texName);
    }

    /**
     * Detach the SurfaceTexture from the OpenGL ES context that owns the OpenGL ES texture object.
     */
    @SuppressWarnings("unused") /* used by JNI */
    private void SurfaceTexture_detachFromGLContext() {
        mSurfaceTextureThread.detachFromGLContext();
    }

    /**
     * Wait for a frame and update the TexImage
     *
     * @return true on success, false on error or timeout
     */
    @SuppressWarnings("unused") /* used by JNI */
    private boolean SurfaceTexture_waitAndUpdateTexImage(float[] transformMatrix) {
        return mSurfaceTextureThread.waitAndUpdateTexImage(transformMatrix);
    }

    /**
     * Get a Surface from the SurfaceTexture
     */
    @SuppressWarnings("unused") /* used by JNI */
    private Surface SurfaceTexture_getSurface() {
        return mSurfaceTextureThread.getSurface();
    }
}
