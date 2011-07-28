package org.videolan.vlc.android;

import android.graphics.Bitmap;

public interface AudioPlayer {
	
	public void update();
	
	public interface AudioPlayerControl {
		String getTitle();
		Bitmap getCover();
		int getLength();
		int getTime();
		boolean hasMedia();
		String getArtist();
		void play();
		void pause();
		boolean isPlaying();
	}

}
