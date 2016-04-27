/*
 * *************************************************************************
 *  Util.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.databinding.BindingAdapter;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;

import java.util.concurrent.atomic.AtomicInteger;

public class UiTools {

    public static final int ITEM_FOCUS_ON = ContextCompat.getColor(VLCApplication.getAppContext(), R.color.orange800);
    public static final int ITEM_FOCUS_OFF = ContextCompat.getColor(VLCApplication.getAppContext(), R.color.transparent);

    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    public static final int DELETE_DURATION = 3000;

    /** Print an on-screen message to alert the user */
    public static void snacker(@NonNull View view, @NonNull int stringId) {
        Snackbar.make(view, stringId, Snackbar.LENGTH_SHORT).show();
    }

    /** Print an on-screen message to alert the user */
    public static void snacker(@NonNull View view, @NonNull String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
    }

    /** Print an on-screen message to alert the user, with undo action */
    public static void snackerWithCancel(@NonNull View view, @NonNull String message, @NonNull final Runnable action) {
        snackerWithCancel(view, message, action, null);
    }

    /** Print an on-screen message to alert the user, with undo action */
    public static void snackerWithCancel(@NonNull View view, @NonNull String message, @NonNull final Runnable action, @Nullable final Runnable cancelAction) {
        Snackbar.make(view, message, DELETE_DURATION)
                .setAction(android.R.string.cancel, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (action != null)
                            sHandler.removeCallbacks(action);
                        if (cancelAction != null)
                            cancelAction.run();
                    }
                })
                .show();
        if (action != null)
            sHandler.postDelayed(action, DELETE_DURATION);
    }

    public static int convertPxToDp(int px) {
        DisplayMetrics metrics = VLCApplication.getAppResources().getDisplayMetrics();
        float logicalDensity = metrics.density;
        int dp = Math.round(px / logicalDensity);
        return dp;
    }

    public static int convertDpToPx(int dp) {
        return Math.round(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                        VLCApplication.getAppResources().getDisplayMetrics())
        );
    }

    /**
     * Get a resource id from an attribute id.
     * @param context
     * @param attrId
     * @return the resource id
     */
    public static int getResourceFromAttribute(Context context, int attrId) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[] {attrId});
        int resId = a.getResourceId(0, 0);
        a.recycle();
        return resId;
    }

    /**
     * Get a color id from an attribute id.
     * @param context
     * @param attrId
     * @return the color id
     */
    public static int getColorFromAttribute(Context context, int attrId) {
        return VLCApplication.getAppResources().getColor(getResourceFromAttribute(context, attrId));
    }
    /**
     * Set the alignment mode of the specified TextView with the desired align
     * mode from preferences.
     *
     * See @array/audio_title_alignment_values
     *
     * @param alignMode Align mode as read from preferences
     * @param t Reference to the textview
     */
    @BindingAdapter({"bind:alignMode"})
    public static void setAlignModeByPref(TextView t, int alignMode) {
        switch (alignMode) {
            case 0:
                break;
            case 1:
                t.setEllipsize(TextUtils.TruncateAt.END);
                break;
            case 2:
                t.setEllipsize(TextUtils.TruncateAt.START);
                break;
            case 3:
                t.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                t.setMarqueeRepeatLimit(-1);
                t.setSelected(true);
                break;
        }
    }

    /**
     * Generate a value suitable for use in {@link #setId(int)}.
     * This value will not collide with ID values generated at build time by aapt for R.id.
     *
     * @return a generated ID value
     */
    public static int generateViewId() {
        for (;;) {
            final int result = sNextGeneratedId.get();
            // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
            int newValue = result + 1;
            if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
            if (sNextGeneratedId.compareAndSet(result, newValue)) {
                return result;
            }
        }
    }

    public static boolean isBlackThemeEnabled() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());
        return pref.getBoolean("enable_black_theme", false);
    }

    public static void fillAboutView(View v) {
        TextView link = (TextView) v.findViewById(R.id.main_link);
        link.setText(Html.fromHtml(VLCApplication.getAppResources().getString(R.string.about_link)));

        String revision = VLCApplication.getAppResources().getString(R.string.build_revision)+" VLC: "+VLCApplication.getAppResources().getString(R.string.build_vlc_revision);
        String builddate = VLCApplication.getAppResources().getString(R.string.build_time);
        String builder = VLCApplication.getAppResources().getString(R.string.build_host);

        TextView compiled = (TextView) v.findViewById(R.id.main_compiled);
        compiled.setText(builder + " (" + builddate + ")");
        TextView textview_rev = (TextView) v.findViewById(R.id.main_revision);
        textview_rev.setText(VLCApplication.getAppResources().getString(R.string.revision) + " " + revision + " (" + builddate + ") " + BuildConfig.FLAVOR_abi);

        final ImageView logo = (ImageView) v.findViewById(R.id.logo);
        logo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimationSet anim = new AnimationSet(true);
                RotateAnimation rotate = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                rotate.setDuration(800);
                rotate.setInterpolator(new DecelerateInterpolator());
                anim.addAnimation(rotate);
                logo.startAnimation(anim);
            }
        });
    }

    public static void setKeyboardVisibility(final View v, final boolean show) {
        final InputMethodManager inputMethodManager = (InputMethodManager) VLCApplication.getAppContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                if (show)
                    inputMethodManager.showSoftInput(v, InputMethodManager.SHOW_FORCED);
                else
                    inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        });
    }
}
