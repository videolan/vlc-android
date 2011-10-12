package org.videolan.vlc.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.videolan.vlc.android.widget.FlingViewGroup;
import org.videolan.vlc.android.widget.FlingViewGroup.ViewSwitchListener;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
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
    ArrayList<HashMap<String, String>> list = new ArrayList<HashMap<String, String>>();

    private HorizontalScrollView mHeader;
    private AudioServiceController mAudioController;

    private AudioSongsListAdapter mSongsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_browser);

        mFlingViewGroup = (FlingViewGroup) findViewById(R.id.content);
        mFlingViewGroup.setOnViewSwitchedListener(mViewSwitchListener);

        mHeader = (HorizontalScrollView) findViewById(R.id.header);
        mAudioController = AudioServiceController.getInstance();

        mSongsAdapter = new AudioSongsListAdapter(this, android.R.layout.simple_list_item_1);

        ListView songsList = (ListView) findViewById(R.id.songs_list);
        songsList.setAdapter(mSongsAdapter);
        songsList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> av, View v, int p, long id) {
                mAudioController.load(mSongsAdapter.getPaths(), p);
                Intent intent = new Intent(AudioBrowserActivity.this, AudioPlayerActivity.class);
                startActivity(intent);
            }
        });
        updateLists();
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

    private void updateLists() {
        List<Media> audioList = MediaLibrary.getInstance(this).getAudioItems();
        for (int i = 0; i < audioList.size(); i++) {
            mSongsAdapter.add(audioList.get(i));
        }
        mSongsAdapter.notifyDataSetChanged();
    }

}
