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
import android.content.SharedPreferences;
import android.databinding.BindingAdapter;
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.DiffUtilAdapter;
import org.videolan.vlc.gui.helpers.ImageLoaderKt;
import org.videolan.vlc.gui.helpers.SelectorViewHolder;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.media.MediaGroup;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.MediaItemDiffCallback;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class VideoListAdapter extends DiffUtilAdapter<MediaWrapper, VideoListAdapter.ViewHolder> {

    public final static String TAG = "VLC/VideoListAdapter";

    private boolean mListMode = false;
    private IEventsHandler mEventsHandler;
    private int mSelectionCount = 0;
    private int mGridCardWidth = 0;

    private boolean mIsSeenMediaMarkerVisible;

    VideoListAdapter(IEventsHandler eventsHandler) {
        super();
        mEventsHandler = eventsHandler;
        final SharedPreferences settings = VLCApplication.getSettings();
        mIsSeenMediaMarkerVisible = settings == null || settings.getBoolean("media_seen", true);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final ViewDataBinding binding = DataBindingUtil.inflate(inflater, mListMode ? R.layout.video_list_card : R.layout.video_grid_card, parent, false);
        if (!mListMode) {
            final GridLayoutManager.LayoutParams params = (GridLayoutManager.LayoutParams) binding.getRoot().getLayoutParams();
            params.width = mGridCardWidth;
            params.height = params.width*10/16;
            binding.getRoot().setLayoutParams(params);
        }
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final MediaWrapper media = getDataset().get(position);
        if (media == null)
            return;
        holder.binding.setVariable(BR.scaleType, ImageView.ScaleType.CENTER_CROP);
        fillView(holder, media);
        holder.binding.setVariable(BR.media, media);
        holder.selectView(media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (payloads.isEmpty()) onBindViewHolder(holder, position);
        else {
            final MediaWrapper media = getDataset().get(position);
            for (Object data : payloads) {
                switch ((int) data) {
                    case Constants.UPDATE_THUMB:
                        ImageLoaderKt.loadImage(holder.thumbView, media);
                        break;
                    case Constants.UPDATE_TIME:
                    case Constants.UPDATE_SEEN:
                        fillView(holder, media);
                        break;
                    case Constants.UPDATE_SELECTION:
                        holder.selectView(media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
                        break;
                }
            }
        }
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        holder.binding.setVariable(BR.cover, UiTools.Resources.DEFAULT_COVER_VIDEO_DRAWABLE);
    }

    @Nullable
    public MediaWrapper getItem(int position) {
        return isPositionValid(position) ? getDataset().get(position) : null;
    }

    private boolean isPositionValid(int position) {
        return position >= 0 && position < getDataset().size();
    }

    public boolean contains(MediaWrapper mw) {
        return getDataset().indexOf(mw) != -1;
    }

    public List<MediaWrapper> getAll() {
        return getDataset();
    }

    List<MediaWrapper> getSelection() {
        final List<MediaWrapper> selection = new LinkedList<>();
        for (int i = 0; i < getDataset().size(); ++i) {
            MediaWrapper mw = getDataset().get(i);
            if (mw.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)) {
                if (mw instanceof MediaGroup)
                    selection.addAll(((MediaGroup) mw).getAll());
                else
                    selection.add(mw);
            }

        }
        return selection;
    }

    @MainThread
    int getSelectionCount() {
        return mSelectionCount;
    }

    @MainThread
    void resetSelectionCount() {
        mSelectionCount = 0;
    }

    @MainThread
    void updateSelectionCount(boolean selected) {
        mSelectionCount += selected ? 1 : -1;
    }

    @MainThread
    public void clear() {
        update(new ArrayList<MediaWrapper>());
    }

    private void fillView(ViewHolder holder, MediaWrapper media) {
        String text = "";
        String resolution = "";
        int max = 0;
        int progress = 0;
        long seen = 0L;

        if (media.getType() == MediaWrapper.TYPE_GROUP) {
            text = media.getDescription();
        } else {
            /* Time / Duration */
            resolution = Tools.getResolution(media);
            if (media.getLength() > 0) {
                final long lastTime = media.getTime();
                if (lastTime > 0) {
                    max = (int) (media.getLength() / 1000);
                    progress = (int) (lastTime / 1000);
                }
                if (TextUtils.isEmpty(resolution)) text = Tools.millisToText(media.getLength());
                else text = Tools.millisToText(media.getLength())+"  |  "+resolution;
            } else text = resolution;
            seen = mIsSeenMediaMarkerVisible ? media.getSeen() : 0L;
        }

        holder.binding.setVariable(BR.time, text);
        holder.binding.setVariable(BR.max, max);
        holder.binding.setVariable(BR.progress, progress);
        holder.binding.setVariable(BR.seen, seen);
    }

    public void setListMode(boolean value) {
        mListMode = value;
    }

    void setGridCardWidth(int gridCardWidth) {
        mGridCardWidth = gridCardWidth;
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
        return getDataset().size();
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }

    int getListWithPosition(List<MediaWrapper>  list, int position) {
        MediaWrapper mw;
        int offset = 0;
        for (int i = 0; i < getItemCount(); ++i) {
            mw = getDataset().get(i);
            if (mw instanceof MediaGroup) {
                for (MediaWrapper item : ((MediaGroup) mw).getAll())
                    list.add(item);
                if (i < position)
                    offset += ((MediaGroup)mw).size()-1;
            } else
                list.add(mw);
        }
        return position+offset;
    }

    public class ViewHolder extends SelectorViewHolder<ViewDataBinding> implements View.OnFocusChangeListener {
        private ImageView thumbView;

        public ViewHolder(ViewDataBinding binding) {
            super(binding);
            thumbView = itemView.findViewById(R.id.ml_item_thumbnail);
            binding.setVariable(BR.holder, this);
            binding.setVariable(BR.cover, UiTools.Resources.DEFAULT_COVER_VIDEO_DRAWABLE);
            if (AndroidUtil.isMarshMallowOrLater) itemView.setOnContextClickListener(new View.OnContextClickListener() {
                @Override
                public boolean onContextClick(View v) {
                    onMoreClick(v);
                    return true;
                }
            });
        }

        public void onClick(View v) {
            final int position = getLayoutPosition();
            if (isPositionValid(position))
                mEventsHandler.onClick(v, position, getDataset().get(position));
        }

        public void onMoreClick(View v){
            final int position = getLayoutPosition();
            if (isPositionValid(position))
                mEventsHandler.onCtxClick(v, position, getItem(position));
        }

        public boolean onLongClick(View v) {
            final int position = getLayoutPosition();
            return isPositionValid(position) && mEventsHandler.onLongClick(v, position, getDataset().get(position));
        }

        @Override
        public void selectView(boolean selected) {
            thumbView.setImageResource(selected ? R.drawable.ic_action_mode_select_1610 : mListMode ? 0 : R.drawable.black_gradient);
            super.selectView(selected);
        }

        @Override
        protected boolean isSelected() {
            return getDataset().get(getLayoutPosition()).hasStateFlags(MediaLibraryItem.FLAG_SELECTED);
        }
    }

    @BindingAdapter({"time", "resolution"})
    public static void setLayoutHeight(View view, String time, String resolution) {
        final ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.height = TextUtils.isEmpty(time) && TextUtils.isEmpty(resolution) ?
                ViewGroup.LayoutParams.MATCH_PARENT :
                ViewGroup.LayoutParams.WRAP_CONTENT;
        view.setLayoutParams(layoutParams);
    }

    @Override
    protected MediaItemDiffCallback<MediaWrapper> createCB() {
        return new VideoItemDiffCallback();
    }

    @Override
    protected void onUpdateFinished() {
        mEventsHandler.onUpdateFinished(null);
    }

    private class VideoItemDiffCallback extends MediaItemDiffCallback<MediaWrapper> {

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            final MediaWrapper oldItem = oldList.get(oldItemPosition);
            final MediaWrapper newItem = newList.get(newItemPosition);
            return oldItem == newItem || (oldItem.getType() == newItem.getType() && oldItem.equals(newItem));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            final MediaWrapper oldItem = oldList.get(oldItemPosition);
            final MediaWrapper newItem = newList.get(newItemPosition);
            return oldItem == newItem || (oldItem.getTime() == newItem.getTime()
                    && TextUtils.equals(oldItem.getArtworkMrl(), newItem.getArtworkMrl())
                    && oldItem.getSeen() == newItem.getSeen());
        }

        @Nullable
        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            final MediaWrapper oldItem = oldList.get(oldItemPosition);
            final MediaWrapper newItem = newList.get(newItemPosition);
            if (oldItem.getTime() != newItem.getTime())
                return Constants.UPDATE_TIME;
            if (!TextUtils.equals(oldItem.getArtworkMrl(), newItem.getArtworkMrl()))
                return Constants.UPDATE_THUMB;
            else
                return Constants.UPDATE_SEEN;
        }
    }

    void setSeenMediaMarkerVisible(boolean seenMediaMarkerVisible) {
        mIsSeenMediaMarkerVisible = seenMediaMarkerVisible;
    }

    @Override
    protected boolean detectMoves() {
        return true;
    }
}
