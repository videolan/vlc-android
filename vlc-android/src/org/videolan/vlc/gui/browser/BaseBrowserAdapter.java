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

import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.util.SparseArrayCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
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
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.BrowserItemSeparatorBinding;
import org.videolan.vlc.databinding.DirectoryViewItemBinding;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.MediaItemFilter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.videolan.medialibrary.media.MediaLibraryItem.TYPE_MEDIA;

public class BaseBrowserAdapter extends RecyclerView.Adapter<BaseBrowserAdapter.ViewHolder> implements Filterable {
    protected static final String TAG = "VLC/BaseBrowserAdapter";

    private static int FOLDER_RES_ID = R.drawable.ic_menu_folder;

    private static final BitmapDrawable IMAGE_FOLDER = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), FOLDER_RES_ID));
    private static final BitmapDrawable IMAGE_AUDIO = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_audio_normal));
    private static final BitmapDrawable IMAGE_VIDEO = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_video_normal));
    private static final BitmapDrawable IMAGE_SUBTITLE = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_subtitle_normal));
    private static final BitmapDrawable IMAGE_UNKNOWN = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_unknown_normal));

    ArrayList<MediaLibraryItem> mMediaList = new ArrayList<>();
    ArrayList<MediaLibraryItem> mOriginalData = null;
    BaseBrowserFragment fragment;
    SparseArrayCompat<WeakReference<MediaViewHolder>> mHolders = new SparseArrayCompat<>();
    private int mTop = 0;
    private int mMediaCount = 0;
    private ItemFilter mFilter = new ItemFilter();
    private boolean mActionMode;
    private List<Integer> mSelectedItems = new LinkedList<>();

    BaseBrowserAdapter(BaseBrowserFragment fragment){
        this.fragment = fragment;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ViewHolder vh;
        View v;
        if (viewType == MediaLibraryItem.TYPE_MEDIA) {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.directory_view_item, parent, false);
            vh = new MediaViewHolder(v);
        } else {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.browser_item_separator, parent, false);
            vh = new SeparatorViewHolder(v);
        }
        return vh;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        if (viewType == TYPE_MEDIA) {
            onBindMediaViewHolder((MediaViewHolder) holder, position);
        } else {
            SeparatorViewHolder vh = (SeparatorViewHolder) holder;
            vh.binding.setTitle(mMediaList.get(position).getTitle());
        }
    }

    private void onBindMediaViewHolder(final MediaViewHolder vh, int position) {
        mHolders.put(position, new WeakReference<>(vh));
        final MediaWrapper media = (MediaWrapper) getItem(position);
        vh.binding.setItem(media);
        vh.binding.setHasContextMenu(true);
        if (fragment instanceof NetworkBrowserFragment && fragment.isRootDirectory())
            vh.binding.setProtocol(getProtocol(media));
        vh.binding.setImage(getIcon(media));
        vh.setContextMenuListener();
        vh.setViewBackground(vh.itemView.hasFocus(), mSelectedItems.contains(position));
    }


    @Override
    public void onViewRecycled(ViewHolder holder) {
        mHolders.remove(holder.getAdapterPosition());
    }

    @Override
    public int getItemCount() {
        return mMediaList.size();
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

    class MediaViewHolder extends ViewHolder<DirectoryViewItemBinding> implements View.OnLongClickListener {

        MediaViewHolder(View v) {
            super(v);
            binding = DataBindingUtil.bind(v);
            binding.setHolder(this);
            v.findViewById(R.id.layout_item).setTag(R.id.layout_item, this);
            v.setTag(binding);
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    binding.browserCheckbox.toggle();
                    return true;
                }
            });
        }

        void setContextMenuListener() {
            itemView.setOnLongClickListener(this);
        }

        protected void openStorage() {
            MediaWrapper mw = new MediaWrapper(((Storage) getItem(getAdapterPosition())).getUri());
            mw.setType(MediaWrapper.TYPE_DIR);
            fragment.browse(mw, getAdapterPosition(), binding.browserCheckbox.isChecked());
        }

        public void onCheckBoxClick(View v){
            if (getItem(getAdapterPosition()) instanceof Storage) {
                checkBoxAction(v, ((Storage) getItem(getAdapterPosition())).getUri().getPath());
            }
        }

        @Override
        public int getType() {
            return TYPE_MEDIA;
        }

        public void onClick(View v){
            if (mActionMode) {
                MediaWrapper mediaWrapper = (MediaWrapper) getItem(getLayoutPosition());
                if (mediaWrapper.getType() == MediaWrapper.TYPE_AUDIO ||
                        mediaWrapper.getType() == MediaWrapper.TYPE_VIDEO ||
                        mediaWrapper.getType() == MediaWrapper.TYPE_DIR) {
                    setSelected();
                    fragment.invalidateActionMode();
                }
            } else
                openMediaFromView(this, v);
        }

        public void onMoreClick(View v) {
            fragment.openContextMenu(getLayoutPosition());
        }

        @Override
        public boolean onLongClick(View v) {
            int position = getLayoutPosition();
            MediaWrapper mediaWrapper = (MediaWrapper) getItem(position);
            if (mediaWrapper.getType() == MediaWrapper.TYPE_AUDIO ||
                    mediaWrapper.getType() == MediaWrapper.TYPE_VIDEO ||
                    mediaWrapper.getType() == MediaWrapper.TYPE_DIR) {
                if (mActionMode)
                    return false;
                setSelected();
                fragment.startActionMode();
            } else
                fragment.mRecyclerView.openContextMenu(position);
            return true;
        }

        private void setSelected() {
            Integer position = getLayoutPosition();
            boolean selected = !mSelectedItems.contains(position);
            if (selected)
                mSelectedItems.add(position);
            else
                mSelectedItems.remove(position);
            setViewBackground(itemView.hasFocus(), mSelectedItems.contains(position));
        }

        private void setViewBackground(boolean focus, boolean selected) {
            if (focus)
                itemView.setBackgroundColor(UiTools.ITEM_FOCUS_ON);
            else
                itemView.setBackgroundColor(selected ? UiTools.ITEM_SELECTION_ON : UiTools.ITEM_FOCUS_OFF);
        }
    }

    private class SeparatorViewHolder extends ViewHolder<BrowserItemSeparatorBinding> {

        SeparatorViewHolder(View v) {
            super(v);
            binding = DataBindingUtil.bind(v);
        }

        @Override
        public int getType() {
            return MediaLibraryItem.TYPE_DUMMY;
        }
    }

    public void clear(){
        mMediaList.clear();
        notifyDataSetChanged();
    }

    public boolean isEmpty(){
        return mMediaList.isEmpty();
    }

    public void addItem(MediaLibraryItem item, boolean notify, boolean top){
        addItem(item, notify, top, -1);
    }

    public void addItem(MediaLibraryItem item, boolean notify, int position){
        addItem(item, notify, false, position);
    }

    public void addItem(MediaLibraryItem item, boolean notify, boolean top, int positionTo){
        int position;
        if (positionTo != -1)
            position = positionTo;
        else
            position = top ? mTop : mMediaList.size();

        if (item .getItemType() == TYPE_MEDIA && item.getTitle().startsWith("."))
            return;

        if (item .getItemType() == TYPE_MEDIA && (((MediaWrapper) item).getType() == MediaWrapper.TYPE_VIDEO || ((MediaWrapper) item).getType() == MediaWrapper.TYPE_AUDIO))
            mMediaCount++;

        mMediaList.add(position, item);
        if (notify)
            notifyItemInserted(position);
    }

    public void setTop (int top) {
        mTop = top;
    }

    void setDescription(int position, String description){
        Object item = getItem(position);
        if (item instanceof MediaWrapper)
            ((MediaWrapper) item).setDescription(description);
        else if (item instanceof Storage)
            ((Storage) item).setDescription(description);
        else
            return;
        WeakReference<MediaViewHolder> wr = mHolders.get(position);
        if (wr != null && wr.get() != null)
            wr.get().binding.setItem((MediaLibraryItem) item);
    }

    public void addAll(ArrayList<MediaWrapper> mediaList){
        mMediaList.clear();
        boolean isHoneyComb = AndroidUtil.isHoneycombOrLater();
        for (MediaWrapper mw : mediaList) {
            mMediaList.add(mw);
            if (mw.getType() == MediaWrapper.TYPE_AUDIO || (isHoneyComb && mw.getType() == MediaWrapper.TYPE_VIDEO))
                mMediaCount++;
        }
    }

    void removeItem(int position, boolean notify){
        MediaLibraryItem item = mMediaList.get(position);
        mMediaList.remove(position);
        if (notify) {
            notifyItemRemoved(position);
        }
        if (item .getItemType() == TYPE_MEDIA && (((MediaWrapper) item).getType() == MediaWrapper.TYPE_VIDEO || ((MediaWrapper) item).getType() == MediaWrapper.TYPE_AUDIO))
            mMediaCount--;
    }

    void removeItem(String path, boolean notify){
        int position = -1;
        for (int i = 0; i< getItemCount(); ++i) {
            MediaLibraryItem item = mMediaList.get(i);
            if (item .getItemType() == TYPE_MEDIA && TextUtils.equals(path, ((MediaWrapper) item).getUri().toString())) {
                position = i;
                if (((MediaWrapper) item).getType() == MediaWrapper.TYPE_VIDEO || ((MediaWrapper) item).getType() == MediaWrapper.TYPE_AUDIO)
                    mMediaCount--;
            }
        }
        if (position == -1)
            return;
        mMediaList.remove(position);
        if (notify) {
            notifyItemRemoved(position);
        }
    }

    public ArrayList<MediaLibraryItem> getAll(){
        return mMediaList;
    }

    public MediaLibraryItem getItem(int position){
        if (position < 0 || position >= mMediaList.size())
            return null;
        return mMediaList.get(position);
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

    protected void checkBoxAction(View v, String path){}

    protected void openMediaFromView(MediaViewHolder holder, View v) {
        final MediaWrapper mw = (MediaWrapper) getItem(holder.getAdapterPosition());
        if (mw == null)
            return;
        mw.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);

        if (mw.getType() == MediaWrapper.TYPE_DIR)
            fragment.browse(mw, holder.getAdapterPosition(), true);
        else
            MediaUtils.openMedia(v.getContext(), mw);

    }

    void setActionMode(boolean actionMode) {
        mActionMode = actionMode;
        if (!actionMode) {
            LinkedList<Integer> positions = new LinkedList<>(mSelectedItems);
            mSelectedItems.clear();
            for (Integer position : positions)
                notifyItemChanged(position);
        }
    }

    List<Integer> getSelectedPositions() {
        return mSelectedItems;
    }

    ArrayList<MediaWrapper> getSelection() {
        ArrayList<MediaWrapper> selection = new ArrayList<>();
        for (Integer selected : mSelectedItems) {
            MediaWrapper media = (MediaWrapper) mMediaList.get(selected);
            selection.add(media);
        }
        return selection;
    }


    @Override
    public Filter getFilter() {
        return mFilter;
    }

    void restoreList() {
        if (mOriginalData != null) {
            mMediaList.clear();
            mMediaList.addAll(mOriginalData);
            mOriginalData = null;
            notifyDataSetChanged();
        }
    }

    private class ItemFilter extends MediaItemFilter {

        @Override
        protected List<MediaLibraryItem> initData() {
            if (mOriginalData == null)
                mOriginalData = new ArrayList<>(mMediaList);
            Log.d(TAG, "initData: "+mOriginalData.size());
            return mOriginalData;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            mMediaList.clear();
            mMediaList.addAll((Collection<MediaWrapper>) filterResults.values);
            notifyDataSetChanged();
        }
    }
}
