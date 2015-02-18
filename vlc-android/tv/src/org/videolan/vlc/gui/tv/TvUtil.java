/*****************************************************************************
 * TvUtil.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
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

import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.gui.network.NetworkFragment;
import org.videolan.vlc.gui.tv.browser.VerticalGridActivity;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.interfaces.IVideoBrowser;
import org.videolan.vlc.util.Strings;

import android.app.Activity;
import android.content.Intent;
import android.os.Parcelable;
import android.support.v17.leanback.widget.Row;

public class TvUtil {


    public static void openMedia(Activity activity, Object item , Row row){
        if (item instanceof MediaWrapper) {
            MediaWrapper mediaWrapper = (MediaWrapper) item;
            if (mediaWrapper.getType() == MediaWrapper.TYPE_VIDEO) {
                VideoPlayerActivity.start(activity, mediaWrapper.getLocation(), Strings.getMediaTitle(mediaWrapper));
            } else if (mediaWrapper.getType() == MediaWrapper.TYPE_AUDIO) {
                Intent intent = new Intent(activity,
                        DetailsActivity.class);
                // pass the item information
                intent.putExtra("item", (Parcelable) new MediaItemDetails(mediaWrapper.getTitle(), mediaWrapper.getArtist(), mediaWrapper.getAlbum(), mediaWrapper.getLocation()));
                activity.startActivity(intent);
            } else if (mediaWrapper.getType() == MediaWrapper.TYPE_DIR){
                Intent intent = new Intent(activity, VerticalGridActivity.class);
                intent.putExtra(MainTvActivity.BROWSER_TYPE, MainTvActivity.HEADER_NETWORK);
                intent.putExtra(NetworkFragment.KEY_MRL, mediaWrapper.getLocation());
                activity.startActivity(intent);
            }
        } else if (item instanceof CardPresenter.SimpleCard){
            Intent intent = new Intent(activity, VerticalGridActivity.class);
            intent.putExtra(MainTvActivity.BROWSER_TYPE, row.getId());
            activity.startActivity(intent);
        }
    }
}
