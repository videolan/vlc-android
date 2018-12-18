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
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import androidx.databinding.BindingAdapter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.FragmentActivity;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;
import android.text.TextUtils;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.LocalePair;
import org.videolan.vlc.util.Settings;
import org.videolan.vlc.viewmodels.SortableModel;

import java.util.List;
import java.util.Locale;

public class UiTools {
    private static final String TAG = "VLC/UiTools";

    public static class Resources {
        private static final Bitmap DEFAULT_COVER_VIDEO = BitmapCache.getFromResource(VLCApplication.getAppResources(), R.drawable.ic_no_thumbnail_1610);
        private static final Bitmap DEFAULT_COVER_AUDIO = BitmapCache.getFromResource(VLCApplication.getAppResources(), R.drawable.ic_no_song);
        public static final int ITEM_FOCUS_OFF = ContextCompat.getColor(VLCApplication.getAppContext(), R.color.transparent);
        public static final int ITEM_FOCUS_ON = ContextCompat.getColor(VLCApplication.getAppContext(), R.color.orange500transparent);
        public static final int ITEM_SELECTION_ON = ContextCompat.getColor(VLCApplication.getAppContext(), R.color.orange200transparent);
        public static final BitmapDrawable DEFAULT_COVER_ARTIST_DRAWABLE = new BitmapDrawable(VLCApplication.getAppResources(), BitmapCache.getFromResource(VLCApplication.getAppResources(), R.drawable.ic_no_artist));
        public static final BitmapDrawable DEFAULT_COVER_ALBUM_DRAWABLE = new BitmapDrawable(VLCApplication.getAppResources(), BitmapCache.getFromResource(VLCApplication.getAppResources(), R.drawable.ic_no_album));
        public static final BitmapDrawable DEFAULT_COVER_VIDEO_DRAWABLE = new BitmapDrawable(VLCApplication.getAppResources(), DEFAULT_COVER_VIDEO);
        public static final BitmapDrawable DEFAULT_COVER_AUDIO_DRAWABLE = new BitmapDrawable(VLCApplication.getAppResources(), DEFAULT_COVER_AUDIO);
    }

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
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void snackerConfirm(@NonNull View view, @NonNull String message, @NonNull final Runnable action) {
        final Snackbar snack = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                .setAction(android.R.string.ok, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        action.run();
                    }
                });
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
        return context.getResources().getColor(getResourceFromAttribute(context, attrId));
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

    public static void setViewVisibility(View v, int visibility) {
        if (v != null) v.setVisibility(visibility);
    }

    public static void setViewOnClickListener(View v, View.OnClickListener ocl) {
        if (v != null) v.setOnClickListener(ocl);
    }

    public static boolean isBlackThemeEnabled(Context context) {
        final SharedPreferences pref = Settings.INSTANCE.getInstance(context);
        return pref.getBoolean("enable_black_theme", false);
    }

    public static void fillAboutView(View v) {
        final TextView link = v.findViewById(R.id.main_link);
        link.setText(Html.fromHtml(v.getContext().getString(R.string.about_link)));

        final String revision = v.getContext().getString(R.string.build_revision)+" VLC: "+v.getContext().getString(R.string.build_vlc_revision);
        final String builddate = v.getContext().getString(R.string.build_time);
        final String builder = v.getContext().getString(R.string.build_host);

        final TextView compiled = v.findViewById(R.id.main_compiled);
        compiled.setText(builder + " (" + builddate + ")");
        final TextView textview_rev = v.findViewById(R.id.main_revision);
        textview_rev.setText(v.getContext().getString(R.string.revision) + " " + revision + " (" + builddate + ") ");

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
        if (v == null) return;
        final InputMethodManager inputMethodManager = (InputMethodManager) v.getContext().getApplicationContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                if (show) inputMethodManager.showSoftInput(v, InputMethodManager.SHOW_FORCED);
                else inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        });
    }

    public static void savePlaylist(FragmentActivity activity, List<MediaWrapper> list) {
        MediaWrapper[] trackList = new MediaWrapper[list.size()];
        list.toArray(trackList);
        addToPlaylist(activity, trackList, SavePlaylistDialog.KEY_TRACKS);
    }

    public static void addToPlaylist(FragmentActivity activity, List<MediaWrapper> list) {
        MediaWrapper[] trackList = new MediaWrapper[list.size()];
        list.toArray(trackList);
        addToPlaylist(activity, trackList, SavePlaylistDialog.KEY_NEW_TRACKS);
    }

    public static void addToPlaylist(FragmentActivity activity, MediaWrapper[] tracks, String key) {
        SavePlaylistDialog savePlaylistDialog = new SavePlaylistDialog();
        final Bundle args = new Bundle();
        args.putParcelableArray(key, tracks);
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
                return Resources.DEFAULT_COVER_ARTIST_DRAWABLE;
            case MediaLibraryItem.TYPE_ALBUM:
                return Resources.DEFAULT_COVER_ALBUM_DRAWABLE;
            case MediaLibraryItem.TYPE_MEDIA:
                if (((MediaWrapper)item).getType() == MediaWrapper.TYPE_VIDEO)
                    return Resources.DEFAULT_COVER_VIDEO_DRAWABLE;
            default:
                return Resources.DEFAULT_COVER_AUDIO_DRAWABLE;
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

    public static void updateSortTitles(MediaBrowserFragment sortable) {
        final Menu menu = sortable.getMenu();
        if (menu == null) return;
        final SortableModel model = sortable.getViewModel();
        final int sort = model.getSort();
        final boolean desc = model.getDesc();
        MenuItem item = menu.findItem(R.id.ml_menu_sortby_name);
        if (item != null) item.setTitle(sort == Medialibrary.SORT_ALPHA && !desc ? R.string.sortby_name_desc : R.string.sortby_name);
        item = menu.findItem(R.id.ml_menu_sortby_filename);
        if (item != null) item.setTitle(sort == Medialibrary.SORT_FILENAME && !desc ? R.string.sortby_filename_desc : R.string.sortby_filename);
        item = menu.findItem(R.id.ml_menu_sortby_artist_name);
        if (item != null) item.setTitle(sort == Medialibrary.SORT_ARTIST && !desc ? R.string.sortby_artist_name_desc : R.string.sortby_artist_name);
        item = menu.findItem(R.id.ml_menu_sortby_album_name);
        if (item != null) item.setTitle(sort == Medialibrary.SORT_ALBUM && !desc ? R.string.sortby_album_name_desc : R.string.sortby_album_name);
        item = menu.findItem(R.id.ml_menu_sortby_length);
        if (item != null) item.setTitle(sort == Medialibrary.SORT_DURATION && !desc ? R.string.sortby_length_desc : R.string.sortby_length);
        item = menu.findItem(R.id.ml_menu_sortby_date);
        if (item != null) item.setTitle(sort == Medialibrary.SORT_RELEASEDATE && !desc ? R.string.sortby_date_desc : R.string.sortby_date);
        item = menu.findItem(R.id.ml_menu_sortby_last_modified);
        if (item != null) item.setTitle(sort == Medialibrary.SORT_RELEASEDATE && !desc ? R.string.sortby_last_modified_date_desc : R.string.sortby_last_modified_date);
//        item = menu.findItem(R.id.ml_menu_sortby_number); TODO sort by track number
//        if (item != null) item.setTitle(sort == Medialibrary.SORT_ && !desc ? R.string.sortby_number_desc : R.string.sortby_number);

    }

    public static void confirmExit(final Activity activity) {
        new AlertDialog.Builder(activity)
                .setMessage(R.string.exit_app_msg)
                .setTitle(R.string.exit_app)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                activity.finish();
            }
        })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        }).create().show();
    }

    public static void newStorageDetected(final Activity activity, final String path) {
        if (activity == null) return;
        final String uuid = FileUtils.getFileNameFromPath(path);
        final String deviceName = FileUtils.getStorageTag(uuid);
        final String message = String.format(activity.getString(R.string.ml_external_storage_msg), deviceName != null ? deviceName : uuid);
        final Intent serviceInent = new Intent(Constants.ACTION_DISCOVER_DEVICE, null, activity, MediaParsingService.class)
                .putExtra(Constants.EXTRA_PATH, path);
        if (activity instanceof AppCompatActivity) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setTitle(R.string.ml_external_storage_title)
                    .setCancelable(false)
                    .setMessage(message)
                    .setPositiveButton(R.string.ml_external_storage_accept, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (activity != null)
                                activity.startService(serviceInent);
                        }
                    })
                    .setNegativeButton(R.string.ml_external_storage_decline, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Settings.INSTANCE.getInstance(activity)
                                    .edit()
                                    .putBoolean("ignore_"+ uuid, true)
                                    .apply();
                            dialog.dismiss();
                        }
                    });
            builder.show();
        } else {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity)
                    .setTitle(R.string.ml_external_storage_title)
                    .setCancelable(false)
                .setMessage(message)
                .setPositiveButton(R.string.ml_external_storage_accept, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (activity != null)
                            activity.startService(serviceInent);
                    }
                })
                .setNegativeButton(R.string.ml_external_storage_decline, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Settings.INSTANCE.getInstance(activity)
                                .edit()
                                .putBoolean("ignore_"+ uuid, true)
                                .apply();
                        dialog.dismiss();
                    }
                });
            builder.show();
        }
    }

    public static void setOnDragListener(final Activity activity) {
        final View view = AndroidUtil.isNougatOrLater ? activity.getWindow().peekDecorView() : null;
        if (view != null) view.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return true;
                    case DragEvent.ACTION_DROP:
                        final ClipData clipData = event.getClipData();
                        if (clipData == null) return false;
                        final int itemsCount = clipData.getItemCount();
                        for (int i = 0; i < itemsCount; i++) {
                            final DragAndDropPermissions permissions = activity.requestDragAndDropPermissions(event);
                            if (permissions != null)  {
                                final ClipData.Item item = clipData.getItemAt(i);
                                if (item.getUri() != null) MediaUtils.INSTANCE.openUri(activity, item.getUri());
                                else if (item.getText() != null) {
                                    final Uri uri = Uri.parse(item.getText().toString());
                                    final MediaWrapper media = new MediaWrapper(uri);
                                    if (!"file".equals(uri.getScheme())) media.setType(MediaWrapper.TYPE_STREAM);
                                    MediaUtils.INSTANCE.openMedia(activity, media);
                                }
                                return true;
                            }
                        }
                        return false;
                    default:
                        return false;
                }
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static void setRotationAnimation(Activity activity) {
        if (!AndroidUtil.isJellyBeanMR2OrLater) return;
        final Window win = activity.getWindow();
        final WindowManager.LayoutParams winParams = win.getAttributes();
        winParams.rotationAnimation = AndroidUtil.isOOrLater ? WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS : WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
        win.setAttributes(winParams);
    }

    public static void setLocale(Context context) {
        // Are we using advanced debugging - locale?
        String p = VLCApplication.getLocale();
        if (!p.equals("")) {
            Locale locale;
            // workaround due to region code
            if (p.equals("zh-TW")) {
                locale = Locale.TRADITIONAL_CHINESE;
            } else if(p.startsWith("zh")) {
                locale = Locale.CHINA;
            } else if(p.equals("pt-BR")) {
                locale = new Locale("pt", "BR");
            } else if(p.equals("bn-IN") || p.startsWith("bn")) {
                locale = new Locale("bn", "IN");
            } else {
                /**
                 * Avoid a crash of
                 * java.lang.AssertionError: couldn't initialize LocaleData for locale
                 * if the user enters nonsensical region codes.
                 */
                if(p.contains("-")) p = p.substring(0, p.indexOf('-'));
                locale = new Locale(p);
            }
            Locale.setDefault(locale);
            final Configuration config = new Configuration();
            config.locale = locale;
            context.getResources().updateConfiguration(config,
                    context.getResources().getDisplayMetrics());
        }
    }

    public static void restartDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(context.getResources().getString(R.string.restart_vlc))
                .setMessage(context.getResources().getString(R.string.restart_message))
                .setPositiveButton(R.string.restart_message_OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                })
                .setNegativeButton(R.string.restart_message_Later, null)
                .create()
                .show();
    }

    public static LocalePair getLocalesUsedInProject(Context context) {
        final String[] localesEntryValues = context.getAssets().getLocales();
        final String[] localesEntry = new String[localesEntryValues.length];
        for (int i=0; i<localesEntryValues.length; i++) {
            localesEntry[i] = new Locale(localesEntryValues[i]).getDisplayLanguage(new Locale(localesEntryValues[i]));
        }
        return new LocalePair(localesEntry, localesEntryValues);
    }

    public static void deleteSubtitleDialog(Context context, final DialogInterface.OnClickListener positiveListener, final DialogInterface.OnClickListener negativeListener) {
        new AlertDialog.Builder(context)
                .setTitle(context.getResources().getString(R.string.delete_sub_title))
                .setMessage(context.getResources().getString(R.string.delete_sub_message))
                .setPositiveButton(R.string.delete_sub_yes, positiveListener)
                .setNegativeButton(R.string.delete_sub_no, negativeListener)
                .create()
                .show();
    }

}
