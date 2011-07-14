package org.videolan.vlc.android;

import java.util.ArrayList;

import org.videolan.vlc.android.widget.AudioPlayer;

import android.app.Activity;
import android.app.ActivityGroup;
import android.app.LocalActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

public class AudioActivityGroup extends ActivityGroup {
	public final static String TAG = "VLC/AudioActivityGroup";

	private ArrayList<String> mHistory;
	private AudioPlayer mAudioPlayer;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mHistory = new ArrayList<String>();
		mAudioPlayer = new AudioPlayer(this);
		AudioServiceController audioController = AudioServiceController.getInstance();
		audioController.addAudioPlayer(mAudioPlayer);
		mAudioPlayer.setAudioPlayerControl(audioController);
		mAudioPlayer.showMiniPlayer();
		
		// Load VideoListActivity by default
		Intent intent = new Intent(this, AudioBrowserActivity.class);
		startChildAcitvity("AudioBrowserActivity", intent);
	}
	
	
	public void startChildAcitvity(String id, Intent intent) {
		Window window = getLocalActivityManager().startActivity(
				id, intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
		if (window != null) {
			mHistory.add(id);
			setContentView(window.getDecorView());
		}
	}


	@Override
	public void finishFromChild(Activity child) {
		LocalActivityManager manager = getLocalActivityManager();
		int index = mHistory.size() - 1;

		if (index > 0) {
			manager.destroyActivity(mHistory.get(index), true);
			mHistory.remove(index);
			index--;
			String id = mHistory.get(index);
			Activity activity = manager.getActivity(id);
			setContentView(activity.getWindow().getDecorView());
		} else {
			finish();
		}
	}
	
	@Override
	public void onBackPressed() {
		int index = mHistory.size()-1;
		
		if (index > 0) {
			getCurrentActivity().finish();
			return;
		}
		super.onBackPressed();
	}


	@Override
	public void setContentView(View view) {
		FrameLayout fl = new FrameLayout(this);
		fl.addView(view);
		fl.addView(mAudioPlayer);
		super.setContentView(fl);
	}
	
	@Override
	protected void onDestroy() {
		AudioServiceController.getInstance().unbindAudioService();
		super.onDestroy();
	}
}
