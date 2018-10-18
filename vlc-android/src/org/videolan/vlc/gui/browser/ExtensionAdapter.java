package org.videolan.vlc.gui.browser;

import androidx.databinding.DataBindingUtil;
import android.net.Uri;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.ExtensionItemViewBinding;
import org.videolan.vlc.extensions.api.VLCExtensionItem;
import org.videolan.vlc.media.MediaUtils;

import java.util.ArrayList;
import java.util.List;

public class ExtensionAdapter extends RecyclerView.Adapter<ExtensionAdapter.ViewHolder> {
    private static final String TAG = "VLC/ExtensionAdapter";

    ExtensionBrowser mFragment;
    List<VLCExtensionItem> mItemsList = new ArrayList<>();

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener {

        public ExtensionItemViewBinding binding;

        public ViewHolder(ExtensionItemViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
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
                MediaUtils.INSTANCE.openMedia(v.getContext(), mw);
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
        final VLCExtensionItem item = getItem(position);
        holder.binding.setItem(item);
        holder.binding.executePendingBindings();
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
