/*****************************************************************************
 * Medialibrary.java
 *****************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.medialibrary.interfaces;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.videolan.medialibrary.EventTools;
import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.media.Album;
import org.videolan.medialibrary.interfaces.media.Artist;
import org.videolan.medialibrary.interfaces.media.Folder;
import org.videolan.medialibrary.interfaces.media.Genre;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.MlService;
import org.videolan.medialibrary.interfaces.media.Playlist;
import org.videolan.medialibrary.interfaces.media.VideoGroup;
import org.videolan.medialibrary.media.SearchAggregate;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

abstract public class Medialibrary {

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
    public final static int TrackNumber = 11;
    public final static int TrackId = 12;
    public final static int NbVideo = 13;
    public final static int NbAudio = 14;
    public final static int NbMedia = 15;

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
    public static final int ML_INIT_DB_UNRECOVERABLE = 5;

    public static final int ML_SET_TIME_ERROR = 0;
    public static final int ML_SET_TIME_BEGIN = 1;
    public static final int ML_SET_TIME_AS_IS = 2;
    public static final int ML_SET_TIME_END = 3;

    public static final int HISTORY_TYPE_GLOBAL = 0;
    public static final int HISTORY_TYPE_LOCAL = 1;
    public static final int HISTORY_TYPE_NETWORK = 2;

    public static final MediaWrapper[] EMPTY_COLLECTION = {};
    public static final String VLC_MEDIA_DB_NAME = "/vlc_media.db";
    public static final String THUMBS_FOLDER_NAME = "/thumbs";
    public static final String MEDIALIB_FOLDER_NAME = "/medialib";

    protected volatile boolean mIsInitiated = false;
    protected volatile boolean mIsWorking = false;
    protected static final MutableLiveData<Boolean> sRunning = new MutableLiveData<>();

    protected final List<ArtistsCb> mArtistsCbs = new ArrayList<>();
    protected final List<AlbumsCb> mAlbumsCbs = new ArrayList<>();
    protected final List<MediaCb> mMediaCbs = new ArrayList<>();
    protected final List<GenresCb> mGenreCbs = new ArrayList<>();
    protected final List<PlaylistsCb> mPlaylistCbs = new ArrayList<>();
    protected final List<HistoryCb> mHistoryCbs = new ArrayList<>();
    protected final List<MediaGroupCb> mMediaGroupCbs = new ArrayList<>();
    protected final List<FoldersCb> mFoldersCbs = new ArrayList<>();
    protected final List<OnMedialibraryReadyListener> onMedialibraryReadyListeners = new ArrayList<>();
    protected final List<OnDeviceChangeListener> onDeviceChangeListeners = new ArrayList<>();
    protected volatile boolean isMedialibraryStarted = false;
    protected final List<DevicesDiscoveryCb> devicesDiscoveryCbList = new ArrayList<>();
    protected final List<RootsEventsCb> rootsEventsCbList = new ArrayList<>();
    private MedialibraryExceptionHandler mExceptionHandler;

    protected static final Medialibrary instance = MLServiceLocator.getAbstractMedialibrary();

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
    public static Medialibrary getInstance() {
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

    public static String[] getBanList() {
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
                "/WhatsApp/Media/WhatsApp%20Animated%20Gifs/",
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
        void onMediaDeleted(long[] id);
        void onMediaConvertedToExternal(long[] id);
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

    public interface HistoryCb {
        void onHistoryModified();
    }

    public interface MediaGroupCb {
        void onMediaGroupsAdded();
        void onMediaGroupsModified();
        void onMediaGroupsDeleted();
    }

    public interface FoldersCb {
        void onFoldersAdded();
        void onFoldersModified();
        void onFoldersDeleted();
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
    public void onMediaUpdated() {
        synchronized (mMediaCbs) {
            for (MediaCb cb : mMediaCbs) cb.onMediaModified();
        }
    }

    @SuppressWarnings("unused")
    public void onMediaDeleted(long[] ids) {
        synchronized (mMediaCbs) {
            for (MediaCb cb : mMediaCbs) cb.onMediaDeleted(ids);
        }
    }

    @SuppressWarnings("unused")
    public void onMediaConvertedToExternal(long[] ids) {
        synchronized (mMediaCbs) {
            for (MediaCb cb : mMediaCbs) cb.onMediaConvertedToExternal(ids);
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

    @SuppressWarnings("unused")
    public void onHistoryChanged(int type) {
        synchronized (mHistoryCbs) {
            for (HistoryCb cb : mHistoryCbs) cb.onHistoryModified();
        }
    }

    @SuppressWarnings("unused")
    public void onMediaGroupAdded() {
        synchronized (mMediaGroupCbs) {
            for (MediaGroupCb cb : mMediaGroupCbs) cb.onMediaGroupsAdded();
        }
    }

    @SuppressWarnings("unused")
    public void onMediaGroupModified() {
        synchronized (mMediaGroupCbs) {
            for (MediaGroupCb cb : mMediaGroupCbs) cb.onMediaGroupsModified();
        }
    }

    @SuppressWarnings("unused")
    public void onMediaGroupDeleted() {
        synchronized (mMediaGroupCbs) {
            for (MediaGroupCb cb : mMediaGroupCbs) cb.onMediaGroupsDeleted();
        }
    }

    @SuppressWarnings("unused")
    public void onFoldersAdded() {
        synchronized (mFoldersCbs) {
            for (FoldersCb cb : mFoldersCbs) cb.onFoldersAdded();
        }
    }

    @SuppressWarnings("unused")
    public void onFoldersModified() {
        synchronized (mFoldersCbs) {
            for (FoldersCb cb : mFoldersCbs) cb.onFoldersModified();
        }
    }

    @SuppressWarnings("unused")
    public void onFoldersDeleted() {
        synchronized (mFoldersCbs) {
            for (FoldersCb cb : mFoldersCbs) cb.onFoldersDeleted();
        }
    }

    public void onDiscoveryStarted() {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryStarted();
        }
        synchronized (rootsEventsCbList) {
            if (!rootsEventsCbList.isEmpty())
                for (RootsEventsCb cb : rootsEventsCbList)
                    cb.onDiscoveryStarted();
        }
    }

    public void onDiscoveryProgress(String root) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryProgress(root);
        }
        synchronized (rootsEventsCbList) {
            if (!rootsEventsCbList.isEmpty())
                for (RootsEventsCb cb : rootsEventsCbList)
                    cb.onDiscoveryProgress(root);
        }
    }

    public void onDiscoveryCompleted() {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryCompleted();
        }
        synchronized (rootsEventsCbList) {
            if (!rootsEventsCbList.isEmpty())
                for (RootsEventsCb cb : rootsEventsCbList)
                    cb.onDiscoveryCompleted();
        }
    }

    public void onDiscoveryFailed(String root) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryFailed(root);
        }
        synchronized (rootsEventsCbList) {
            if (!rootsEventsCbList.isEmpty())
                for (RootsEventsCb cb : rootsEventsCbList)
                    cb.onDiscoveryFailed(root);
        }
    }

    public void onParsingStatsUpdated(int done, int scheduled) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onParsingStatsUpdated(done, scheduled);
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
    public void onReloadStarted(String root) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onReloadStarted(root);
        }
    }

    @SuppressWarnings("unused")
    public void onReloadCompleted(String root) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onReloadCompleted(root);
        }
    }

    @SuppressWarnings("unused")
    public void onRootBanned(String root, boolean success) {
        synchronized (rootsEventsCbList) {
            if (!rootsEventsCbList.isEmpty())
                for (RootsEventsCb cb : rootsEventsCbList)
                    cb.onRootBanned(root, success);
        }
    }

    @SuppressWarnings("unused")
    public void onRootUnbanned(String root, boolean success) {
        synchronized (rootsEventsCbList) {
            if (!rootsEventsCbList.isEmpty())
                for (RootsEventsCb cb : rootsEventsCbList)
                    cb.onRootUnbanned(root, success);
        }
    }


    @SuppressWarnings("unused")
    void onRootAdded(String root, boolean success) {
        synchronized (rootsEventsCbList) {
            if (!rootsEventsCbList.isEmpty())
                for (RootsEventsCb cb : rootsEventsCbList)
                    cb.onRootAdded(root, success);
        }
    }

    @SuppressWarnings("unused")
    public void onRootRemoved(String root, boolean success) {
        synchronized (rootsEventsCbList) {
            if (!rootsEventsCbList.isEmpty())
                for (RootsEventsCb cb : rootsEventsCbList)
                    cb.onRootRemoved(root, success);
        }
    }

    //    public static LiveData<MediaWrapper> lastThumb = new SingleEvent<>();
    @SuppressWarnings({"unused", "unchecked"})
    public void onMediaThumbnailReady(MediaWrapper media, boolean success) {
        if (success) ((MutableLiveData<MediaWrapper>) EventTools.getInstance().lastThumb).postValue(media);
    }

    public void addMediaCb(MediaCb mediaUpdatedCb) {
        synchronized (mMediaCbs) {
            mMediaCbs.add(mediaUpdatedCb);
        }
    }

    public void removeMediaCb(MediaCb mediaUpdatedCb) {
        synchronized (mMediaCbs) {
            mMediaCbs.remove(mediaUpdatedCb);
        }
    }

    public void addArtistsCb(ArtistsCb artistsAddedCb) {
        synchronized (mArtistsCbs) {
            mArtistsCbs.add(artistsAddedCb);
        }
    }

    public void removeArtistsCb(ArtistsCb artistsAddedCb) {
        synchronized (mArtistsCbs) {
            mArtistsCbs.remove(artistsAddedCb);
        }
    }

    public void addAlbumsCb(AlbumsCb AlbumsAddedCb) {
        synchronized (mAlbumsCbs) {
            mAlbumsCbs.add(AlbumsAddedCb);
        }
    }

    public void removeAlbumsCb(AlbumsCb AlbumsAddedCb) {
        synchronized (mAlbumsCbs) {
            mAlbumsCbs.remove(AlbumsAddedCb);
        }
    }

    public void addGenreCb(GenresCb GenreCb) {
        synchronized (mGenreCbs) {
            this.mGenreCbs.add(GenreCb);
        }
    }

    public void removeGenreCb(GenresCb GenreCb) {
        synchronized (mGenreCbs) {
            this.mGenreCbs.remove(GenreCb);
        }
    }

    public void addPlaylistCb(PlaylistsCb playlistCb) {
        synchronized (mPlaylistCbs) {
            this.mPlaylistCbs.add(playlistCb);
        }
    }

    public void removePlaylistCb(PlaylistsCb playlistCb) {
        synchronized (mPlaylistCbs) {
            this.mPlaylistCbs.remove(playlistCb);
        }
    }

    public void addHistoryCb(HistoryCb historyCb) {
        synchronized (mHistoryCbs) {
            this.mHistoryCbs.add(historyCb);
        }
    }

    public void removeHistoryCb(HistoryCb historyCb) {
        synchronized (mHistoryCbs) {
            this.mHistoryCbs.remove(historyCb);
        }
    }

    public void addMediaGroupCb(MediaGroupCb mediaGroupCb) {
        synchronized (mMediaGroupCbs) {
            this.mMediaGroupCbs.add(mediaGroupCb);
        }
    }

    public void removeMediaGroupCb(MediaGroupCb mediaGroupCb) {
        synchronized (mMediaGroupCbs) {
            this.mMediaGroupCbs.remove(mediaGroupCb);
        }
    }

    public void addFoldersCb(FoldersCb foldersCb) {
        synchronized (mFoldersCbs) {
            this.mFoldersCbs.add(foldersCb);
        }
    }

    public void removeFoldersCb(FoldersCb foldersCb) {
        synchronized (mFoldersCbs) {
            this.mFoldersCbs.remove(foldersCb);
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

    public void addRootsEventsCb(RootsEventsCb cb) {
        synchronized (rootsEventsCbList) {
            if (!rootsEventsCbList.contains(cb))
                rootsEventsCbList.add(cb);
        }
    }

    public void removeRootsEventsCb(RootsEventsCb cb) {
        synchronized (rootsEventsCbList) {
            rootsEventsCbList.remove(cb);
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

    abstract public boolean construct(Context context);
    abstract public int init(Context context);
    abstract public void start();
    abstract public void banFolder(@NonNull String path);
    abstract public void unbanFolder(@NonNull String path);
    abstract public String[] bannedFolders();
    abstract public String[] getDevices();
    abstract public void addDevice(@NonNull String uuid, @NonNull String path, boolean removable);
    abstract public boolean isDeviceKnown(@NonNull String uuid, @NonNull String path, boolean removable);
    abstract public boolean deleteRemovableDevices();
    abstract public void discover(@NonNull String path);
    abstract public void setLibVLCInstance(long libVLC);
    abstract public boolean setDiscoverNetworkEnabled(boolean enabled);
    abstract public void removeFolder(@NonNull String mrl);
    abstract public String[] getFoldersList();
    abstract public boolean removeDevice(String uuid, String path);
    abstract public MediaWrapper[] getVideos();
    abstract public MediaWrapper[] getPagedVideos(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public MediaWrapper[] getVideos(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    abstract public MediaWrapper[] getRecentVideos();
    abstract public MediaWrapper[] getAudio();
    abstract public MediaWrapper[] getAudio(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    abstract public MediaWrapper[] getPagedAudio(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbitems, int offset);
    abstract public MediaWrapper[] getRecentAudio();
    abstract public int getVideoCount();
    abstract public int getAudioCount();
    abstract public VideoGroup[] getVideoGroups(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int getVideoGroupsCount(@Nullable String query);
    abstract public void setVideoGroupsPrefixLength(int lenght);

    abstract public VideoGroup createVideoGroup(String name);

    abstract public VideoGroup createVideoGroup(long[] ids);

    abstract public VideoGroup getVideoGroup(long id);

    abstract public boolean regroupAll();

    abstract public boolean regroup(long mediaId);
    abstract public Album[] getAlbums(boolean includeMissing, boolean onlyFavorites);
    abstract public Album[] getAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    abstract public Album[] getPagedAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int getAlbumsCount();
    abstract public int getAlbumsCount(String query);
    abstract public Album getAlbum(long albumId);
    abstract public Artist[] getArtists(boolean all, boolean includeMissing, boolean onlyFavorites);
    abstract public Artist[] getArtists(boolean all, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    abstract public Artist[] getPagedArtists(boolean all, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int getArtistsCount(boolean all);
    abstract public int getArtistsCount(String query);
    abstract public Artist getArtist(long artistId);
    abstract public Genre[] getGenres(boolean includeMissing, boolean onlyFavorites);
    abstract public Genre[] getGenres(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    abstract public Genre[] getPagedGenres(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int getGenresCount();
    abstract public int getGenresCount(String query);
    abstract public Genre getGenre(long genreId);
    abstract public Playlist[] getPlaylists(Playlist.Type type, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    abstract public Playlist[] getPlaylists(Playlist.Type type, boolean onlyFavorites);
    abstract public Playlist[] getPagedPlaylists(Playlist.Type type, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int getPlaylistsCount();
    abstract public int getPlaylistsCount(String query);
    abstract public Playlist getPlaylist(long playlistId, boolean includeMissing, boolean onlyFavorites);
    abstract public Playlist createPlaylist(String name, boolean includeMissing, boolean onlyFavorites);
    abstract public void pauseBackgroundOperations();
    abstract public void resumeBackgroundOperations();
    abstract public void reload();
    abstract public void reload(String entrypoint);
    abstract public void forceParserRetry();
    abstract public void forceRescan();
    abstract public MediaWrapper[] history(int type);
    abstract public boolean clearHistory(int type);
    abstract public void clearDatabase(boolean restorePlaylist);
    abstract public boolean addToHistory(String mrl, String title);
    abstract public MediaWrapper getMedia(long id);
    abstract public MediaWrapper getMedia(Uri uri);
    abstract public MediaWrapper getMedia(String mrl);
    abstract public MediaWrapper addMedia(String mrl, long duration);
    abstract public boolean removeExternalMedia(long id);
    abstract public boolean flushUserProvidedThumbnails();
    abstract public MediaWrapper addStream(String mrl, String title);
    abstract public Folder[] getFolders(int type, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public Folder getFolder(int type, long id);
    abstract public int getFoldersCount(int type);
    abstract public int setLastTime(long mediaId, long time);
    abstract public boolean setLastPosition(long mediaId, float position);
    abstract public SearchAggregate search(String query, boolean includeMissing, boolean onlyFavorites);
    abstract public MediaWrapper[] searchMedia(String query);
    abstract public MediaWrapper[] searchMedia(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int getMediaCount(String query);
    abstract public MediaWrapper[] searchAudio(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int getAudioCount(String query);
    abstract public MediaWrapper[] searchVideo(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int getVideoCount(String query);
    abstract public Artist[] searchArtist(String query);
    abstract public Artist[] searchArtist(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public Album[] searchAlbum(String query);
    abstract public Album[] searchAlbum(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public Genre[] searchGenre(String query);
    abstract public Genre[] searchGenre(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public Playlist[] searchPlaylist(String query, Playlist.Type type, boolean includeMissing, boolean onlyFavorites);
    abstract public Playlist[] searchPlaylist(String query, Playlist.Type type, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public Folder[] searchFolders(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);
    abstract public int getFoldersCount(String query);
    abstract public VideoGroup[] searchVideoGroups(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset);

    abstract public MlService getService(MlService.Type type);
    abstract public boolean fitsInSubscriptionCache(MediaWrapper media);
    abstract public void cacheNewSubscriptionMedia();
    abstract public boolean setSubscriptionMaxCachedMedia(int nbMedia);
    abstract public boolean setSubscriptionMaxCacheSize(long size);
    abstract public boolean setMaxCacheSize(long size);
    abstract public int getSubscriptionMaxCachedMedia();
    abstract public long getSubscriptionMaxCacheSize();
    abstract public long getMaxCacheSize();
    abstract public boolean refreshAllSubscriptions();
}
