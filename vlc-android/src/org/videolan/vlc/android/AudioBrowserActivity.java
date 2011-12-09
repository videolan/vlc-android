package org.videolan.vlc.android;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.videolan.vlc.android.widget.FlingViewGroup;
import org.videolan.vlc.android.widget.FlingViewGroup.ViewSwitchListener;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class AudioBrowserActivity extends Activity {
    public final static String TAG = "VLC/AudioBrowserActivity";

    private FlingViewGroup mFlingViewGroup;

    private HorizontalScrollView mHeader;
    private AudioServiceController mAudioController;
    private MediaLibrary mMediaLibrary;

    private AudioSongsListAdapter mSongsAdapter;
    private AudioPlaylistAdapter mArtistsAdapter;
    private AudioPlaylistAdapter mAlbumsAdapter;
    private AudioPlaylistAdapter mGenresAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_browser);

        mFlingViewGroup = (FlingViewGroup) findViewById(R.id.content);
        mFlingViewGroup.setOnViewSwitchedListener(mViewSwitchListener);

        mHeader = (HorizontalScrollView) findViewById(R.id.header);
        mAudioController = AudioServiceController.getInstance();

        mMediaLibrary = MediaLibrary.getInstance(this);
        mMediaLibrary.addUpdateHandler(mHandler);

        mSongsAdapter = new AudioSongsListAdapter(this, R.layout.audio_browser_item);
        mArtistsAdapter = new AudioPlaylistAdapter(this, R.layout.audio_browser_item);
        mAlbumsAdapter = new AudioPlaylistAdapter(this, R.layout.audio_browser_item);
        mGenresAdapter = new AudioPlaylistAdapter(this, R.layout.audio_browser_item);
        ListView songsList = (ListView) findViewById(R.id.songs_list);
        ListView artistList = (ListView) findViewById(R.id.artists_list);
        ListView albumList = (ListView) findViewById(R.id.albums_list);
        ListView genreList = (ListView) findViewById(R.id.genres_list);
        songsList.setAdapter(mSongsAdapter);
        artistList.setAdapter(mArtistsAdapter);
        albumList.setAdapter(mAlbumsAdapter);
        genreList.setAdapter(mGenresAdapter);
        songsList.setOnItemClickListener(songListener);
        artistList.setOnItemClickListener(playlistListener);
        albumList.setOnItemClickListener(playlistListener);
        genreList.setOnItemClickListener(playlistListener);

        updateLists();
    }

    OnItemClickListener songListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            mAudioController.load(mSongsAdapter.getPaths(), p);
            Intent intent = new Intent(AudioBrowserActivity.this, AudioPlayerActivity.class);
            startActivity(intent);
        }
    };

    OnItemClickListener playlistListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
            AudioPlaylistAdapter adapter = (AudioPlaylistAdapter) av.getAdapter();
            List<String> playlist = adapter.getPlaylist(p);
            if (playlist.isEmpty())
                return;
            mAudioController.load(playlist, 0);
            Intent intent = new Intent(AudioBrowserActivity.this, AudioPlayerActivity.class);
            startActivity(intent);
        }
    };

    @Override
    protected void onDestroy() {
        mMediaLibrary.removeUpdateHandler(mHandler);
        mSongsAdapter.clear();
        mArtistsAdapter.clear();
        mAlbumsAdapter.clear();
        mGenresAdapter.clear();
        super.onDestroy();
    }

    private ViewSwitchListener mViewSwitchListener = new ViewSwitchListener() {

        int mCurrentPosition = 0;

        @Override
        public void onSwitching(float progress) {
            LinearLayout hl = (LinearLayout) findViewById(R.id.header_layout);
            int width = hl.getChildAt(0).getWidth();
            int x = (int) (progress * width);
            mHeader.smoothScrollTo(x, 0);
        }

        @Override
        public void onSwitched(int position) {
            LinearLayout hl = (LinearLayout) findViewById(R.id.header_layout);
            TextView oldView = (TextView) hl.getChildAt(mCurrentPosition);
            oldView.setTextColor(Color.GRAY);
            TextView newView = (TextView) hl.getChildAt(position);
            newView.setTextColor(Color.WHITE);
            mCurrentPosition = position;
        }

    };

    /**
     * Handle changes on the list
     */
    protected Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MediaLibrary.MEDIA_ITEMS_UPDATED:
                updateLists();
                break;
        }
    }
    };

    private Comparator<Media> byPath = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
            return String.CASE_INSENSITIVE_ORDER.compare(m1.getFile().getPath(), m2.getFile().getPath());
        };
    };
    private Comparator<Media> byAlbum = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
            int res = String.CASE_INSENSITIVE_ORDER.compare(m1.getAlbum(), m2.getAlbum());
            if (res == 0)
                res = byPath.compare(m1, m2);
            return res;
        };
    };
    private Comparator<Media> byArtist = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
            int res = String.CASE_INSENSITIVE_ORDER.compare(m1.getArtist(), m2.getArtist());
            if (res == 0)
                res = byAlbum.compare(m1, m2);
            return res;
        };
    };
    private Comparator<Media> byGenre = new Comparator<Media>() {
        public int compare(Media m1, Media m2) {
            int res = String.CASE_INSENSITIVE_ORDER.compare(m1.getGenre(), m2.getGenre());
            if (res == 0)
                res = byArtist.compare(m1, m2);
            return res;
        };
    };

    private void updateLists() {
        List<Media> audioList = MediaLibrary.getInstance(this).getAudioItems();
        mSongsAdapter.clear();
        mArtistsAdapter.clear();
        mAlbumsAdapter.clear();
        mGenresAdapter.clear();

        Collections.sort(audioList, byPath);
        for (int i = 0; i < audioList.size(); i++)
            mSongsAdapter.add(audioList.get(i));

        Collections.sort(audioList, byArtist);
        for (int i = 0; i < audioList.size(); i++) {
            Media media = audioList.get(i);
            mArtistsAdapter.add(media.getArtist(), media);
        }

        Collections.sort(audioList, byAlbum);
        for (int i = 0; i < audioList.size(); i++) {
            Media media = audioList.get(i);
            mAlbumsAdapter.add(media.getAlbum(), media);
        }

        Collections.sort(audioList, byGenre);
        for (int i = 0; i < audioList.size(); i++) {
            Media media = audioList.get(i);
            mGenresAdapter.add(media.getGenre(), media);
        }

        mSongsAdapter.notifyDataSetChanged();
        mArtistsAdapter.notifyDataSetChanged();
        mAlbumsAdapter.notifyDataSetChanged();
        mGenresAdapter.notifyDataSetChanged();
    }
}
