package org.videolan.vlc.gui;

import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.databinding.SearchItemBinding;
import org.videolan.vlc.gui.helpers.SelectorViewHolder;
import org.videolan.vlc.gui.helpers.UiTools;


public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

    MediaLibraryItem[] mDataList;
    SearchActivity.ClickHandler mClickHandler;
    private final LayoutInflater mLayoutInflater;

    SearchResultAdapter(LayoutInflater inflater) {
        super();
        mLayoutInflater = inflater;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(SearchItemBinding.inflate(mLayoutInflater, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (TextUtils.isEmpty(mDataList[position].getArtworkMrl()))
            holder.binding.setCover(UiTools.getDefaultCover(mDataList[position]));
        holder.binding.setItem(mDataList[position]);
    }

    public void add(MediaLibraryItem[] newList) {
        mDataList = newList;
        notifyDataSetChanged();
    }

    void setClickHandler(SearchActivity.ClickHandler clickHandler) {
        mClickHandler = clickHandler;
    }

    @Override
    public int getItemCount() {
        return mDataList == null ? 0 : mDataList.length;
    }

    public class ViewHolder extends SelectorViewHolder<SearchItemBinding> {

        public ViewHolder(SearchItemBinding binding) {
            super(binding);
            binding.setHolder(this);
            binding.setHandler(mClickHandler);
        }
    }
}
