/*****************************************************************************
 * MedialibraryImpl.java
 *****************************************************************************
 * Copyright © 2017-2019 VLC authors and VideoLAN
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

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.Album;
import org.videolan.medialibrary.interfaces.media.Artist;
import org.videolan.medialibrary.interfaces.media.Folder;
import org.videolan.medialibrary.interfaces.media.Genre;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.Playlist;
import org.videolan.medialibrary.interfaces.media.VideoGroup;
import org.videolan.medialibrary.media.SearchAggregate;

import java.io.File;

public class MedialibraryImpl extends Medialibrary {
    private static final String TAG = "VLC/JMedialibrary";

    public int init(Context context) {
        if (context == null) return ML_INIT_FAILED;
        if (mIsInitiated) return ML_INIT_ALREADY_INITIALIZED;
        sContext = context;
        final File extFilesDir = context.getExternalFilesDir(null);
        File dbDirectory = context.getDir("db", Context.MODE_PRIVATE);
        if (extFilesDir == null || !extFilesDir.exists()
                || dbDirectory == null || !dbDirectory.canWrite())
            return ML_INIT_FAILED;
        LibVLC.loadLibraries();
        try {
            System.loadLibrary("c++_shared");
            System.loadLibrary("mla");
        } catch (UnsatisfiedLinkError ule) {
            Log.e(TAG, "Can't load mla: " + ule);
            return ML_INIT_FAILED;
        }
        final File oldDir = new File(extFilesDir + THUMBS_FOLDER_NAME);
        if (oldDir.isDirectory()) {
            //remove old thumbnails directory
            new Thread(new Runnable() {
                @Override
                public void run() {

                    String[] children = oldDir.list();
                    if (children != null) {
                        for (String child : children) {
                            new File(oldDir, child).delete();
                        }
                    }
                    oldDir.delete();
                }
            }).start();
        }

        int initCode = nativeInit(dbDirectory + VLC_MEDIA_DB_NAME, extFilesDir + MEDIALIB_FOLDER_NAME);
        if (initCode == ML_INIT_DB_CORRUPTED) {
            Log.e(TAG, "Medialib database is corrupted. Clearing it and try to restore playlists");
            nativeClearDatabase(true);
        }

        mIsInitiated = initCode != ML_INIT_FAILED;
        return initCode;
    }

    @Override
    public void start() {
        if (isStarted()) return;
        nativeStart();
        isMedialibraryStarted = true;
        synchronized (onMedialibraryReadyListeners) {
            for (OnMedialibraryReadyListener listener : onMedialibraryReadyListeners) listener.onMedialibraryReady();
        }
        nativeSetMediaAddedCbFlag(FLAG_MEDIA_ADDED_AUDIO_EMPTY|FLAG_MEDIA_ADDED_VIDEO_EMPTY);
        nativeSetMediaUpdatedCbFlag(FLAG_MEDIA_UPDATED_AUDIO_EMPTY|FLAG_MEDIA_UPDATED_VIDEO_EMPTY);
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

    public boolean isDeviceKnown(@NonNull String uuid, @NonNull String path, boolean removable) {
        return mIsInitiated && nativeIsDeviceKnown(VLCUtil.encodeVLCString(uuid), Tools.encodeVLCMrl(path), removable);
    }

    public boolean deleteRemovableDevices() {
        return mIsInitiated && nativeDeleteRemovableDevices();
    }

    public void addDevice(@NonNull String uuid, @NonNull String path, boolean removable) {
        if (!mIsInitiated) return;
        nativeAddDevice(VLCUtil.encodeVLCString(uuid), Tools.encodeVLCMrl(path), removable);
        synchronized (onDeviceChangeListeners) {
            for (OnDeviceChangeListener listener : onDeviceChangeListeners) listener.onDeviceChange();
        }
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
        final boolean removed = !TextUtils.isEmpty(uuid) && !TextUtils.isEmpty(path) && nativeRemoveDevice(VLCUtil.encodeVLCString(uuid), Tools.encodeVLCMrl(path));
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

    @WorkerThread
    public MediaWrapper[] getVideos() {
        return mIsInitiated ? nativeGetVideos() : new MediaWrapper[0];
    }

    @WorkerThread
    public MediaWrapper[] getPagedVideos(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetSortedPagedVideos(sort, desc, nbItems, offset) : new MediaWrapper[0];
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

    @WorkerThread
    public int getVideoCount() {
        return mIsInitiated ? nativeGetVideoCount() : 0;
    }

    @WorkerThread
    public int getAudioCount() {
        return mIsInitiated ? nativeGetAudioCount() : 0;
    }

    @Override
    @WorkerThread
    public VideoGroup[] getVideoGroups(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetVideoGroups(sort, desc, nbItems, offset) : new VideoGroup[0];
    }

    @Override
    @WorkerThread
    public int getVideoGroupsCount(@Nullable String query) {
        return mIsInitiated ? nativeGetVideoGroupsCount(query) : 0;
    }

    @Override
    @WorkerThread
    public void setVideoGroupsPrefixLength(int lenght) {
        if (mIsInitiated) nativeSetVideoGroupsPrefixLength(lenght);
    }

    @Override
    @WorkerThread
    public VideoGroup createVideoGroup(String name) {
        return mIsInitiated && !TextUtils.isEmpty(name) ? nativeCreateGroupByName(name) : null;
    }

    @Override
    @WorkerThread
    public VideoGroup createVideoGroup(long[] ids) {
        return mIsInitiated && (ids.length != 0) ? nativeCreateGroup(ids) : null;
    }

    @Override
    public boolean regroupAll() {
        return mIsInitiated && nativeRegroupAll();
    }


    public boolean regroup(long mediaId) {
        return mIsInitiated && mediaId > 0 && nativeRegroup(mediaId);
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
        if (mIsInitiated) nativeReload();
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

    public void clearDatabase(boolean restorePlaylist) {
        if (mIsInitiated) nativeClearDatabase(restorePlaylist);
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

    public boolean removeExternalMedia(long id) {
        return mIsInitiated && nativeRemoveExternalMedia(id);
    }

    @Nullable
    public MediaWrapper addStream(String mrl, String title) {
        final String vlcMrl = Tools.encodeVLCMrl(mrl);
        final String vlcTitle = Tools.encodeVLCMrl(title);
        return mIsInitiated && !TextUtils.isEmpty(vlcMrl) ? nativeAddStream(vlcMrl, vlcTitle) : null;
    }

    @NonNull
    @WorkerThread
    public Folder[] getFolders(int type, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetFolders(type, sort, desc, nbItems, offset) : new Folder[0];
    }

    @WorkerThread
    public int getFoldersCount(int type) {
        return mIsInitiated ? nativeGetFoldersCount(type) : 0;
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

    @Override
    public Folder[] searchFolders(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedFolders(query, sort, desc, nbItems, offset) : new Folder[0];
    }

    @Override
    public int getFoldersCount(String query) {
        return mIsInitiated ? nativeGetSearchFoldersCount(query) : 0;
    }

    @Override
    public VideoGroup[] searchVideoGroups(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedGroups(query, sort, desc, nbItems, offset) : new VideoGroup[0];
    }

    // Native methods
    private native int nativeInit(String dbPath, String thumbsPath);
    private native void nativeStart();
    private native void nativeRelease();

    private native void nativeClearDatabase(boolean keepPlaylist);
    private native void nativeBanFolder(String path);
    private native void nativeUnbanFolder(String path);
    private native void nativeAddDevice(String uuid, String path, boolean removable);
    private native boolean nativeIsDeviceKnown(String uuid, String path, boolean removable);
    private native boolean nativeDeleteRemovableDevices();
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
    private native boolean nativeRemoveExternalMedia(long id);
    private native MediaWrapper nativeAddStream(String mrl, String title);
    private native MediaWrapper[] nativeGetVideos();
    private native MediaWrapper[] nativeGetSortedVideos(int sort, boolean desc);
    private native MediaWrapper[] nativeGetRecentVideos();
    private native MediaWrapper[] nativeGetAudio();
    private native MediaWrapper[] nativeGetSortedAudio(int sort, boolean desc);
    private native MediaWrapper[] nativeGetSortedPagedAudio(int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeGetSortedPagedVideos(int sort, boolean desc, int nbItems, int offset);
    private native MediaWrapper[] nativeGetRecentAudio();
    private native int nativeGetVideoCount();
    private native int nativeGetAudioCount();
    private native VideoGroup[] nativeGetVideoGroups(int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetVideoGroupsCount(String query);
    private native void nativeSetVideoGroupsPrefixLength(int length);

    private native VideoGroup nativeCreateGroupByName(String name);

    private native VideoGroup nativeCreateGroup(long[] ids);

    private native boolean nativeRegroupAll();

    private native boolean nativeRegroup(long mediaId);
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
    private native Folder[] nativeGetFolders(int type, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetFoldersCount(int type);
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
    private native Folder[] nativeSearchPagedFolders(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchFoldersCount(String query);
    private native VideoGroup[] nativeSearchPagedGroups(String query, int sort, boolean desc, int nbItems, int offset);
    private native void nativeRequestThumbnail(long mediaId);
}
