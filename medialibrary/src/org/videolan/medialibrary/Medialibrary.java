package org.videolan.medialibrary;

import android.Manifest;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import org.videolan.libvlc.LibVLC;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.medialibrary.interfaces.EntryPointsEventsCb;
import org.videolan.medialibrary.interfaces.MediaAddedCb;
import org.videolan.medialibrary.interfaces.MediaUpdatedCb;
import org.videolan.medialibrary.media.Album;
import org.videolan.medialibrary.media.Artist;
import org.videolan.medialibrary.media.Genre;
import org.videolan.medialibrary.media.HistoryItem;
import org.videolan.medialibrary.media.MediaSearchAggregate;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Playlist;
import org.videolan.medialibrary.media.SearchAggregate;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import videolan.org.commontools.LiveEvent;

public class Medialibrary {
    private static final String TAG = "VLC/JMedialibrary";

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

    public static final int FLAG_MEDIA_UPDATED_AUDIO        = 1 << 0;
    public static final int FLAG_MEDIA_UPDATED_AUDIO_EMPTY  = 1 << 1;
    public static final int FLAG_MEDIA_UPDATED_VIDEO        = 1 << 2;
    public static final int FLAG_MEDIA_ADDED_AUDIO          = 1 << 3;
    public static final int FLAG_MEDIA_ADDED_AUDIO_EMPTY    = 1 << 4;
    public static final int FLAG_MEDIA_ADDED_VIDEO          = 1 << 5;

    public static final int ML_INIT_SUCCESS = 0;
    public static final int ML_INIT_ALREADY_INITIALIZED = 1;
    public static final int ML_INIT_FAILED = 2;
    public static final int ML_INIT_DB_RESET = 3;

    public static final String ACTION_IDLE = "action_idle";
    public static final String STATE_IDLE = "state_idle";

    public static final MediaWrapper[] EMPTY_COLLECTION = {};
    public static final String VLC_MEDIA_DB_NAME = "/vlc_media.db";
    public static final String THUMBS_FOLDER_NAME = "/thumbs";


    private long mInstanceID;
    private volatile boolean mIsInitiated = false;
    private volatile boolean mIsWorking = false;

    private MediaUpdatedCb mediaUpdatedCb = null;
    private MediaAddedCb mediaAddedCb = null;
    private ArtistsAddedCb mArtistsAddedCb = null;
    private ArtistsModifiedCb mArtistsModifiedCb = null;
    private AlbumsAddedCb mAlbumsAddedCb = null;
    private AlbumsModifiedCb mAlbumsModifiedCb = null;
    private final List<OnMedialibraryReadyListener> onMedialibraryReadyListeners = new ArrayList<>();
    private volatile boolean isMedialibraryStarted = false;
    private final List<DevicesDiscoveryCb> devicesDiscoveryCbList = new ArrayList<>();
    private final List<EntryPointsEventsCb> entryPointsEventsCbList = new ArrayList<>();
    private static Context sContext;

    private static final Medialibrary instance = new Medialibrary();

    public static Context getContext() {
        return sContext;
    }

    public int init(Context context) {
        if (context == null) return ML_INIT_FAILED;
        sContext = context;
        File extFilesDir = context.getExternalFilesDir(null);
        File dbDirectory = context.getDir("db", Context.MODE_PRIVATE);
        if (extFilesDir == null || !extFilesDir.exists()
                || dbDirectory == null || !dbDirectory.canWrite())
            return ML_INIT_FAILED;
        LibVLC.loadLibraries();
        int initCode = nativeInit(dbDirectory+ VLC_MEDIA_DB_NAME, extFilesDir+ THUMBS_FOLDER_NAME);
        mIsInitiated = initCode != ML_INIT_FAILED;
        return initCode;
    }

