package org.videolan.medialibrary.interfaces;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.videolan.medialibrary.ServiceLocator;
import org.videolan.medialibrary.SingleEvent;
import org.videolan.medialibrary.interfaces.media.AAlbum;
import org.videolan.medialibrary.interfaces.media.AArtist;
import org.videolan.medialibrary.interfaces.media.AFolder;
import org.videolan.medialibrary.interfaces.media.AGenre;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;
import org.videolan.medialibrary.interfaces.media.APlaylist;
import org.videolan.medialibrary.media.SearchAggregate;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

abstract public class AMedialibrary {

    // Sorting
    public final static int SORT_DEFAULT = 0;
    public final static int SORT_ALPHA = 1;
    public final static int SORT_DURATION = 2;
    public final static int SORT_INSERTIONDATE = 3;
    public final static int SORT_LASTMODIFICATIONDATE = 4;
    public final static int SORT_RELEASEDATE = 5;
    public final static int SORT_FILESIZE = 6;
    public final static int SORT_ARTIST = 7;
    public final static int SORT_PLAYCOUNT = 8;
    public final static int SORT_ALBUM = 9;
    public final static int SORT_FILENAME = 10;

    protected long mInstanceID;
    public static final int FLAG_MEDIA_UPDATED_AUDIO        = 1 << 0;
    public static final int FLAG_MEDIA_UPDATED_AUDIO_EMPTY  = 1 << 1;
    public static final int FLAG_MEDIA_UPDATED_VIDEO        = 1 << 2;
    public static final int FLAG_MEDIA_UPDATED_VIDEO_EMPTY  = 1 << 3;
    public static final int FLAG_MEDIA_ADDED_AUDIO          = 1 << 4;
    public static final int FLAG_MEDIA_ADDED_AUDIO_EMPTY    = 1 << 5;
    public static final int FLAG_MEDIA_ADDED_VIDEO          = 1 << 6;
    public static final int FLAG_MEDIA_ADDED_VIDEO_EMPTY    = 1 << 7;

    public static final int ML_INIT_SUCCESS = 0;
    public static final int ML_INIT_ALREADY_INITIALIZED = 1;
    public static final int ML_INIT_FAILED = 2;
    public static final int ML_INIT_DB_RESET = 3;

    public static final String ACTION_IDLE = "action_idle";
    public static final String STATE_IDLE = "state_idle";

    public static final AMediaWrapper[] EMPTY_COLLECTION = {};
    public static final String VLC_MEDIA_DB_NAME = "/vlc_media.db";
    public static final String THUMBS_FOLDER_NAME = "/thumbs";

    protected volatile boolean mIsInitiated = false;
    protected volatile boolean mIsWorking = false;
    protected static MutableLiveData<Boolean> sRunning = new MutableLiveData<>();

    protected final List<ArtistsCb> mArtistsCbs = new ArrayList<>();
    protected final List<AlbumsCb> mAlbumsCbs = new ArrayList<>();
    protected final List<MediaCb> mMediaCbs = new ArrayList<>();
    protected final List<GenresCb> mGenreCbs = new ArrayList<>();
    protected final List<PlaylistsCb> mPlaylistCbs = new ArrayList<>();
    protected final List<OnMedialibraryReadyListener> onMedialibraryReadyListeners = new ArrayList<>();
    protected final List<OnDeviceChangeListener> onDeviceChangeListeners = new ArrayList<>();
    protected volatile boolean isMedialibraryStarted = false;
    protected final List<DevicesDiscoveryCb> devicesDiscoveryCbList = new ArrayList<>();
    protected final List<EntryPointsEventsCb> entryPointsEventsCbList = new ArrayList<>();
    protected static Context sContext;
    public static LiveData<AMediaWrapper> lastThumb = new SingleEvent<>();

    protected static final AMedialibrary instance = ServiceLocator.getAMedialibrary();

    public static Context getContext() {
        return sContext;
    }

    public static LiveData<Boolean> getState() {
        return sRunning;
    }

    public boolean isStarted() {
        return isMedialibraryStarted;
    }

    protected void finalize() throws Throwable {
        super.finalize();
    }

    @NonNull
    public static AMedialibrary getInstance() {
        System.out.println("This is the real medialibrary");
        return instance;
    }

    /* used only before API 13: substitute for NewWeakGlobalRef */
    @SuppressWarnings("unused") /* Used from JNI */
    private Object getWeakReference() {
        return new WeakReference<>(this);
    }

    public long getId() {
        return mInstanceID;
    }

