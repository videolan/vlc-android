package org.videolan.vlc.util;

import android.widget.Filter;

import org.videolan.medialibrary.media.MediaLibraryItem;

import java.util.ArrayList;
import java.util.List;


public abstract class MediaItemFilter extends Filter {

    protected abstract List<? extends MediaLibraryItem> initData();

        @Override
        protected FilterResults performFiltering(CharSequence charSequence) {
            List<? extends MediaLibraryItem> referenceDataList = initData();
            final String[] queryStrings = charSequence.toString().trim().toLowerCase().split(" ");
            FilterResults results = new FilterResults();
            ArrayList<MediaLibraryItem> list = new ArrayList<>(referenceDataList.size());
            MediaLibraryItem media;
            for (int i = 0 ; i < referenceDataList.size() ; ++i) {
                media = referenceDataList.get(i);
                for (String queryString : queryStrings) {
                    if (queryString.length() < 2)
                        continue;
                    if (media.getTitle() != null && media.getTitle().toLowerCase().contains(queryString)) {
                        list.add(media);
                        break ; //avoid duplicates in search results, and skip useless processing
                    }
                }
            }
            results.values = list;
            results.count = list.size();
            return results;
        }
}
