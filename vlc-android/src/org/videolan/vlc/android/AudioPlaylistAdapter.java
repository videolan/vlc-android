package org.videolan.vlc.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class AudioPlaylistAdapter extends ArrayAdapter<String> {

    private ArrayList<String> mTitles;
    private HashMap<String, ArrayList<Media>> mPlaylists;

    public AudioPlaylistAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
        mTitles = new ArrayList<String>();
        mPlaylists = new HashMap<String, ArrayList<Media>>();
    }

    public void add(String title, Media media) {
        ArrayList<Media> list;
        if (!mTitles.contains(title)) {
            list = new ArrayList<Media>();
            mPlaylists.put(title, list);
            mTitles.add(title);
            super.add(title);
        } else {
            list = mPlaylists.get(title);
        }
        list.add(media);
    }

    @Override
    public void clear() {
        for (String item : mTitles) {
            mPlaylists.get(item).clear();
            mPlaylists.remove(item);
        }
        mTitles.clear();
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

        String name = mTitles.get(position);
        ArrayList<Media> list = mPlaylists.get(name);
        holder.title.setText(name);
        holder.artist.setText(R.string.songs);
        holder.artist.setText(list.size() + " " + holder.artist.getText());
        return v;
    }

    static class ViewHolder {
        TextView title;
        TextView artist;
    }

    public List<String> getPlaylist(int position) {
        List<String> playlist = new ArrayList<String>();
        if (position >= 0 && position < mTitles.size()) {
            List<Media> mediaList = mPlaylists.get(mTitles.get(position));
            for (int i = 0; i < mediaList.size(); i++) {
                playlist.add(mediaList.get(i).getPath());
            }
        }
        return playlist;

    }
}