    public void start() {
        nativeStart();
        isMedialibraryStarted = true;
        synchronized (onMedialibraryReadyListeners) {
            for (OnMedialibraryReadyListener listener : onMedialibraryReadyListeners) listener.onMedialibraryReady();
        }
        nativeSetMediaAddedCbFlag(FLAG_MEDIA_ADDED_AUDIO|FLAG_MEDIA_ADDED_VIDEO);
        nativeSetMediaUpdatedCbFlag(FLAG_MEDIA_UPDATED_AUDIO|FLAG_MEDIA_UPDATED_VIDEO);
    }

    public boolean isStarted() {
        return isMedialibraryStarted;
    }

    public void banFolder(@NonNull String path) {
        if (mIsInitiated && new File(path).exists())
            nativeBanFolder(Tools.encodeVLCMrl(path));
    }

    public void unbanFolder(@NonNull String path) {
        if (mIsInitiated && new File(path).exists())
            nativeUnbanFolder(Tools.encodeVLCMrl(path));
    }

    public String[] getDevices() {
        return mIsInitiated ? nativeDevices() : new String[0];
    }

    public boolean addDevice(@NonNull String uuid, @NonNull String path, boolean removable) {
        return nativeAddDevice(Tools.encodeVLCMrl(uuid), Tools.encodeVLCMrl(path), removable);
    }

    public void discover(@NonNull String path) {
        if (mIsInitiated) nativeDiscover(Tools.encodeVLCMrl(path));
    }

    public void removeFolder(@NonNull String mrl) {
        if (!mIsInitiated) return;
        final String[] folders = getFoldersList();
        for (String folder : folders) {
            if (!folder.equals(mrl) && folder.contains(mrl))
                removeFolder(folder);
        }
        nativeRemoveEntryPoint(Tools.encodeVLCMrl(mrl));
    }

    public String[] getFoldersList() {
        if (!mIsInitiated)
            return new String[0];
        return nativeEntryPoints();
    }

    public boolean removeDevice(String uuid) {
        return mIsInitiated && !TextUtils.isEmpty(uuid) && nativeRemoveDevice(Tools.encodeVLCMrl(uuid));
    }

    @Override
    protected void finalize() throws Throwable {
        if (mIsInitiated)
            nativeRelease();
        super.finalize();
    }

    @NonNull
    public static Medialibrary getInstance() {
        return instance;
    }

    @WorkerThread
    public MediaWrapper[] getVideos() {
        return mIsInitiated ? nativeGetVideos() : new MediaWrapper[0];
    }

    @WorkerThread
    public MediaWrapper[] getVideos(int sort, boolean desc) {
        return mIsInitiated ? nativeGetSortedVideos(sort, desc) : new MediaWrapper[0];
    }

    @WorkerThread
    public MediaWrapper[] getRecentVideos() {
        return mIsInitiated ? nativeGetRecentVideos() : new MediaWrapper[0];
    }

    @WorkerThread
    public MediaWrapper[] getAudio() {
        return mIsInitiated ? nativeGetAudio() : new MediaWrapper[0];
    }

    @WorkerThread
    public MediaWrapper[] getAudio(int sort, boolean desc) {
        return mIsInitiated ? nativeGetSortedAudio(sort, desc) : new MediaWrapper[0];
    }

    @WorkerThread
    public MediaWrapper[] getRecentAudio() {
        return mIsInitiated ? nativeGetRecentAudio() : new MediaWrapper[0];
    }

    public int getVideoCount() {
        return mIsInitiated ? nativeGetVideoCount() : 0;
    }

    public int getAudioCount() {
        return mIsInitiated ? nativeGetAudioCount() : 0;
    }


    @WorkerThread
    public Album[] getAlbums() {
        return getAlbums(Medialibrary.SORT_DEFAULT, false);
    }

    @WorkerThread
    public Album[] getAlbums(int sort, boolean desc) {
        return mIsInitiated ? nativeGetAlbums(sort, desc) : new Album[0];
    }

