package vlc.android;

public class libVLC {
	/**
	 *  Constructor
	 */
	public libVLC()
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
	public void readMedia()
	{
		readMedia(p_instance);
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
	 */
	private native void readMedia(int instance);

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
