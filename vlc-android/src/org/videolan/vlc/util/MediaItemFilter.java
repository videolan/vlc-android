package org.videolan.vlc.util;

import android.widget.Filter;

import org.videolan.medialibrary.media.MediaLibraryItem;

import java.util.ArrayList;
import java.util.List;


public abstract class MediaItemFilter extends Filter {

    protected static final String TAG = "VLC/MediaItemFilter";

    protected abstract List<? extends MediaLibraryItem> initData();

    @Override
    protected FilterResults performFiltering(CharSequence charSequence) {
        final String[] queryStrings = charSequence.toString().trim().toLowerCase().split(" ");
        FilterResults results = new FilterResults();
        ArrayList<MediaLibraryItem> list = new ArrayList<>();
        for (MediaLibraryItem item : initData()) {
            for (String queryString : queryStrings) {
                if (queryString.length() < 2)
                    continue;
                if (item.getTitle() != null && item.getTitle().toLowerCase().contains(queryString)) {
                    list.add(item);
                    break ; //avoid duplicates in search results, and skip useless processing
                }
            }
        }
        results.values = list;
        results.count = list.size();
        return results;
    }
}
