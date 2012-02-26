package org.videolan.vlc.gui.video;

import org.videolan.vlc.R;
import org.videolan.vlc.TrackInfo;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class MediaInfoAdapter extends ArrayAdapter<TrackInfo> {

    public MediaInfoAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.audio_browser_playlist, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.text = (TextView) v.findViewById(R.id.text);
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        TrackInfo track = getItem(position);
        String title;
        String text;
        Resources res = getContext().getResources();
        switch (track.Type)
        {
            case TrackInfo.TYPE_AUDIO:
                title = res.getString(R.string.track_audio);
                text = res.getQuantityString(R.plurals.track_audio_info, track.Channels, track.Codec, track.Channels, track.Samplerate);
                break;
            case TrackInfo.TYPE_VIDEO:
                title = res.getString(R.string.track_video);
                text = res.getString(R.string.track_video_info, track.Codec, track.Width, track.Height);
                break;
            case TrackInfo.TYPE_TEXT:
                title = res.getString(R.string.track_text);
                text = track.Codec;
                break;
            default:
                title = res.getString(R.string.track_unknown);
                text = "";
        }

        holder.title.setText(title);
        holder.text.setText(text);

        return v;
    }

    static class ViewHolder {
        TextView title;
        TextView text;
    }

}
