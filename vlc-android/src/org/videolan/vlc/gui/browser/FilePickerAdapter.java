/*
 * *************************************************************************
 *  FilePickerAdapter.java
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

package org.videolan.vlc.gui.browser;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import org.videolan.libvlc.Media;
import org.videolan.vlc.media.MediaWrapper;

public class FilePickerAdapter extends BaseBrowserAdapter {

    public FilePickerAdapter(BaseBrowserFragment fragment) {
        super(fragment);
    }

    public void addItem(Object media, boolean notify, boolean top){
        MediaWrapper mediaWrapper;
        if (media instanceof MediaWrapper)
            mediaWrapper = (MediaWrapper) media;
        else
            mediaWrapper = new MediaWrapper((Media)media);
        if (filter(mediaWrapper))
            super.addItem(mediaWrapper, notify, top);
    }

    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        final MediaViewHolder vh = (MediaViewHolder) holder;
        final MediaWrapper media = (MediaWrapper) getItem(position);
        vh.binding.setMedia(media);
        vh.binding.setHasContextMenu(false);
        vh.binding.setType(TYPE_MEDIA);
        vh.binding.setProtocol(null);
        vh.binding.executePendingBindings();

        vh.binding.dviIcon.setBackgroundResource(getIconResId(media));
    }

    //TODO update with different filter types in other cases than subtitles selection
    private boolean filter(MediaWrapper mediaWrapper) {
        return mediaWrapper.getType() == MediaWrapper.TYPE_DIR || mediaWrapper.getType() == MediaWrapper.TYPE_SUBTITLE;
    }

    protected void openMediaFromView(MediaViewHolder holder, View v) {
        final MediaWrapper media = (MediaWrapper) getItem(holder.getAdapterPosition());
        if (media.getType() == MediaWrapper.TYPE_DIR)
            fragment.browse(media, holder.getAdapterPosition(), true);
        else
            ((FilePickerFragment)fragment).pickFile(media);
    }
}
