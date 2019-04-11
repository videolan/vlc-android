package org.videolan.libvlc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;

import org.videolan.R;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.libvlc.util.DisplayManager;
import org.videolan.libvlc.util.VLCVideoLayout;

class VideoHelper implements IVLCVout.OnNewVideoLayoutListener {
    private static final String TAG = "LibVLC/VideoHelper";

    private MediaPlayer.ScaleType mCurrentScaleType = MediaPlayer.ScaleType.SURFACE_BEST_FIT;

    private int mVideoHeight = 0;
    private int mVideoWidth = 0;
    private int mVideoVisibleHeight = 0;
    private int mVideoVisibleWidth = 0;
    private int mVideoSarNum = 0;
    private int mVideoSarDen = 0;

    private FrameLayout mVideoSurfaceFrame;
    private SurfaceView mVideoSurface = null;
    private SurfaceView mSubtitlesSurface = null;
    private TextureView mVideoTexture = null;

    private final Handler mHandler = new Handler();
    private View.OnLayoutChangeListener mOnLayoutChangeListener = null;
    private DisplayManager mDisplayManager;

    private org.videolan.libvlc.MediaPlayer mMediaPlayer;

    VideoHelper(MediaPlayer player, VLCVideoLayout surfaceFrame, DisplayManager dm, boolean subtitles, boolean textureView) {
        init(player, surfaceFrame, dm, subtitles, !textureView);
    }

    private void init(MediaPlayer player, VLCVideoLayout surfaceFrame, DisplayManager dm, boolean subtitles, boolean useSurfaceView) {
        mMediaPlayer = player;
        mDisplayManager = dm;
        final boolean isPrimary = mDisplayManager == null || mDisplayManager.isPrimary();
        if (isPrimary) {
            mVideoSurfaceFrame = surfaceFrame.findViewById(R.id.player_surface_frame);
            if (useSurfaceView) {
                ViewStub stub = mVideoSurfaceFrame.findViewById(R.id.surface_stub);
                mVideoSurface = stub != null ? (SurfaceView) stub.inflate() : (SurfaceView) mVideoSurfaceFrame.findViewById(R.id.surface_video);
                if (subtitles) {
                    stub = mVideoSurfaceFrame.findViewById(R.id.subtitles_surface_stub);
                    mSubtitlesSurface = stub != null ? (SurfaceView) stub.inflate() : (SurfaceView) mVideoSurfaceFrame.findViewById(R.id.surface_subtitles);
                    mSubtitlesSurface.setZOrderMediaOverlay(true);
                    mSubtitlesSurface.getHolder().setFormat(PixelFormat.TRANSLUCENT);
                }
            } else {
                final ViewStub stub = mVideoSurfaceFrame.findViewById(R.id.texture_stub);
                mVideoTexture = stub != null ? (TextureView) stub.inflate() : (TextureView) mVideoSurfaceFrame.findViewById(R.id.texture_video);;
            }
        } else if (mDisplayManager.getPresentation() != null){
            mVideoSurfaceFrame = mDisplayManager.getPresentation().getSurfaceFrame();
            mVideoSurface = mDisplayManager.getPresentation().getSurfaceView();
            mSubtitlesSurface = mDisplayManager.getPresentation().getSubtitlesSurfaceView();
        }
    }

    void release() {
        if (mMediaPlayer.getVLCVout().areViewsAttached()) detachViews();
        mMediaPlayer = null;
        mVideoSurfaceFrame = null;
        mHandler.removeCallbacks(null);
        mVideoSurface = null;
        mSubtitlesSurface = null;
        mVideoTexture = null;
    }

