package org.videolan.vlc.android;

import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;


public class SurfaceCallback implements Callback {

	public SurfaceCallback(LibVLC l) {
		libvlc = l;
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		// TODO Auto-generated method stub
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// Give the surface to draw the video to libVLC
		libvlc.setSurface(holder.getSurface());
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		
	}

	private LibVLC libvlc;
}