    @WorkerThread
    public Album getAlbum(long albumId) {
        return mIsInitiated ? nativeGetAlbum(albumId) : null;
    }

    @WorkerThread
    public Artist[] getArtists(boolean all) {
        return getArtists(all, Medialibrary.SORT_DEFAULT, false);
    }

    @WorkerThread
    public Artist[] getArtists(boolean all, int sort, boolean desc) {
        return mIsInitiated ? nativeGetArtists(all, sort, desc) : new Artist[0];
    }

    public Artist getArtist(long artistId) {
        return mIsInitiated ? nativeGetArtist(artistId) : null;
    }

    @WorkerThread
    public Genre[] getGenres() {
        return getGenres(Medialibrary.SORT_DEFAULT, false);
    }

    @WorkerThread
    public Genre[] getGenres(int sort, boolean desc) {
        return mIsInitiated ? nativeGetGenres(sort, desc) : new Genre[0];
    }

    public Genre getGenre(long genreId) {
        return mIsInitiated ? nativeGetGenre(genreId) : null;
    }

    @WorkerThread
    public Playlist[] getPlaylists() {
        return getPlaylists(Medialibrary.SORT_DEFAULT, false);
    }

    @WorkerThread
    public Playlist[] getPlaylists(int sort, boolean desc) {
        return mIsInitiated ? nativeGetPlaylists(sort, desc) : new Playlist[0];
    }

    public Playlist getPlaylist(long playlistId) {
        return mIsInitiated ? nativeGetPlaylist(playlistId) : null;
    }

    public Playlist createPlaylist(String name) {
        return mIsInitiated && !TextUtils.isEmpty(name) ? nativePlaylistCreate(name) : null;
    }

    public void pauseBackgroundOperations() {
        if (mIsInitiated)
            nativePauseBackgroundOperations();
    }

    public void resumeBackgroundOperations() {
        if (mIsInitiated)
            nativeResumeBackgroundOperations();
    }

    public void reload() {
        if (mIsInitiated && !isWorking()) nativeReload();
    }

    public void reload(String entryPoint) {
        if (mIsInitiated && !TextUtils.isEmpty(entryPoint))
            nativeReload(Tools.encodeVLCMrl(entryPoint));
    }

    public void forceParserRetry() {
        if (mIsInitiated) nativeForceParserRetry();
    }

    public void forceRescan() {
        if (mIsInitiated) nativeForceRescan();
    }

    @WorkerThread
    public MediaWrapper[] lastMediaPlayed() {
        return mIsInitiated ? nativeLastMediaPlayed() : EMPTY_COLLECTION;
    }

    @WorkerThread
    public HistoryItem[] lastStreamsPlayed() {
        return mIsInitiated ? nativeLastStreamsPlayed() : new HistoryItem[0];
    }

    public boolean clearHistory() {
        return mIsInitiated && nativeClearHistory();
    }

    public boolean addToHistory(String mrl, String title) {
        return mIsInitiated && nativeAddToHistory(Tools.encodeVLCMrl(mrl), Tools.encodeVLCMrl(title));
    }

    @Nullable
    public MediaWrapper getMedia(long id) {
        return mIsInitiated ? nativeGetMedia(id) : null;
    }

    @Nullable
    public MediaWrapper getMedia(Uri uri) {
        final String vlcMrl = Tools.encodeVLCMrl(uri.toString());
        return mIsInitiated && !TextUtils.isEmpty(vlcMrl) ? nativeGetMediaFromMrl(vlcMrl) : null;
    }

    @Nullable
    public MediaWrapper getMedia(String mrl) {
        final String vlcMrl = Tools.encodeVLCMrl(mrl);
        return mIsInitiated && !TextUtils.isEmpty(vlcMrl) ? nativeGetMediaFromMrl(vlcMrl) : null;
    }

