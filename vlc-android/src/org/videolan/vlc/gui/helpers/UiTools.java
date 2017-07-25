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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.databinding.BindingAdapter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.BaseAudioBrowser;
import org.videolan.vlc.gui.browser.SortableFragment;
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog;
import org.videolan.vlc.util.MediaLibraryItemComparator;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class UiTools {

    public static final int ITEM_FOCUS_ON = ContextCompat.getColor(VLCApplication.getAppContext(), R.color.orange500);
    public static final int ITEM_FOCUS_OFF = ContextCompat.getColor(VLCApplication.getAppContext(), R.color.transparent);
    public static final int ITEM_SELECTION_ON = ContextCompat.getColor(VLCApplication.getAppContext(), R.color.orange200transparent);
    public static final int ITEM_BG_TRANSPARENT = ContextCompat.getColor(VLCApplication.getAppContext(), R.color.transparent);

    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    public static final int DELETE_DURATION = 3000;

    /** Print an on-screen message to alert the user */
    public static void snacker(@NonNull View view, @NonNull int stringId) {
        Snackbar.make(view, stringId, Snackbar.LENGTH_SHORT).show();
    }

    /** Print an on-screen message to alert the user */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void snacker(@NonNull View view, @NonNull String message) {
        Snackbar snack = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
        if (AndroidUtil.isLolliPopOrLater)
            snack.getView().setElevation(view.getResources().getDimensionPixelSize(R.dimen.audio_player_elevation));
        snack.show();
    }

    /** Print an on-screen message to alert the user, with undo action */
    public static void snackerWithCancel(@NonNull View view, @NonNull String message, @NonNull final Runnable action) {
        snackerWithCancel(view, message, action, null);
    }

    /** Print an on-screen message to alert the user, with undo action */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void snackerWithCancel(@NonNull View view, @NonNull String message, @Nullable final Runnable action, @Nullable final Runnable cancelAction) {
        Snackbar snack = Snackbar.make(view, message, DELETE_DURATION)
                .setAction(android.R.string.cancel, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (action != null)
                            sHandler.removeCallbacks(action);
                        if (cancelAction != null)
                            cancelAction.run();
                    }
                });
        if (AndroidUtil.isLolliPopOrLater)
            snack.getView().setElevation(view.getResources().getDimensionPixelSize(R.dimen.audio_player_elevation));
        snack.show();
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
    @BindingAdapter({"alignMode"})
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

    public static void setViewVisibility(View v, int visibility) {
        if (v != null)
            v.setVisibility(visibility);
    }

    public static boolean isBlackThemeEnabled() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());
        return pref.getBoolean("enable_black_theme", false);
    }

    public static void fillAboutView(View v) {
        final TextView link = v.findViewById(R.id.main_link);
        link.setText(Html.fromHtml(VLCApplication.getAppResources().getString(R.string.about_link)));

        final String revision = VLCApplication.getAppResources().getString(R.string.build_revision)+" VLC: "+VLCApplication.getAppResources().getString(R.string.build_vlc_revision);
        final String builddate = VLCApplication.getAppResources().getString(R.string.build_time);
        final String builder = VLCApplication.getAppResources().getString(R.string.build_host);

        final TextView compiled = v.findViewById(R.id.main_compiled);
        compiled.setText(builder + " (" + builddate + ")");
        final TextView textview_rev = v.findViewById(R.id.main_revision);
        textview_rev.setText(VLCApplication.getAppResources().getString(R.string.revision) + " " + revision + " (" + builddate + ") " + BuildConfig.FLAVOR_abi);

        final ImageView logo = v.findViewById(R.id.logo);
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

    public static void addToPlaylist(FragmentActivity activity, List<MediaWrapper> list) {
        MediaWrapper[] trackList = new MediaWrapper[list.size()];
        list.toArray(trackList);
        addToPlaylist(activity, trackList);
    }

    public static void addToPlaylist(FragmentActivity activity, MediaWrapper[] tracks) {
        SavePlaylistDialog savePlaylistDialog = new SavePlaylistDialog();
        Bundle args = new Bundle();
        args.putParcelableArray(SavePlaylistDialog.KEY_NEW_TRACKS, tracks);
        savePlaylistDialog.setArguments(args);
        savePlaylistDialog.show(activity.getSupportFragmentManager(), "fragment_add_to_playlist");
    }

    public static void checkMainThread() {
        if (Looper.getMainLooper() != Looper.myLooper())
            throw new IllegalThreadStateException();
    }

    public static BitmapDrawable getDefaultCover(MediaLibraryItem item) {
        switch (item.getItemType()) {
            case MediaLibraryItem.TYPE_ARTIST:
                return AsyncImageLoader.DEFAULT_COVER_ARTIST_DRAWABLE;
            case MediaLibraryItem.TYPE_ALBUM:
                return AsyncImageLoader.DEFAULT_COVER_ALBUM_DRAWABLE;
            case MediaLibraryItem.TYPE_MEDIA:
                if (((MediaWrapper)item).getType() == MediaWrapper.TYPE_VIDEO)
                    return AsyncImageLoader.DEFAULT_COVER_VIDEO_DRAWABLE;
            default:
                return AsyncImageLoader.DEFAULT_COVER_AUDIO_DRAWABLE;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static Bitmap blurBitmap(Bitmap bitmap) {
        return blurBitmap(bitmap, 15.0f);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static Bitmap blurBitmap(Bitmap bitmap, float radius) {
        if (bitmap == null || bitmap.getConfig() == null)
            return null;

		//Let's create an empty bitmap with the same size of the bitmap we want to blur
		Bitmap outBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

		//Instantiate a new Renderscript
		RenderScript rs = RenderScript.create(VLCApplication.getAppContext());

		//Create an Intrinsic Blur Script using the Renderscript
		ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));


		//Create the Allocations (in/out) with the Renderscript and the in/out bitmaps
		Allocation allIn = Allocation.createFromBitmap(rs, bitmap);
		Allocation allOut = Allocation.createFromBitmap(rs, outBitmap);

		//Set the radius of the blur
		blurScript.setRadius(radius);

		//Perform the Renderscript
		blurScript.setInput(allIn);
		blurScript.forEach(allOut);

		//Copy the final bitmap created by the out Allocation to the outBitmap
		allOut.copyTo(outBitmap);

		//After finishing everything, we destroy the Renderscript.
		rs.destroy();

		return outBitmap;
	}

    public static void updateSortTitles(SortableFragment sortable, Menu menu) {
        MenuItem item = menu.findItem(R.id.ml_menu_sortby_name);
        if (item != null) {
            if (sortable.sortDirection(MediaLibraryItemComparator.SORT_BY_TITLE) == 1
                    || (sortable.getSortBy() == MediaLibraryItemComparator.SORT_DEFAULT
                    && sortable.getDefaultSort() == MediaLibraryItemComparator.SORT_BY_TITLE))
                item.setTitle(R.string.sortby_name_desc);
            else
                item.setTitle(R.string.sortby_name);
        }

        if (sortable instanceof BaseAudioBrowser && sortable.sortDirection(MediaLibraryItemComparator.SORT_DEFAULT) == 1) {
            int defaultSortby = ((BaseAudioBrowser)sortable).getCurrentAdapter().getDefaultSort();
            int defaultDirection = ((BaseAudioBrowser)sortable).getCurrentAdapter().getDefaultDirection();
            menu.findItem(R.id.ml_menu_sortby_length).setTitle(R.string.sortby_length);
            menu.findItem(R.id.ml_menu_sortby_number).setTitle(R.string.sortby_number);
            menu.findItem(R.id.ml_menu_sortby_artist_name).setTitle(R.string.sortby_artist_name);
            menu.findItem(R.id.ml_menu_sortby_name).setTitle(defaultSortby == MediaLibraryItemComparator.SORT_BY_TITLE && defaultDirection == 1
                    ? R.string.sortby_name_desc
                    : R.string.sortby_name);
            menu.findItem(R.id.ml_menu_sortby_date).setTitle(defaultSortby == MediaLibraryItemComparator.SORT_BY_DATE && defaultDirection == 1
                    ? R.string.sortby_date_desc
                    : R.string.sortby_date);
            menu.findItem(R.id.ml_menu_sortby_album_name).setTitle(defaultSortby == MediaLibraryItemComparator.SORT_BY_ALBUM && defaultDirection == 1
                    ? R.string.sortby_album_name_desc
                    : R.string.sortby_album_name);
            return;
        }
        item = menu.findItem(R.id.ml_menu_sortby_artist_name);
        if (item != null) {
            if (sortable.sortDirection(MediaLibraryItemComparator.SORT_BY_ARTIST) == 1)
                item.setTitle(R.string.sortby_artist_name_desc);
            else
                item.setTitle(R.string.sortby_artist_name);
        }
        item = menu.findItem(R.id.ml_menu_sortby_album_name);
        if (item != null) {
            if (sortable.sortDirection(MediaLibraryItemComparator.SORT_BY_ALBUM) == 1)
                item.setTitle(R.string.sortby_album_name_desc);
            else
                item.setTitle(R.string.sortby_album_name);
        }
        item = menu.findItem(R.id.ml_menu_sortby_length);
        if (item != null) {
            if (sortable.sortDirection(MediaLibraryItemComparator.SORT_BY_LENGTH) == 1)
                item.setTitle(R.string.sortby_length_desc);
            else
                item.setTitle(R.string.sortby_length);
        }
        item = menu.findItem(R.id.ml_menu_sortby_date);
        if (item != null) {
            if(sortable.sortDirection(MediaLibraryItemComparator.SORT_BY_DATE) == 1)
                item.setTitle(R.string.sortby_date_desc);
            else
                item.setTitle(R.string.sortby_date);
        }
        item = menu.findItem(R.id.ml_menu_sortby_number);
        if (item != null) {
            if (sortable.sortDirection(MediaLibraryItemComparator.SORT_BY_NUMBER) == 1)
                item.setTitle(R.string.sortby_number_desc);
            else
                item.setTitle(R.string.sortby_number);
        }
    }
}
