package org.videolan.vlc.gui.browser;

import android.content.res.Resources;
import android.databinding.DataBindingUtil;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.vlc.R;
import org.videolan.vlc.databinding.ExtensionItemViewBinding;
import org.videolan.vlc.extensions.api.VLCExtensionItem;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.HttpImageLoader;

import java.util.ArrayList;
import java.util.List;

public class ExtensionAdapter extends RecyclerView.Adapter<ExtensionAdapter.ViewHolder> {

    ExtensionBrowser mFragment;
    List<VLCExtensionItem> mItemsList = new ArrayList<>();

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener {

        public ExtensionItemViewBinding binding;

        public ViewHolder(ExtensionItemViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnLongClickListener(this);
            binding.setHolder(this);
        }

        public void onClick(View v){
            final VLCExtensionItem item = getItem(getLayoutPosition());
            if (item.type == VLCExtensionItem.TYPE_DIRECTORY) {
                mFragment.browseItem(item);
            } else if (item.type == VLCExtensionItem.TYPE_AUDIO || item.type == VLCExtensionItem.TYPE_VIDEO){
                MediaWrapper mw = new MediaWrapper(Uri.parse(item.link));
                mw.setDisplayTitle(item.getTitle());
                mw.setDescription(item.getSubTitle());
                mw.setType(getTypeAccordingToItem(item.type));
                MediaUtils.openMedia(v.getContext(), mw);
            }
        }

        public void onMoreClick(View v){
            openContextMenu();
        }

        @Override
        public boolean onLongClick(View v) {
            return openContextMenu();
        }

        private boolean openContextMenu() {
            if (mFragment == null)
                return false;
            mFragment.openContextMenu(getLayoutPosition());
            return true;
        }
    }

    public ExtensionAdapter(ExtensionBrowser fragment) {
        mFragment = fragment;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder((ExtensionItemViewBinding) DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()),R.layout.extension_item_view, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final ViewHolder vh = holder;
        final VLCExtensionItem item = getItem(position);
        vh.binding.setItem(item);
        vh.binding.executePendingBindings();
        Resources res = holder.itemView.getContext().getResources();
        vh.binding.setImage(new BitmapDrawable(res, BitmapFactory.decodeResource(res, getIconResId(item))));
        if (item.imageUri != null && (TextUtils.equals("http", item.imageUri.getScheme())))
                AsyncImageLoader.LoadImage(new HttpImageLoader(item.getImageUri().toString(), holder.binding), null);
    }

    private int getIconResId(VLCExtensionItem item) {
        switch (item.type){
            case VLCExtensionItem.TYPE_AUDIO:
                return R.drawable.ic_browser_audio_normal;
            case VLCExtensionItem.TYPE_DIRECTORY:
                return R.drawable.ic_menu_folder;
            case VLCExtensionItem.TYPE_VIDEO:
                return R.drawable.ic_browser_video_normal;
            case VLCExtensionItem.TYPE_SUBTITLE:
                return R.drawable.ic_browser_subtitle_normal;
            default:
                return R.drawable.ic_browser_unknown_normal;
        }
    }

    public VLCExtensionItem getItem(int position) {
        return mItemsList.get(position);
    }

    public List<VLCExtensionItem> getAll() {
        return mItemsList;
    }

    @Override
    public int getItemCount() {
        return mItemsList.size();
    }

    public void addAll(List<VLCExtensionItem> list) {
        mItemsList.clear();
        mItemsList.addAll(list);
        notifyDataSetChanged();
    }

    public void clear() {
        mItemsList.clear();
    }

    private int getTypeAccordingToItem(int type) {
        switch (type) {
            case VLCExtensionItem.TYPE_DIRECTORY:
                return MediaWrapper.TYPE_DIR;
            case VLCExtensionItem.TYPE_VIDEO:
                return MediaWrapper.TYPE_VIDEO;
            case VLCExtensionItem.TYPE_AUDIO:
                return MediaWrapper.TYPE_AUDIO;
            case VLCExtensionItem.TYPE_PLAYLIST:
                return MediaWrapper.TYPE_PLAYLIST;
            case VLCExtensionItem.TYPE_SUBTITLE:
                return MediaWrapper.TYPE_SUBTITLE;
            default:
                return MediaWrapper.TYPE_ALL;
        }
    }
}