    @Nullable
    public MediaWrapper addMedia(String mrl) {
        final String vlcMrl = Tools.encodeVLCMrl(mrl);
        return mIsInitiated && !TextUtils.isEmpty(vlcMrl) ? nativeAddMedia(vlcMrl) : null;
    }

    public void requestThumbnail(long id) {
        if (mIsInitiated) nativeRequestThumbnail(id);
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

    public boolean increasePlayCount(long mediaId) {
        return mIsInitiated && mediaId > 0 && nativeIncreasePlayCount(mediaId);
    }

    // If media is not in ML, find it with its path
    public MediaWrapper findMedia(MediaWrapper mw) {
        if (mIsInitiated && mw != null && mw.getId() == 0L) {
            Uri uri = mw.getUri();
            MediaWrapper libraryMedia = getMedia(uri);
            if (libraryMedia == null && TextUtils.equals("file", uri.getScheme()) &&
                    uri.getPath() != null && uri.getPath().startsWith("/sdcard")) {
                uri = Tools.convertLocalUri(uri);
                libraryMedia = getMedia(uri);
            }
            if (libraryMedia != null)
                return libraryMedia;
        }
        return mw;
    }

    @SuppressWarnings("unused")
    public void onMediaAdded(MediaWrapper[] mediaList) {
        if (mediaAddedCb != null)
            mediaAddedCb.onMediaAdded(mediaList);
    }

    @SuppressWarnings("unused")
    public void onMediaUpdated(MediaWrapper[] mediaList) {
        if (mediaUpdatedCb != null)
            mediaUpdatedCb.onMediaUpdated(mediaList);
    }

    @SuppressWarnings("unused")
    public void onMediaDeleted(long[] ids) {
        for (long id : ids)
            Log.d(TAG, "onMediaDeleted: "+id);
    }

    @SuppressWarnings("unused")
    public void onArtistsAdded() {
        if (mArtistsAddedCb != null) mArtistsAddedCb.onArtistsAdded();
    }

    @SuppressWarnings("unused")
    public void onArtistsModified() {
        if (mArtistsModifiedCb != null)
            mArtistsModifiedCb.onArtistsModified();
    }

    @SuppressWarnings("unused")
    public void onAlbumsAdded() {
        if (mAlbumsAddedCb != null)
            mAlbumsAddedCb.onAlbumsAdded();
    }

    @SuppressWarnings("unused")
    public void onAlbumsModified() {
        if (mAlbumsModifiedCb != null)
            mAlbumsModifiedCb.onAlbumsModified();
    }

    @SuppressWarnings("unused")
    public void onArtistsDeleted(long[] ids) {
        for (long id : ids)
            Log.d(TAG, "onArtistsDeleted: "+id);
    }

    @SuppressWarnings("unused")
    public void onAlbumsDeleted(long[] ids) {
        for (long id : ids)
            Log.d(TAG, "onAlbumsDeleted: "+id);
    }

    public void onDiscoveryStarted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryStarted(entryPoint);
        }
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onDiscoveryStarted(entryPoint);
        }
    }

    public void onDiscoveryProgress(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryProgress(entryPoint);
        }
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onDiscoveryProgress(entryPoint);
        }
    }

    public void onDiscoveryCompleted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryCompleted(entryPoint);
        }
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onDiscoveryCompleted(entryPoint);
        }
    }

    public void onParsingStatsUpdated(int percent) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onParsingStatsUpdated(percent);
        }
    }

    @SuppressWarnings("unused")
    public void onBackgroundTasksIdleChanged(boolean isIdle) {
        mIsWorking = !isIdle;
        LocalBroadcastManager.getInstance(sContext).sendBroadcast(new Intent(ACTION_IDLE).putExtra(STATE_IDLE, isIdle));
        if (isIdle) {
            synchronized (onMedialibraryReadyListeners) {
                for (OnMedialibraryReadyListener listener : onMedialibraryReadyListeners) listener.onMedialibraryIdle();
            }
        }
    }

    @SuppressWarnings("unused")
    void onReloadStarted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onReloadStarted(entryPoint);
        }
    }

    @SuppressWarnings("unused")
    void onReloadCompleted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onReloadCompleted(entryPoint);
        }
    }

    @SuppressWarnings("unused")
    void onEntryPointBanned(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointBanned(entryPoint, success);
        }
    }

    @SuppressWarnings("unused")
    void onEntryPointUnbanned(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointUnbanned(entryPoint, success);
        }
    }

    @SuppressWarnings("unused")
    void onEntryPointRemoved(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointRemoved(entryPoint, success);
        }
    }

    public static LiveData<MediaWrapper> lastThumb = new LiveEvent<>();
    @SuppressWarnings({"unused", "unchecked"})
    void onMediaThumbnailReady(MediaWrapper media, boolean success) {
        if (success) ((MutableLiveData)lastThumb).postValue(media);
    }

    public void setMediaUpdatedCb(MediaUpdatedCb mediaUpdatedCb, int flags) {
        if (!mIsInitiated)
            return;
        this.mediaUpdatedCb = mediaUpdatedCb;
        nativeSetMediaUpdatedCbFlag(flags);
    }

    public void removeMediaUpdatedCb() {
        if (!mIsInitiated)
            return;
        setMediaUpdatedCb(null, 0);
    }

    public void setMediaAddedCb(MediaAddedCb mediaAddedCb, int flags) {
        if (!mIsInitiated)
            return;
        this.mediaAddedCb = mediaAddedCb;
        nativeSetMediaAddedCbFlag(flags);
    }

    public void setArtistsAddedCb(ArtistsAddedCb artistsAddedCb) {
        if (!mIsInitiated) return;
        this.mArtistsAddedCb = artistsAddedCb;
    }

    public void setArtistsModifiedCb(ArtistsModifiedCb artistsModifiedCb) {
        if (!mIsInitiated)
            return;
        this.mArtistsModifiedCb = artistsModifiedCb;
    }

    public void setAlbumsAddedCb(AlbumsAddedCb AlbumsAddedCb) {
        if (!mIsInitiated)
            return;
        this.mAlbumsAddedCb = AlbumsAddedCb;
    }

    public void setAlbumsModifiedCb(AlbumsModifiedCb AlbumsModifiedCb) {
        if (!mIsInitiated)
            return;
        this.mAlbumsModifiedCb = AlbumsModifiedCb;
    }

    public SearchAggregate search(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearch(query) : null;
    }

    public MediaSearchAggregate searchMedia(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchMedia(query) : null;
    }

    public Artist[] searchArtist(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchArtist(query) : null;
    }

    public Album[] searchAlbum(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchAlbum(query) : null;
    }

    public Genre[] searchGenre(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchGenre(query) : null;
    }

    public Playlist[] searchPlaylist(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPlaylist(query) : null;
    }

    public void addDeviceDiscoveryCb(DevicesDiscoveryCb cb) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.contains(cb))
                devicesDiscoveryCbList.add(cb);
        }
    }

    public void removeDeviceDiscoveryCb(DevicesDiscoveryCb cb) {
        synchronized (devicesDiscoveryCbList) {
            devicesDiscoveryCbList.remove(cb);
        }
    }

    public void addOnMedialibraryReadyListener(OnMedialibraryReadyListener cb) {
        synchronized (onMedialibraryReadyListeners) {
            if (!onMedialibraryReadyListeners.contains(cb))
                onMedialibraryReadyListeners.add(cb);
        }
    }

    public void removeOnMedialibraryReadyListener(OnMedialibraryReadyListener cb) {
        synchronized (onMedialibraryReadyListeners) {
            onMedialibraryReadyListeners.remove(cb);
        }
    }

    public void addEntryPointsEventsCb(EntryPointsEventsCb cb) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.contains(cb))
                entryPointsEventsCbList.add(cb);
        }
    }

    public void removeEntryPointsEventsCb(EntryPointsEventsCb cb) {
        synchronized (entryPointsEventsCbList) {
            entryPointsEventsCbList.remove(cb);
        }
    }

    public void removeMediaAddedCb() {
        if (!mIsInitiated)
            return;
        setMediaAddedCb(null, 0);
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

    /* used only before API 13: substitute for NewWeakGlobalRef */
    @SuppressWarnings("unused") /* Used from JNI */
    private Object getWeakReference() {
        return new WeakReference<>(this);
    }


    // Native methods
    private native int nativeInit(String dbPath, String thumbsPath);
    private native void nativeStart();
    private native void nativeRelease();
    private native void nativeBanFolder(String path);
    private native void nativeUnbanFolder(String path);
    private native boolean nativeAddDevice(String uuid, String path, boolean removable);
    private native String[] nativeDevices();
    private native void nativeDiscover(String path);
    private native void nativeRemoveEntryPoint(String path);
    private native String[] nativeEntryPoints();
    private native boolean nativeRemoveDevice(String uuid);
    private native MediaWrapper[] nativeLastMediaPlayed();
    private native HistoryItem[] nativeLastStreamsPlayed();
    private native  boolean nativeAddToHistory(String mrl, String title);
    private native  boolean nativeClearHistory();
    private native MediaWrapper nativeGetMedia(long id);
    private native MediaWrapper nativeGetMediaFromMrl(String mrl);
    private native MediaWrapper nativeAddMedia(String mrl);
    private native MediaWrapper[] nativeGetVideos();
    private native MediaWrapper[] nativeGetSortedVideos(int sort, boolean desc);
    private native MediaWrapper[] nativeGetRecentVideos();
    private native MediaWrapper[] nativeGetAudio();
    private native MediaWrapper[] nativeGetSortedAudio(int sort, boolean desc);
    private native MediaWrapper[] nativeGetRecentAudio();
    private native int nativeGetVideoCount();
    private native int nativeGetAudioCount();
    private native Album[] nativeGetAlbums(int sort, boolean desc);
    private native Album nativeGetAlbum(long albumtId);
    private native Artist[] nativeGetArtists(boolean all, int sort, boolean desc);
    private native Artist nativeGetArtist(long artistId);
    private native Genre[] nativeGetGenres(int sort, boolean desc);
    private native Genre nativeGetGenre(long genreId);
    private native Playlist[] nativeGetPlaylists(int sort, boolean desc);
    private native Playlist nativeGetPlaylist(long playlistId);
    private native Playlist nativePlaylistCreate(String name);
    private native void nativePauseBackgroundOperations();
    private native void nativeResumeBackgroundOperations();
    private native void nativeReload();
    private native void nativeReload(String entryPoint);
    private native void nativeForceParserRetry();
    private native void nativeForceRescan();
    private native boolean nativeIncreasePlayCount(long mediaId);
    private native void nativeSetMediaUpdatedCbFlag(int flags);
    private native void nativeSetMediaAddedCbFlag(int flags);
    private native SearchAggregate nativeSearch(String query);
    private native MediaSearchAggregate nativeSearchMedia(String query);
    private native Artist[] nativeSearchArtist(String query);
    private native Album[] nativeSearchAlbum(String query);
    private native Genre[] nativeSearchGenre(String query);
    private native Playlist[] nativeSearchPlaylist(String query);
    private native void nativeRequestThumbnail(long mediaId);

    private boolean canReadStorage(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public interface ArtistsAddedCb {
        void onArtistsAdded();
    }

    public interface ArtistsModifiedCb {
        void onArtistsModified();
    }

    public interface AlbumsAddedCb {
        void onAlbumsAdded();
    }

    public interface AlbumsModifiedCb {
        void onAlbumsModified();
    }

    public interface OnMedialibraryReadyListener {
        void onMedialibraryReady();
        void onMedialibraryIdle();
    }
}
