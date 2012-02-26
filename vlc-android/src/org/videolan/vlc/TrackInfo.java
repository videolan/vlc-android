package org.videolan.vlc;

public class TrackInfo {

    protected static final int TYPE_UNKNOWN = -1;
    protected static final int TYPE_AUDIO = 0;
    protected static final int TYPE_VIDEO = 1;
    protected static final int TYPE_TEXT = 2;

    public int Type;
    public int Id;
    public String Codec;

    /* Video */
    public int Height;
    public int Width;

    /* Audio */
    public int Channels;
    public int Samplerate;
}
