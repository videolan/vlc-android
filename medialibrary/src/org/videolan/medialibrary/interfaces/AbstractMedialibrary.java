package org.videolan.medialibrary.interfaces;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.SingleEvent;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractFolder;
import org.videolan.medialibrary.interfaces.media.AbstractGenre;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;
import org.videolan.medialibrary.interfaces.media.AbstractPlaylist;
import org.videolan.medialibrary.interfaces.media.AbstractVideoGroup;
import org.videolan.medialibrary.media.SearchAggregate;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

abstract public class AbstractMedialibrary {

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
    public static final int ML_INIT_DB_CORRUPTED = 4;

    public static final AbstractMediaWrapper[] EMPTY_COLLECTION = {};
    public static final String VLC_MEDIA_DB_NAME = "/vlc_media.db";
    public static final String THUMBS_FOLDER_NAME = "/thumbs";
    public static final String MEDIALIB_FOLDER_NAME = "/medialib";

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
    private MedialibraryExceptionHandler mExceptionHandler;
    protected static Context sContext;
    public static LiveData<AbstractMediaWrapper> lastThumb = new SingleEvent<>();

    protected static final AbstractMedialibrary instance = MLServiceLocator.getAbstractMedialibrary();

    public static Context getContext() {
        return sContext;
    }

    public static LiveData<Boolean> getState() {
        return sRunning;
    }

    public enum ThumbnailSizeType {
        /// A small sized thumbnail. Considered to be the default value before model 17
        Thumbnail,
        /// A banner type thumbnail. The exact size is application dependent.
        Banner
    }

    public boolean isStarted() {
        return isMedialibraryStarted;
    }

    protected void finalize() throws Throwable {
        super.finalize();
    }

