/*****************************************************************************
 * Medialibrary.java
 *****************************************************************************
 * Copyright © 2017-2018 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.medialibrary;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import org.videolan.libvlc.LibVLC;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.medialibrary.interfaces.EntryPointsEventsCb;
import org.videolan.medialibrary.media.Album;
import org.videolan.medialibrary.media.Artist;
import org.videolan.medialibrary.media.Folder;
import org.videolan.medialibrary.media.Genre;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Playlist;
import org.videolan.medialibrary.media.SearchAggregate;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

@SuppressWarnings("JniMissingFunction")
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

    private long mInstanceID;
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

    public static final MediaWrapper[] EMPTY_COLLECTION = {};
    public static final String VLC_MEDIA_DB_NAME = "/vlc_media.db";
    public static final String THUMBS_FOLDER_NAME = "/thumbs";

    private volatile boolean mIsInitiated = false;
    private volatile boolean mIsWorking = false;
    private static MutableLiveData<Boolean> sRunning = new MutableLiveData<>();

    private final List<ArtistsCb> mArtistsCbs = new ArrayList<>();
    private final List<AlbumsCb> mAlbumsCbs = new ArrayList<>();
    private final List<MediaCb> mMediaCbs = new ArrayList<>();
    private final List<GenresCb> mGenreCbs = new ArrayList<>();
    private final List<PlaylistsCb> mPlaylistCbs = new ArrayList<>();
    private final List<OnMedialibraryReadyListener> onMedialibraryReadyListeners = new ArrayList<>();
    private final List<OnDeviceChangeListener> onDeviceChangeListeners = new ArrayList<>();
    private volatile boolean isMedialibraryStarted = false;
    private final List<DevicesDiscoveryCb> devicesDiscoveryCbList = new ArrayList<>();
    private final List<EntryPointsEventsCb> entryPointsEventsCbList = new ArrayList<>();
    private static Context sContext;

    private static final Medialibrary instance = new Medialibrary();

    public static Context getContext() {
        return sContext;
    }

    public static LiveData<Boolean> getState() {
        return sRunning;
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
        try {
            System.loadLibrary("c++_shared");
            System.loadLibrary("mla");
        } catch (UnsatisfiedLinkError ule)
        {
            Log.e(TAG, "Can't load mla: " + ule);
            return ML_INIT_FAILED;
        }
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
        nativeSetMediaAddedCbFlag(FLAG_MEDIA_ADDED_AUDIO_EMPTY|FLAG_MEDIA_ADDED_VIDEO_EMPTY);
        nativeSetMediaUpdatedCbFlag(FLAG_MEDIA_UPDATED_AUDIO_EMPTY|FLAG_MEDIA_UPDATED_VIDEO_EMPTY);
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
        if (!mIsInitiated) return false;
        final boolean added = nativeAddDevice(Tools.encodeVLCString(uuid), Tools.encodeVLCMrl(path), removable);
        synchronized (onDeviceChangeListeners) {
            for (OnDeviceChangeListener listener : onDeviceChangeListeners) listener.onDeviceChange();
        }
        return added;
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
        if (!mIsInitiated) return new String[0];
        return nativeEntryPoints();
    }

    public boolean removeDevice(String uuid, String path) {
        if (!mIsInitiated) return false;
        final boolean removed = !TextUtils.isEmpty(uuid) && !TextUtils.isEmpty(path) && nativeRemoveDevice(Tools.encodeVLCString(uuid), Tools.encodeVLCMrl(path));
        synchronized (onDeviceChangeListeners) {
            for (OnDeviceChangeListener listener : onDeviceChangeListeners) listener.onDeviceChange();
        }
        return removed;
    }

    @Override
    protected void finalize() throws Throwable {
        if (mIsInitiated) nativeRelease();
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
    public MediaWrapper[] getPagedAudio(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetSortedPagedAudio(sort, desc, nbItems, offset) : new MediaWrapper[0];
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

    @NonNull
    @WorkerThread
    public Album[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetPagedAlbums(sort, desc, nbItems, offset) : new Album[0];
    }

    public int getAlbumsCount() {
        return mIsInitiated ? nativeGetAlbumsCount() : 0;
    }

    public int getAlbumsCount(String query) {
        return mIsInitiated ? nativeGetAlbumSearchCount(query) : 0;
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

    @WorkerThread
    public Artist[] getPagedArtists(boolean all, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetPagedArtists(all, sort, desc, nbItems, offset) : new Artist[0];
    }

    public int getArtistsCount(boolean all) {
        return mIsInitiated ? nativeGetArtistsCount(all) : 0;
    }

    public int getArtistsCount(String query) {
        return mIsInitiated ? nativeGetArtistsSearchCount(query) : 0;
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

    @NonNull
    @WorkerThread
    public Genre[] getPagedGenres(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetPagedGenres(sort, desc, nbItems, offset) : new Genre[0];
    }

    public int getGenresCount() {
        return mIsInitiated ? nativeGetGenresCount() : 0;
    }

    public int getGenresCount(String query) {
        return mIsInitiated ? nativeGetGenreSearchCount(query) : 0;
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

    @WorkerThread
    public Playlist[] getPagedPlaylists(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetPagedPlaylists(sort, desc, nbItems, offset) : new Playlist[0];
    }

    public int getPlaylistsCount() {
        return mIsInitiated ? nativeGetPlaylistsCount() : 0;
    }

    public int getPlaylistsCount(String query) {
        return mIsInitiated ? nativeGetPlaylistSearchCount(query) : 0;
    }

    public Playlist getPlaylist(long playlistId) {
        return mIsInitiated ? nativeGetPlaylist(playlistId) : null;
    }

    public Playlist createPlaylist(String name) {
        return mIsInitiated && !TextUtils.isEmpty(name) ? nativePlaylistCreate(name) : null;
    }

    public void pauseBackgroundOperations() {
        if (mIsInitiated) nativePauseBackgroundOperations();
    }

    public void resumeBackgroundOperations() {
        if (mIsInitiated) nativeResumeBackgroundOperations();
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
    public MediaWrapper[] lastStreamsPlayed() {
        return mIsInitiated ? nativeLastStreamsPlayed() : EMPTY_COLLECTION;
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
        if ("content".equals(uri.getScheme())) return null;
        final String vlcMrl = Tools.encodeVLCMrl(uri.toString());
        return mIsInitiated && !TextUtils.isEmpty(vlcMrl) ? nativeGetMediaFromMrl(vlcMrl) : null;
    }

    @Nullable
    public MediaWrapper getMedia(String mrl) {
        if (mrl != null && mrl.startsWith("content:")) return null;
        final String vlcMrl = Tools.encodeVLCMrl(mrl);
        return mIsInitiated && !TextUtils.isEmpty(vlcMrl) ? nativeGetMediaFromMrl(vlcMrl) : null;
    }

    @Nullable
    public MediaWrapper addMedia(String mrl) {
        final String vlcMrl = Tools.encodeVLCMrl(mrl);
        return mIsInitiated && !TextUtils.isEmpty(vlcMrl) ? nativeAddMedia(vlcMrl) : null;
    }

    @Nullable
    public MediaWrapper addStream(String mrl, String title) {
        final String vlcMrl = Tools.encodeVLCMrl(mrl);
        final String vlcTitle = Tools.encodeVLCMrl(title);
        return mIsInitiated && !TextUtils.isEmpty(vlcMrl) ? nativeAddStream(vlcMrl, vlcTitle) : null;
    }

    @Nullable
    public Folder[] getFolders(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetFolders(sort, desc, nbItems, offset) : new Folder[0];
    }

    @Nullable
    public int getFoldersCount() {
        return mIsInitiated ? nativeGetFoldersCount() : 0;
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
            final Uri uri = mw.getUri();
            final MediaWrapper libraryMedia = getMedia(uri);
            if (libraryMedia != null) {
                libraryMedia.addFlags(mw.getFlags());
                return libraryMedia;
            }
            if (TextUtils.equals("file", uri.getScheme()) &&
                    uri.getPath() != null && uri.getPath().startsWith("/sdcard")) {
                final MediaWrapper alternateMedia = getMedia(Tools.convertLocalUri(uri));
                if (alternateMedia != null) {
                    alternateMedia.addFlags(mw.getFlags());
                    return alternateMedia;
                }
            }
        }
        return mw;
    }

    @SuppressWarnings("unused")
    public void onMediaAdded(MediaWrapper[] mediaList) {
        synchronized (mMediaCbs) {
            for (MediaCb cb : mMediaCbs) cb.onMediaAdded();
        }
    }

    @SuppressWarnings("unused")
    public void onMediaUpdated(MediaWrapper[] mediaList) {
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

    public static LiveData<MediaWrapper> lastThumb = new SingleEvent<>();
    @SuppressWarnings({"unused", "unchecked"})
    void onMediaThumbnailReady(MediaWrapper media, boolean success) {
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

    public SearchAggregate search(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearch(query) : null;
    }

    public MediaWrapper[] searchMedia(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchMedia(query) : null;
    }

    public MediaWrapper[] searchMedia(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedMedia(query, sort, desc, nbItems, offset) : null;
    }

    public int getMediaCount(String query) {
        return mIsInitiated ? nativeGetSearchMediaCount(query) : 0;
    }

    public MediaWrapper[] searchAudio(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedAudio(query, sort, desc, nbItems, offset) : null;
    }

    public int getAudioCount(String query) {
        return mIsInitiated ? nativeGetSearchAudioCount(query) : 0;
    }

    public MediaWrapper[] searchVideo(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedVideo(query, sort, desc, nbItems, offset) : null;
    }

    public int getVideoCount(String query) {
        return mIsInitiated ? nativeGetSearchVideoCount(query) : 0;
    }

    public Artist[] searchArtist(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchArtist(query) : null;
    }

    public Artist[] searchArtist(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedArtist(query, sort, desc, nbItems, offset) : new Artist[0];
    }

    public Album[] searchAlbum(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedAlbum(query, sort, desc, nbItems, offset) : null;
    }

    public Album[] searchAlbum(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchAlbum(query) : null;
    }

    public Genre[] searchGenre(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchGenre(query) : null;
    }

    public Genre[] searchGenre(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedGenre(query, sort, desc, nbItems, offset) : null;
    }

    public Playlist[] searchPlaylist(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPlaylist(query) : null;
    }

    public Playlist[] searchPlaylist(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedPlaylist(query, sort, desc, nbItems, offset) : null;
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
    private native boolean nativeRemoveDevice(String uuid, String path);
    private native MediaWrapper[] nativeLastMediaPlayed();
    private native MediaWrapper[] nativeLastStreamsPlayed();
    private native  boolean nativeAddToHistory(String mrl, String title);
    private native  boolean nativeClearHistory();
    private native MediaWrapper nativeGetMedia(long id);
    private native MediaWrapper nativeGetMediaFromMrl(String mrl);
    private native MediaWrapper nativeAddMedia(String mrl);
    private native MediaWrapper nativeAddStream(String mrl, String title);
    private native MediaWrapper[] nativeGetVideos();
    private native MediaWrapper[] nativeGetSortedVideos(int sort, boolean desc);
    private native MediaWrapper[] nativeGetRecentVideos();
    private native MediaWrapper[] nativeGetAudio();
    private native MediaWrapper[] nativeGetSortedAudio(int sort, boolean desc);
    private native MediaWrapper[] nativeGetSortedPagedAudio(int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeGetRecentAudio();
    private native int nativeGetVideoCount();
    private native int nativeGetAudioCount();
    private native Album[] nativeGetAlbums(int sort, boolean desc);
    private native Album[] nativeGetPagedAlbums(int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetAlbumsCount();
    private native Album nativeGetAlbum(long albumtId);
    private native Artist[] nativeGetArtists(boolean all, int sort, boolean desc);
    private native Artist[] nativeGetPagedArtists(boolean all, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetArtistsCount(boolean all);
    private native Artist nativeGetArtist(long artistId);
    private native Genre[] nativeGetGenres(int sort, boolean desc);
    private native Genre[] nativeGetPagedGenres(int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetGenresCount();
    private native Genre nativeGetGenre(long genreId);
    private native Playlist[] nativeGetPlaylists(int sort, boolean desc);
    private native Playlist[] nativeGetPagedPlaylists(int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetPlaylistsCount();
    private native Playlist nativeGetPlaylist(long playlistId);
    private native Playlist nativePlaylistCreate(String name);
    private native Folder[] nativeGetFolders(int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetFoldersCount();
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
    private native MediaWrapper[] nativeSearchMedia(String query);
    private native MediaWrapper[] nativeSearchPagedMedia(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchMediaCount(String query);
    private native MediaWrapper[] nativeSearchPagedAudio(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchAudioCount(String query);
    private native MediaWrapper[] nativeSearchPagedVideo(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchVideoCount(String query);
    private native Artist[] nativeSearchArtist(String query);
    private native Artist[] nativeSearchPagedArtist(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetArtistsSearchCount(String query);
    private native Album[] nativeSearchAlbum(String query);
    private native Album[] nativeSearchPagedAlbum(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetAlbumSearchCount(String query);
    private native Genre[] nativeSearchGenre(String query);
    private native Genre[] nativeSearchPagedGenre(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetGenreSearchCount(String query);
    private native Playlist[] nativeSearchPlaylist(String query);
    private native Playlist[] nativeSearchPagedPlaylist(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetPlaylistSearchCount(String query);
    private native void nativeRequestThumbnail(long mediaId);

    private boolean canReadStorage(Context context) {
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
}
