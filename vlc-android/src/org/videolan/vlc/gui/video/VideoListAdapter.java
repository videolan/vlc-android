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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.tools.MultiSelectAdapter;
import org.videolan.tools.MultiSelectHelper;
import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.DiffUtilAdapter;
import org.videolan.vlc.gui.helpers.ImageLoaderKt;
import org.videolan.vlc.gui.helpers.SelectorViewHolder;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.MediaItemDiffCallback;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.GridLayoutManager;

public class VideoListAdapter extends DiffUtilAdapter<MediaWrapper, VideoListAdapter.ViewHolder> implements MultiSelectAdapter<MediaWrapper> {

    public final static String TAG = "VLC/VideoListAdapter";

    private boolean mListMode = false;
    private IEventsHandler mEventsHandler;
    private int mGridCardWidth = 0;

    private boolean mIsSeenMediaMarkerVisible;
    private ObservableBoolean mShowFilename = new ObservableBoolean();

    private MultiSelectHelper<MediaWrapper> multiSelectHelper;

    VideoListAdapter(IEventsHandler eventsHandler, boolean seenMarkVisible) {
        super();
        multiSelectHelper = new MultiSelectHelper<>(this, Constants.UPDATE_SELECTION);
        mEventsHandler = eventsHandler;
        mIsSeenMediaMarkerVisible = seenMarkVisible;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final ViewDataBinding binding = DataBindingUtil.inflate(inflater, mListMode ? R.layout.video_list_card : R.layout.video_grid_card, parent, false);
        binding.setVariable(BR.showFilename, mShowFilename);
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
        holder.selectView(multiSelectHelper.isSelected(position));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (payloads.isEmpty()) onBindViewHolder(holder, position);
        else {
            final MediaWrapper media = getDataset().get(position);
            for (Object data : payloads) {
                switch ((int) data) {
                    case Constants.UPDATE_THUMB:
                        ImageLoaderKt.loadImage(holder.overlay, media);
                        break;
                    case Constants.UPDATE_TIME:
                    case Constants.UPDATE_SEEN:
                        fillView(holder, media);
                        break;
                    case Constants.UPDATE_SELECTION:
                        holder.selectView(multiSelectHelper.isSelected(position));
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

    public MultiSelectHelper<MediaWrapper> getMultiSelectHelper() {
        return multiSelectHelper;
    }

    @MainThread
    public void clear() {
        update(new ArrayList<MediaWrapper>());
    }

    private void fillView(ViewHolder holder, MediaWrapper media) {
        String text;
        String resolution;
        int max = 0;
        int progress = 0;
        long seen = 0L;

        if (media.getType() == MediaWrapper.TYPE_GROUP) {
            text = media.getDescription();
        } else {
            /* Time / Duration */
            resolution = Tools.getResolution(media);
            if (media.getLength() > 0) {
                final long lastTime = media.getDisplayTime();
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

    public class ViewHolder extends SelectorViewHolder<ViewDataBinding> implements View.OnFocusChangeListener {
        private ImageView overlay;

        @TargetApi(Build.VERSION_CODES.M)
        public ViewHolder(ViewDataBinding binding) {
            super(binding);
            overlay = itemView.findViewById(R.id.ml_item_overlay);
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
            overlay.setImageResource(selected ? R.drawable.ic_action_mode_select_1610 : mListMode ? 0 : R.drawable.black_gradient);
            if (mListMode) overlay.setVisibility(selected ? View.VISIBLE : View.GONE);
            super.selectView(selected);
        }

        @Override
        protected boolean isSelected() {
            return multiSelectHelper.isSelected(getLayoutPosition());
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
            return oldItem == newItem || (oldItem.getDisplayTime() == newItem.getDisplayTime()
                    && TextUtils.equals(oldItem.getArtworkMrl(), newItem.getArtworkMrl())
                    && oldItem.getSeen() == newItem.getSeen());
        }

        @Nullable
        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            final MediaWrapper oldItem = oldList.get(oldItemPosition);
            final MediaWrapper newItem = newList.get(newItemPosition);
            if (oldItem.getDisplayTime() != newItem.getDisplayTime())
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

    void showFilename(boolean show) {
        mShowFilename.set(show);
    }

    @Override
    protected boolean detectMoves() {
        return true;
    }
}
