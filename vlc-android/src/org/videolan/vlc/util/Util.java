/*****************************************************************************
 * UiTools.java
 *****************************************************************************
 * Copyright © 2011-2017 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.util;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.widget.Toast;

import org.videolan.libvlc.Dialog;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.video.VideoPlayerActivity;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Util {
    public final static String TAG = "VLC/Util";

    public static String readAsset(String assetName, String defaultS) {
        InputStream is = null;
        BufferedReader r = null;
        try {
            is = VLCApplication.getAppResources().getAssets().open(assetName);
            r = new BufferedReader(new InputStreamReader(is, "UTF8"));
            StringBuilder sb = new StringBuilder();
            String line = r.readLine();
            if(line != null) {
                sb.append(line);
                line = r.readLine();
                while(line != null) {
                    sb.append('\n');
                    sb.append(line);
                    line = r.readLine();
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return defaultS;
        } finally {
            close(is);
            close(r);
        }
    }

    public static boolean close(Closeable closeable) {
        if (closeable != null)
            try {
                closeable.close();
                return true;
            } catch (IOException e) {}
        return false;
    }

    public static <T> boolean isArrayEmpty(@Nullable T[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isListEmpty(@Nullable Collection collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isCallable(Intent intent) {
        List<ResolveInfo> list = VLCApplication.getAppContext().getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public static void removeItemInArray(Object[] array, Object item, Object[] destArray) {
        int offset = 0, count = destArray.length;
        for (int i = 0; i<count; ++i) {
            if (array[i].equals(item))
                offset = 1;
            destArray[i] = array[i+offset];
        }
    }

    public static void removePositionInArray(Object[] array, int position, Object[] destArray) {
        int offset = 0, count = destArray.length;
        for (int i = 0; i<count; ++i) {
            if (i == position)
                ++offset;
            destArray[i] = array[i+offset];
        }
    }

    public static void addItemInArray(Object[] array, int position, Object item, Object[] destArray) {
        int offset = 0, count = destArray.length;
        for (int i = 0; i < count; ++i) {
            if (i == position) {
                ++offset;
                destArray[i] = item;
            } else
                destArray[i] = array[i-offset];
        }
    }

    public static boolean arrayContains(Object[] array, Object item) {
        if (Tools.isArrayEmpty(array))
            return false;
        for (Object obj : array)
            if (obj.equals(item))
                return true;
        return false;
    }

    public static <T extends MediaLibraryItem> List<MediaLibraryItem> arrayToMediaArrayList(T[] array) {
        List<MediaLibraryItem> list = new ArrayList<>(array.length);
        Collections.addAll(list, array);
        return list;
    }

    public static <T> List<T> arrayToArrayList(T[] array) {
        List<T> list = new ArrayList<>(array.length);
        Collections.addAll(list, array);
        return list;
    }

    public static <T> void insertOrUdpate(List<T> dataset, T[] items) {
        List<T> newItems = new ArrayList<>();
        outer:
        for (T newItem : items) {
            for (int i = 0; i < dataset.size(); ++i) {
                T oldItem = dataset.get(i);
                if (newItem.equals(oldItem)) {
                    //noinspection UnusedAssignment
                    dataset.set(i, newItem);
                    continue outer;
                }
            }
            newItems.add(newItem);
        }
        dataset.addAll(newItems);
    }

    @NonNull
    public static String getMediaDescription(String artist, String album) {
        boolean hasArtist = !TextUtils.isEmpty(artist);
        boolean hasAlbum = !TextUtils.isEmpty(album);
        if (!hasAlbum && !hasArtist) return "";
        StringBuilder contentBuilder = new StringBuilder(hasArtist ? artist : "");
        if (hasArtist && hasAlbum) contentBuilder.append(" - ");
        if (hasAlbum) contentBuilder.append(album);
        return contentBuilder.toString();
    }

    public static boolean byPassChromecastDialog(Dialog.QuestionDialog dialog) {
        if ("Insecure site".equals(dialog.getTitle())) {
            if ("View certificate".equals(dialog.getAction1Text())) dialog.postAction(1);
            else if ("Accept permanently".equals(dialog.getAction2Text())) dialog.postAction(2);
            dialog.dismiss();
            return true;
        } else if ("Performance warning".equals(dialog.getTitle())) {
            Toast.makeText(VLCApplication.getAppContext(), R.string.cast_performance_warning, Toast.LENGTH_LONG).show();
            dialog.postAction(1);
            dialog.dismiss();
            return true;
        }
        return false;
    }

    public static void checkCpuCompatibility(final Context ctx) {
        WorkersKt.runBackground(new Runnable() {
            @Override
            public void run() {
                if (!VLCInstance.testCompatibleCPU(ctx)) WorkersKt.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ctx instanceof Service) ((Service) ctx).stopSelf();
                        else if (ctx instanceof VideoPlayerActivity) ((VideoPlayerActivity) ctx).exit(Activity.RESULT_CANCELED);
                        else if (ctx instanceof Activity) ((Activity) ctx).finish();
                    }
                });
            }
        });
    }
}
