/*****************************************************************************
 * VideoListAdapter.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.media.MediaGroup;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.Strings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.ViewHolder>
        implements Comparator<MediaWrapper> {

    public final static String TAG = "VLC/VideoListAdapter";

    public final static int SORT_BY_TITLE = 0;
    public final static int SORT_BY_LENGTH = 1;

    public final static int TYPE_LIST = 0;
    public final static int TYPE_GRID = 1;

    public final static int SORT_BY_DATE = 2;
    private int mSortDirection = 1;
    private int mSortBy = SORT_BY_TITLE;
    private boolean mListMode = false;
    private VideoGridFragment mFragment;
    private volatile ArrayList<MediaWrapper> mVideos = new ArrayList<>();
    private ImageView mThumbnail;

    public VideoListAdapter(VideoGridFragment fragment) {
        super();
        mFragment = fragment;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        boolean listMode = viewType == TYPE_LIST;
        LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(mListMode ? R.layout.video_list_card : R.layout.video_grid_card, parent, false);

        if (!mListMode) {
            mThumbnail = (ImageView) v.findViewById(R.id.ml_item_thumbnail);
            int width = mFragment.mGridView.getColumnWidth();
            int height = width*10/16;
            mThumbnail.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        }

        return new ViewHolder(v, listMode);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        MediaWrapper media = getItem(position);
        if (media == null)
            return;
        holder.binding.setVariable(BR.scaleType, ImageView.ScaleType.FIT_CENTER);
        fillView(holder, media);

        holder.binding.setVariable(BR.media, media);
    }

    @MainThread
    public void setTimes(ArrayMap<String, Long> times) {
        boolean notify = false;
        // update times
        for (int i = 0; i < getItemCount(); ++i) {
            MediaWrapper media = mVideos.get(i);
            Long time = times.get(media.getLocation());
            if (time != null) {
                media.setTime(time);
                notify = true;
            }
        }
        if (notify)
            notifyDataSetChanged();
    }

    public int sortDirection(int sortby) {
        if (sortby == mSortBy)
            return  mSortDirection;
        else
            return -1;
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
            case SORT_BY_DATE:
                if (mSortBy == SORT_BY_DATE)
                    mSortDirection *= -1;
                else {
                    mSortBy = SORT_BY_DATE;
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
        if (!isEmpty())
            try {
                Collections.sort(mVideos, this);
            } catch (ArrayIndexOutOfBoundsException e) {} //Exception happening on Android 2.x
    }

    public boolean isEmpty()
    {
        return mVideos.isEmpty();
    }

    @Override
    public int compare(MediaWrapper item1, MediaWrapper item2) {
        int compare = 0;
        switch (mSortBy) {
            case SORT_BY_TITLE:
                compare = item1.getTitle().toUpperCase(Locale.ENGLISH).compareTo(
                        item2.getTitle().toUpperCase(Locale.ENGLISH));
                break;
            case SORT_BY_LENGTH:
                compare = ((Long) item1.getLength()).compareTo(item2.getLength());
                break;
            case SORT_BY_DATE:
                compare = ((Long) item1.getLastModified()).compareTo(item2.getLastModified());
                break;
        }
        return mSortDirection * compare;
    }

    @Nullable
    public MediaWrapper getItem(int position) {
        if (position < 0 || position >= mVideos.size())
            return null;
        else
            return mVideos.get(position);
    }

    public void add(MediaWrapper item) {
        mVideos.add(item);
    }

    public void add(int position, MediaWrapper item) {
        mVideos.add(position, item);
        notifyItemInserted(position);
    }

    @MainThread
    public void remove(MediaWrapper item) {
        remove(getItemPosition(item));
    }

    @MainThread
    public void remove(int position) {
        if (position == -1)
            return;
        mVideos.remove(position);
        notifyItemRemoved(position);
    }

    private int getItemPosition(MediaWrapper mw) {
        if (mw == null || mVideos.isEmpty())
            return -1;
        for (int i = 0 ; i < mVideos.size(); ++i){
            if (mw.equals(mVideos.get(i)))
                return i;
        }
        return -1;
    }

    public void addAll(Collection<MediaWrapper> items) {
        mVideos.clear();
        mVideos.addAll(items);

    }

    public ArrayList<MediaWrapper> getAll() {
        return mVideos;
    }

    @MainThread
    public void update(MediaWrapper item) {
        int position = mVideos.indexOf(item);
        if (position != -1) {
            if (!(mVideos.get(position) instanceof MediaGroup))
                mVideos.set(position, item);
            notifyItemChanged(position);
        } else {
            MediaWrapper mw;
            for (int i = 0; i < mVideos.size(); ++i) {
                mw = mVideos.get(i);
                if (MediaComparators.byName.compare(item, mw) < 0) {
                    position = i;
                    break;
                }
            }
            if (position == -1) {
                position = mVideos.size();
                mVideos.add(position, item);
                notifyItemInserted(position);
            } else {
                mVideos.add(position, item);
                notifyItemRangeChanged(position, mVideos.size());
            }
        }
    }

    public void clear() {
        mVideos.clear();
    }

    private void fillView(ViewHolder holder, MediaWrapper media) {
        String text = "";
        String resolution = "";
        int max = 0;
        int progress = 0;

        if (media.getType() == MediaWrapper.TYPE_GROUP) {
            MediaGroup mediaGroup = (MediaGroup) media;
            int size = mediaGroup.size();
            resolution = VLCApplication.getAppResources().getQuantityString(R.plurals.videos_quantity, size, size);
        } else {
            /* Time / Duration */
            if (media.getLength() > 0) {
                long lastTime = media.getTime();
                if (lastTime > 0) {
                    text = String.format("%s / %s",
                            Strings.millisToText(lastTime),
                            Strings.millisToText(media.getLength()));
                    max = (int) (media.getLength() / 1000);
                    progress = (int) (lastTime / 1000);
                } else {
                    text = Strings.millisToText(media.getLength());
                }
            }
            if (media.getWidth() > 0 && media.getHeight() > 0)
                resolution = String.format("%dx%d", media.getWidth(), media.getHeight());
        }

        holder.binding.setVariable(BR.resolution, resolution);
        holder.binding.setVariable(BR.time, text);
        holder.binding.setVariable(BR.max, max);
        holder.binding.setVariable(BR.progress, progress);
    }

    public void setListMode(boolean value) {
        mListMode = value;
    }

    public boolean isListMode() {
        return mListMode;
    }

    @Override
    public long getItemId(int position) {
        return 0L;
    }

    @Override
    public int getItemCount() {
        return mVideos.size();
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener, View.OnFocusChangeListener {
        boolean listmode;
        public ViewDataBinding binding;

        public ViewHolder(View itemView, boolean listMode) {
            super(itemView);
            binding = DataBindingUtil.bind(itemView);
            this.listmode = listMode;
            binding.setVariable(BR.holder, this);
            itemView.setOnLongClickListener(this);
            itemView.setOnFocusChangeListener(this);
        }

        public void onClick(View v){
            MediaWrapper media = mVideos.get(getAdapterPosition());
            if (media instanceof MediaGroup) {
                MainActivity activity = (MainActivity) mFragment.getActivity();
                activity.showSecondaryFragment(SecondaryActivity.VIDEO_GROUP_LIST, media.getTitle());
            } else {
                media.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                VideoPlayerActivity.start(v.getContext(), media.getUri(), media.getTitle());
            }
        }

        public void onMoreClick(View v){
            if (mFragment == null)
                return;
            mFragment.mGridView.openContextMenu(getAdapterPosition());
        }

        @Override
        public boolean onLongClick(View v) {
            if (mFragment == null)
                return false;
            mFragment.mGridView.openContextMenu(getLayoutPosition());
            return true;
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus)
                itemView.setBackgroundColor(UiTools.ITEM_FOCUS_ON);
            else
                itemView.setBackgroundColor(UiTools.ITEM_FOCUS_OFF);
        }
    }
}
