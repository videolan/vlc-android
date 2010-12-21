package vlc.android;

import android.view.Surface;

public class LibVLC {
	/**
	 *  Constructor
	 */
	public LibVLC()
	{
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
	 * Read a media.
	 */
	public void readMedia(String mrl)
	{
		readMedia(p_instance, mrl);
	}

	/** libVLC instance C pointer */
	private int p_instance;

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
