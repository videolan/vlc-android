/*****************************************************************************
 * TvUtil.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
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
package org.videolan.vlc.gui.tv;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.support.v17.leanback.widget.Row;

import org.videolan.vlc.gui.tv.browser.SortedBrowserFragment;
import org.videolan.vlc.gui.tv.browser.VerticalGridActivity;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;

public class TvUtil {


    public static void openMedia(Activity activity, Object item , Row row){
        if (item instanceof MediaWrapper) {
            MediaWrapper mediaWrapper = (MediaWrapper) item;
            if (mediaWrapper.getType() == MediaWrapper.TYPE_VIDEO) {
                VideoPlayerActivity.start(activity, mediaWrapper.getUri(), MediaUtils.getMediaTitle(mediaWrapper));
            } else if (mediaWrapper.getType() == MediaWrapper.TYPE_AUDIO) {
                Intent intent = new Intent(activity,
                        DetailsActivity.class);
                // pass the item information
                intent.putExtra("item", (Parcelable) new MediaItemDetails(mediaWrapper.getTitle(), mediaWrapper.getArtist(), mediaWrapper.getAlbum(), mediaWrapper.getLocation()));
                activity.startActivity(intent);
            } else if (mediaWrapper.getType() == MediaWrapper.TYPE_DIR){
                Intent intent = new Intent(activity, VerticalGridActivity.class);
                intent.putExtra(MainTvActivity.BROWSER_TYPE, MainTvActivity.HEADER_NETWORK);
//                intent.putExtra(SortedBrowserFragment.KEY_URI, mediaWrapper.getLocation());
                intent.setData(mediaWrapper.getUri());
                activity.startActivity(intent);
            }
        } else if (item instanceof CardPresenter.SimpleCard){
            Intent intent = new Intent(activity, VerticalGridActivity.class);
            intent.putExtra(MainTvActivity.BROWSER_TYPE, ((CardPresenter.SimpleCard) item).getId());
            intent.setData(((CardPresenter.SimpleCard) item).getUri());
            activity.startActivity(intent);
        }
    }

    public static void browseFolder(Activity activity, long type, Uri uri) {
                Intent intent = new Intent(activity, VerticalGridActivity.class);
                intent.putExtra(MainTvActivity.BROWSER_TYPE, type);
                intent.setData(uri);
                activity.startActivity(intent);
    }
}