    void attachViews() {
        if (mVideoSurface == null && mVideoTexture == null) return;
        final IVLCVout vlcVout = mMediaPlayer.getVLCVout();
        if (mVideoSurface != null) {
            vlcVout.setVideoView(mVideoSurface);
            if (mSubtitlesSurface != null)
                vlcVout.setSubtitlesView(mSubtitlesSurface);
        } else if (mVideoTexture != null)
            vlcVout.setVideoView(mVideoTexture);
        else return;
        vlcVout.attachViews(this);

        if (mOnLayoutChangeListener == null) {
            mOnLayoutChangeListener = new View.OnLayoutChangeListener() {
                private final Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        if (mVideoSurfaceFrame != null && mOnLayoutChangeListener != null) updateVideoSurfaces();
                    }
                };
                @Override
                public void onLayoutChange(View v, int left, int top, int right,
                                           int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                        mHandler.removeCallbacks(runnable);
                        mHandler.post(runnable);
                    }
                }
            };
        }
        mVideoSurfaceFrame.addOnLayoutChangeListener(mOnLayoutChangeListener);
        mMediaPlayer.setVideoTrackEnabled(true);
    }

    void detachViews() {
        if (mOnLayoutChangeListener != null && mVideoSurfaceFrame != null) {
            mVideoSurfaceFrame.removeOnLayoutChangeListener(mOnLayoutChangeListener);
            mOnLayoutChangeListener = null;
        }
        mMediaPlayer.setVideoTrackEnabled(false);
        mMediaPlayer.getVLCVout().detachViews();
    }

    private void changeMediaPlayerLayout(int displayW, int displayH) {
        if (mMediaPlayer.isReleased()) return;
        /* Change the video placement using the MediaPlayer API */
        switch (mCurrentScaleType) {
            case SURFACE_BEST_FIT:
                mMediaPlayer.setAspectRatio(null);
                mMediaPlayer.setScale(0);
                break;
            case SURFACE_FIT_SCREEN:
            case SURFACE_FILL: {
                Media.VideoTrack vtrack = mMediaPlayer.getCurrentVideoTrack();
                if (vtrack == null)
                    return;
                final boolean videoSwapped = vtrack.orientation == Media.VideoTrack.Orientation.LeftBottom
                        || vtrack.orientation == Media.VideoTrack.Orientation.RightTop;
                if (mCurrentScaleType == MediaPlayer.ScaleType.SURFACE_FIT_SCREEN) {
                    int videoW = vtrack.width;
                    int videoH = vtrack.height;

                    if (videoSwapped) {
                        int swap = videoW;
                        videoW = videoH;
                        videoH = swap;
                    }
                    if (vtrack.sarNum != vtrack.sarDen)
                        videoW = videoW * vtrack.sarNum / vtrack.sarDen;

                    float ar = videoW / (float) videoH;
                    float dar = displayW / (float) displayH;

                    float scale;
                    if (dar >= ar)
                        scale = displayW / (float) videoW; /* horizontal */
                    else
                        scale = displayH / (float) videoH; /* vertical */
                    mMediaPlayer.setScale(scale);
                    mMediaPlayer.setAspectRatio(null);
                } else {
                    mMediaPlayer.setScale(0);
                    mMediaPlayer.setAspectRatio(!videoSwapped ? ""+displayW+":"+displayH
                            : ""+displayH+":"+displayW);
                }
                break;
            }
            case SURFACE_16_9:
                mMediaPlayer.setAspectRatio("16:9");
                mMediaPlayer.setScale(0);
                break;
            case SURFACE_4_3:
                mMediaPlayer.setAspectRatio("4:3");
                mMediaPlayer.setScale(0);
                break;
            case SURFACE_ORIGINAL:
                mMediaPlayer.setAspectRatio(null);
                mMediaPlayer.setScale(1);
                break;
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    void updateVideoSurfaces() {
        if (mMediaPlayer == null || mMediaPlayer.isReleased()) return;
        final boolean isPrimary = mDisplayManager == null || mDisplayManager.isPrimary();
        final Activity activity = isPrimary && mVideoSurfaceFrame.getContext() instanceof Activity ? (Activity) mVideoSurfaceFrame.getContext() : null;

        int sw;
        int sh;

        // get screen size
        if (activity != null) {
            sw = activity.getWindow().getDecorView().getWidth();
            sh = activity.getWindow().getDecorView().getHeight();
        } else if (mDisplayManager != null && mDisplayManager.getPresentation() != null && mDisplayManager.getPresentation().getWindow() != null) {
            sw = mDisplayManager.getPresentation().getWindow().getDecorView().getWidth();
            sh = mDisplayManager.getPresentation().getWindow().getDecorView().getHeight();
        } else return;

        // sanity check
        if (sw * sh == 0) {
            Log.e(TAG, "Invalid surface size");
            return;
        }

        mMediaPlayer.getVLCVout().setWindowSize(sw, sh);

        ViewGroup.LayoutParams lp = mVideoSurface.getLayoutParams();
        if (mVideoWidth * mVideoHeight == 0 || (AndroidUtil.isNougatOrLater && activity != null && activity.isInPictureInPictureMode())) {
            /* Case of OpenGL vouts: handles the placement of the video using MediaPlayer API */
            lp.width  = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mVideoSurface.setLayoutParams(lp);
            if (mSubtitlesSurface != null)
                mSubtitlesSurface.setLayoutParams(lp);
            lp = mVideoSurfaceFrame.getLayoutParams();
            lp.width  = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mVideoSurfaceFrame.setLayoutParams(lp);
            if (mVideoWidth * mVideoHeight == 0) changeMediaPlayerLayout(sw, sh);
            return;
        }

        if (lp.width == lp.height && lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
            /* We handle the placement of the video using Android View LayoutParams */
            mMediaPlayer.setAspectRatio(null);
            mMediaPlayer.setScale(0);
        }

        double dw = sw, dh = sh;
        final boolean isPortrait = isPrimary && mVideoSurfaceFrame.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        if (sw > sh && isPortrait || sw < sh && !isPortrait) {
            dw = sh;
            dh = sw;
        }

        // compute the aspect ratio
        double ar, vw;
        if (mVideoSarDen == mVideoSarNum) {
            /* No indication about the density, assuming 1:1 */
            vw = mVideoVisibleWidth;
            ar = (double)mVideoVisibleWidth / (double)mVideoVisibleHeight;
        } else {
            /* Use the specified aspect ratio */
            vw = mVideoVisibleWidth * (double)mVideoSarNum / mVideoSarDen;
            ar = vw / mVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double dar = dw / dh;

        switch (mCurrentScaleType) {
            case SURFACE_BEST_FIT:
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_FIT_SCREEN:
                if (dar >= ar)
                    dh = dw / ar; /* horizontal */
                else
                    dw = dh * ar; /* vertical */
                break;
            case SURFACE_FILL:
                break;
            case SURFACE_16_9:
                ar = 16.0 / 9.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_4_3:
                ar = 4.0 / 3.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_ORIGINAL:
                dh = mVideoVisibleHeight;
                dw = vw;
                break;
        }

        // set display size
        lp.width  = (int) Math.ceil(dw * mVideoWidth / mVideoVisibleWidth);
        lp.height = (int) Math.ceil(dh * mVideoHeight / mVideoVisibleHeight);
        mVideoSurface.setLayoutParams(lp);
        if (mSubtitlesSurface != null) mSubtitlesSurface.setLayoutParams(lp);

        // set frame size (crop if necessary)
        lp = mVideoSurfaceFrame.getLayoutParams();
        lp.width = (int) Math.floor(dw);
        lp.height = (int) Math.floor(dh);
        mVideoSurfaceFrame.setLayoutParams(lp);

        mVideoSurface.invalidate();
        if (mSubtitlesSurface != null) mSubtitlesSurface.invalidate();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        mVideoWidth = width;
        mVideoHeight = height;
        mVideoVisibleWidth = visibleWidth;
        mVideoVisibleHeight = visibleHeight;
        mVideoSarNum = sarNum;
        mVideoSarDen = sarDen;
        updateVideoSurfaces();
    }

    void setVideoScale(MediaPlayer.ScaleType type) {
        mCurrentScaleType = type;
        updateVideoSurfaces();
    }

    MediaPlayer.ScaleType getVideoScale() {
        return mCurrentScaleType;
    }
}
