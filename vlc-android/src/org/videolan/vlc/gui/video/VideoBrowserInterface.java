package org.videolan.vlc.gui.video;

import java.util.concurrent.BrokenBarrierException;

import org.videolan.libvlc.Media;

public interface VideoBrowserInterface {
    public static final int HEADER_VIDEO = 0;
	public static final int HEADER_MUSIC = 1;

	public void resetBarrier();
	public void setItemToUpdate(Media item);
	public void await() throws InterruptedException, BrokenBarrierException;
	public void updateItem();
	public void updateList();
}
