/*
 * ************************************************************************
 *  SongsBrowserFragment.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */

package org.videolan.vlc.gui.tv.browser;

import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.audio.AudioBrowserAdapter;
import org.videolan.vlc.gui.tv.SongHeaderAdapter;
import org.videolan.vlc.gui.tv.TvUtil;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserFragmentInterface;
import org.videolan.vlc.gui.view.RecyclerSectionItemGridDecoration;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.util.KextensionsKt;
import org.videolan.vlc.util.WorkersKt;
import org.videolan.vlc.viewmodels.paged.MLPagedModel;
import org.videolan.vlc.viewmodels.paged.PagedTracksModel;

import java.util.ArrayList;
import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.collection.SparseArrayCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class SongsBrowserFragment extends Fragment implements BrowserFragmentInterface, IEventsHandler, PopupMenu.OnMenuItemClickListener, SongHeaderAdapter.OnHeaderSelected, VerticalGridActivity.OnBackPressedListener {

    private MLPagedModel<MediaLibraryItem> viewModel;
    private RecyclerView list;
    private AudioBrowserAdapter adapter;
    private RecyclerView headerList;
    private SongHeaderAdapter headerAdapter;
    private View headerListContainer;
    private int nbColumns;
    private GridLayoutManager gridLayoutManager;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.song_browser, container, false);
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = (MLPagedModel) ViewModelProviders.of(this, new PagedTracksModel.Factory(requireContext(), null)).get(PagedTracksModel.class);

        viewModel.getPagedList().observe(this, new Observer<PagedList<MediaLibraryItem>>() {
            @Override
            public void onChanged(@androidx.annotation.Nullable PagedList<MediaLibraryItem> items) {
                if (items != null) adapter.submitList(items);

                //headers

                int nbColumns = 1;

                switch (viewModel.getSort()) {
                    case Medialibrary.SORT_ALPHA:
                        nbColumns = 9;
                        break;
                }

                headerList.setLayoutManager(new GridLayoutManager(requireActivity(), nbColumns));
                headerAdapter.setSortType(viewModel.getSort());
                final SparseArrayCompat<String> headers = viewModel.getLiveHeaders().getValue();
                ArrayList<String> headerItems = new ArrayList<>();
                for (int i = 0; i < headers.size(); i++) {
                    headerItems.add(headers.valueAt(i));
                }
                headerAdapter.setItems(headerItems);
                headerAdapter.notifyDataSetChanged();


            }
        });

    }

    @Override
    public void onViewCreated(@NonNull final View view, @androidx.annotation.Nullable Bundle savedInstanceState) {
        list = view.findViewById(R.id.list);
        headerList = view.findViewById(R.id.headerList);
        headerListContainer = view.findViewById(R.id.headerListContainer);
        final TextView title = view.findViewById(R.id.title);
        final ImageButton sortButton = view.findViewById(R.id.sortButton);
        final ImageButton headerButton = view.findViewById(R.id.headerButton);
        final View toolbar = view.findViewById(R.id.toolbar);


        title.setText(R.string.tracks);

        sortButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sort(v);
            }
        });
        headerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                headerListContainer.setVisibility(View.VISIBLE);
                headerList.requestFocus();
                list.setVisibility(View.GONE);
            }
        });


        nbColumns = getResources().getInteger(R.integer.tv_songs_col_count);
        gridLayoutManager = new GridLayoutManager(requireActivity(), nbColumns);

        final int spacing = getResources().getDimensionPixelSize(R.dimen.recycler_section_header_spacing);

        //size of an item
        int itemSize = (KextensionsKt.getScreenWidth(requireActivity()) / nbColumns) - (spacing * 2);


        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {

                if (position == adapter.getItemCount() - 1) {
                    return 1;
                }
                if (viewModel.isFirstInSection(position + 1)) {

                    //calculate how many cell it must take
                    int firstSection = viewModel.getPositionForSection(position);
                    int nbItems = position - firstSection;
                    if (BuildConfig.DEBUG)
                        Log.d("SongsBrowserFragment", "Position: " + position + " nb items: " + nbItems + " span: " + (nbItems % nbColumns));

                    return nbColumns - (nbItems % nbColumns);
                }

                return 1;
            }
        });


        list.setLayoutManager(gridLayoutManager);

        adapter = new AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, itemSize);
        adapter.setTV(true);


        list.addItemDecoration(new RecyclerSectionItemGridDecoration(getResources().getDimensionPixelSize(R.dimen.recycler_section_header_tv_height), spacing, true, nbColumns, viewModel));
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (list.computeVerticalScrollOffset() > 0) {
                    toolbar.setVisibility(View.GONE);
                } else {
                    toolbar.setVisibility(View.VISIBLE);

                }
            }
        });

        //header list
        headerListContainer.setVisibility(View.GONE);
        headerAdapter = new SongHeaderAdapter(this);
        headerList.setAdapter(headerAdapter);
        headerList.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                outRect.bottom = 2;
                outRect.top = 2;
                outRect.left = 2;
                outRect.right = 2;
            }
        });


        super.onViewCreated(view, savedInstanceState);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        nbColumns = getResources().getInteger(R.integer.tv_songs_col_count);
        gridLayoutManager.setSpanCount(nbColumns);
        list.setLayoutManager(gridLayoutManager);
    }

    @Override
    public void onActivityCreated(@androidx.annotation.Nullable Bundle savedInstanceState) {
        list.setAdapter(adapter);
        super.onActivityCreated(savedInstanceState);
    }


    @Override
    public void refresh() {
        viewModel.refresh();
    }

    @Override
    public void onClick(@NotNull View v, int position, @NotNull final MediaLibraryItem item) {
        WorkersKt.runBackground(new Runnable() {
            @Override
            public void run() {


                ArrayList<MediaWrapper> itemList = new ArrayList<>(1);
                Collections.addAll(itemList, item.getTracks());
                TvUtil.INSTANCE.playAudioList(getActivity(), itemList, 0);

            }
        });
    }

    @Override
    public boolean onLongClick(@NotNull View v, int position, @NotNull MediaLibraryItem item) {
        return false;
    }

    @Override
    public void onCtxClick(@NotNull View v, int position, @NotNull MediaLibraryItem item) {

    }

    @Override
    public void onUpdateFinished(@NotNull RecyclerView.Adapter<?> adapter) {

    }

    @Override
    public void onImageClick(@NotNull View v, int position, @NotNull MediaLibraryItem item) {

    }

    public void sort(@NotNull View v) {
        PopupMenu menu = new PopupMenu(v.getContext(), v);
        menu.inflate(R.menu.sort_options);
        menu.getMenu().findItem(R.id.ml_menu_sortby_filename).setVisible(viewModel.canSortByFileNameName());
        menu.getMenu().findItem(R.id.ml_menu_sortby_length).setVisible(viewModel.canSortByDuration());
        menu.getMenu().findItem(R.id.ml_menu_sortby_date).setVisible(viewModel.canSortByInsertionDate() || viewModel.canSortByReleaseDate() || viewModel.canSortByLastModified());
        menu.getMenu().findItem(R.id.ml_menu_sortby_date).setVisible(viewModel.canSortByReleaseDate());
        menu.getMenu().findItem(R.id.ml_menu_sortby_last_modified).setVisible(viewModel.canSortByLastModified());
        menu.getMenu().findItem(R.id.ml_menu_sortby_number).setVisible(false);
        menu.setOnMenuItemClickListener(this);
        menu.show();
    }

    public boolean onMenuItemClick(@NotNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ml_menu_sortby_name:
                sortBy(Medialibrary.SORT_ALPHA);
                return true;
            case R.id.ml_menu_sortby_filename:
                sortBy(Medialibrary.SORT_FILENAME);
                return true;
            case R.id.ml_menu_sortby_length:
                sortBy(Medialibrary.SORT_DURATION);
                return true;
            case R.id.ml_menu_sortby_date:
                sortBy(Medialibrary.SORT_RELEASEDATE);
                return true;
            case R.id.ml_menu_sortby_last_modified:
                sortBy(Medialibrary.SORT_LASTMODIFICATIONDATE);
                return true;
            case R.id.ml_menu_sortby_artist_name:
                sortBy(Medialibrary.SORT_ARTIST);
                return true;
            case R.id.ml_menu_sortby_album_name:
                sortBy(Medialibrary.SORT_ALBUM);
                return true;
            case R.id.ml_menu_sortby_number:
                sortBy(Medialibrary.SORT_FILESIZE);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void sortBy(int sort) {
        viewModel.sort(sort);
    }

    @Override
    public void onHeaderSelected(@NotNull String header) {
        headerListContainer.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);

        final int positionForSectionByName = viewModel.getPositionForSectionByName(header);
        if (list.getChildAt(positionForSectionByName) == null) {
            adapter.setFocusNext(positionForSectionByName);
            list.scrollToPosition(positionForSectionByName);
            if (BuildConfig.DEBUG)
                Log.d("SongBrowserFragment", "Setting focus next: " + positionForSectionByName);
        } else {
            list.getChildAt(positionForSectionByName).requestFocus();
            if (BuildConfig.DEBUG) Log.d("SongBrowserFragment", "Requesting focus");
        }
    }

    @Override
    public boolean onBackPressed() {
        if (headerListContainer.getVisibility() == View.VISIBLE) {
            headerListContainer.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            return true;
        }
        return false;
    }
}
