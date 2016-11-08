package org.videolan.vlc.interfaces;


import android.widget.Filter;

public interface Filterable {
    boolean enableSearchOption();
    Filter getFilter();
    void restoreList();
    void setSearchVisibility(boolean visible);
}
