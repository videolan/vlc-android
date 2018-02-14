/**
 * **************************************************************************
 * BaseBrowserAdapter.java
 * ****************************************************************************
 * Copyright © 2015-2017 VLC authors and VideoLAN
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
import android.net.Uri;
import android.support.annotation.MainThread;
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
import org.videolan.vlc.gui.helpers.SelectorViewHolder;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.MediaItemFilter;
import org.videolan.vlc.util.MediaLibraryItemComparator;
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

    private static final BitmapDrawable IMAGE_QA_MOVIES = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_movies_normal));
    private static final BitmapDrawable IMAGE_QA_MUSIC = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_music_normal));
    private static final BitmapDrawable IMAGE_QA_PODCASTS = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_podcasts_normal));
    private static final BitmapDrawable IMAGE_QA_DOWNLOAD = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_download_normal));

    private List<MediaLibraryItem> mOriginalData = null;
    protected final BaseBrowserFragment fragment;
    private int mTop = 0, mMediaCount = 0, mSelectionCount = 0;
    private ItemFilter mFilter = new ItemFilter();
    private final boolean mFilesRoot, mNetworkRoot, mSpecialIcons;

    BaseBrowserAdapter(BaseBrowserFragment fragment) {
        this.fragment = fragment;
        final boolean root = fragment.isRootDirectory();
        final boolean fileBrowser = fragment instanceof FileBrowserFragment;
        mFilesRoot = root && fileBrowser;
        mNetworkRoot = root && fragment instanceof NetworkBrowserFragment;
        final String mrl = fragment.mMrl;
        mSpecialIcons = mFilesRoot || fileBrowser && mrl != null && mrl.endsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
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
            vh.binding.setTitle(getDataset().get(position).getTitle());
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (payloads.isEmpty()) onBindViewHolder(holder, position);
        else if (payloads.get(0) instanceof CharSequence){
            ((MediaViewHolder) holder).binding.text.setVisibility(View.VISIBLE);
            ((MediaViewHolder) holder).binding.text.setText((CharSequence) payloads.get(0));
        } else if (payloads.get(0) instanceof MediaWrapper)
            holder.selectView(((MediaWrapper)payloads.get(0)).hasStateFlags(FLAG_SELECTED));
    }

    private void onBindMediaViewHolder(final MediaViewHolder vh, int position) {
        final MediaWrapper media = (MediaWrapper) getItem(position);
        vh.binding.setItem(media);
        vh.binding.setHasContextMenu(true);
        if (mNetworkRoot) vh.binding.setProtocol(getProtocol(media));
        vh.binding.setCover(getIcon(media, mSpecialIcons));
        vh.selectView(media.hasStateFlags(FLAG_SELECTED));
    }

    @Override
    public int getItemCount() {
        return getDataset().size();
    }

    public MediaLibraryItem get(int position) {
        return getDataset().get(position);
    }

    public abstract class ViewHolder<T extends ViewDataBinding> extends SelectorViewHolder<T> {

        public ViewHolder(T binding) {
            super(binding);
        }

        public void onClick(View v){}

        public boolean onLongClick(View v){ return false; }

        public void onCheckBoxClick(View v){}

        public void onMoreClick(View v){}

        public abstract int getType();

    }

    class MediaViewHolder extends ViewHolder<BrowserItemBinding> implements View.OnFocusChangeListener {

        MediaViewHolder(final BrowserItemBinding binding) {
            super(binding);
            binding.setHolder(this);
            if (AndroidUtil.isMarshMallowOrLater) itemView.setOnContextClickListener(new View.OnContextClickListener() {
                @Override
                public boolean onContextClick(View v) {
                    onMoreClick(v);
                    return true;
                }
            });
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
            if (position < getDataset().size() && position >= 0)
                fragment.onClick(v, position, getDataset().get(position));
        }

        public void onMoreClick(View v) {
            int position = getLayoutPosition();
            if (position < getDataset().size() && position >= 0)
                fragment.onCtxClick(v, position, getDataset().get(position));
        }

        public boolean onLongClick(View v) {
            int position = getLayoutPosition();
            if (getItem(position).getItemType() == TYPE_STORAGE) {
                binding.browserCheckbox.toggle();
                onCheckBoxClick(binding.browserCheckbox);
                return true;
            }
            return position < getDataset().size() && position >= 0
                    && fragment.onLongClick(v, position, getDataset().get(position));
        }

        @Override
        protected boolean isSelected() {
            final MediaLibraryItem item = getItem(getLayoutPosition());
            return item != null && item.hasStateFlags(FLAG_SELECTED);
        }
    }

    private class SeparatorViewHolder extends ViewHolder<BrowserItemSeparatorBinding> {

        public SeparatorViewHolder(BrowserItemSeparatorBinding binding) {
            super(binding);
        }

        @Override
        public int getType() {
            return MediaLibraryItem.TYPE_DUMMY;
        }
    }

    public void clear() {
        if (!isEmpty()) update(new ArrayList<MediaLibraryItem>(0));
    }

    public boolean isEmpty() {
        return Util.isListEmpty(peekLast());
    }

    @MainThread
    public void addItem(MediaLibraryItem item, boolean top) {
        addItem(item, top, -1);
    }

    void addItem(final MediaLibraryItem item, final boolean top, final int positionTo) {
        VLCApplication.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                int position;
                final List<MediaLibraryItem> list = new ArrayList<>(peekLast());
                if (positionTo != -1) position = positionTo;
                else position = top ? mTop : list.size();

                if (position <= list.size()) {
                    list.add(position, item);
                    update(list);
                }
            }
        });
    }

    public void setTop (int top) {
        mTop = top;
    }

    void removeItem(int position) {
        if (position < getItemCount()) removeItem(getDataset().get(position));
    }

    void removeItem(MediaLibraryItem item) {
        if (item.getItemType() == TYPE_MEDIA && (((MediaWrapper) item).getType() == MediaWrapper.TYPE_VIDEO || ((MediaWrapper) item).getType() == MediaWrapper.TYPE_AUDIO))
            mMediaCount--;
        final List<MediaLibraryItem> list = new ArrayList<>(peekLast());
        list.remove(item);
        //Force adapter to sort items.
        if (sMediaComparator.sortBy == MediaLibraryItemComparator.SORT_DEFAULT) sMediaComparator.sortBy = getDefaultSort();
        update(list);
    }

    void removeItem(String path) {

        MediaLibraryItem mediaItem = null;
        for (MediaLibraryItem item : peekLast()) {
            if (item .getItemType() == TYPE_MEDIA && TextUtils.equals(path, ((MediaWrapper) item).getUri().toString())) {
                mediaItem = item;
                break;
            }
        }
        if (mediaItem != null) removeItem(mediaItem);
    }

    public List<MediaLibraryItem> getAll(){
        return getDataset();
    }

    public MediaLibraryItem getItem(int position){
        if (position < 0 || position >= getDataset().size())
            return null;
        return getDataset().get(position);
    }

    public int getItemViewType(int position){
        return getItem(position).getItemType();
    }

    int getMediaCount() {
        return mMediaCount;
    }

    BitmapDrawable getIcon(MediaWrapper media, boolean specialFolders) {
        switch (media.getType()){
            case MediaWrapper.TYPE_AUDIO:
                return IMAGE_AUDIO;
            case MediaWrapper.TYPE_DIR:
                if (specialFolders) {
                    final Uri uri = media.getUri();
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MOVIES_DIRECTORY_URI.equals(uri)
                            || AndroidDevices.MediaFolders.WHATSAPP_VIDEOS_FILE_URI.equals(uri))
                        return IMAGE_QA_MOVIES;
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MUSIC_DIRECTORY_URI.equals(uri))
                        return IMAGE_QA_MUSIC;
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_PODCAST_DIRECTORY_URI.equals(uri))
                        return IMAGE_QA_PODCASTS;
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI.equals(uri))
                        return IMAGE_QA_DOWNLOAD;
                }
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
        if (media.getType() != MediaWrapper.TYPE_DIR)
            return null;
        return media.getUri().getScheme();
    }

    protected void checkBoxAction(View v, String mrl){}

    List<MediaWrapper> getSelection() {
        List<MediaWrapper> selection = new ArrayList<>();
        for (MediaLibraryItem item : getDataset()) {
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

    @SuppressWarnings("unchecked")
    @Override
    protected List<MediaLibraryItem> prepareList(List<? extends MediaLibraryItem> list) {
        if (fragment.isSortEnabled() && needsSorting())
            Collections.sort(list, sMediaComparator);
        mMediaCount = 0;
        for (MediaLibraryItem item : list) {
            if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA
                    && (((MediaWrapper)item).getType() == MediaWrapper.TYPE_AUDIO|| (AndroidUtil.isHoneycombOrLater && ((MediaWrapper)item).getType() == MediaWrapper.TYPE_VIDEO)))
                ++mMediaCount;
        }
        return (List<MediaLibraryItem>) list;
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
                mOriginalData = new ArrayList<>(getDataset());
            return mOriginalData;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            update((List<MediaLibraryItem>) filterResults.values);
        }
    }
}
