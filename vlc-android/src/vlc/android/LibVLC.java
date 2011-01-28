package vlc.android;

import android.opengl.GLSurfaceView;
import android.view.Surface;

public class LibVLC {
	/**
	 *  Constructor
	 */
	public LibVLC(GLSurfaceView s, Vout v)
	{
		surfaceView = s;
		vout = v;
		System.loadLibrary("vlcjni");
	};

	/**
	 * Destructor
	 */
	public void finalize()
	{
		if(this.p_instance != 0)
			this.destroy(p_instance);
	}
	
	/**
	 * Give to libvlc the surface to draw the video.
	 * @param f: the surface to draw
	 */
	public native void setSurface(Surface f);

	/**
	 * Initialize the libVLC class
	 * @return true if the libVLC C library was created correctly, false overwise
	 */
	public boolean Init()
	{
		this.p_instance = this.init();
		return this.p_instance != 0;
	}
	
	/**
	 * Transmit to the renderer the size of the video.
	 * This function is called by the native code.
	 * @param frameWidth
	 * @param frameHeight
	 */
	public void setVoutSize(int frameWidth, int frameHeight)
	{
		vout.frameWidth = frameWidth;
		vout.frameHeight = frameHeight;
		vout.mustInit = true;
	}
	
	/**
	 * Transmit the image given by VLC to the renderer.
	 * This function is called by the native code.
	 * @param image the image data.
	 */
	public void displayCallback(byte[] image)
	{
		vout.image = image;
		vout.hasReceivedFrame = true;
		surfaceView.requestRender();
	}
	
	/**
	 * Read a media.
	 */
	public void readMedia(String mrl)
	{
		readMedia(p_instance, mrl);
	}

	/** libVLC instance C pointer */
	private int p_instance;
	private GLSurfaceView surfaceView;
	private Vout vout;
	
	/**
	 * Initialize the libvlc C library
	 * @return a pointer to the libvlc instance
	 */
	private native int init();

	/**
	 * Close the libvlc C library
	 * @param instance: the instance of libVLC
	 */
	private native void destroy(int instance);
	
	/**
	 * Read a media
	 * @param instance: the instance of libVLC
	 * @param mrl: the media mrl
	 */
	private native void readMedia(int instance, String mrl);

	/**
     * Get the libVLC version
     * @return the libVLC version string
     */
    public native String version();

    /**
     * Get the libVLC compiler
     * @return the libVLC compiler string
     */
    public native String compiler();

    /**
     * Get the libVLC changeset
     * @return the libVLC changeset string
     */
    public native String changeset();
}
