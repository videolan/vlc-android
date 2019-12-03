package org.videolan.libvlc.util;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.videolan.R;
import org.videolan.libvlc.MediaPlayer;

/**
 * VLC-ready layout which includes 2 {@link SurfaceView} (video+subtitles) and 1 {@link TextureView}
 * All these surfaces are stubs, only the relevant one(s) will be inflated
 * Use it preferably with {@link MediaPlayer}.attachViews()
 */
public class VLCVideoLayout extends FrameLayout {

    public VLCVideoLayout(@NonNull Context context) {
        super(context);
        setupLayout(context);
    }

    public VLCVideoLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setupLayout(context);
    }

    public VLCVideoLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupLayout(context);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public VLCVideoLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setupLayout(context);
    }

    private void setupLayout(@NonNull Context context) {
        inflate(context, R.layout.vlc_video_layout, this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setBackgroundResource(R.color.black);
        final ViewGroup.LayoutParams lp = getLayoutParams();
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        setLayoutParams(lp);
    }
}
