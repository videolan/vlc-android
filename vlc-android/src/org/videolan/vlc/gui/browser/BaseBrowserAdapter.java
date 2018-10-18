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

import android.annotation.TargetApi;
import androidx.databinding.ViewDataBinding;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Storage;
import org.videolan.tools.MultiSelectAdapter;
import org.videolan.tools.MultiSelectHelper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.BrowserItemBinding;
import org.videolan.vlc.databinding.BrowserItemSeparatorBinding;
import org.videolan.vlc.gui.DiffUtilAdapter;
import org.videolan.vlc.gui.helpers.SelectorViewHolder;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;

import java.util.ArrayList;
import java.util.List;

import static org.videolan.medialibrary.media.MediaLibraryItem.TYPE_MEDIA;
import static org.videolan.medialibrary.media.MediaLibraryItem.TYPE_STORAGE;

public class BaseBrowserAdapter extends DiffUtilAdapter<MediaLibraryItem, BaseBrowserAdapter.ViewHolder> implements MultiSelectAdapter<MediaLibraryItem> {
    protected static final String TAG = "VLC/BaseBrowserAdapter";

    private static int FOLDER_RES_ID = R.drawable.ic_menu_folder;

    private MultiSelectHelper<MediaLibraryItem> multiSelectHelper;

    private static class Images {
        private static final BitmapDrawable IMAGE_FOLDER = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), FOLDER_RES_ID));
        private static final BitmapDrawable IMAGE_AUDIO = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_audio_normal));
        private static final BitmapDrawable IMAGE_VIDEO = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_video_normal));
        private static final BitmapDrawable IMAGE_SUBTITLE = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_subtitle_normal));
        private static final BitmapDrawable IMAGE_UNKNOWN = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_unknown_normal));
        private static final BitmapDrawable IMAGE_QA_MOVIES = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_movies_normal));
        private static final BitmapDrawable IMAGE_QA_MUSIC = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_music_normal));
        private static final BitmapDrawable IMAGE_QA_PODCASTS = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_podcasts_normal));
        private static final BitmapDrawable IMAGE_QA_DOWNLOAD = new BitmapDrawable(VLCApplication.getAppResources(), BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_browser_download_normal));
    }
    protected final BaseBrowserFragment fragment;
    private int mMediaCount = 0;
    private final boolean mNetworkRoot, mSpecialIcons;

    BaseBrowserAdapter(BaseBrowserFragment fragment) {
        this.fragment = fragment;
        multiSelectHelper = new MultiSelectHelper<>(this, Constants.UPDATE_SELECTION);
        final boolean root = fragment.isRootDirectory();
        final boolean fileBrowser = fragment instanceof FileBrowserFragment;
        final boolean filesRoot = root && fileBrowser;
        mNetworkRoot = root && fragment instanceof NetworkBrowserFragment;
        final String mrl = fragment.getMrl();
        mSpecialIcons = filesRoot || fileBrowser && mrl != null && mrl.endsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
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
        } else if (payloads.get(0) instanceof Integer) {
            final int value = (Integer) payloads.get(0);
            if (value == Constants.UPDATE_SELECTION) holder.selectView(multiSelectHelper.isSelected(position));
        }
    }

    private void onBindMediaViewHolder(final MediaViewHolder vh, int position) {
        final MediaWrapper media = (MediaWrapper) getItem(position);
        final boolean isFavorite = media.hasStateFlags(MediaLibraryItem.FLAG_FAVORITE);
        vh.binding.setItem(media);
        vh.binding.setHasContextMenu((!mNetworkRoot || isFavorite)
                && !"content".equals(media.getUri().getScheme())
                && !"otg".equals(media.getUri().getScheme()));
        if (media.getType() != MediaWrapper.TYPE_DIR) vh.binding.setFilename(media.getFileName());
        if (mNetworkRoot) vh.binding.setProtocol(getProtocol(media));
        vh.binding.setCover(getIcon(media, mSpecialIcons));
        vh.selectView(multiSelectHelper.isSelected(position));
    }

    @Override
    public int getItemCount() {
        return getDataset().size();
    }

    public MultiSelectHelper<MediaLibraryItem> getMultiSelectHelper() {
        return multiSelectHelper;
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

        @TargetApi(Build.VERSION_CODES.M)
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
            fragment.browse(mw, binding.browserCheckbox.isChecked());
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
            if (getItem(position).getItemType() == TYPE_STORAGE && AndroidDevices.showTvUi(itemView.getContext())) {
                binding.browserCheckbox.toggle();
                onCheckBoxClick(binding.browserCheckbox);
                return true;
            }
            return position < getDataset().size() && position >= 0
                    && fragment.onLongClick(v, position, getDataset().get(position));
        }

        @Override
        protected boolean isSelected() {
            return multiSelectHelper.isSelected(getLayoutPosition());
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
                return Images.IMAGE_AUDIO;
            case MediaWrapper.TYPE_DIR:
                if (specialFolders) {
                    final Uri uri = media.getUri();
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MOVIES_DIRECTORY_URI.equals(uri)
                            || AndroidDevices.MediaFolders.WHATSAPP_VIDEOS_FILE_URI.equals(uri))
                        return Images.IMAGE_QA_MOVIES;
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MUSIC_DIRECTORY_URI.equals(uri))
                        return Images.IMAGE_QA_MUSIC;
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_PODCAST_DIRECTORY_URI.equals(uri))
                        return Images.IMAGE_QA_PODCASTS;
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI.equals(uri))
                        return Images.IMAGE_QA_DOWNLOAD;
                }
                return Images.IMAGE_FOLDER;
            case MediaWrapper.TYPE_VIDEO:
                return Images.IMAGE_VIDEO;
            case MediaWrapper.TYPE_SUBTITLE:
                return Images.IMAGE_SUBTITLE;
            default:
                return Images.IMAGE_UNKNOWN;
        }
    }

    private String getProtocol(MediaWrapper media) {
        if (media.getType() != MediaWrapper.TYPE_DIR)
            return null;
        return media.getUri().getScheme();
    }

    protected void checkBoxAction(View v, String mrl){}

    @SuppressWarnings("unchecked")
    @Override
    protected List<MediaLibraryItem> prepareList(List<? extends MediaLibraryItem> list) {
        final List<MediaLibraryItem> internalList = new ArrayList<>(list);
        mMediaCount = 0;
        for (MediaLibraryItem item : internalList) {
            if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA
                    && (((MediaWrapper)item).getType() == MediaWrapper.TYPE_AUDIO || ((MediaWrapper)item).getType() == MediaWrapper.TYPE_VIDEO))
                ++mMediaCount;
        }
        return internalList;
    }

    @Override
    protected void onUpdateFinished() {
        fragment.onUpdateFinished(this);
    }
}
