package org.videolan.vlc.interfaces;


import android.widget.Filter;

public interface Filterable {
    Filter getFilter();
    void restoreList();
}