    public boolean isWorking() {
        return mIsWorking;
    }

    public boolean isInitiated() {
        return mIsInitiated;
    }

    public static String[] getBlackList() {
        return new String[] {
                "/Android/data/",
                "/Android/media/",
                "/Alarms/",
                "/Ringtones/",
                "/Notifications/",
                "/alarms/",
                "/ringtones/",
                "/notifications/",
                "/audio/Alarms/",
                "/audio/Ringtones/",
                "/audio/Notifications/",
                "/audio/alarms/",
                "/audio/ringtones/",
                "/audio/notifications/",
                "/WhatsApp/Media/WhatsApp Animated Gifs/",
        };
    }

    public static File[] getDefaultFolders() {
        return new File[]{
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
        };
    }

    protected boolean canReadStorage(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public interface MediaCb {
        void onMediaAdded();
        void onMediaModified();
        void onMediaDeleted();
    }

    public interface ArtistsCb {
        void onArtistsAdded();
        void onArtistsModified();
        void onArtistsDeleted();
    }

    public interface AlbumsCb {
        void onAlbumsAdded();
        void onAlbumsModified();
        void onAlbumsDeleted();
    }

    public interface GenresCb {
        void onGenresAdded();
        void onGenresModified();
        void onGenresDeleted();
    }

    public interface PlaylistsCb {
        void onPlaylistsAdded();
        void onPlaylistsModified();
        void onPlaylistsDeleted();
    }

    public interface OnMedialibraryReadyListener {
        void onMedialibraryReady();
        void onMedialibraryIdle();
    }

    public interface OnDeviceChangeListener {
        void onDeviceChange();
    }

    abstract public int init(Context context);
    abstract public void start();
    abstract public void banFolder(@NonNull String path);
    abstract public void unbanFolder(@NonNull String path);
    abstract public String[] getDevices();
    abstract public boolean addDevice(@NonNull String uuid, @NonNull String path, boolean removable);
    abstract public void discover(@NonNull String path);
    abstract public void removeFolder(@NonNull String mrl);
    abstract public String[] getFoldersList();
    abstract public boolean removeDevice(String uuid, String path);
    abstract public AMediaWrapper[] getVideos();
    abstract public AMediaWrapper[] getPagedVideos(int sort, boolean desc, int nbItems, int offset);
    abstract public AMediaWrapper[] getVideos(int sort, boolean desc);
    abstract public AMediaWrapper[] getRecentVideos();
    abstract public AMediaWrapper[] getAudio();
    abstract public AMediaWrapper[] getAudio(int sort, boolean desc);
    abstract public AMediaWrapper[] getPagedAudio(int sort, boolean desc, int nbitems, int offset);
    abstract public AMediaWrapper[] getRecentAudio();
    abstract public int getVideoCount();
    abstract public int getAudioCount();
    abstract public AAlbum[] getAlbums();
    abstract public AAlbum[] getAlbums(int sort, boolean desc);
    abstract public AAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset);
    abstract public int getAlbumsCount();
    abstract public int getAlbumsCount(String query);
    abstract public AAlbum getAlbum(long albumId);
    abstract public AArtist[] getArtists(boolean all);
    abstract public AArtist[] getArtists(boolean all, int sort, boolean desc);
    abstract public AArtist[] getPagedArtists(boolean all, int sort, boolean desc, int nbItems, int offset);
    abstract public int getArtistsCount(boolean all);
    abstract public int getArtistsCount(String query);
    abstract public AArtist getArtist(long artistId);
    abstract public AGenre[] getGenres();
    abstract public AGenre[] getGenres(int sort, boolean desc);
    abstract public AGenre[] getPagedGenres(int sort, boolean desc, int nbItems, int offset);
    abstract public int getGenresCount();
    abstract public int getGenresCount(String query);
    abstract public AGenre getGenre(long genreId);
    abstract public APlaylist[] getPlaylists(int sort, boolean desc);
    abstract public APlaylist[] getPlaylists();
    abstract public APlaylist[] getPagedPlaylists(int sort, boolean desc, int nbItems, int offset);
    abstract public int getPlaylistsCount();
    abstract public int getPlaylistsCount(String query);
    abstract public APlaylist getPlaylist(long playlistId);
    abstract public APlaylist createPlaylist(String name);
    abstract public void pauseBackgroundOperations();
    abstract public void resumeBackgroundOperations();
    abstract public void reload();
    abstract public void reload(String entrypoint);
    abstract public void forceParserRetry();
    abstract public void forceRescan();
    abstract public AMediaWrapper[] lastMediaPlayed();
    abstract public AMediaWrapper[] lastStreamsPlayed();
    abstract public boolean clearHistory();
    abstract public boolean addToHistory(String mrl, String title);
    abstract public AMediaWrapper getMedia(long id);
    abstract public AMediaWrapper getMedia(Uri uri);
    abstract public AMediaWrapper getMedia(String mrl);
    abstract public AMediaWrapper addMedia(String mrl);
    abstract public boolean removeExternalMedia(long id);
    abstract public AMediaWrapper addStream(String mrl, String title);
    abstract public AFolder[] getFolders(int type, int sort, boolean desc, int nbItems, int offset);
    abstract public int getFoldersCount(int type);
    abstract public void requestThumbnail(long id);
    abstract public boolean increasePlayCount(long mediaId);
    abstract public AMediaWrapper findMedia(AMediaWrapper mw);
    abstract public void onMediaAdded(AMediaWrapper[] mediaList);
    abstract public void onMediaUpdated(AMediaWrapper[] mediaList);
    abstract public void onMediaDeleted();
    abstract public void onArtistsAdded();
    abstract public void onArtistsModified();
    abstract public void onArtistsDeleted();
    abstract public void onAlbumsAdded();
    abstract public void onAlbumsModified();
    abstract public void onAlbumsDeleted();
    abstract public void onGenresAdded();
    abstract public void onGenresModified();
    abstract public void onGenresDeleted();
    abstract public void onPlaylistsAdded();
    abstract public void onPlaylistsModified();
    abstract public void onPlaylistsDeleted();
    abstract public void onDiscoveryStarted(String entryPoint);
    abstract public void onDiscoveryProgress(String entryPoint);
    abstract public void onDiscoveryCompleted(String entryPoint);
    abstract public void onParsingStatsUpdated(int percent);
    abstract public void onBackgroundTasksIdleChanged(boolean isIdle);
    abstract public void onReloadStarted(String entryPoint);
    abstract public void onReloadCompleted(String entryPoint);
    abstract public void onEntryPointBanned(String entryPoint, boolean success);
    abstract public void onEntryPointUnbanned(String entryPoint, boolean success);
    abstract public void onEntryPointRemoved(String entryPoint, boolean success);
    abstract public void onMediaThumbnailReady(AMediaWrapper Amedia, boolean success);
    abstract public void addMediaCb(MediaCb mediaUpdated);
    abstract public void removeMediaCb(MediaCb mediaUpdatedCb);
    abstract public void addArtistsCb(ArtistsCb artictAddedCb);
    abstract public void removeArtistsCb(ArtistsCb artistCb);
    abstract public void addAlbumsCb(AlbumsCb albumsAddedCb);
    abstract public void removeAlbumsCb(AlbumsCb albumsAddedCb);
    abstract public void addGenreCb(GenresCb genresAddedCb);
    abstract public void removeGenreCb(GenresCb genresAddedCb);
    abstract public void addPlaylistCb(PlaylistsCb playlistsAddedCb);
    abstract public void removePlaylistCb(PlaylistsCb playlistsAddedCb);
    abstract public SearchAggregate search(String query);
    abstract public AMediaWrapper[] searchMedia(String query);
    abstract public AMediaWrapper[] searchMedia(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int getMediaCount(String query);
    abstract public AMediaWrapper[] searchAudio(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int getAudioCount(String query);
    abstract public AMediaWrapper[] searchVideo(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int getVideoCount(String query);
    abstract public AArtist[] searchArtist(String query);
    abstract public AArtist[] searchArtist(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public AAlbum[] searchAlbum(String query);
    abstract public AAlbum[] searchAlbum(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public AGenre[] searchGenre(String query);
    abstract public AGenre[] searchGenre(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public APlaylist[] searchPlaylist(String query);
    abstract public APlaylist[] searchPlaylist(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public void addDeviceDiscoveryCb(DevicesDiscoveryCb cb);
    abstract public void removeDeviceDiscoveryCb(DevicesDiscoveryCb cb);
    abstract public void addOnMedialibraryReadyListener(OnMedialibraryReadyListener cb);
    abstract public void removeOnMedialibraryReadyListener(OnMedialibraryReadyListener cb);
    abstract public void addEntryPointsEventsCb(EntryPointsEventsCb cb);
    abstract public void removeEntryPointsEventsCb(EntryPointsEventsCb cb);
    abstract public void addOnDeviceChangeListener(OnDeviceChangeListener cb);
    abstract public void removeOnDeviceChangeListener(OnDeviceChangeListener cb);
}
