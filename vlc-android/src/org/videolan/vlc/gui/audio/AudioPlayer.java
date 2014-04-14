/*****************************************************************************
 * AudioMiniPlayer.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.gui.audio;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.R;
import org.videolan.vlc.RepeatType;
import org.videolan.vlc.Util;
import org.videolan.vlc.gui.CommonDialogs;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.CommonDialogs.MenuType;
import org.videolan.vlc.gui.audio.widget.CoverMediaSwitcher;
import org.videolan.vlc.gui.audio.widget.HeaderMediaSwitcher;
import org.videolan.vlc.interfaces.IAudioPlayer;
import org.videolan.vlc.widget.AudioMediaSwitcher.AudioMediaSwitcherListener;

import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class AudioPlayer extends Fragment implements IAudioPlayer {
    public static final String TAG = "VLC/AudioMiniPlayer";

    private ProgressBar mProgressBar;
    private HeaderMediaSwitcher mHeaderMediaSwitcher;
    private CoverMediaSwitcher mCoverMediaSwitcher;
    private TextView mTime;
    private TextView mHeaderTime;
    private TextView mLength;
    private ImageButton mPlayPause;
    private ImageButton mHeaderPlayPause;
    private ImageButton mNext;
    private ImageButton mPrevious;
    private ImageButton mShuffle;
    private ImageButton mRepeat;
    private ImageButton mAdvFunc;
    private ImageButton mPlaylistSwitch;
    private SeekBar mTimeline;
    private AudioPlaylistView mSongsList;

    ViewSwitcher mSwitcher;

    private AudioServiceController mAudioController;
    private boolean mShowRemainingTime = false;

    private AudioPlaylistAdapter mSongsListAdapter;

    private boolean mAdvFuncVisible;
    private boolean mPlaylistSwitchVisible;
    private boolean mHeaderPlayPauseVisible;
    private boolean mProgressBarVisible;
    private boolean mHeaderTimeVisible;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioController = AudioServiceController.getInstance();

        mSongsListAdapter = new AudioPlaylistAdapter(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.audio_player, container, false);

        mProgressBar = (ProgressBar) v.findViewById(R.id.progressBar);

        mHeaderMediaSwitcher = (HeaderMediaSwitcher) v.findViewById(R.id.audio_media_switcher);
        mHeaderMediaSwitcher.setAudioMediaSwitcherListener(mHeaderMediaSwitcherListener);
        mCoverMediaSwitcher = (CoverMediaSwitcher) v.findViewById(R.id.cover_media_switcher);
        mCoverMediaSwitcher.setAudioMediaSwitcherListener(mCoverMediaSwitcherListener);

        mTime = (TextView) v.findViewById(R.id.time);
        mHeaderTime = (TextView) v.findViewById(R.id.header_time);
        mLength = (TextView) v.findViewById(R.id.length);
        mPlayPause = (ImageButton) v.findViewById(R.id.play_pause);
        mHeaderPlayPause = (ImageButton) v.findViewById(R.id.header_play_pause);
        mNext = (ImageButton) v.findViewById(R.id.next);
        mPrevious = (ImageButton) v.findViewById(R.id.previous);
        mShuffle = (ImageButton) v.findViewById(R.id.shuffle);
        mRepeat = (ImageButton) v.findViewById(R.id.repeat);
        mAdvFunc = (ImageButton) v.findViewById(R.id.adv_function);
        mPlaylistSwitch = (ImageButton) v.findViewById(R.id.playlist_switch);
        mTimeline = (SeekBar) v.findViewById(R.id.timeline);

        mSongsList = (AudioPlaylistView) v.findViewById(R.id.songs_list);
        mSongsList.setAdapter(mSongsListAdapter);

        mSwitcher = (ViewSwitcher) v.findViewById(R.id.view_switcher);
        mSwitcher.setInAnimation(getActivity(), android.R.anim.fade_in);
        mSwitcher.setOutAnimation(getActivity(), android.R.anim.fade_out);

        mAdvFuncVisible = false;
        mPlaylistSwitchVisible = false;
        mHeaderPlayPauseVisible = true;
        mProgressBarVisible = true;
        mHeaderTimeVisible = true;
        restoreHedaderButtonVisibilities();

        mTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTimeLabelClick(v);
            }
        });
        mPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlayPauseClick(v);
            }
        });
        mPlayPause.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                onStopClick(v);
                return true;
            }
        });
        mHeaderPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlayPauseClick(v);
            }
        });
        mHeaderPlayPause.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                onStopClick(v);
                return true;
            }
        });
        mNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onNextClick(v);
            }
        });
        mPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPreviousClick(v);
            }
        });
        mShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onShuffleClick(v);
            }
        });
        mRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRepeatClick(v);
            }
        });
        mAdvFunc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAdvancedOptions(v);
            }
        });
        mPlaylistSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSwitcher.showNext();
                if (mSwitcher.getDisplayedChild() == 0)
                    mPlaylistSwitch.setImageResource(Util.getResourceFromAttribute(getActivity(),
                                                     R.attr.ic_playlist_pressed));
                else
                    mPlaylistSwitch.setImageResource(Util.getResourceFromAttribute(getActivity(),
                                                     R.attr.ic_playlist_normal));
            }
        });
        mSongsList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> av, View v, int p, long id) {
                mAudioController.load(mSongsListAdapter.getLocations(), p);
            }
        });
        mSongsList.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                    int position, long id) {
                getActivity().openContextMenu(view);
                return true;
            }
        });
        mSongsList.setOnItemDraggedListener(new AudioPlaylistView.OnItemDraggedListener() {
            @Override
            public void OnItemDradded(int positionStart, int positionEnd) {
                mAudioController.moveItem(positionStart, positionEnd);
            }
        });
        mSongsList.setOnItemRemovedListener(new AudioPlaylistView.OnItemRemovedListener() {
            @Override
            public void onItemRemoved(int position) {
                mAudioController.remove(position);
            }
        });
        registerForContextMenu(mSongsList);

        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        getView().cancelLongPress();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        MenuInflater menuInflater = getActivity().getMenuInflater();
        menuInflater.inflate(R.menu.audio_player, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        if(info == null) // info can be null
            return super.onContextItemSelected(item);
        int id = item.getItemId();

        if(id == R.id.audio_player_mini_remove) {
            Log.d(TAG, "Context menu removing " + info.position);
            mAudioController.remove(info.position);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * Show the audio player from an intent
     *
     * @param context The context of the activity
     */
    public static void start(Context context) {
        Intent intent = new Intent();
        intent.setAction(MainActivity.ACTION_SHOW_PLAYER);
        context.getApplicationContext().sendBroadcast(intent);
    }

    @Override
    public synchronized void update() {

        if (mAudioController == null)
            return;

        if (mAudioController.hasMedia()) {
            show();
        } else {
            hide();
            return;
        }

        mHeaderMediaSwitcher.updateMedia();
        mCoverMediaSwitcher.updateMedia();

        FragmentActivity act = getActivity();

        if (mAudioController.isPlaying()) {
            mPlayPause.setImageResource(Util.getResourceFromAttribute(act, R.attr.ic_pause));
            mPlayPause.setContentDescription(getString(R.string.pause));
            mHeaderPlayPause.setImageResource(Util.getResourceFromAttribute(act, R.attr.ic_pause));
            mHeaderPlayPause.setContentDescription(getString(R.string.pause));
        } else {
            mPlayPause.setImageResource(Util.getResourceFromAttribute(act, R.attr.ic_play));
            mPlayPause.setContentDescription(getString(R.string.play));
            mHeaderPlayPause.setImageResource(Util.getResourceFromAttribute(act, R.attr.ic_play));
            mHeaderPlayPause.setContentDescription(getString(R.string.play));
        }
        if (mAudioController.isShuffling()) {
            mShuffle.setImageResource(Util.getResourceFromAttribute(act, R.attr.ic_shuffle_pressed));
        } else {
            mShuffle.setImageResource(Util.getResourceFromAttribute(act, R.attr.ic_shuffle_normal));
        }
        switch(mAudioController.getRepeatType()) {
        case None:
            mRepeat.setImageResource(Util.getResourceFromAttribute(act, R.attr.ic_repeat_normal));
            break;
        case Once:
            mRepeat.setImageResource(Util.getResourceFromAttribute(act, R.attr.ic_repeat_one));
            break;
        default:
        case All:
            mRepeat.setImageResource(Util.getResourceFromAttribute(act, R.attr.ic_repeat_pressed));
            break;
        }
        if (mAudioController.hasNext())
            mNext.setVisibility(ImageButton.VISIBLE);
        else
            mNext.setVisibility(ImageButton.INVISIBLE);
        if (mAudioController.hasPrevious())
            mPrevious.setVisibility(ImageButton.VISIBLE);
        else
            mPrevious.setVisibility(ImageButton.INVISIBLE);
        mTimeline.setOnSeekBarChangeListener(mTimelineListner);

        updateList();
    }

    @Override
    public synchronized void updateProgress() {
        int time = mAudioController.getTime();
        int length = mAudioController.getLength();
        mTime.setText(Util.millisToString(mShowRemainingTime ? time-length : time));
        mHeaderTime.setText(Util.millisToString(time));
        mLength.setText(Util.millisToString(length));
        mTimeline.setMax(length);
        mTimeline.setProgress(time);
        mProgressBar.setMax(length);
        mProgressBar.setProgress(time);
    }

    private void updateList() {
        ArrayList<Media> audioList = new ArrayList<Media>();
        String currentItem = null;
        int currentIndex = -1;

        LibVLC libVLC = LibVLC.getExistingInstance();
        for (int i = 0; i < libVLC.getMediaList().size(); i++) {
            audioList.add(libVLC.getMediaList().getMedia(i));
        }
        currentItem = mAudioController.getCurrentMediaLocation();

        mSongsListAdapter.clear();

        for (int i = 0; i < audioList.size(); i++) {
            Media media = audioList.get(i);
            if (currentItem != null && currentItem.equals(media.getLocation()))
                currentIndex = i;
            mSongsListAdapter.add(media);
        }
        mSongsListAdapter.setCurrentIndex(currentIndex);
        mSongsList.setSelection(currentIndex);

        mSongsListAdapter.notifyDataSetChanged();
    }

    OnSeekBarChangeListener mTimelineListner = new OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onStartTrackingTouch(SeekBar arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
            if (fromUser) {
                mAudioController.setTime(prog);
                mTime.setText(Util.millisToString(mShowRemainingTime ? prog-mAudioController.getLength() : prog));
                mHeaderTime.setText(Util.millisToString(prog));
        }
    }
    };

    public void onTimeLabelClick(View view) {
        mShowRemainingTime = !mShowRemainingTime;
        update();
    }

    public void onPlayPauseClick(View view) {
        if (mAudioController.isPlaying()) {
            mAudioController.pause();
        } else {
            mAudioController.play();
        }
    }

    public void onStopClick(View view) {
        mAudioController.stop();
    }

    public void onNextClick(View view) {
        mAudioController.next();
    }

    public void onPreviousClick(View view) {
        mAudioController.previous();
    }

    public void onRepeatClick(View view) {
        switch (mAudioController.getRepeatType()) {
            case None:
                mAudioController.setRepeatType(RepeatType.All);
                break;
            case All:
                mAudioController.setRepeatType(RepeatType.Once);
                break;
            default:
            case Once:
                mAudioController.setRepeatType(RepeatType.None);
                break;
        }
        update();
    }

    public void onShuffleClick(View view) {
        mAudioController.shuffle();
        update();
    }

    public void showAdvancedOptions(View v) {
        CommonDialogs.advancedOptions(getActivity(), v, MenuType.Audio);
    }

    public void show() {
        MainActivity activity = (MainActivity)getActivity();
        activity.showAudioPlayer();
    }

    public void hide() {
        MainActivity activity = (MainActivity)getActivity();
        activity.hideAudioPlayer();
    }

    /**
     * Set the visibilities of the player header elements.
     * @param advFuncVisible
     * @param playlistSwitchVisible
     * @param headerPlayPauseVisible
     */
    public void setHeaderVisibilities(boolean advFuncVisible, boolean playlistSwitchVisible,
                                      boolean headerPlayPauseVisible, boolean progressBarVisible,
                                      boolean headerTimeVisible) {
        mAdvFuncVisible = advFuncVisible;
        mPlaylistSwitchVisible = playlistSwitchVisible;
        mHeaderPlayPauseVisible = headerPlayPauseVisible;
        mProgressBarVisible = progressBarVisible;
        mHeaderTimeVisible = headerTimeVisible;
        restoreHedaderButtonVisibilities();
    }

    private void restoreHedaderButtonVisibilities() {
        mAdvFunc.setVisibility(mAdvFuncVisible ? ImageButton.VISIBLE : ImageButton.GONE);
        mPlaylistSwitch.setVisibility(mPlaylistSwitchVisible ? ImageButton.VISIBLE : ImageButton.GONE);
        mHeaderPlayPause.setVisibility(mHeaderPlayPauseVisible ? ImageButton.VISIBLE : ImageButton.GONE);
        mProgressBar.setVisibility(mProgressBarVisible ? ProgressBar.VISIBLE : ProgressBar.GONE);
        mHeaderTime.setVisibility(mHeaderTimeVisible ? TextView.VISIBLE : TextView.GONE);
    }

    private void hideHedaderButtons() {
        mAdvFunc.setVisibility(ImageButton.GONE);
        mPlaylistSwitch.setVisibility(ImageButton.GONE);
        mHeaderPlayPause.setVisibility(ImageButton.GONE);
        mHeaderTime.setVisibility(TextView.GONE);
    }

    private final AudioMediaSwitcherListener mHeaderMediaSwitcherListener = new AudioMediaSwitcherListener() {

        @Override
        public void onMediaSwitching() {}

        @Override
        public void onMediaSwitched(int position) {
            if (position == AudioMediaSwitcherListener.PREVIOUS_MEDIA)
                mAudioController.previous();
            else if (position == AudioMediaSwitcherListener.NEXT_MEDIA)
                mAudioController.next();
        }

        @Override
        public void onTouchDown() {
            hideHedaderButtons();
        }

        @Override
        public void onTouchUp() {
            restoreHedaderButtonVisibilities();
        }

        @Override
        public void onTouchClick() {
            MainActivity activity = (MainActivity)getActivity();
            activity.slideUpOrDownAudioPlayer();
        }
    };

    private final AudioMediaSwitcherListener mCoverMediaSwitcherListener = new AudioMediaSwitcherListener() {

        @Override
        public void onMediaSwitching() {}

        @Override
        public void onMediaSwitched(int position) {
            if (position == AudioMediaSwitcherListener.PREVIOUS_MEDIA)
                mAudioController.previous();
            else if (position == AudioMediaSwitcherListener.NEXT_MEDIA)
                mAudioController.next();
        }

        @Override
        public void onTouchDown() {}

        @Override
        public void onTouchUp() {}

        @Override
        public void onTouchClick() {}
    };

}
