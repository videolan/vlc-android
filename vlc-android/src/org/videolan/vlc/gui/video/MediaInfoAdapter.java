/*****************************************************************************
 * MediaInfoAdapter.java
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

package org.videolan.vlc.gui.video;

import android.content.Context;
import android.content.res.Resources;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.vlc.R;
import org.videolan.vlc.util.Strings;

import java.util.List;

public class MediaInfoAdapter extends RecyclerView.Adapter<MediaInfoAdapter.ViewHolder> {
    private LayoutInflater inflater;
    private List<Media.Track> mDataset;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (inflater == null)
            inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return new ViewHolder(inflater.inflate(R.layout.info_item, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Media.Track track = mDataset.get(position);
        String title;
        StringBuilder textBuilder = new StringBuilder();
        Resources res = holder.itemView.getContext().getResources();
        switch (track.type) {
            case Media.Track.Type.Audio:
                title = res.getString(R.string.track_audio);
                appendCommon(textBuilder, res, track);
                appendAudio(textBuilder, res, (Media.AudioTrack)track);
                break;
            case Media.Track.Type.Video:
                title = res.getString(R.string.track_video);
                appendCommon(textBuilder, res, track);
                appendVideo(textBuilder, res, (Media.VideoTrack) track);
                break;
            case Media.Track.Type.Text:
                title = res.getString(R.string.track_text);
                appendCommon(textBuilder, res, track);
                break;
            default:
                title = res.getString(R.string.track_unknown);
        }
        holder.title.setText(title);
        holder.text.setText(textBuilder.toString());
    }

    @Override
    public int getItemCount() {
        return mDataset == null ? 0 : mDataset.size();
    }

    public void setTracks(List<Media.Track> tracks) {
        int size = getItemCount();
        mDataset = tracks;
        if (size > 0)
            notifyItemRangeRemoved(0, size-1);
        notifyItemRangeInserted(0, tracks.size());
    }

    private void appendCommon(StringBuilder textBuilder, Resources res, Media.Track track) {
        if (track.bitrate != 0)
            textBuilder.append(res.getString(R.string.track_bitrate_info, Strings.readableSize(track.bitrate)));
        textBuilder.append(res.getString(R.string.track_codec_info, track.codec));
        if (track.language != null && !track.language.equalsIgnoreCase("und"))
            textBuilder.append(res.getString(R.string.track_language_info, track.language));
    }

    private void appendAudio(StringBuilder textBuilder, Resources res, Media.AudioTrack track) {
        textBuilder.append(res.getQuantityString(R.plurals.track_channels_info_quantity, track.channels, track.channels));
        textBuilder.append(res.getString(R.string.track_samplerate_info, track.rate));
    }

    private void appendVideo(StringBuilder textBuilder, Resources res, Media.VideoTrack track) {
        final double framerate = track.frameRateNum / (double) track.frameRateDen;
        if( track.width != 0 && track.height != 0 )
            textBuilder.append(res.getString(R.string.track_resolution_info, track.width, track.height));
        if( !Double.isNaN(framerate) )
            textBuilder.append(res.getString(R.string.track_framerate_info, framerate));
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView title, text;

        public ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            text = itemView.findViewById(R.id.subtitle);
        }
    }
}
