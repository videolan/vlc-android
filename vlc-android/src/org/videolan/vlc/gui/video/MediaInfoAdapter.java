/*****************************************************************************
 * MediaInfoAdapter.java
 *****************************************************************************
 * Copyright © 2011-2015 VLC authors and VideoLAN
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.vlc.R;
import org.videolan.vlc.util.Strings;

public class MediaInfoAdapter extends ArrayAdapter<Media.Track> {

    public MediaInfoAdapter(Context context) {
        super(context, 0);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.info_item, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.text = (TextView) v.findViewById(R.id.subtitle);
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        Media.Track track = getItem(position);
        String title;
        StringBuilder textBuilder = new StringBuilder(1024);
        Resources res = getContext().getResources();
        switch (track.type)
        {
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

        return v;
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

    static class ViewHolder {
        TextView title;
        TextView text;
    }

}
