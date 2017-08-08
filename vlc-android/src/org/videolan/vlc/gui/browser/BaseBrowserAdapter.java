/**
 * **************************************************************************
 * BaseBrowserAdapter.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 * Author: Geoffrey Métais
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.browser;

import android.databinding.ViewDataBinding;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.MainThread;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Storage;
import org.videolan.vlc.R;
import org.videolan.vlc.SortableAdapter;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.BrowserItemBinding;
import org.videolan.vlc.databinding.BrowserItemSeparatorBinding;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.MediaItemFilter;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.videolan.medialibrary.media.MediaLibraryItem.FLAG_SELECTED;
import static org.videolan.medialibrary.media.MediaLibraryItem.TYPE_MEDIA;
import static org.videolan.medialibrary.media.MediaLibraryItem.TYPE_STORAGE;

public class BaseBrowserAdapter extends SortableAdapter<MediaLibraryItem, BaseBrowserAdapter.ViewHolder> implements Filterable {
    protected static final String TAG = "VLC/BaseBrowserAdapter";

    private static int FOLDER_RES_ID = R.drawable.ic_menu_folder;

    private static final BitmapDrawable IMAGE_FOLDER = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), FOLDER_RES_ID));
    private static final BitmapDrawable IMAGE_AUDIO = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_audio_normal));
    private static final BitmapDrawable IMAGE_VIDEO = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_video_normal));
    private static final BitmapDrawable IMAGE_SUBTITLE = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_subtitle_normal));
    private static final BitmapDrawable IMAGE_UNKNOWN = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_unknown_normal));

    private ArrayList<MediaLibraryItem> mOriginalData = null;
    protected final BaseBrowserFragment fragment;
    private int mTop = 0, mMediaCount = 0, mSelectionCount = 0;
    private ItemFilter mFilter = new ItemFilter();

    BaseBrowserAdapter(BaseBrowserFragment fragment){
        this.fragment = fragment;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == MediaLibraryItem.TYPE_MEDIA || viewType == MediaLibraryItem.TYPE_STORAGE)
            return new MediaViewHolder(BrowserItemBinding.inflate(inflater, parent, false));
        else
            return new SeparatorViewHolder(BrowserItemSeparatorBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        if (viewType == TYPE_MEDIA) {
            onBindMediaViewHolder((MediaViewHolder) holder, position);
        } else {
            SeparatorViewHolder vh = (SeparatorViewHolder) holder;
            vh.binding.setTitle(mDataset.get(position).getTitle());
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (payloads.isEmpty())
            onBindViewHolder(holder, position);
        else if (payloads.get(0) instanceof CharSequence){
            ((MediaViewHolder) holder).binding.text.setVisibility(View.VISIBLE);
            ((MediaViewHolder) holder).binding.text.setText((CharSequence) payloads.get(0));
        } else if (payloads.get(0) instanceof MediaWrapper)
            ((MediaViewHolder)holder).setViewBackground(holder.itemView.hasFocus(), ((MediaWrapper)payloads.get(0)).hasStateFlags(FLAG_SELECTED));
    }

    private void onBindMediaViewHolder(final MediaViewHolder vh, int position) {
        final MediaWrapper media = (MediaWrapper) getItem(position);
        vh.binding.setItem(media);
        vh.binding.setHasContextMenu(true);
        if (fragment instanceof NetworkBrowserFragment && fragment.isRootDirectory())
            vh.binding.setProtocol(getProtocol(media));
        vh.binding.setCover(getIcon(media));
        vh.setContextMenuListener();
        vh.setViewBackground(vh.itemView.hasFocus(), media.hasStateFlags(FLAG_SELECTED));
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public MediaLibraryItem get(int position) {
        return mDataset.get(position);
    }

    public abstract class ViewHolder<T extends ViewDataBinding> extends RecyclerView.ViewHolder {

        public T binding;

        public ViewHolder(View itemView) {
            super(itemView);
        }
        public void onClick(View v){}

        public void onCheckBoxClick(View v){}

        public void onMoreClick(View v){}

        public abstract int getType();

    }

    class MediaViewHolder extends ViewHolder<BrowserItemBinding> implements View.OnLongClickListener {

        MediaViewHolder(final BrowserItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.setHolder(this);
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    binding.browserCheckbox.toggle();
                    onCheckBoxClick(binding.browserCheckbox);
                    return true;
                }
            });
            if (AndroidDevices.isAndroidTv())
                itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean b) {
                        setViewBackground(b, false);
                    }
                });
        }

        void setContextMenuListener() {
            itemView.setOnLongClickListener(this);
        }

        protected void openStorage() {
            MediaWrapper mw = new MediaWrapper(((Storage) getItem(getLayoutPosition())).getUri());
            mw.setType(MediaWrapper.TYPE_DIR);
            fragment.browse(mw, getLayoutPosition(), binding.browserCheckbox.isChecked());
        }

        public void onCheckBoxClick(View v) {
            if (getItem(getLayoutPosition()).getItemType() == TYPE_STORAGE)
                checkBoxAction(v, ((Storage) getItem(getLayoutPosition())).getUri().toString());
        }

        @Override
        public int getType() {
            return TYPE_MEDIA;
        }

        public void onClick(View v){
            int position = getLayoutPosition();
            if (position < mDataset.size() && position >= 0)
                fragment.onClick(v, position, mDataset.get(position));
        }

        public void onMoreClick(View v) {
            int position = getLayoutPosition();
            if (position < mDataset.size() && position >= 0)
                fragment.onCtxClick(v, position, mDataset.get(position));
        }

        @Override
        public boolean onLongClick(View v) {
            int position = getLayoutPosition();
            return position < mDataset.size() && position >= 0
                && fragment.onLongClick(v, position, mDataset.get(position));
        }

        private void setViewBackground(boolean focus, boolean selected) {
            if (focus)
                itemView.setBackgroundColor(UiTools.ITEM_FOCUS_ON);
            else
                itemView.setBackgroundColor(selected ? UiTools.ITEM_SELECTION_ON : UiTools.ITEM_FOCUS_OFF);
        }
    }

    private class SeparatorViewHolder extends ViewHolder<BrowserItemSeparatorBinding> {

        SeparatorViewHolder(BrowserItemSeparatorBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        public int getType() {
            return MediaLibraryItem.TYPE_DUMMY;
        }
    }

    public void clear() {
        if (!isEmpty())
            update(new ArrayList<MediaLibraryItem>(0));
    }

    public boolean isEmpty() {
        return Util.isListEmpty(peekLast());
    }

    public void addItem(MediaLibraryItem item, boolean top) {
        addItem(item, top, -1);
    }

    void addItem(MediaLibraryItem item, boolean top, int positionTo) {
        int position;
        ArrayList<MediaLibraryItem> list = new ArrayList<>(peekLast());
        if (positionTo != -1)
            position = positionTo;
        else
            position = top ? mTop : list.size();

        if (position <= list.size()) {
            list.add(position, item);
            update(list);
        }
    }

    public void setTop (int top) {
        mTop = top;
    }

    void removeItem(int position) {
        if (position >= getItemCount())
            return;
        removeItem(mDataset.get(position));
    }

    void removeItem(MediaLibraryItem item) {
        if (item.getItemType() == TYPE_MEDIA && (((MediaWrapper) item).getType() == MediaWrapper.TYPE_VIDEO || ((MediaWrapper) item).getType() == MediaWrapper.TYPE_AUDIO))
            mMediaCount--;
        ArrayList<MediaLibraryItem> list = new ArrayList<>(peekLast());
        list.remove(item);
        update(list);
    }

    void removeItem(String path) {

        MediaLibraryItem mediaItem = null;
        for (MediaLibraryItem item : mDataset) {
            if (item .getItemType() == TYPE_MEDIA && TextUtils.equals(path, ((MediaWrapper) item).getUri().toString())) {
                mediaItem = item;
                break;
            }
        }
        if (mediaItem != null)
            removeItem(mediaItem);
    }

    public ArrayList<MediaLibraryItem> getAll(){
        return mDataset;
    }

    public MediaLibraryItem getItem(int position){
        if (position < 0 || position >= mDataset.size())
            return null;
        return mDataset.get(position);
    }

    public int getItemViewType(int position){
        return getItem(position).getItemType();
    }

    int getMediaCount() {
        return mMediaCount;
    }

    BitmapDrawable getIcon(MediaWrapper media) {
        switch (media.getType()){
            case MediaWrapper.TYPE_AUDIO:
                return IMAGE_AUDIO;
            case MediaWrapper.TYPE_DIR:
                return IMAGE_FOLDER;
            case MediaWrapper.TYPE_VIDEO:
                return IMAGE_VIDEO;
            case MediaWrapper.TYPE_SUBTITLE:
                return IMAGE_SUBTITLE;
            default:
                return IMAGE_UNKNOWN;
        }
    }
    String getProtocol(MediaWrapper media) {
        if (!fragment.isRootDirectory() || !(fragment instanceof NetworkBrowserFragment))
            return null;
        if (media.getType() != MediaWrapper.TYPE_DIR)
            return null;
        return media.getUri().getScheme();
    }

    protected void checkBoxAction(View v, String mrl){}

    ArrayList<MediaWrapper> getSelection() {
        ArrayList<MediaWrapper> selection = new ArrayList<>();
        for (MediaLibraryItem item : mDataset) {
            if (item.hasStateFlags(FLAG_SELECTED))
                selection.add((MediaWrapper) item);
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

    @Override
    public Filter getFilter() {
        return mFilter;
    }

    @Override
    protected ArrayList<MediaLibraryItem> prepareList(ArrayList<MediaLibraryItem> list) {
        if (fragment.isSortEnabled() && needsSorting())
            Collections.sort(list, sMediaComparator);
        mMediaCount = 0;
        for (MediaLibraryItem item : list) {
            if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA
                    && (((MediaWrapper)item).getType() == MediaWrapper.TYPE_AUDIO|| (AndroidUtil.isHoneycombOrLater && ((MediaWrapper)item).getType() == MediaWrapper.TYPE_VIDEO)))
                ++mMediaCount;
        }
        return list;
    }

    @Override
    protected void onUpdateFinished() {
        super.onUpdateFinished();
        fragment.onUpdateFinished(null);
    }

    void restoreList() {
        if (mOriginalData != null) {
            update(new ArrayList<>(mOriginalData));
            mOriginalData = null;
        }
    }

    private class ItemFilter extends MediaItemFilter {

        @Override
        protected List<MediaLibraryItem> initData() {
            if (mOriginalData == null)
                mOriginalData = new ArrayList<>(mDataset);
            return mOriginalData;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            update((ArrayList<MediaLibraryItem>) filterResults.values);
        }
    }
}
