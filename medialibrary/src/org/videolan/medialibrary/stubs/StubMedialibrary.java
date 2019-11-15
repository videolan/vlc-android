package org.videolan.medialibrary.stubs;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.interfaces.AbstractMedialibrary;
import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractFolder;
import org.videolan.medialibrary.interfaces.media.AbstractGenre;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;
import org.videolan.medialibrary.interfaces.media.AbstractPlaylist;
import org.videolan.medialibrary.interfaces.media.AbstractVideoGroup;
import org.videolan.medialibrary.media.SearchAggregate;

import java.util.ArrayList;
import java.util.Arrays;

public class StubMedialibrary extends AbstractMedialibrary {

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
        for (AbstractFolder folder : dt.mFolders) {
            results.add(folder.getTitle());
        }
        return results.toArray(new String[0]);
    }
    public AbstractMediaWrapper[] getVideos() {
        return getVideos(SORT_DEFAULT, false);
    }

    //TODO sublist of sorted result ...
    public AbstractMediaWrapper[] getPagedVideos(int sort, boolean desc, int nbItems, int offset) {
        return dt.sortMedia(dt.secureSublist(dt.mVideoMediaWrappers, offset, offset + nbItems), sort, desc);
    }

    public AbstractMediaWrapper[] getVideos(int sort, boolean desc) {
        return dt.sortMedia(dt.mVideoMediaWrappers, sort, desc);
    }

    public AbstractMediaWrapper[] getRecentVideos() {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getItemType() == AbstractMediaWrapper.TYPE_VIDEO) results.add(media);
        }
        return results.toArray(new AbstractMediaWrapper[0]);
    }

    public AbstractMediaWrapper[] getAudio() {
        return getAudio(SORT_DEFAULT, false);
    }

    public AbstractMediaWrapper[] getAudio(int sort, boolean desc) {
        return dt.sortMedia(dt.mAudioMediaWrappers, sort, desc);
    }

    public AbstractMediaWrapper[] getPagedAudio(int sort, boolean desc, int nbitems, int offset) {
        return dt.sortMedia(dt.secureSublist(dt.mAudioMediaWrappers, offset, offset + nbitems), sort, desc);
    }

    public AbstractMediaWrapper[] getRecentAudio() {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getItemType() == AbstractMediaWrapper.TYPE_AUDIO) results.add(media);
        }
        return results.toArray(new AbstractMediaWrapper[0]);
    }

    public int getVideoCount() {
        return dt.mVideoMediaWrappers.size();
    }

    public int getAudioCount() {
        return dt.mAudioMediaWrappers.size();
    }

    @Override
    public AbstractVideoGroup[] getVideoGroups(int sort, boolean desc, int nbItems, int offset) {
        return new AbstractVideoGroup[0];
    }

    @Override
    public int getVideoGroupsCount() {
        return 0;
    }

    @Override
    public void setVideoGroupsPrefixLength(int lenght) {}

    public AbstractAlbum[] getAlbums() {
        return getAlbums(SORT_DEFAULT, false);
    }

    public AbstractAlbum[] getAlbums(int sort, boolean desc) {
        return dt.sortAlbum(dt.mAlbums, sort, desc);
    }

    public AbstractAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        return dt.sortAlbum(dt.secureSublist(dt.mAlbums, offset, offset + nbItems), sort, desc);
    }

    public int getAlbumsCount() {
        return dt.mAlbums.size();
    }

    public int getAlbumsCount(String query) {
        int count = 0;
        for (AbstractAlbum album : dt.mAlbums) {
            if (album.getTitle().contains(query)) count++;
        }
        return count;
    }

    public AbstractAlbum getAlbum(long albumId) {
        for (AbstractAlbum album : dt.mAlbums) {
            if (album.getId() == albumId) return album;
        }
        return null;
    }

    public AbstractArtist[] getArtists(boolean all) {
        return getArtists(all, SORT_DEFAULT, false);
    }

    private AbstractArtist[] getAlbumArtists() {
        ArrayList<AbstractArtist> results = new ArrayList<>();
        for (AbstractAlbum album : dt.mAlbums) {
            results.add(album.getAlbumArtist());
        }
        return results.toArray(new AbstractArtist[0]);
    }

    public AbstractArtist[] getArtists(boolean all, int sort, boolean desc) {
        ArrayList<AbstractArtist> results;
        if (all) results = dt.mArtists;
        else results = new ArrayList<>(Arrays.asList(getAlbumArtists()));
        return dt.sortArtist(results, sort, desc);
    }

    public AbstractArtist[] getPagedArtists(boolean all, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractArtist> results;
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
        for (AbstractArtist artist : dt.mArtists) {
            if (artist.getTitle().contains(query)) count++;
        }
        return count;
    }

    public AbstractArtist getArtist(long artistId) {
        for (AbstractArtist artist : dt.mArtists) {
            if (artist.getId() == artistId) return artist;
        }
        return null;
    }

    public AbstractGenre[] getGenres() {
        return dt.mGenres.toArray(new AbstractGenre[0]);
    }

    public AbstractGenre[] getGenres(int sort, boolean desc) {
        return dt.sortGenre(dt.mGenres, sort, desc);
    }

    public AbstractGenre[] getPagedGenres(int sort, boolean desc, int nbItems, int offset) {
        return dt.sortGenre(dt.secureSublist(dt.mGenres, offset, offset + nbItems), sort, desc);
    }

    public int getGenresCount() {
        return dt.mGenres.size();
    }

    public int getGenresCount(String query) {
        int count = 0;
        for (AbstractGenre genre : dt.mGenres) {
            if (genre.getTitle().contains(query)) count++;
        }
        return count;
    }

    public AbstractGenre getGenre(long genreId) {
        for (AbstractGenre genre : dt.mGenres) {
            if (genre.getId() == genreId) return genre;
        }
        return null;
    }

    public AbstractPlaylist[] getPlaylists() {
        return dt.mPlaylists.toArray(new AbstractPlaylist[0]);
    }

    public AbstractPlaylist[] getPlaylists(int sort, boolean desc) {
        return dt.sortPlaylist(dt.mPlaylists, sort, desc);
    }

    public AbstractPlaylist[] getPagedPlaylists(int sort, boolean desc, int nbItems, int offset) {
        return dt.sortPlaylist(dt.secureSublist(dt.mPlaylists, offset, offset + nbItems), sort, desc);
    }

    public int getPlaylistsCount() {
        return dt.mPlaylists.size();
    }

    public int getPlaylistsCount(String query) {
        int count = 0;
        for (AbstractPlaylist playlist : dt.mPlaylists) {
            if (playlist.getTitle().contains(query)) count ++;
        }
        return count;
    }

    public AbstractPlaylist getPlaylist(long playlistId) {
        for (AbstractPlaylist playlist : dt.mPlaylists) {
            if (playlist.getId() == playlistId) return playlist;
        }
        return null;
    }

    public AbstractPlaylist createPlaylist(String name) {
        AbstractPlaylist playlist = MLServiceLocator.getAbstractPlaylist(dt.getUUID(), name, 0);
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

    public AbstractMediaWrapper[] lastMediaPlayed() {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mHistory) {
            if (media.getItemType() == AbstractMediaWrapper.TYPE_VIDEO ||
                media.getItemType() == AbstractMediaWrapper.TYPE_AUDIO) results.add(media);
            // the native method specifies an nbItems of 100, offset 0
            if (results.size() >= 100) break;
        }
        return results.toArray(new AbstractMediaWrapper[0]);
    }

    public AbstractMediaWrapper[] lastStreamsPlayed() {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mHistory) {
            if (media.getItemType() == AbstractMediaWrapper.TYPE_STREAM) results.add(media);
            // the native method specifies an nbItems of 100, offset 0
            if (results.size() >= 100) break;
        }
        return results.toArray(new AbstractMediaWrapper[0]);
    }

    //TODO see when it would return false
    public boolean clearHistory() {
        dt.mHistory.clear();
        return true;
    }

    @Override
    public void clearDatabase(boolean restorePlaylist) {}

    //TODO what if two files have the same name ??
    // TODO what happens in case of false return
    public boolean addToHistory(String mrl, String title) {
        AbstractMediaWrapper media = getMedia(mrl, title);
        if (media == null) {
            media = addStream(mrl, title);
        }
        dt.mHistory.add(media);
        increasePlayCount(media.getId());
        return true;
    }

    // TODO Handle uri to mrl
    private AbstractMediaWrapper getMedia(String mrl, String title) {
        for (AbstractMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getTitle().equals(title)) return media;
        }
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getTitle().equals(title)) return media;
        }
        for (AbstractMediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getTitle().equals(title)) return media;
        }
        return null;
    }

    public AbstractMediaWrapper getMedia(long id) {
        for (AbstractMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getId() == id) return media;
        }
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getId() == id) return media;
        }
        for (AbstractMediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getId() == id) return media;
        }
        return null;
    }

    public AbstractMediaWrapper getMedia(Uri uri) {
        for (AbstractMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getUri().equals(uri)) return media;
        }
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getUri().equals(uri)) return media;
        }
        for (AbstractMediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getUri().equals(uri)) return media;
        }
        return null;
    }

    public AbstractMediaWrapper getMedia(String mrl) {
        return null;
    }

    /* TODO maybe add a list of medias not in the medialibrary which can be retrieved with mrl to
     * simulate adding a media from system */
    public AbstractMediaWrapper addMedia(String mrl) {
        return null;
    }

    public boolean removeExternalMedia(long id) {
        return true;
    }

    public AbstractMediaWrapper addStream(String mrl, String title) {
        return null;
    }

    public AbstractFolder[] getFolders(int type, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int getFoldersCount(int type) {
        return 0;
    }

    public void requestThumbnail(long id) {}

    public boolean increasePlayCount(long mediaId) {
        for (int i = 0 ; i < dt.mVideoMediaWrappers.size() ; i++) {
            AbstractMediaWrapper media = dt.mVideoMediaWrappers.get(i);
            if (media.getId() == mediaId) {
                media.setSeen(media.getSeen() + 1);
                dt.mVideoMediaWrappers.set(i, media);
            }
        }
        return true;
    }

    public SearchAggregate search(String query) {
        AbstractMediaWrapper[] videos = searchVideo(query);
        AbstractMediaWrapper[] tracks = searchAudio(query);
        AbstractAlbum[] albums = searchAlbum(query);
        AbstractArtist[] artists = searchArtist(query);
        AbstractGenre[] genres = searchGenre(query);
        AbstractPlaylist[] playlists = searchPlaylist(query);
        return new SearchAggregate(albums, artists, genres, videos, tracks, playlists);
    }

    public AbstractMediaWrapper[] searchMedia(String query) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        for (AbstractMediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        return results.toArray(new AbstractMediaWrapper[0]);
    }

    public AbstractMediaWrapper[] searchMedia(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>(Arrays.asList(searchMedia(query)));
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getMediaCount(String query) {
        int count = 0;
        for (AbstractMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getTitle().contains(query)) count++;
        }
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getTitle().contains(query)) count++;
        }
        for (AbstractMediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getTitle().contains(query)) count++;
        }
        return count;
    }

    private AbstractMediaWrapper[] searchAudio(String query) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        return dt.sortMedia(results, SORT_DEFAULT, false);
    }

    public AbstractMediaWrapper[] searchAudio(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getAudioCount(String query) {
        int count = 0;
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getTitle().contains(query)) count++;
        }
        return count;
    }

    private AbstractMediaWrapper[] searchVideo(String query) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        return dt.sortMedia(results, SORT_DEFAULT, false);
    }

    public AbstractMediaWrapper[] searchVideo(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getTitle().contains(query)) results.add(media);
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getVideoCount(String query) {
        int count = 0;
        for (AbstractMediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getTitle().contains(query)) count++;
        }
        return count;
    }

    public AbstractArtist[] searchArtist(String query) {
        ArrayList<AbstractArtist> results = new ArrayList<>();
        for (AbstractArtist artist: dt.mArtists) {
            if (artist.getTitle().contains(query)) results.add(artist);
        }
        return results.toArray(new AbstractArtist[0]);
    }

    public AbstractArtist[] searchArtist(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractArtist> results = new ArrayList<>(Arrays.asList(searchArtist(query)));
        return dt.sortArtist(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public AbstractAlbum[] searchAlbum(String query) {
        ArrayList<AbstractAlbum> results = new ArrayList<>();
        for (AbstractAlbum album: dt.mAlbums) {
            if (album.getTitle().contains(query)) results.add(album);
        }
        return results.toArray(new AbstractAlbum[0]);
    }
    public AbstractAlbum[] searchAlbum(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractAlbum> results = new ArrayList<>(Arrays.asList(searchAlbum(query)));
        return dt.sortAlbum(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public AbstractGenre[] searchGenre(String query) {
        ArrayList<AbstractGenre> results = new ArrayList<>();
        for (AbstractGenre genre: dt.mGenres) {
            if (genre.getTitle().contains(query)) results.add(genre);
        }
        return results.toArray(new AbstractGenre[0]);
    }

    public AbstractGenre[] searchGenre(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractGenre> results = new ArrayList<>(Arrays.asList(searchGenre(query)));
        return dt.sortGenre(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public AbstractPlaylist[] searchPlaylist(String query) {
        ArrayList<AbstractPlaylist> results = new ArrayList<>();
        for (AbstractPlaylist playlist: dt.mPlaylists) {
            if (playlist.getTitle().contains(query)) results.add(playlist);
        }
        return results.toArray(new AbstractPlaylist[0]);
    }

    public AbstractPlaylist[] searchPlaylist(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractPlaylist> results = new ArrayList<>(Arrays.asList(searchPlaylist(query)));
        return dt.sortPlaylist(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }
}
