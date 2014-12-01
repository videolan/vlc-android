package org.videolan.vlc.gui.video;

import java.util.concurrent.BrokenBarrierException;

import org.videolan.libvlc.Media;

public interface VideoBrowserInterface {
    public static final long HEADER_VIDEO = 0;
	public static final long HEADER_MUSIC = 1;
	public static final long HEADER_CATEGORIES = 2;
	public static final long HEADER_MISC = 3;
	public static final long FILTER_ARTIST = 3;
	public static final long FILTER_GENRE = 4;

	public static final String MEDIA_SECTION = "id";
	public static final String AUDIO_CATEGORY = "category";
	public static final String AUDIO_FILTER = "filter";

	public void resetBarrier();
	public void setItemToUpdate(Media item);
	public void await() throws InterruptedException, BrokenBarrierException;
	public void updateItem();
	public void updateList();
}