    @NonNull
    public static AbstractMedialibrary getInstance() {
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

    public interface MedialibraryExceptionHandler {
        void onUnhandledException(String context, String errMsg, boolean clearSuggested);
    }

    public MedialibraryExceptionHandler getExceptionHandler() {
        return mExceptionHandler;
    }

    public void setExceptionHandler(MedialibraryExceptionHandler mExceptionHandler) {
        this.mExceptionHandler = mExceptionHandler;
    }


    // If media is not in ML, find it with its path
    public AbstractMediaWrapper findMedia(AbstractMediaWrapper mw) {
        if (mIsInitiated && mw != null && mw.getId() == 0L) {
            final Uri uri = mw.getUri();
            final AbstractMediaWrapper libraryMedia = getMedia(uri);
            if (libraryMedia != null) {
                libraryMedia.addFlags(mw.getFlags());
                return libraryMedia;
            }
            if (TextUtils.equals("file", uri.getScheme()) &&
                    uri.getPath() != null && uri.getPath().startsWith("/sdcard")) {
                final AbstractMediaWrapper alternateMedia = getMedia(Tools.convertLocalUri(uri));
                if (alternateMedia != null) {
                    alternateMedia.addFlags(mw.getFlags());
                    return alternateMedia;
                }
            }
        }
        return mw;
    }

    @SuppressWarnings("unused")
    public void onMediaAdded(AbstractMediaWrapper[] mediaList) {
        synchronized (mMediaCbs) {
            for (MediaCb cb : mMediaCbs) cb.onMediaAdded();
        }
    }

    @SuppressWarnings("unused")
    public void onMediaUpdated() {
        synchronized (mMediaCbs) {
            for (MediaCb cb : mMediaCbs) cb.onMediaModified();
        }
    }

    @SuppressWarnings("unused")
    public void onMediaDeleted() {
        synchronized (mMediaCbs) {
            for (MediaCb cb : mMediaCbs) cb.onMediaDeleted();
        }
    }

    @SuppressWarnings("unused")
    public void onArtistsAdded() {
        synchronized (mArtistsCbs) {
            for (ArtistsCb cb : mArtistsCbs) cb.onArtistsAdded();
        }
    }

    @SuppressWarnings("unused")
    public void onArtistsModified() {
        synchronized (mArtistsCbs) {
            for (ArtistsCb cb : mArtistsCbs) cb.onArtistsModified();
        }
    }

    @SuppressWarnings("unused")
    public void onArtistsDeleted() {
        synchronized (mArtistsCbs) {
            for (ArtistsCb cb : mArtistsCbs) cb.onArtistsDeleted();
        }
    }

    @SuppressWarnings("unused")
    public void onAlbumsAdded() {
        synchronized (mAlbumsCbs) {
            for (AlbumsCb cb : mAlbumsCbs) cb.onAlbumsAdded();
        }
    }

    @SuppressWarnings("unused")
    public void onAlbumsModified() {
        synchronized (mAlbumsCbs) {
            for (AlbumsCb cb : mAlbumsCbs) cb.onAlbumsModified();
        }
    }

    @SuppressWarnings("unused")
    public void onAlbumsDeleted() {
        synchronized (mAlbumsCbs) {
            for (AlbumsCb cb : mAlbumsCbs) cb.onAlbumsDeleted();
        }
    }

    @SuppressWarnings("unused")
    public void onGenresAdded() {
        synchronized (mGenreCbs) {
            for (GenresCb cb : mGenreCbs) cb.onGenresAdded();
        }
    }

    @SuppressWarnings("unused")
    public void onGenresModified() {
        synchronized (mGenreCbs) {
            for (GenresCb cb : mGenreCbs) cb.onGenresModified();
        }
    }

    @SuppressWarnings("unused")
    public void onGenresDeleted() {
        synchronized (mGenreCbs) {
            for (GenresCb cb : mGenreCbs) cb.onGenresDeleted();
        }
    }

    @SuppressWarnings("unused")
    public void onPlaylistsAdded() {
        synchronized (mPlaylistCbs) {
            for (PlaylistsCb cb : mPlaylistCbs) cb.onPlaylistsAdded();
        }
    }

    @SuppressWarnings("unused")
    public void onPlaylistsModified() {
        synchronized (mPlaylistCbs) {
            for (PlaylistsCb cb : mPlaylistCbs) cb.onPlaylistsModified();
        }
    }

    @SuppressWarnings("unused")
    public void onPlaylistsDeleted() {
        synchronized (mPlaylistCbs) {
            for (PlaylistsCb cb : mPlaylistCbs) cb.onPlaylistsDeleted();
        }
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
        sRunning.postValue(mIsWorking);
        if (isIdle) {
            synchronized (onMedialibraryReadyListeners) {
                for (OnMedialibraryReadyListener listener : onMedialibraryReadyListeners) listener.onMedialibraryIdle();
            }
        }
    }
    @SuppressWarnings("unused")
    public void onUnhandledException(String context, String errMsg, boolean clearSuggested) {
        if (mExceptionHandler != null) mExceptionHandler.onUnhandledException(context, errMsg, clearSuggested);
    }

    @SuppressWarnings("unused")
    public void onReloadStarted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onReloadStarted(entryPoint);
        }
    }

    @SuppressWarnings("unused")
    public void onReloadCompleted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onReloadCompleted(entryPoint);
        }
    }

    @SuppressWarnings("unused")
    public void onEntryPointBanned(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointBanned(entryPoint, success);
        }
    }

    @SuppressWarnings("unused")
    public void onEntryPointUnbanned(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointUnbanned(entryPoint, success);
        }
    }


    @SuppressWarnings("unused")
    void onEntryPointAdded(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointAdded(entryPoint, success);
        }
    }

    @SuppressWarnings("unused")
    public void onEntryPointRemoved(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointRemoved(entryPoint, success);
        }
    }

    //    public static LiveData<AbstractMediaWrapper> lastThumb = new SingleEvent<>();
    @SuppressWarnings({"unused", "unchecked"})
    public void onMediaThumbnailReady(AbstractMediaWrapper media, boolean success) {
        if (success) ((MutableLiveData)lastThumb).postValue(media);
    }

    public void addMediaCb(MediaCb mediaUpdatedCb) {
        if (!mIsInitiated) return;
        synchronized (mMediaCbs) {
//            if (mMediaCbs.isEmpty()) {
//                nativeSetMediaAddedCbFlag(FLAG_MEDIA_ADDED_AUDIO|FLAG_MEDIA_ADDED_VIDEO);
//                nativeSetMediaUpdatedCbFlag(FLAG_MEDIA_UPDATED_AUDIO|FLAG_MEDIA_UPDATED_VIDEO);
//            }
            mMediaCbs.add(mediaUpdatedCb);
        }
    }

    public void removeMediaCb(MediaCb mediaUpdatedCb) {
        if (!mIsInitiated) return;
        synchronized (mMediaCbs) {
            mMediaCbs.remove(mediaUpdatedCb);
//            if (mMediaCbs.isEmpty()) {
//                nativeSetMediaAddedCbFlag(0);
//                nativeSetMediaUpdatedCbFlag(0);
//            }
        }
    }

    public void addArtistsCb(ArtistsCb artistsAddedCb) {
        if (!mIsInitiated) return;
        synchronized (mArtistsCbs) {
            mArtistsCbs.add(artistsAddedCb);
        }
    }

    public void removeArtistsCb(ArtistsCb artistsAddedCb) {
        if (!mIsInitiated) return;
        synchronized (mArtistsCbs) {
            mArtistsCbs.remove(artistsAddedCb);
        }
    }

    public void addAlbumsCb(AlbumsCb AlbumsAddedCb) {
        if (!mIsInitiated) return;
        synchronized (mAlbumsCbs) {
            mAlbumsCbs.add(AlbumsAddedCb);
        }
    }

    public void removeAlbumsCb(AlbumsCb AlbumsAddedCb) {
        if (!mIsInitiated) return;
        synchronized (mAlbumsCbs) {
            mAlbumsCbs.remove(AlbumsAddedCb);
        }
    }

    public void addGenreCb(GenresCb GenreCb) {
        if (!mIsInitiated) return;
        synchronized (mGenreCbs) {
            this.mGenreCbs.add(GenreCb);
        }
    }

    public void removeGenreCb(GenresCb GenreCb) {
        if (!mIsInitiated) return;
        synchronized (mGenreCbs) {
            this.mGenreCbs.remove(GenreCb);
        }
    }

    public void addPlaylistCb(PlaylistsCb playlistCb) {
        if (!mIsInitiated) return;
        synchronized (mPlaylistCbs) {
            this.mPlaylistCbs.add(playlistCb);
        }
    }

    public void removePlaylistCb(PlaylistsCb playlistCb) {
        if (!mIsInitiated) return;
        synchronized (mPlaylistCbs) {
            this.mPlaylistCbs.remove(playlistCb);
        }
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

    public void addOnDeviceChangeListener(OnDeviceChangeListener listener) {
        synchronized (onDeviceChangeListeners) {
            if (!onDeviceChangeListeners.contains(listener))
                onDeviceChangeListeners.add(listener);
        }
    }

    public void removeOnDeviceChangeListener(OnDeviceChangeListener listener) {
        synchronized (onDeviceChangeListeners) {
            onDeviceChangeListeners.remove(listener);
        }
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
    abstract public AbstractMediaWrapper[] getVideos();
    abstract public AbstractMediaWrapper[] getPagedVideos(int sort, boolean desc, int nbItems, int offset);
    abstract public AbstractMediaWrapper[] getVideos(int sort, boolean desc);
    abstract public AbstractMediaWrapper[] getRecentVideos();
    abstract public AbstractMediaWrapper[] getAudio();
    abstract public AbstractMediaWrapper[] getAudio(int sort, boolean desc);
    abstract public AbstractMediaWrapper[] getPagedAudio(int sort, boolean desc, int nbitems, int offset);
    abstract public AbstractMediaWrapper[] getRecentAudio();
    abstract public int getVideoCount();
    abstract public int getAudioCount();
    abstract public AbstractVideoGroup[] getVideoGroups(int sort, boolean desc, int nbItems, int offset);
    abstract public int getVideoGroupsCount();
    abstract public void setVideoGroupsPrefixLength(int lenght);
    abstract public AbstractAlbum[] getAlbums();
    abstract public AbstractAlbum[] getAlbums(int sort, boolean desc);
    abstract public AbstractAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset);
    abstract public int getAlbumsCount();
    abstract public int getAlbumsCount(String query);
    abstract public AbstractAlbum getAlbum(long albumId);
    abstract public AbstractArtist[] getArtists(boolean all);
    abstract public AbstractArtist[] getArtists(boolean all, int sort, boolean desc);
    abstract public AbstractArtist[] getPagedArtists(boolean all, int sort, boolean desc, int nbItems, int offset);
    abstract public int getArtistsCount(boolean all);
    abstract public int getArtistsCount(String query);
    abstract public AbstractArtist getArtist(long artistId);
    abstract public AbstractGenre[] getGenres();
    abstract public AbstractGenre[] getGenres(int sort, boolean desc);
    abstract public AbstractGenre[] getPagedGenres(int sort, boolean desc, int nbItems, int offset);
    abstract public int getGenresCount();
    abstract public int getGenresCount(String query);
    abstract public AbstractGenre getGenre(long genreId);
    abstract public AbstractPlaylist[] getPlaylists(int sort, boolean desc);
    abstract public AbstractPlaylist[] getPlaylists();
    abstract public AbstractPlaylist[] getPagedPlaylists(int sort, boolean desc, int nbItems, int offset);
    abstract public int getPlaylistsCount();
    abstract public int getPlaylistsCount(String query);
    abstract public AbstractPlaylist getPlaylist(long playlistId);
    abstract public AbstractPlaylist createPlaylist(String name);
    abstract public void pauseBackgroundOperations();
    abstract public void resumeBackgroundOperations();
    abstract public void reload();
    abstract public void reload(String entrypoint);
    abstract public void forceParserRetry();
    abstract public void forceRescan();
    abstract public AbstractMediaWrapper[] lastMediaPlayed();
    abstract public AbstractMediaWrapper[] lastStreamsPlayed();
    abstract public boolean clearHistory();
    abstract public void clearDatabase(boolean restorePlaylist);
    abstract public boolean addToHistory(String mrl, String title);
    abstract public AbstractMediaWrapper getMedia(long id);
    abstract public AbstractMediaWrapper getMedia(Uri uri);
    abstract public AbstractMediaWrapper getMedia(String mrl);
    abstract public AbstractMediaWrapper addMedia(String mrl);
    abstract public boolean removeExternalMedia(long id);
    abstract public AbstractMediaWrapper addStream(String mrl, String title);
    abstract public AbstractFolder[] getFolders(int type, int sort, boolean desc, int nbItems, int offset);
    abstract public int getFoldersCount(int type);
    abstract public boolean increasePlayCount(long mediaId);
    abstract public SearchAggregate search(String query);
    abstract public AbstractMediaWrapper[] searchMedia(String query);
    abstract public AbstractMediaWrapper[] searchMedia(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int getMediaCount(String query);
    abstract public AbstractMediaWrapper[] searchAudio(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int getAudioCount(String query);
    abstract public AbstractMediaWrapper[] searchVideo(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public int getVideoCount(String query);
    abstract public AbstractArtist[] searchArtist(String query);
    abstract public AbstractArtist[] searchArtist(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public AbstractAlbum[] searchAlbum(String query);
    abstract public AbstractAlbum[] searchAlbum(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public AbstractGenre[] searchGenre(String query);
    abstract public AbstractGenre[] searchGenre(String query, int sort, boolean desc, int nbItems, int offset);
    abstract public AbstractPlaylist[] searchPlaylist(String query);
    abstract public AbstractPlaylist[] searchPlaylist(String query, int sort, boolean desc, int nbItems, int offset);
}
