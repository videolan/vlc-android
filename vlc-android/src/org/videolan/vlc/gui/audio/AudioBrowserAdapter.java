package org.videolan.vlc.gui.audio;

import android.app.Activity;
import android.content.Context;
import android.databinding.ViewDataBinding;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.medialibrary.media.DummyItem;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.BR;
import org.videolan.vlc.databinding.AudioBrowserItemBinding;
import org.videolan.vlc.databinding.AudioBrowserSeparatorBinding;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;

import java.util.ArrayList;
import java.util.Arrays;

public class AudioBrowserAdapter extends RecyclerView.Adapter<AudioBrowserAdapter.ViewHolder> {

    private static final String TAG = "VLC/AudioBrowserAdapter";

    public static final int TYPE_MEDIA = 0;
    public static final int TYPE_SEPARATOR = 1;
    private boolean mMakeSections = true;

    public interface ClickHandler {
        void onClick(View v, int position, MediaLibraryItem item);
        void onCtxClick(View v, int position, MediaLibraryItem item);
    }

    ArrayList<MediaLibraryItem> mDataList = new ArrayList<>();
    ArrayList<DummyItem> mSeparators = new ArrayList<>();
    Activity mContext;
    ClickHandler mClickHandler;

    public AudioBrowserAdapter(Activity context, ClickHandler clickHandler, boolean sections) {
        mContext = context;
        mClickHandler = clickHandler;
        mMakeSections = sections;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (viewType == TYPE_SEPARATOR) {
            AudioBrowserSeparatorBinding binding = AudioBrowserSeparatorBinding.inflate(inflater, parent, false);
            return new ViewHolder(binding);
        } else {
            AudioBrowserItemBinding binding = AudioBrowserItemBinding.inflate(inflater, parent, false);
            return new MediaItemViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (position >= mDataList.size())
            return;
        holder.vdb.setVariable(BR.item, mDataList.get(position));
        if (holder.getType() == TYPE_MEDIA)
            holder.vdb.setVariable(BR.cover, AsyncImageLoader.DEFAULT_COVER_AUDIO_DRAWABLE);
    }

    @Override
    public int getItemCount() {
        return mDataList == null ? 0 :  mDataList.size();
    }

    public MediaLibraryItem getItem(int position) {
        if (position < 0 || position >= mDataList.size())
            return null;
        return mDataList.get(position);
    }

    public ArrayList<MediaLibraryItem> getAll() {
        return mDataList;
    }

    public ArrayList<MediaLibraryItem> getMediaItems() {
        ArrayList<MediaLibraryItem> list = new ArrayList<>();
        for (MediaLibraryItem item : mDataList)
            if (!(item instanceof DummyItem))
                list.add(item);
        return list;
    }

    public int getListWithPosition(ArrayList<MediaLibraryItem> list, int position) {
        int offset = 0;
        for (int i = 0; i < mDataList.size(); ++i)
            if (mDataList.get(i) instanceof DummyItem) {
                if (i < position)
                    ++offset;
            } else
                    list.add(mDataList.get(i));
        return position-offset;
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= mDataList.size())
            return super.getItemId(position);
        else
            return mDataList.get(position).getId();
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position) instanceof DummyItem ? TYPE_SEPARATOR : TYPE_MEDIA;
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    public void clear() {
        mDataList.clear();
        notifyDataSetChanged();
    }

    public void addAll(MediaLibraryItem[] items) {
        if (mContext == null)
            return;
        if (mMakeSections) {
            mDataList.clear();
            boolean isLetter;
            String currentLetter = null;
            for (MediaLibraryItem item : items) {
                String title = item.getTitle();
                if (TextUtils.isEmpty(title))
                    continue;
                String firstLetter = title.substring(0, 1).toUpperCase();
                isLetter = Character.isLetter(title.charAt(0));
                if (currentLetter == null) {
                    currentLetter = isLetter ? firstLetter : "#";
                    DummyItem sep = new DummyItem(currentLetter);
                    mDataList.add(sep);
                    mSeparators.add(sep);
                }
                //Add a new separator
                if (isLetter && !TextUtils.equals(currentLetter, firstLetter)) {
                    currentLetter = firstLetter;
                    DummyItem sep = new DummyItem(currentLetter);
                    mDataList.add(sep);
                    mSeparators.add(sep);
                }
                    mDataList.add(item);
            }
        } else
            mDataList = new ArrayList<>(Arrays.asList(items));
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    public void remove(final int position) {
        mDataList.remove(position);
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyItemRemoved(position);
            }
        });
    }

    public void addItem(final int position, MediaLibraryItem item) {
        mDataList.add(position, item);
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyItemInserted(position);
            }
        });
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ViewDataBinding vdb;

        public ViewHolder(ViewDataBinding vdb) {
            super(vdb.getRoot());
            this.vdb = vdb;
        }

        public int getType() {
            return TYPE_SEPARATOR;
        }
    }
    public class MediaItemViewHolder extends ViewHolder implements View.OnLongClickListener {

        public MediaItemViewHolder(AudioBrowserItemBinding binding) {
            super(binding);
            binding.setHolder(this);
            itemView.setOnLongClickListener(this);
        }

        public void onClick(View v) {
            if (mClickHandler != null)
                mClickHandler.onClick(v, getLayoutPosition(), ((AudioBrowserItemBinding)vdb).getItem());
        }

        public void onMoreClick(View v) {
            if (mClickHandler != null)
                mClickHandler.onCtxClick(v, getLayoutPosition(), ((AudioBrowserItemBinding)vdb).getItem());
        }

        @Override
        public boolean onLongClick(View view) {
            onMoreClick(view);
            return true;
        }

        public int getType() {
            return TYPE_MEDIA;
        }
    }
}
