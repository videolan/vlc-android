package org.videolan.medialibrary.stubs;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.interfaces.AMedialibrary;
import org.videolan.medialibrary.interfaces.media.AAlbum;
import org.videolan.medialibrary.interfaces.media.AArtist;
import org.videolan.medialibrary.interfaces.media.AFolder;
import org.videolan.medialibrary.interfaces.media.AGenre;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;
import org.videolan.medialibrary.interfaces.media.APlaylist;
import org.videolan.medialibrary.media.SearchAggregate;

import java.util.ArrayList;
import java.util.Arrays;

public class StubMedialibrary extends AMedialibrary {

    private StubDataSource dt = StubDataSource.getInstance();
    private String TAG = this.getClass().getName();

    public int init(Context context) {
        if (context == null) return ML_INIT_FAILED;
        sContext = context;
        dt.init();
        return ML_INIT_SUCCESS;
    }

    public void start() {
        isMedialibraryStarted = true;
        synchronized (onMedialibraryReadyListeners) {
            for (OnMedialibraryReadyListener listener : onMedialibraryReadyListeners) listener.onMedialibraryReady();
        }
    }

    public void banFolder(@NonNull String path) {
        if (!dt.mBannedFolders.contains(path))
            dt.mBannedFolders.add(path);
    }


    // TODO checkout error handling for ban / unban folder
    // TODO checkout probably useless as there is no folder parsing
    // TODO also unban folder might trigger an action
    public void unbanFolder(@NonNull String path) {
        dt.mBannedFolders.remove(path);
    }

    public String[] getDevices() {
        return new String[0];
    }

    public boolean addDevice(@NonNull String uuid, @NonNull String path, boolean removable) {
        return false;
    }

    public void discover(@NonNull String path) {
        onDiscoveryStarted(path);
        onDiscoveryCompleted(path);
    }
    public void removeFolder(@NonNull String mrl) {}

    public boolean removeDevice(String uuid, String path) {
        return true;
    }


    public String[] getFoldersList() {
        ArrayList<String> results = new ArrayList<>();
        for (AFolder folder : dt.mFolders) {
            results.add(folder.getTitle());
        }
        return results.toArray(new String[0]);
    }
    public AMediaWrapper[] getVideos() {
        return getVideos(SORT_DEFAULT, false);
    }

    //TODO sublist of sorted result ...
    public AMediaWrapper[] getPagedVideos(int sort, boolean desc, int nbItems, int offset) {
        return dt.sortMedia(dt.secureSublist(dt.mVideoMediaWrappers, offset, offset + nbItems), sort, desc);
    }

    public AMediaWrapper[] getVideos(int sort, boolean desc) {
        return dt.sortMedia(dt.mVideoMediaWrappers, sort, desc);
    }

