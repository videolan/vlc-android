package org.videolan.vlc.android;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class AudioSongsListAdapter extends ArrayAdapter<Media> {

    private ArrayList<Media> mMediaList;

    public AudioSongsListAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
        mMediaList = new ArrayList<Media>();
    }

    @Override
    public void add(Media m) {
        mMediaList.add(m);
        super.add(m);
    }

    @Override
    public void clear() {
        mMediaList.clear();
        super.clear();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.audio_browser_item, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.artist = (TextView) v.findViewById(R.id.artist);
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        Media media = getItem(position);
        holder.title.setText(media.getTitle());
        holder.artist.setText(media.getArtist() + " - " + media.getAlbum());
        return v;
    }

    public List<String> getPaths() {
        List<String> paths = new ArrayList<String>();
        for (int i = 0; i < mMediaList.size(); i++) {
            paths.add(mMediaList.get(i).getPath());
        }
        return paths;
    }

    static class ViewHolder {
        TextView title;
        TextView artist;
    }
}
