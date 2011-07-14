package org.videolan.vlc.android;
import org.videolan.vlc.android.IAudioServiceCallback;

interface IAudioService {
	void play();
	void pause();
	void stop();
	String getCurrentMediaPath();
	void load(String mediaPath);
	boolean isPlaying();
	boolean hasMedia();
	String getTitle();
	String getArtist();
	void addAudioCallback(IAudioServiceCallback cb);
	void removeAudioCallback(IAudioServiceCallback cb);
}