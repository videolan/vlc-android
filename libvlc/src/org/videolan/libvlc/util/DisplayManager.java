package org.videolan.libvlc.util;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PixelFormat;
import android.media.MediaRouter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.videolan.BuildConfig;
import org.videolan.R;
import org.videolan.libvlc.RendererItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

public class DisplayManager {
    private static final String TAG = "VLC/DisplayManager";

    private Activity mActivity;
    private LiveData<RendererItem> mSelectedRenderer;
    private RendererItem mRendererItem;
    private boolean mTextureView;

    private MediaRouter mMediaRouter;
    private MediaRouter.SimpleCallback mMediaRouterCallback;

    private SecondaryDisplay mPresentation;
    private DisplayType mDisplayType;
    private int mPresentationId = -1;

    public DisplayManager(@NonNull Activity activity, @Nullable LiveData<RendererItem> selectedRender, boolean textureView, boolean cloneMode, boolean benchmark) {
        mActivity = activity;
        mSelectedRenderer = selectedRender;
        mMediaRouter = (MediaRouter) activity.getApplicationContext().getSystemService(Context.MEDIA_ROUTER_SERVICE);
        mTextureView = textureView;
        mPresentation = !(cloneMode || benchmark) ? createPresentation() : null;
        if (mSelectedRenderer != null) {
            mSelectedRenderer.observeForever(mRendererObs);
            mRendererItem = mSelectedRenderer.getValue();
        }
        mDisplayType = benchmark ? DisplayType.PRIMARY : getCurrentType();
    }

    public boolean isPrimary() {
        return mDisplayType == DisplayType.PRIMARY;
    }

    public boolean isSecondary() {
        return mDisplayType == DisplayType.PRESENTATION;
    }

    public boolean isOnRenderer() {
        return mDisplayType == DisplayType.RENDERER;
    }

    private Observer<RendererItem> mRendererObs = new Observer<RendererItem>() {
        @Override
        public void onChanged(RendererItem rendererItem) {
            mRendererItem = rendererItem;
            updateDisplayType();
        }
    };

    private DialogInterface.OnDismissListener mOnDismissListener = new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
            if (dialog == mPresentation) {
                if (BuildConfig.DEBUG) Log.i(TAG, "Presentation was dismissed.");
                mPresentation = null;
                mPresentationId = -1;
            }
        }
    };

    public void release() {
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
        }
        if (mSelectedRenderer != null) mSelectedRenderer.removeObserver(mRendererObs);
    }

    private void updateDisplayType() {
        if (mDisplayType != getCurrentType()) new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mActivity.recreate();
            }
        }, 100L);
    }

    private DisplayType getCurrentType() {
        if (mPresentationId != -1) return DisplayType.PRESENTATION;
        if (mRendererItem != null) return DisplayType.RENDERER;
        return DisplayType.PRIMARY;
    }

    @Nullable
    public SecondaryDisplay getPresentation() {
        return mPresentation;
    }

    @Nullable
    public DisplayType getDisplayType() {
        return mDisplayType;
    }

    private SecondaryDisplay createPresentation() {
        if (mMediaRouter == null) return null;
        final MediaRouter.RouteInfo route = mMediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO);
        final Display presentationDisplay = route != null ? route.getPresentationDisplay() : null;
        if (presentationDisplay != null) {
            if (BuildConfig.DEBUG) Log.i(TAG, "Showing presentation on display: "+presentationDisplay);
            final SecondaryDisplay presentation = new SecondaryDisplay(mActivity, presentationDisplay);
            presentation.setOnDismissListener(mOnDismissListener);
            try {
                presentation.show();
                mPresentationId = presentationDisplay.getDisplayId();
                return presentation;
            } catch (WindowManager.InvalidDisplayException ex) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Couldn't show presentation!  Display was removed in " + "the meantime.", ex);
                mPresentationId = -1;
            }
        } else if (BuildConfig.DEBUG) Log.i(TAG, "No secondary display detected");
        return null;
    }

    public boolean setMediaRouterCallback() {
        if (mMediaRouter == null || mMediaRouterCallback != null) return false;
        mMediaRouterCallback = new MediaRouter.SimpleCallback() {
            @Override
            public void onRoutePresentationDisplayChanged(MediaRouter router, MediaRouter.RouteInfo info) {
                if (BuildConfig.DEBUG) Log.d(TAG, "onRoutePresentationDisplayChanged: info="+info);
                final int newDisplayId = (info.getPresentationDisplay() != null) ? info.getPresentationDisplay().getDisplayId() : -1;
                if (newDisplayId == mPresentationId) return;
                mPresentationId = newDisplayId;
                if (newDisplayId == -1) removePresentation();
                else updateDisplayType();
            }
        };
        mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_LIVE_VIDEO, mMediaRouterCallback);
        return true;
    }

    public void removeMediaRouterCallback() {
        if (mMediaRouter != null) mMediaRouter.removeCallback(mMediaRouterCallback);
        mMediaRouterCallback = null;
    }

    private void removePresentation() {
        if (mMediaRouter == null) return;
        // Dismiss the current presentation if the display has changed.
        if (BuildConfig.DEBUG) Log.i(TAG, "Dismissing presentation because the current route no longer " + "has a presentation display.");
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
        }
        updateDisplayType();
    }

    public class SecondaryDisplay extends Presentation {
        public static final String TAG = "VLC/SecondaryDisplay";

        private FrameLayout mSurfaceFrame;
        private SurfaceView mSurfaceView;
        private SurfaceView mSubtitlesSurfaceView;

        public SecondaryDisplay(Context outerContext, Display display) {
            super(outerContext, display);
        }

        public SecondaryDisplay(Context outerContext, Display display, int theme) {
            super(outerContext, display, theme);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.player_remote);
            mSurfaceFrame = findViewById(R.id.remote_player_surface_frame);
            mSurfaceView = mSurfaceFrame.findViewById(R.id.remote_player_surface);
            mSubtitlesSurfaceView = mSurfaceFrame.findViewById(R.id.remote_subtitles_surface);
            mSubtitlesSurfaceView.setZOrderMediaOverlay(true);
            mSubtitlesSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
            if (BuildConfig.DEBUG) Log.i(TAG, "Secondary display created");
        }

        public FrameLayout getSurfaceFrame() {
            return mSurfaceFrame;
        }

        public SurfaceView getSurfaceView() {
            return mSurfaceView;
        }

        public SurfaceView getSubtitlesSurfaceView() {
            return mSubtitlesSurfaceView;
        }
    }

    public enum DisplayType { PRIMARY, PRESENTATION, RENDERER }
}
