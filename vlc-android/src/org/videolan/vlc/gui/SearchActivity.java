package org.videolan.vlc.gui;

import android.app.SearchManager;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.SearchAggregate;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.SearchActivityBinding;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.media.MediaUtils;

public class SearchActivity extends AppCompatActivity implements TextWatcher, TextView.OnEditorActionListener {

    public final static String TAG = "VLC/SearchActivity";

    private Medialibrary mMedialibrary;
    private SearchActivityBinding mBinding;
    private ClickHandler mClickHandler = new ClickHandler();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("enable_black_theme", false))
            setTheme(R.style.Theme_VLC_Black);
        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction()) || "com.google.android.gms.actions.SEARCH_ACTION".equals(intent.getAction())) {
            mBinding = DataBindingUtil.setContentView(this, R.layout.search_activity);
            mBinding.setHandler(mClickHandler);
            mMedialibrary = VLCApplication.getMLInstance();
            String query = intent.getStringExtra(SearchManager.QUERY);
            initializeLists();
            if (!TextUtils.isEmpty(query)) {
                mBinding.searchEditText.setText(query);
                mBinding.searchEditText.setSelection(query.length());
                performSearh(query);
            }
        }
        mBinding.searchEditText.addTextChangedListener(this);
        mBinding.searchEditText.setOnEditorActionListener(this);
    }

    private void performSearh(final String query) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final SearchAggregate searchAggregate = mMedialibrary.search(query);
                mBinding.setSearchAggregate(searchAggregate);
                SearchActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((SearchResultAdapter)mBinding.albumsResults.getAdapter()).add(searchAggregate.getAlbums());
                        ((SearchResultAdapter)mBinding.artistsResults.getAdapter()).add(searchAggregate.getArtists());
                        ((SearchResultAdapter)mBinding.genresResults.getAdapter()).add(searchAggregate.getGenres());
                        ((SearchResultAdapter)mBinding.playlistsResults.getAdapter()).add(searchAggregate.getPlaylists());
                        ((SearchResultAdapter)mBinding.episodesResults.getAdapter()).add(searchAggregate.getMediaSearchAggregate().getEpisodes());
                        ((SearchResultAdapter)mBinding.moviesResults.getAdapter()).add(searchAggregate.getMediaSearchAggregate().getMovies());
                        ((SearchResultAdapter)mBinding.othersResults.getAdapter()).add(searchAggregate.getMediaSearchAggregate().getOthers());
                        ((SearchResultAdapter)mBinding.songsResults.getAdapter()).add(searchAggregate.getMediaSearchAggregate().getTracks());
                    }
                });
            }
        });
    }

    private void initializeLists() {
        int count = mBinding.resultsContainer.getChildCount();
        for (int i = 0; i < count; ++i) {
            View v = mBinding.resultsContainer.getChildAt(i);
            if (v instanceof ContextMenuRecyclerView) {
                ((RecyclerView)v).setAdapter(new SearchResultAdapter());
                ((RecyclerView)v).setLayoutManager(new LinearLayoutManager(this));
                ((SearchResultAdapter)((RecyclerView)v).getAdapter()).setClickHandler(mClickHandler);
            }
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        if (s == null || s.length() < 3)
            mBinding.setSearchAggregate(new SearchAggregate());
        else
            performSearh(s.toString());
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            UiTools.setKeyboardVisibility(mBinding.getRoot(), false);
            return true;
        }
        return false;
    }

    private void clear() {
        mBinding.searchEditText.removeTextChangedListener(this);
        mBinding.searchEditText.setText("");
        mBinding.searchEditText.addTextChangedListener(this);
        mBinding.setSearchAggregate(new SearchAggregate());
    }

    public class ClickHandler {
        public void onClean(View v) {
            clear();
        }
        public void onBack(View v) {
            finish();
        }
        public void onItemClick(MediaLibraryItem item) {
            MediaUtils.openArray(SearchActivity.this, item.getTracks(mMedialibrary), 0);
            finish();
        }
    }
}
