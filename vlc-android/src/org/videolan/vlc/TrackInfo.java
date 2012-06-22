package org.videolan.vlc;

public class TrackInfo {

    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_TEXT = 2;
    public static final int TYPE_META = 3;

    public int Type;
    public int Id;
    public String Codec;
    public String Language;

    /* Video */
    public int Height;
    public int Width;
    public float Framerate;

    /* Audio */
    public int Channels;
    public int Samplerate;

    /* MetaData */
    public long Length;
    public String Title;
    public String Artist;
    public String Album;
    public String Genre;
    public String ArtworkURL;
}
