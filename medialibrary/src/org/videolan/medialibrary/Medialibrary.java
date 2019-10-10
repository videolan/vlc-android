/*
 *****************************************************************************
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

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.videolan.libvlc.LibVLC;
import org.videolan.medialibrary.interfaces.AbstractMedialibrary;
import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractFolder;
import org.videolan.medialibrary.interfaces.media.AbstractGenre;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;
import org.videolan.medialibrary.interfaces.media.AbstractPlaylist;
import org.videolan.medialibrary.interfaces.media.AbstractVideoGroup;
import org.videolan.medialibrary.media.SearchAggregate;

import java.io.File;

public class Medialibrary extends AbstractMedialibrary {
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
        } catch (UnsatisfiedLinkError ule)
        {
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

    @WorkerThread
    public AbstractMediaWrapper[] getVideos() {
        return mIsInitiated ? nativeGetVideos() : new AbstractMediaWrapper[0];
    }

    @WorkerThread
    public AbstractMediaWrapper[] getPagedVideos(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetSortedPagedVideos(sort, desc, nbItems, offset) : new AbstractMediaWrapper[0];
    }

    @WorkerThread
    public AbstractMediaWrapper[] getVideos(int sort, boolean desc) {
        return mIsInitiated ? nativeGetSortedVideos(sort, desc) : new AbstractMediaWrapper[0];
    }

    @WorkerThread
    public AbstractMediaWrapper[] getRecentVideos() {
        return mIsInitiated ? nativeGetRecentVideos() : new AbstractMediaWrapper[0];
    }

    @WorkerThread
    public AbstractMediaWrapper[] getAudio() {
        return mIsInitiated ? nativeGetAudio() : new AbstractMediaWrapper[0];
    }

    @WorkerThread
    public AbstractMediaWrapper[] getAudio(int sort, boolean desc) {
        return mIsInitiated ? nativeGetSortedAudio(sort, desc) : new AbstractMediaWrapper[0];
    }

    @WorkerThread
    public AbstractMediaWrapper[] getPagedAudio(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetSortedPagedAudio(sort, desc, nbItems, offset) : new AbstractMediaWrapper[0];
    }

    @WorkerThread
    public AbstractMediaWrapper[] getRecentAudio() {
        return mIsInitiated ? nativeGetRecentAudio() : new AbstractMediaWrapper[0];
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
    public AbstractVideoGroup[] getVideoGroups(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetVideoGroups(sort, desc, nbItems, offset) : new AbstractVideoGroup[0];
    }

    @Override
    @WorkerThread
    public int getVideoGroupsCount() {
        return mIsInitiated ? nativeGetVideoGroupsCount() : 0;
    }

    @Override
    @WorkerThread
    public void setVideoGroupsPrefixLength(int lenght) {
        if (mIsInitiated) nativeSetVideoGroupsPrefixLength(lenght);
    }


    @WorkerThread
    public AbstractAlbum[] getAlbums() {
        return getAlbums(AbstractMedialibrary.SORT_DEFAULT, false);
    }

    @WorkerThread
    public AbstractAlbum[] getAlbums(int sort, boolean desc) {
        return mIsInitiated ? nativeGetAlbums(sort, desc) : new AbstractAlbum[0];
    }

    @NonNull
    @WorkerThread
    public AbstractAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetPagedAlbums(sort, desc, nbItems, offset) : new AbstractAlbum[0];
    }

    public int getAlbumsCount() {
        return mIsInitiated ? nativeGetAlbumsCount() : 0;
    }

    public int getAlbumsCount(String query) {
        return mIsInitiated ? nativeGetAlbumSearchCount(query) : 0;
    }

    @WorkerThread
    public AbstractAlbum getAlbum(long albumId) {
        return mIsInitiated ? nativeGetAlbum(albumId) : null;
    }

    @WorkerThread
    public AbstractArtist[] getArtists(boolean all) {
        return getArtists(all, AbstractMedialibrary.SORT_DEFAULT, false);
    }

    @WorkerThread
    public AbstractArtist[] getArtists(boolean all, int sort, boolean desc) {
        return mIsInitiated ? nativeGetArtists(all, sort, desc) : new AbstractArtist[0];
    }

    @WorkerThread
    public AbstractArtist[] getPagedArtists(boolean all, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetPagedArtists(all, sort, desc, nbItems, offset) : new AbstractArtist[0];
    }

    public int getArtistsCount(boolean all) {
        return mIsInitiated ? nativeGetArtistsCount(all) : 0;
    }

    public int getArtistsCount(String query) {
        return mIsInitiated ? nativeGetArtistsSearchCount(query) : 0;
    }

    public AbstractArtist getArtist(long artistId) {
        return mIsInitiated ? nativeGetArtist(artistId) : null;
    }

    @WorkerThread
    public AbstractGenre[] getGenres() {
        return getGenres(AbstractMedialibrary.SORT_DEFAULT, false);
    }

    @WorkerThread
    public AbstractGenre[] getGenres(int sort, boolean desc) {
        return mIsInitiated ? nativeGetGenres(sort, desc) : new AbstractGenre[0];
    }

    @NonNull
    @WorkerThread
    public AbstractGenre[] getPagedGenres(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetPagedGenres(sort, desc, nbItems, offset) : new AbstractGenre[0];
    }

    public int getGenresCount() {
        return mIsInitiated ? nativeGetGenresCount() : 0;
    }

    public int getGenresCount(String query) {
        return mIsInitiated ? nativeGetGenreSearchCount(query) : 0;
    }

    public AbstractGenre getGenre(long genreId) {
        return mIsInitiated ? nativeGetGenre(genreId) : null;
    }

    @WorkerThread
    public AbstractPlaylist[] getPlaylists() {
        return getPlaylists(AbstractMedialibrary.SORT_DEFAULT, false);
    }

    @WorkerThread
    public AbstractPlaylist[] getPlaylists(int sort, boolean desc) {
        return mIsInitiated ? nativeGetPlaylists(sort, desc) : new AbstractPlaylist[0];
    }

    @WorkerThread
    public AbstractPlaylist[] getPagedPlaylists(int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetPagedPlaylists(sort, desc, nbItems, offset) : new AbstractPlaylist[0];
    }

    public int getPlaylistsCount() {
        return mIsInitiated ? nativeGetPlaylistsCount() : 0;
    }

    public int getPlaylistsCount(String query) {
        return mIsInitiated ? nativeGetPlaylistSearchCount(query) : 0;
    }

    public AbstractPlaylist getPlaylist(long playlistId) {
        return mIsInitiated ? nativeGetPlaylist(playlistId) : null;
    }

    public AbstractPlaylist createPlaylist(String name) {
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
    public AbstractMediaWrapper[] lastMediaPlayed() {
        return mIsInitiated ? nativeLastMediaPlayed() : EMPTY_COLLECTION;
    }

    @WorkerThread
    public AbstractMediaWrapper[] lastStreamsPlayed() {
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
    public AbstractMediaWrapper getMedia(long id) {
        return mIsInitiated ? nativeGetMedia(id) : null;
    }

    @Nullable
    public AbstractMediaWrapper getMedia(Uri uri) {
        if ("content".equals(uri.getScheme())) return null;
        final String vlcMrl = Tools.encodeVLCMrl(uri.toString());
        return mIsInitiated && !TextUtils.isEmpty(vlcMrl) ? nativeGetMediaFromMrl(vlcMrl) : null;
    }

    @Nullable
    public AbstractMediaWrapper getMedia(String mrl) {
        if (mrl != null && mrl.startsWith("content:")) return null;
        final String vlcMrl = Tools.encodeVLCMrl(mrl);
        return mIsInitiated && !TextUtils.isEmpty(vlcMrl) ? nativeGetMediaFromMrl(vlcMrl) : null;
    }

    @Nullable
    public AbstractMediaWrapper addMedia(String mrl) {
        final String vlcMrl = Tools.encodeVLCMrl(mrl);
        return mIsInitiated && !TextUtils.isEmpty(vlcMrl) ? nativeAddMedia(vlcMrl) : null;
    }

    public boolean removeExternalMedia(long id) {
        return mIsInitiated && nativeRemoveExternalMedia(id);
    }

    @Nullable
    public AbstractMediaWrapper addStream(String mrl, String title) {
        final String vlcMrl = Tools.encodeVLCMrl(mrl);
        final String vlcTitle = Tools.encodeVLCMrl(title);
        return mIsInitiated && !TextUtils.isEmpty(vlcMrl) ? nativeAddStream(vlcMrl, vlcTitle) : null;
    }

    @NonNull
    @WorkerThread
    public AbstractFolder[] getFolders(int type, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated ? nativeGetFolders(type, sort, desc, nbItems, offset) : new AbstractFolder[0];
    }

    @WorkerThread
    public int getFoldersCount(int type) {
        return mIsInitiated ? nativeGetFoldersCount(type) : 0;
    }

    public boolean increasePlayCount(long mediaId) {
        return mIsInitiated && mediaId > 0 && nativeIncreasePlayCount(mediaId);
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

    public SearchAggregate search(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearch(query) : null;
    }

    public AbstractMediaWrapper[] searchMedia(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchMedia(query) : null;
    }

    public AbstractMediaWrapper[] searchMedia(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedMedia(query, sort, desc, nbItems, offset) : null;
    }

    public int getMediaCount(String query) {
        return mIsInitiated ? nativeGetSearchMediaCount(query) : 0;
    }

    public AbstractMediaWrapper[] searchAudio(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedAudio(query, sort, desc, nbItems, offset) : null;
    }

    public int getAudioCount(String query) {
        return mIsInitiated ? nativeGetSearchAudioCount(query) : 0;
    }

    public AbstractMediaWrapper[] searchVideo(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedVideo(query, sort, desc, nbItems, offset) : null;
    }

    public int getVideoCount(String query) {
        return mIsInitiated ? nativeGetSearchVideoCount(query) : 0;
    }

    public AbstractArtist[] searchArtist(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchArtist(query) : null;
    }

    public AbstractArtist[] searchArtist(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedArtist(query, sort, desc, nbItems, offset) : new AbstractArtist[0];
    }

    public AbstractAlbum[] searchAlbum(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedAlbum(query, sort, desc, nbItems, offset) : null;
    }

    public AbstractAlbum[] searchAlbum(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchAlbum(query) : null;
    }

    public AbstractGenre[] searchGenre(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchGenre(query) : null;
    }

    public AbstractGenre[] searchGenre(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedGenre(query, sort, desc, nbItems, offset) : null;
    }

    public AbstractPlaylist[] searchPlaylist(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPlaylist(query) : null;
    }

    public AbstractPlaylist[] searchPlaylist(String query, int sort, boolean desc, int nbItems, int offset) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPagedPlaylist(query, sort, desc, nbItems, offset) : null;
    }

    // Native methods
    private native int nativeInit(String dbPath, String thumbsPath);
    private native void nativeStart();
    private native void nativeRelease();

    private native void nativeClearDatabase(boolean keepPlaylist);
    private native void nativeBanFolder(String path);
    private native void nativeUnbanFolder(String path);
    private native boolean nativeAddDevice(String uuid, String path, boolean removable);
    private native String[] nativeDevices();
    private native void nativeDiscover(String path);
    private native void nativeRemoveEntryPoint(String path);
    private native String[] nativeEntryPoints();
    private native boolean nativeRemoveDevice(String uuid, String path);
    private native AbstractMediaWrapper[] nativeLastMediaPlayed();
    private native AbstractMediaWrapper[] nativeLastStreamsPlayed();
    private native  boolean nativeAddToHistory(String mrl, String title);
    private native  boolean nativeClearHistory();
    private native AbstractMediaWrapper nativeGetMedia(long id);
    private native AbstractMediaWrapper nativeGetMediaFromMrl(String mrl);
    private native AbstractMediaWrapper nativeAddMedia(String mrl);
    private native boolean nativeRemoveExternalMedia(long id);
    private native AbstractMediaWrapper nativeAddStream(String mrl, String title);
    private native AbstractMediaWrapper[] nativeGetVideos();
    private native AbstractMediaWrapper[] nativeGetSortedVideos(int sort, boolean desc);
    private native AbstractMediaWrapper[] nativeGetRecentVideos();
    private native AbstractMediaWrapper[] nativeGetAudio();
    private native AbstractMediaWrapper[] nativeGetSortedAudio(int sort, boolean desc);
    private native AbstractMediaWrapper[] nativeGetSortedPagedAudio(int sort, boolean desc, int nbItems, int offset);
    private native AbstractMediaWrapper[] nativeGetSortedPagedVideos(int sort, boolean desc, int nbItems, int offset);
    private native AbstractMediaWrapper[] nativeGetRecentAudio();
    private native int nativeGetVideoCount();
    private native int nativeGetAudioCount();
    private native AbstractVideoGroup[] nativeGetVideoGroups(int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetVideoGroupsCount();
    private native void nativeSetVideoGroupsPrefixLength(int length);
    private native AbstractAlbum[] nativeGetAlbums(int sort, boolean desc);
    private native AbstractAlbum[] nativeGetPagedAlbums(int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetAlbumsCount();
    private native AbstractAlbum nativeGetAlbum(long albumtId);
    private native AbstractArtist[] nativeGetArtists(boolean all, int sort, boolean desc);
    private native AbstractArtist[] nativeGetPagedArtists(boolean all, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetArtistsCount(boolean all);
    private native AbstractArtist nativeGetArtist(long artistId);
    private native AbstractGenre[] nativeGetGenres(int sort, boolean desc);
    private native AbstractGenre[] nativeGetPagedGenres(int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetGenresCount();
    private native AbstractGenre nativeGetGenre(long genreId);
    private native AbstractPlaylist[] nativeGetPlaylists(int sort, boolean desc);
    private native AbstractPlaylist[] nativeGetPagedPlaylists(int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetPlaylistsCount();
    private native AbstractPlaylist nativeGetPlaylist(long playlistId);
    private native AbstractPlaylist nativePlaylistCreate(String name);
    private native AbstractFolder[] nativeGetFolders(int type, int sort, boolean desc, int nbItems, int offset);
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
    private native AbstractMediaWrapper[] nativeSearchMedia(String query);
    private native AbstractMediaWrapper[] nativeSearchPagedMedia(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchMediaCount(String query);
    private native AbstractMediaWrapper[] nativeSearchPagedAudio(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchAudioCount(String query);
    private native AbstractMediaWrapper[] nativeSearchPagedVideo(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetSearchVideoCount(String query);
    private native AbstractArtist[] nativeSearchArtist(String query);
    private native AbstractArtist[] nativeSearchPagedArtist(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetArtistsSearchCount(String query);
    private native AbstractAlbum[] nativeSearchAlbum(String query);
    private native AbstractAlbum[] nativeSearchPagedAlbum(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetAlbumSearchCount(String query);
    private native AbstractGenre[] nativeSearchGenre(String query);
    private native AbstractGenre[] nativeSearchPagedGenre(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetGenreSearchCount(String query);
    private native AbstractPlaylist[] nativeSearchPlaylist(String query);
    private native AbstractPlaylist[] nativeSearchPagedPlaylist(String query, int sort, boolean desc, int nbItems, int offset);
    private native int nativeGetPlaylistSearchCount(String query);
    private native void nativeRequestThumbnail(long mediaId);
}
