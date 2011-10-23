package org.videolan.vlc.android;

import java.util.Comparator;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class VideoListAdapter extends ArrayAdapter<Media>
                                 implements Comparator<Media> {

    public final static int SORT_BY_TITLE = 0;
    public final static int SORT_BY_LENGTH = 1;
    private int mSortDirection = 1;
    private int mSortBy = SORT_BY_TITLE;

    public VideoListAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
    }

    public final static String TAG = "VLC/MediaLibraryAdapter";

    public synchronized void update(Media item) {
        int position = getPosition(item);
        if (position != -1) {
            remove(item);
            insert(item, position);
        }
    }

    public void sortBy(int sortby) {
        switch (sortby) {
            case SORT_BY_TITLE:
                if (mSortBy == SORT_BY_TITLE)
                    mSortDirection *= -1;
                else {
                    mSortBy = SORT_BY_TITLE;
                    mSortDirection = 1;
                }
                break;
            case SORT_BY_LENGTH:
                if (mSortBy == SORT_BY_LENGTH)
                    mSortDirection *= -1;
                else {
                    mSortBy = SORT_BY_LENGTH;
                    mSortDirection *= 1;
                }
                break;
            default:
                mSortBy = SORT_BY_TITLE;
                mSortDirection = 1;
                break;
        }
        sort();
    }

    public void sort() {
        super.sort(this);
    }

    public int compare(Media item1, Media item2) {
        int compare = 0;
        switch (mSortBy) {
            case SORT_BY_TITLE:
                compare = item1.getTitle().toUpperCase().compareTo(
                        item2.getTitle().toUpperCase());
                break;
            case SORT_BY_LENGTH:
                compare = ((Long) item1.getLength()).compareTo(item2.getLength());
                break;
        }
        return mSortDirection * compare;
    }

    /**
     * Display the view of a file browser item.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.video_list_item, parent, false);
            holder = new ViewHolder();
            holder.thumbnail = (ImageView) v.findViewById(R.id.ml_item_thumbnail);
            holder.length = (TextView) v.findViewById(R.id.ml_item_length);
            holder.title = (TextView) v.findViewById(R.id.ml_item_title);
            holder.more = (ImageView) v.findViewById(R.id.ml_item_more);
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        Media media = getItem(position);
        holder.title.setText(media.getTitle());
        holder.length.setText(" " + Util.millisToString(media.getLength()) + " ");

        Bitmap thumbnail;
        if (media.getPicture() != null) {
            thumbnail = media.getPicture();
            holder.thumbnail.setImageBitmap(thumbnail);
        } else {
            // set default thumbnail
            thumbnail = BitmapFactory.decodeResource(MainActivity.getInstance().getResources(), R.drawable.thumbnail);
            holder.thumbnail.setImageBitmap(thumbnail);
        }

        holder.more.setTag(media);
        holder.more.setOnClickListener(moreClickListener);

        return v;
    }

    private OnClickListener moreClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            Media item = (Media) v.getTag();
            Intent intent = new Intent(getContext(), MediaInfoActivity.class);
            intent.putExtra("filePath", item.getPath());
            VideoActivityGroup group = VideoActivityGroup.getInstance();
            group.startChildAcitvity("MediaInfo", intent);
        }
    };

    static class ViewHolder {
        ImageView thumbnail;
        TextView length;
        TextView title;
        ImageView more;
    }
}
