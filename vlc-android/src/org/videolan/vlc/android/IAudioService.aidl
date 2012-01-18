package org.videolan.vlc.android;
import org.videolan.vlc.android.IAudioServiceCallback;

interface IAudioService {
    void play();
    void pause();
    void stop();
    void next();
    void previous();
    void shuffle();
    void setTime(long time);
    String getCurrentMediaPath();
    void load(in List<String> mediaPathList, int position);
    boolean isPlaying();
    boolean isShuffling();
    int getRepeatType();
    void setRepeatType(int t);
    boolean hasMedia();
    boolean hasNext();
    boolean hasPrevious();
    String getTitle();
    String getArtist();
    String getAlbum();
    int getTime();
    int getLength();
    Bitmap getCover();
    void addAudioCallback(IAudioServiceCallback cb);
    void removeAudioCallback(IAudioServiceCallback cb);
}
