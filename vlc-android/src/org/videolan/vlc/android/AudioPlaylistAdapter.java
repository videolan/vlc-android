package org.videolan.vlc.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class AudioPlaylistAdapter extends ArrayAdapter<String> {

    private ArrayList<String> mTitles;
    private ArrayList<Boolean> mExpanded;
    private HashMap<String, ArrayList<Media>> mPlaylists;

    public AudioPlaylistAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
        mTitles = new ArrayList<String>();
        mExpanded = new ArrayList<Boolean>();
        mPlaylists = new HashMap<String, ArrayList<Media>>();
    }

    public void add(String title, Media media) {
        ArrayList<Media> list;
        if (!mTitles.contains(title)) {
            list = new ArrayList<Media>();
            mPlaylists.put(title, list);
            mTitles.add(title);
            mExpanded.add(false);
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
        mExpanded.clear();
        mTitles.clear();
        super.clear();
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
            holder.more = (ImageView) v.findViewById(R.id.ml_item_more);
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        String name = mTitles.get(position);
        ArrayList<Media> list = mPlaylists.get(name);
        holder.title.setText(name);
        if (mExpanded.get(position)) {
            StringBuilder sb = new StringBuilder();
            for (Media media : list) {
                sb.append(" - ");
                sb.append(media.getTitle());
                sb.append("\n");
            }
            holder.text.setText(sb.toString());
        }
        else {
            holder.text.setText(R.string.songs);
            holder.text.setText(list.size() + " " + holder.text.getText());
        }

        holder.more.setTag(position);
        holder.more.setOnClickListener(moreClickListener);
        return v;
    }

    private OnClickListener moreClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            int position = (Integer) v.getTag();
            boolean expanded = mExpanded.get(position);
            for (int i = 0; i < mExpanded.size(); ++i)
                mExpanded.set(i, false);
            mExpanded.set(position, !expanded);
            notifyDataSetChanged();
        }
    };

    static class ViewHolder {
        TextView title;
        TextView text;
        ImageView more;
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