    public AMediaWrapper[] getRecentVideos() {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getItemType() == AMediaWrapper.TYPE_VIDEO) results.add(media);
        }
        return results.toArray(new AMediaWrapper[0]);
    }

    public AMediaWrapper[] getAudio() {
        return getAudio(SORT_DEFAULT, false);
    }

    public AMediaWrapper[] getAudio(int sort, boolean desc) {
        return dt.sortMedia(dt.mAudioMediaWrappers, sort, desc);
    }

    public AMediaWrapper[] getPagedAudio(int sort, boolean desc, int nbitems, int offset) {
        return dt.sortMedia(dt.secureSublist(dt.mAudioMediaWrappers, offset, offset + nbitems), sort, desc);
    }

    public AMediaWrapper[] getRecentAudio() {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getItemType() == AMediaWrapper.TYPE_AUDIO) results.add(media);
        }
        return results.toArray(new AMediaWrapper[0]);
    }

    public int getVideoCount() {
        return dt.mVideoMediaWrappers.size();
    }

    public int getAudioCount() {
        return dt.mAudioMediaWrappers.size();
    }

    public AAlbum[] getAlbums() {
        return getAlbums(SORT_DEFAULT, false);
    }

    public AAlbum[] getAlbums(int sort, boolean desc) {
        return dt.sortAlbum(dt.mAlbums, sort, desc);
    }

    public AAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        return dt.sortAlbum(dt.secureSublist(dt.mAlbums, offset, offset + nbItems), sort, desc);
    }

    public int getAlbumsCount() {
        return dt.mAlbums.size();
    }

    public int getAlbumsCount(String query) {
        int count = 0;
        for (AAlbum album : dt.mAlbums) {
            if (album.getTitle().contains(query)) count++;
        }
        return count;
    }

    public AAlbum getAlbum(long albumId) {
        for (AAlbum album : dt.mAlbums) {
            if (album.getId() == albumId) return album;
        }
        return null;
    }

    public AArtist[] getArtists(boolean all) {
        return getArtists(all, SORT_DEFAULT, false);
    }

    private AArtist[] getAlbumArtists() {
        ArrayList<AArtist> results = new ArrayList<>();
        for (AAlbum album : dt.mAlbums) {
            results.add(album.getAlbumArtist());
        }
        return results.toArray(new AArtist[0]);
    }

    public AArtist[] getArtists(boolean all, int sort, boolean desc) {
        ArrayList<AArtist> results;
        if (all) results = dt.mArtists;
        else results = new ArrayList<>(Arrays.asList(getAlbumArtists()));
        return dt.sortArtist(results, sort, desc);
    }

    public AArtist[] getPagedArtists(boolean all, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AArtist> results;
        if (all) results = dt.mArtists;
        else results = new ArrayList<>(Arrays.asList(getAlbumArtists()));
        return dt.sortArtist(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getArtistsCount(boolean all) {
        int count;
        if (all) count = dt.mArtists.size();
        else count = getAlbumArtists().length;
        return count;
    }

    public int getArtistsCount(String query) {
        int count = 0;
        for (AArtist artist : dt.mArtists) {
            if (artist.getTitle().contains(query)) count++;
        }
        return count;
    }

    public AArtist getArtist(long artistId) {
        for (AArtist artist : dt.mArtists) {
            if (artist.getId() == artistId) return artist;
        }
        return null;
    }

    public AGenre[] getGenres() {
        return dt.mGenres.toArray(new AGenre[0]);
    }

    public AGenre[] getGenres(int sort, boolean desc) {
        return dt.sortGenre(dt.mGenres, sort, desc);
    }

    public AGenre[] getPagedGenres(int sort, boolean desc, int nbItems, int offset) {
        return dt.sortGenre(dt.secureSublist(dt.mGenres, offset, offset + nbItems), sort, desc);
    }

    public int getGenresCount() {
        return dt.mGenres.size();
    }

    public int getGenresCount(String query) {
        int count = 0;
        for (AGenre genre : dt.mGenres) {
            if (genre.getTitle().contains(query)) count++;
        }
        return count;
    }

    public AGenre getGenre(long genreId) {
        for (AGenre genre : dt.mGenres) {
            if (genre.getId() == genreId) return genre;
        }
        return null;
    }

    public APlaylist[] getPlaylists() {
        return dt.mPlaylists.toArray(new APlaylist[0]);
    }

    public APlaylist[] getPlaylists(int sort, boolean desc) {
        return dt.sortPlaylist(dt.mPlaylists, sort, desc);
    }

    public APlaylist[] getPagedPlaylists(int sort, boolean desc, int nbItems, int offset) {
        return dt.sortPlaylist(dt.secureSublist(dt.mPlaylists, offset, offset + nbItems), sort, desc);
    }

    public int getPlaylistsCount() {
        return dt.mPlaylists.size();
    }

    public int getPlaylistsCount(String query) {
        int count = 0;
        for (APlaylist playlist : dt.mPlaylists) {
            if (playlist.getTitle().contains(query)) count ++;
        }
        return count;
    }

    public APlaylist getPlaylist(long playlistId) {
        for (APlaylist playlist : dt.mPlaylists) {
            if (playlist.getId() == playlistId) return playlist;
        }
        return null;
    }

    public APlaylist createPlaylist(String name) {
        APlaylist playlist = MLServiceLocator.getAPlaylist(dt.getUUID(), name, 0);
        dt.mPlaylists.add(playlist);
        onPlaylistsAdded();
        return playlist;
    }

    public void pauseBackgroundOperations() {}
    public void resumeBackgroundOperations() {}
    public void reload() {
        Log.e(TAG, "reload: no entrypoint");
        reload("");
    }
    public void reload(String entrypoint) {
        Log.e(TAG, "reload(string entrypoint): ");
        onReloadStarted(entrypoint);
        onReloadCompleted(entrypoint);
        onBackgroundTasksIdleChanged(true);
    }
    public void forceParserRetry() {}
    public void forceRescan() {}

    public AMediaWrapper[] lastMediaPlayed() {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mHistory) {
            if (media.getItemType() == AMediaWrapper.TYPE_VIDEO ||
                media.getItemType() == AMediaWrapper.TYPE_AUDIO) results.add(media);
            // the native method specifies an nbItems of 100, offset 0
            if (results.size() >= 100) break;
        }
        return results.toArray(new AMediaWrapper[0]);
    }

    public AMediaWrapper[] lastStreamsPlayed() {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mHistory) {
            if (media.getItemType() == AMediaWrapper.TYPE_STREAM) results.add(media);
            // the native method specifies an nbItems of 100, offset 0
            if (results.size() >= 100) break;
        }
        return results.toArray(new AMediaWrapper[0]);
    }

    //TODO see when it would return false
    public boolean clearHistory() {
        dt.mHistory.clear();
        return true;
    }

    //TODO what if two files have the same name ??
    // TODO what happens in case of false return
    public boolean addToHistory(String mrl, String title) {
        AMediaWrapper media = getMedia(mrl, title);
        if (media == null) {
            media = addStream(mrl, title);
        }
        dt.mHistory.add(media);
        increasePlayCount(media.getId());
        return true;
    }

    // TODO Handle uri to mrl
    private AMediaWrapper getMedia(String mrl, String title) {
        for (AMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getTitle().equals(title)) return media;
        }
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getTitle().equals(title)) return media;
        }
        for (AMediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getTitle().equals(title)) return media;
        }
        return null;
    }

    public AMediaWrapper getMedia(long id) {
        for (AMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getId() == id) return media;
        }
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getId() == id) return media;
        }
        for (AMediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getId() == id) return media;
        }
        return null;
    }

    public AMediaWrapper getMedia(Uri uri) {
        for (AMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getUri().equals(uri)) return media;
        }
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getUri().equals(uri)) return media;
        }
        for (AMediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getUri().equals(uri)) return media;
        }
        return null;
    }

    public AMediaWrapper getMedia(String mrl) {
        return null;
    }

    /* TODO maybe add a list of medias not in the medialibrary which can be retrieved with mrl to
     * simulate adding a media from system */
    public AMediaWrapper addMedia(String mrl) {
        return null;
    }

    public boolean removeExternalMedia(long id) {
        return true;
    }

    public AMediaWrapper addStream(String mrl, String title) {
        return null;
    }

    public AFolder[] getFolders(int type, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int getFoldersCount(int type) {
        return 0;
    }

    public void requestThumbnail(long id) {}

    public boolean increasePlayCount(long mediaId) {
        for (int i = 0 ; i < dt.mVideoMediaWrappers.size() ; i++) {
            AMediaWrapper media = dt.mVideoMediaWrappers.get(i);
            if (media.getId() == mediaId) {
                media.setSeen(media.getSeen() + 1);
                dt.mVideoMediaWrappers.set(i, media);
            }
        }
        return true;
    }

    public SearchAggregate search(String query) {
        AMediaWrapper[] videos = searchVideo(query);
        AMediaWrapper[] tracks = searchAudio(query);
        AAlbum[] albums = searchAlbum(query);
        AArtist[] artists = searchArtist(query);
        AGenre[] genres = searchGenre(query);
        APlaylist[] playlists = searchPlaylist(query);
        return new SearchAggregate(albums, artists, genres, videos, tracks, playlists);
    }

    public AMediaWrapper[] searchMedia(String query) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        for (AMediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        return results.toArray(new AMediaWrapper[0]);
    }

    public AMediaWrapper[] searchMedia(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AMediaWrapper> results = new ArrayList<>(Arrays.asList(searchMedia(query)));
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getMediaCount(String query) {
        int count = 0;
        for (AMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getTitle().contains(query)) count++;
        }
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getTitle().contains(query)) count++;
        }
        for (AMediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getTitle().contains(query)) count++;
        }
        return count;
    }

    private AMediaWrapper[] searchAudio(String query) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        return dt.sortMedia(results, SORT_DEFAULT, false);
    }

    public AMediaWrapper[] searchAudio(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getAudioCount(String query) {
        int count = 0;
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getTitle().contains(query)) count++;
        }
        return count;
    }

    private AMediaWrapper[] searchVideo(String query) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        return dt.sortMedia(results, SORT_DEFAULT, false);
    }

    public AMediaWrapper[] searchVideo(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getVideoCount(String query) {
        int count = 0;
        for (AMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getTitle().contains(query)) count++;
        }
        return count;
    }

    public AArtist[] searchArtist(String query) {
        ArrayList<AArtist> results = new ArrayList<>();
        for (AArtist artist: dt.mArtists) {
            if (artist.getTitle().contains(query)) results.add(artist);
        }
        return results.toArray(new AArtist[0]);
    }

    public AArtist[] searchArtist(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AArtist> results = new ArrayList<>(Arrays.asList(searchArtist(query)));
        return dt.sortArtist(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public AAlbum[] searchAlbum(String query) {
        ArrayList<AAlbum> results = new ArrayList<>();
        for (AAlbum album: dt.mAlbums) {
            if (album.getTitle().contains(query)) results.add(album);
        }
        return results.toArray(new AAlbum[0]);
    }
    public AAlbum[] searchAlbum(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AAlbum> results = new ArrayList<>(Arrays.asList(searchAlbum(query)));
        return dt.sortAlbum(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public AGenre[] searchGenre(String query) {
        ArrayList<AGenre> results = new ArrayList<>();
        for (AGenre genre: dt.mGenres) {
            if (genre.getTitle().contains(query)) results.add(genre);
        }
        return results.toArray(new AGenre[0]);
    }

    public AGenre[] searchGenre(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AGenre> results = new ArrayList<>(Arrays.asList(searchGenre(query)));
        return dt.sortGenre(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public APlaylist[] searchPlaylist(String query) {
        ArrayList<APlaylist> results = new ArrayList<>();
        for (APlaylist playlist: dt.mPlaylists) {
            if (playlist.getTitle().contains(query)) results.add(playlist);
        }
        return results.toArray(new APlaylist[0]);
    }

    public APlaylist[] searchPlaylist(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<APlaylist> results = new ArrayList<>(Arrays.asList(searchPlaylist(query)));
        return dt.sortPlaylist(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }
}
