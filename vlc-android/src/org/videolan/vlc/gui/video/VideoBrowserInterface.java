package org.videolan.vlc.gui.video;

import java.util.concurrent.BrokenBarrierException;

import org.videolan.libvlc.Media;

public interface VideoBrowserInterface {
    static final String ACTION_SCAN_START = "org.videolan.vlc.gui.ScanStart";
    static final String ACTION_SCAN_STOP = "org.videolan.vlc.gui.ScanStop";
    static final int UPDATE_ITEM = 0;

	public void resetBarrier();
	public void setItemToUpdate(Media item);
	public void await() throws InterruptedException, BrokenBarrierException;
}
