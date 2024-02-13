package org.videolan.medialibrary.stubs;

import android.content.Context;
import android.net.Uri;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.videolan.medialibrary.MLContextTools;
import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.Medialibrary;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StubMedialibrary extends Medialibrary {

    private StubDataSource dt = StubDataSource.getInstance();

    public boolean construct(Context context) {
        return true;
    }

    public int init(Context context) {
        if (context == null) return ML_INIT_FAILED;
        MLContextTools.getInstance().setContext(context);
        return ML_INIT_SUCCESS;
    }

    @Override
    public boolean isStarted() {
        return true;
    }

    public void start() {
        isMedialibraryStarted = true;
        synchronized (onMedialibraryReadyListeners) {
            for (OnMedialibraryReadyListener listener : onMedialibraryReadyListeners)
                listener.onMedialibraryReady();
        }
    }

    public void banFolder(@NonNull String path) {
        if (!dt.mBannedFolders.contains(path))
            dt.mBannedFolders.add(path);
    }

    public void unbanFolder(@NonNull String path) {
        dt.mBannedFolders.remove(path);
    }

    public String[] bannedFolders() {
        return new String[0];
    }

    public String[] getDevices() {
        return new String[0];
    }

    public void addDevice(@NonNull String uuid, @NonNull String path, boolean removable) { }

    @Override
    public boolean isDeviceKnown(@NonNull String uuid, @NonNull String path, boolean removable) {
        return false;
    }

    @Override
    public boolean deleteRemovableDevices() {
        return false;
    }

    public void loadJsonData(String jsonContent) {
        dt.loadJsonData(jsonContent);
        reload();
    }

    public void discover(@NonNull String path) {
        onDiscoveryStarted();
        onDiscoveryCompleted();
        onBackgroundTasksIdleChanged(true);
    }

    @Override
    public void setLibVLCInstance(long libVLC) { }

    @Override
    public boolean setDiscoverNetworkEnabled(boolean enabled) {
        return false;
    }

    public void removeFolder(@NonNull String mrl) {}

    public boolean removeDevice(String uuid, String path) {
        return true;
    }

    public String[] getFoldersList() {
        ArrayList<String> results = new ArrayList<>();
        for (Folder folder : dt.mFolders) {
            results.add(folder.getTitle());
        }
        return results.toArray(new String[0]);
    }

    public MediaWrapper[] getVideos() {
        return getVideos(SORT_DEFAULT, false, true, false);
    }

    public MediaWrapper[] getPagedVideos(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        return dt.sortMedia(dt.secureSublist(dt.mVideoMediaWrappers, offset, offset + nbItems), sort, desc);
    }

    public MediaWrapper[] getVideos(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        return dt.sortMedia(dt.mVideoMediaWrappers, sort, desc);
    }

    public MediaWrapper[] getRecentVideos() {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getItemType() == MediaWrapper.TYPE_VIDEO) results.add(media);
        }
        return results.toArray(new MediaWrapper[0]);
    }

    public MediaWrapper[] getAudio() {
        return getAudio(SORT_DEFAULT, false, true, false);
    }

    public MediaWrapper[] getAudio(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        return dt.sortMedia(dt.mAudioMediaWrappers, sort, desc);
    }

    public MediaWrapper[] getPagedAudio(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbitems, int offset) {
        return dt.sortMedia(dt.secureSublist(dt.mAudioMediaWrappers, offset, offset + nbitems), sort, desc);
    }

    public MediaWrapper[] getRecentAudio() {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getItemType() == MediaWrapper.TYPE_AUDIO) results.add(media);
        }
        return results.toArray(new MediaWrapper[0]);
    }

    public int getVideoCount() {
        return dt.mVideoMediaWrappers.size();
    }

    public int getAudioCount() {
        return dt.mAudioMediaWrappers.size();
    }

    @Override
    public VideoGroup[] getVideoGroups(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        return new VideoGroup[0];
    }

    @Override
    public int getVideoGroupsCount(@Nullable String query) {
        return 0;
    }

    @Override
    public void setVideoGroupsPrefixLength(int lenght) {}

    @Override
    public VideoGroup createVideoGroup(String name) {
        return null;
    }

    @Override
    public VideoGroup createVideoGroup(long[] ids) {
        return null;
    }

    @Override
    public VideoGroup getVideoGroup(long id) {
        return null;
    }

    @Override
    public boolean regroupAll() {
        return false;
    }

    @Override
    public boolean regroup(long mediaId) {
        return false;
    }

    public Album[] getAlbums(boolean includeMissing, boolean onlyFavorites) {
        return getAlbums(SORT_DEFAULT, false, includeMissing, onlyFavorites);
    }

    public Album[] getAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        return dt.sortAlbum(dt.mAlbums, sort, desc);
    }

    public Album[] getPagedAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        return dt.sortAlbum(dt.secureSublist(dt.mAlbums, offset, offset + nbItems), sort, desc);
    }

    public int getAlbumsCount() {
        return dt.mAlbums.size();
    }

    public int getAlbumsCount(String query) {
        int count = 0;
        for (Album album : dt.mAlbums) {
            if (Tools.hasSubString(album.getTitle(), query)) count++;
        }
        return count;
    }

    public Album getAlbum(long albumId) {
        for (Album album : dt.mAlbums) {
            if (album.getId() == albumId) return album;
        }
        return null;
    }

    @Override
    public Artist[] getArtists(boolean all, boolean includeMissing, boolean onlyFavorites) {
        return getArtists(all, SORT_DEFAULT, false, true, false);
    }

    private boolean checkForArtist(ArrayList<Artist> list, Artist newArtist) {
        for (Artist artist : list ) {
            if (artist.getTitle().equals(newArtist.getTitle())) {
                return true;
            }
        }
        return false;
    }

    private Artist[] getAlbumArtists() {
        ArrayList<Artist> results = new ArrayList<>();
        for (Album album : dt.mAlbums) {
            Artist artist = album.retrieveAlbumArtist();
            if (!checkForArtist(results, artist)) {
                results.add(artist);
            }
        }
        return results.toArray(new Artist[0]);
    }

    public Artist[] getArtists(boolean all, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        ArrayList<Artist> results;
        if (all) results = dt.mArtists;
        else results = new ArrayList<>(Arrays.asList(getAlbumArtists()));
        return dt.sortArtist(results, sort, desc);
    }

    public Artist[] getPagedArtists(boolean all, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<Artist> results;
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
        for (Artist artist : dt.mArtists) {
            if (Tools.hasSubString(artist.getTitle(), query)) count++;
        }
        return count;
    }

    public Artist getArtist(long artistId) {
        for (Artist artist : dt.mArtists) {
            if (artist.getId() == artistId) return artist;
        }
        return null;
    }

    public Genre[] getGenres(boolean includeMissing, boolean onlyFavorites) {
        return dt.mGenres.toArray(new Genre[0]);
    }

    public Genre[] getGenres(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        return dt.sortGenre(dt.mGenres, sort, desc);
    }

    public Genre[] getPagedGenres(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        return dt.sortGenre(dt.secureSublist(dt.mGenres, offset, offset + nbItems), sort, desc);
    }

    public int getGenresCount() {
        return dt.mGenres.size();
    }

    public int getGenresCount(String query) {
        int count = 0;
        for (Genre genre : dt.mGenres) {
            if (Tools.hasSubString(genre.getTitle(), query)) count++;
        }
        return count;
    }

    public Genre getGenre(long genreId) {
        for (Genre genre : dt.mGenres) {
            if (genre.getId() == genreId) return genre;
        }
        return null;
    }

    public Playlist[] getPlaylists(Playlist.Type type, boolean onlyFavorites) {
        return dt.mPlaylists.toArray(new Playlist[0]);
    }

    public Playlist[] getPlaylists(Playlist.Type type, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        return dt.sortPlaylist(dt.mPlaylists, sort, desc);
    }

    public Playlist[] getPagedPlaylists(Playlist.Type type, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        return dt.sortPlaylist(dt.secureSublist(dt.mPlaylists, offset, offset + nbItems), sort, desc);
    }

    public int getPlaylistsCount() {
        return dt.mPlaylists.size();
    }

    public int getPlaylistsCount(String query) {
        int count = 0;
        for (Playlist playlist : dt.mPlaylists) {
            if (Tools.hasSubString(playlist.getTitle(), query)) count++;
        }
        return count;
    }

    public Playlist getPlaylist(long playlistId, boolean includeMissing, boolean onlyFavorites) {
        for (Playlist playlist : dt.mPlaylists) {
            if (playlist.getId() == playlistId) return playlist;
        }
        return null;
    }

    public Playlist createPlaylist(String name, boolean includeMissing, boolean onlyFavorites) {
        Playlist playlist = MLServiceLocator.getAbstractPlaylist(dt.getUUID(), name, 0, 0L, 0, 0, 0, 0, false);
        dt.mPlaylists.add(playlist);
        onPlaylistsAdded();
        return playlist;
    }

    public void pauseBackgroundOperations() {}
    public void resumeBackgroundOperations() {}

    public void reload() {
        reload("");
    }

    public void reload(String entrypoint) {
        onReloadStarted(entrypoint);
        onReloadCompleted(entrypoint);
        onBackgroundTasksIdleChanged(true);
    }
    public void forceParserRetry() {}
    public void forceRescan() {}

    public MediaWrapper[] history(int type) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mHistory) {
            if (media.getType() == MediaWrapper.TYPE_VIDEO ||
                    media.getType() == MediaWrapper.TYPE_AUDIO) results.add(media);
            // the native method specifies an nbItems of 100, offset 0
            if (results.size() >= 100) break;
        }
        return results.toArray(new MediaWrapper[0]);
    }

    public boolean clearHistory(int type) {
        dt.mHistory.clear();
        return true;
    }

    @Override
    public void clearDatabase(boolean restorePlaylist) {}

    //TODO what if two files have the same name ??
    // TODO what happens in case of false return
    public boolean addToHistory(String mrl, String title) {
        MediaWrapper media = getMedia(mrl, title);
        if (media == null) {
            media = addStream(mrl, title);
        }
        dt.mHistory.add(media);
        setLastTime(media.getId(), media.getTime());
        return true;
    }

    // TODO Handle uri to mrl
    private MediaWrapper getMedia(String mrl, String title) {
        mrl = Tools.encodeVLCMrl(mrl);
        for (MediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getLocation().equals(mrl)) return media;
        }
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getLocation().equals(mrl)) return media;
        }
        for (MediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getLocation().equals(mrl)) return media;
        }

        if (!URLUtil.isNetworkUrl(mrl))
            return dt.addMediaWrapper(mrl, title, MediaWrapper.TYPE_ALL);
        return null;
    }

    public MediaWrapper getMedia(long id) {
        for (MediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getId() == id) return media;
        }
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getId() == id) return media;
        }
        for (MediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getId() == id) return media;
        }
        return null;
    }

    public MediaWrapper getMedia(Uri uri) {
        for (MediaWrapper media : dt.mVideoMediaWrappers) {
            if (media.getUri().equals(uri)) return media;
        }
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getUri().equals(uri)) return media;
        }
        for (MediaWrapper media : dt.mStreamMediaWrappers) {
            if (media.getUri().equals(uri)) return media;
        }
        return null;
    }

    public MediaWrapper getMedia(String mrl) {
        return null;
    }

    /* TODO maybe add a list of medias not in the medialibrary which can be retrieved with mrl to
     * simulate adding a media from system */
    public MediaWrapper addMedia(String mrl, long duration) {
        return null;
    }

    public boolean removeExternalMedia(long id) {
        return true;
    }

    public boolean flushUserProvidedThumbnails() {
        return true;
    }

    public MediaWrapper addStream(String mrl, String title) {
        return dt.addMediaWrapper(mrl, title, MediaWrapper.TYPE_STREAM);
    }

    // TODO: Fix sorting, offset etc
    public Folder[] getFolders(int type, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        List<Folder> folders = new ArrayList<>();
        if (type == Folder.TYPE_FOLDER_VIDEO) {
            for (Folder folder : dt.mFolders) {
                if (folders.contains(folder)) continue;
                String path = folder.mMrl;
                if (path.isEmpty()) continue;
                for (MediaWrapper mediaWrapper : dt.mVideoMediaWrappers) {
                    String childPath = mediaWrapper.getUri().getPath();
                    if (childPath == null) continue;
                    if (isParentFolder(path, childPath)) {
                        folders.add(folder);
                        break;
                    }
                }
            }
        }
        return folders.toArray(new Folder[0]);
    }

    @Override
    public Folder getFolder(int type, long id) {
        return null;
    }

    public int getFoldersCount(int type) {
        return getFolders(type, 0, false, true, false, 0, 0).length;
    }

    public void requestThumbnail(long id) {}

    public int setLastTime(long mediaId, long time) {
        for (int i = 0; i < dt.mVideoMediaWrappers.size(); i++) {
            MediaWrapper media = dt.mVideoMediaWrappers.get(i);
            if (media.getId() == mediaId) {
                media.setSeen(media.getSeen() + 1);
                dt.mVideoMediaWrappers.set(i, media);
            }
        }
        return ML_SET_TIME_BEGIN;
    }

    public boolean setLastPosition(long mediaId, float poistion) {
        return true;
    }

        public SearchAggregate search(String query, boolean includeMissing, boolean onlyFavorites) {
        MediaWrapper[] videos = searchVideo(query);
        MediaWrapper[] tracks = searchAudio(query);
        Album[] albums = searchAlbum(query);
        Artist[] artists = searchArtist(query);
        Genre[] genres = searchGenre(query);
        Playlist[] playlists = searchPlaylist(query, Playlist.Type.All, true, false);
        return new SearchAggregate(albums, artists, genres, videos, tracks, playlists);
    }

    public MediaWrapper[] searchMedia(String query) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mVideoMediaWrappers) {
            if (Tools.hasSubString(media.getTitle(), query)) results.add(media);
        }
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (Tools.hasSubString(media.getTitle(), query)) results.add(media);
        }
        for (MediaWrapper media : dt.mStreamMediaWrappers) {
            if (Tools.hasSubString(media.getTitle(), query)) results.add(media);
        }
        return results.toArray(new MediaWrapper[0]);
    }

    public MediaWrapper[] searchMedia(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<MediaWrapper> results = new ArrayList<>(Arrays.asList(searchMedia(query)));
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getMediaCount(String query) {
        int count = 0;
        for (MediaWrapper media : dt.mVideoMediaWrappers) {
            if (Tools.hasSubString(media.getTitle(), query)) count++;
        }
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (Tools.hasSubString(media.getTitle(), query)) count++;
        }
        for (MediaWrapper media : dt.mStreamMediaWrappers) {
            if (Tools.hasSubString(media.getTitle(), query)) count++;
        }
        return count;
    }

    private MediaWrapper[] searchAudio(String query) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (Tools.hasSubString(media.getTitle(), query)) results.add(media);
        }
        return dt.sortMedia(results, SORT_DEFAULT, false);
    }

    public MediaWrapper[] searchAudio(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (Tools.hasSubString(media.getTitle(), query)) results.add(media);
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getAudioCount(String query) {
        int count = 0;
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (Tools.hasSubString(media.getTitle(), query)) count++;
        }
        return count;
    }

    private MediaWrapper[] searchVideo(String query) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mVideoMediaWrappers) {
            if (Tools.hasSubString(media.getTitle(), query)) results.add(media);
        }
        return dt.sortMedia(results, SORT_DEFAULT, false);
    }

    public MediaWrapper[] searchVideo(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mVideoMediaWrappers) {
            if (Tools.hasSubString(media.getTitle(), query)) results.add(media);
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getVideoCount(String query) {
        int count = 0;
        for (MediaWrapper media : dt.mVideoMediaWrappers) {
            if (Tools.hasSubString(media.getTitle(), query)) count++;
        }
        return count;
    }

    public Artist[] searchArtist(String query) {
        ArrayList<Artist> results = new ArrayList<>();
        for (Artist artist : dt.mArtists) {
            if (Tools.hasSubString(artist.getTitle(), query)) results.add(artist);
        }
        return results.toArray(new Artist[0]);
    }

    public Artist[] searchArtist(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<Artist> results = new ArrayList<>(Arrays.asList(searchArtist(query)));
        return dt.sortArtist(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public Album[] searchAlbum(String query) {
        ArrayList<Album> results = new ArrayList<>();
        for (Album album : dt.mAlbums) {
            if (Tools.hasSubString(album.getTitle(), query)) results.add(album);
        }
        return results.toArray(new Album[0]);
    }

    public Album[] searchAlbum(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<Album> results = new ArrayList<>(Arrays.asList(searchAlbum(query)));
        return dt.sortAlbum(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public Genre[] searchGenre(String query) {
        ArrayList<Genre> results = new ArrayList<>();
        for (Genre genre : dt.mGenres) {
            if (Tools.hasSubString(genre.getTitle(), query)) results.add(genre);
        }
        return results.toArray(new Genre[0]);
    }

    public Genre[] searchGenre(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<Genre> results = new ArrayList<>(Arrays.asList(searchGenre(query)));
        return dt.sortGenre(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public Playlist[] searchPlaylist(String query, Playlist.Type type, boolean includeMissing, boolean onlyFavorites) {
        ArrayList<Playlist> results = new ArrayList<>();
        for (Playlist playlist : dt.mPlaylists) {
            if (Tools.hasSubString(playlist.getTitle(), query)) results.add(playlist);
        }
        return results.toArray(new Playlist[0]);
    }

    public Playlist[] searchPlaylist(String query, Playlist.Type type, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<Playlist> results = new ArrayList<>(Arrays.asList(searchPlaylist(query, type, includeMissing, onlyFavorites)));
        return dt.sortPlaylist(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    @Override
    public Folder[] searchFolders(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        return new Folder[0];
    }

    @Override
    public int getFoldersCount(String query) {
        return 0;
    }

    @Override
    public VideoGroup[] searchVideoGroups(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        return new VideoGroup[0];
    }

    private boolean isParentFolder(String parentMrl, String childMrl) {
        if (!childMrl.contains(parentMrl)) return false;
        File mediaFile = new File(childMrl);
        String parentPath = mediaFile.getParent();
        return parentPath.equals(parentMrl);
    }

    @Override
    public MlService getService(MlService.Type type) {
        return null;
    }

    @Override
    public boolean fitsInSubscriptionCache(MediaWrapper media) {
        return false;
    }

    @Override
    public void cacheNewSubscriptionMedia() {

    }

    @Override
    public boolean setSubscriptionMaxCachedMedia(int nbMedia) {
        return false;
    }

    @Override
    public boolean setSubscriptionMaxCacheSize(long size) {
        return false;
    }

    @Override
    public boolean setMaxCacheSize(long size) {
        return false;
    }

    @Override
    public int getSubscriptionMaxCachedMedia() {
        return -1;
    }

    @Override
    public long getSubscriptionMaxCacheSize() {
        return -1L;
    }

    @Override
    public long getMaxCacheSize() {
        return -1L;
    }

    @Override
    public boolean refreshAllSubscriptions() {
        return false;
    }
}
