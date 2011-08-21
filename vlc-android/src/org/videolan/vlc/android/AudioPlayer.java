package org.videolan.vlc.android;

import android.graphics.Bitmap;

public interface AudioPlayer {
	
	public void update();
	
	public interface AudioPlayerControl {
		String getTitle();
		String getArtist();
		String getAlbum();
		Bitmap getCover();
		int getLength();
		int getTime();
		boolean hasMedia();
		boolean hasNext();
		boolean hasPrevious();
		void play();
		void pause();
		boolean isPlaying();
		void next();
		void previous();
	}

}
