package org.videolan.vlc.gui.browser;

import android.content.res.Resources;
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.ExtensionItemViewBinding;
import org.videolan.vlc.extensions.api.VLCExtensionItem;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ExtensionAdapter extends RecyclerView.Adapter<ExtensionAdapter.ViewHolder> {

    ExtensionBrowser mFragment;
    List<VLCExtensionItem> mItemsList = new ArrayList<>();
    static HashMap<String, SoftReference<Bitmap>> iconsMap = new HashMap<>();

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener {

        ExtensionItemViewBinding binding;

        public ViewHolder(View itemView) {
            super(itemView);
            binding = DataBindingUtil.bind(itemView);
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
        View v;
        v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.extension_item_view, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final ViewHolder vh = holder;
        final VLCExtensionItem item = getItem(position);
        vh.binding.setItem(item);
        vh.binding.executePendingBindings();
        Resources res = holder.itemView.getContext().getResources();
        vh.binding.setImage(new BitmapDrawable(res, BitmapFactory.decodeResource(res, getIconResId(item))));
        if (item.imageUri != null) {
            if (TextUtils.equals("http", item.imageUri.getScheme()))
                AsyncImageLoader.LoadImage(new HttpImageFetcher(holder.binding, item.getImageUri().toString()), null);
        }
    }

    private static class HttpImageFetcher extends AsyncImageLoader.CoverFetcher {
        final String imageLink;

        HttpImageFetcher(ViewDataBinding binding, String imageLink) {
            super(binding);
            this.imageLink = imageLink;
        }

        @Override
        public Bitmap getImage() {
            if (iconsMap.containsKey(imageLink)) {
                Bitmap bd = iconsMap.get(imageLink).get();
                if (bd != null) {
                    return bd;
                } else
                    iconsMap.remove(imageLink);
            }
            HttpURLConnection urlConnection = null;
            Bitmap icon = null;
            try {
                URL url = new URL(imageLink);
                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                icon = BitmapFactory.decodeStream(in);
                iconsMap.put(imageLink, new SoftReference<>(icon));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
            }
            return icon;
        }

        @Override
        public void updateBindImage(Bitmap bitmap, View target) {
            if (bitmap != null && (bitmap.getWidth() != 1 && bitmap.getHeight() != 1)) {
                binding.setVariable(BR.scaleType, ImageView.ScaleType.FIT_CENTER);
                binding.setVariable(BR.image, new BitmapDrawable(VLCApplication.getAppResources(), bitmap));
            }
        }
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
