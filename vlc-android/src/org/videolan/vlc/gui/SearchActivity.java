package org.videolan.vlc.gui;

import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.databinding.DataBindingUtil;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.SearchAggregate;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.SearchActivityBinding;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Settings;
import org.videolan.vlc.util.WorkersKt;

public class SearchActivity extends AppCompatActivity implements TextWatcher, TextView.OnEditorActionListener {

    public final static String TAG = "VLC/SearchActivity";

    private Medialibrary mMedialibrary;
    private SearchActivityBinding mBinding;
    private ClickHandler mClickHandler = new ClickHandler();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Settings.INSTANCE.getInstance(this).getBoolean("enable_black_theme", false))
            setTheme(R.style.Theme_VLC_Black);
        final Intent intent = getIntent();
        mBinding = DataBindingUtil.setContentView(this, R.layout.search_activity);
        mBinding.setHandler(mClickHandler);
        mMedialibrary = VLCApplication.getMLInstance();
        if (Intent.ACTION_SEARCH.equals(intent.getAction()) || "com.google.android.gms.actions.SEARCH_ACTION".equals(intent.getAction())) {
            final String query = intent.getStringExtra(SearchManager.QUERY);
            initializeLists();
            if (!TextUtils.isEmpty(query)) {
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                if (mMedialibrary.isInitiated()) {
                    mBinding.searchEditText.setText(query);
                    mBinding.searchEditText.setSelection(query.length());
                    performSearh(query);
                } else
                    setupMediaLibraryReceiver(query);
            }
        }
        mBinding.searchEditText.addTextChangedListener(this);
        mBinding.searchEditText.setOnEditorActionListener(this);
    }

    private void performSearh(final String query) {
        WorkersKt.runIO(new Runnable() {
            @Override
            public void run() {
                final SearchAggregate searchAggregate = mMedialibrary.search(query);
                mBinding.setSearchAggregate(searchAggregate);
                if (searchAggregate != null) {
                    WorkersKt.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            ((SearchResultAdapter)mBinding.albumsResults.getAdapter()).add(searchAggregate.getAlbums());
                            ((SearchResultAdapter)mBinding.artistsResults.getAdapter()).add(searchAggregate.getArtists());
                            ((SearchResultAdapter)mBinding.genresResults.getAdapter()).add(searchAggregate.getGenres());
                            ((SearchResultAdapter)mBinding.playlistsResults.getAdapter()).add(searchAggregate.getPlaylists());
                            ((SearchResultAdapter)mBinding.othersResults.getAdapter()).add(searchAggregate.getVideos());
                            ((SearchResultAdapter)mBinding.songsResults.getAdapter()).add(searchAggregate.getTracks());
                        }
                    });
                }
            }
        });
    }

    private void initializeLists() {
        int count = mBinding.resultsContainer.getChildCount();
        final LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        for (int i = 0; i < count; ++i) {
            final View v = mBinding.resultsContainer.getChildAt(i);
            if (v instanceof RecyclerView) {
                ((RecyclerView)v).setAdapter(new SearchResultAdapter(inflater));
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
            MediaUtils.INSTANCE.playTracks(SearchActivity.this, item, 0);
            finish();
        }
    }
    protected void setupMediaLibraryReceiver(final String query) {
        final BroadcastReceiver libraryReadyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                LocalBroadcastManager.getInstance(SearchActivity.this).unregisterReceiver(this);
                mBinding.searchEditText.setText(query);
                mBinding.searchEditText.setSelection(query.length());
                performSearh(query);
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(libraryReadyReceiver, new IntentFilter(VLCApplication.ACTION_MEDIALIBRARY_READY));
    }
}
