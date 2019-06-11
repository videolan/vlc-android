package org.videolan.medialibrary.stubs;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.videolan.medialibrary.interfaces.AMedialibrary;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.medialibrary.interfaces.EntryPointsEventsCb;
import org.videolan.medialibrary.interfaces.media.AAlbum;
import org.videolan.medialibrary.interfaces.media.AArtist;
import org.videolan.medialibrary.interfaces.media.AFolder;
import org.videolan.medialibrary.interfaces.media.AGenre;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;
import org.videolan.medialibrary.interfaces.media.APlaylist;
import org.videolan.medialibrary.media.SearchAggregate;

public class StubMedialibrary extends AMedialibrary {

    public int init(Context context) {
        return ML_INIT_SUCCESS;
    }

    public void start() {

    }

    public void banFolder(@NonNull String path) {

    }

    public void unbanFolder(@NonNull String path) {

    }

    public String[] getDevices() {
        return null;
    }

    public boolean addDevice(@NonNull String uuid, @NonNull String path, boolean removable) {
        return false;
    }

    public void discover(@NonNull String path) {}
    public void removeFolder(@NonNull String mrl) {}

    public String[] getFoldersList() {
        return null;
    }

    public boolean removeDevice(String uuid, String path) {
        return true;
    }

    public AMediaWrapper[] getVideos() {
        return null;
    }

    public AMediaWrapper[] getPagedVideos(int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public AMediaWrapper[] getVideos(int sort, boolean desc) {
        return null;
    }

    public AMediaWrapper[] getRecentVideos() {
        return null;
    }

    public AMediaWrapper[] getAudio() {
        return null;
    }

    public AMediaWrapper[] getAudio(int sort, boolean desc) {
        return null;
    }

    public AMediaWrapper[] getPagedAudio(int sort, boolean desc, int nbitems, int offset) {
        return null;
    }

    public AMediaWrapper[] getRecentAudio() {
        return null;
    }

    public int getVideoCount() {
        return 0;
    }

    public int getAudioCount() {
        return 0;
    }

    public AAlbum[] getAlbums() {
        return null;
    }

    public AAlbum[] getAlbums(int sort, boolean desc) {
        return null;
    }

    public AAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int getAlbumsCount() {
        return 0;
    }

    public int getAlbumsCount(String query) {
        return 0;
    }

    public AAlbum getAlbum(long albumId) {
        return null;
    }

    public AArtist[] getArtists(boolean all) {
        return null;
    }

    public AArtist[] getArtists(boolean all, int sort, boolean desc) {
        return null;
    }

    public AArtist[] getPagedArtists(boolean all, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int getArtistsCount(boolean all) {
        return 0;
    }

    public int getArtistsCount(String query) {
        return 0;
    }

    public AArtist getArtist(long artistId) {
        return null;
    }

    public AGenre[] getGenres() {
        return null;
    }

    public AGenre[] getGenres(int sort, boolean desc) {
        return null;
    }

    public AGenre[] getPagedGenres(int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int getGenresCount() {
        return 0;
    }

    public int getGenresCount(String query) {
        return 0;
    }

    public AGenre getGenre(long genreId) {
        return null;
    }

    public APlaylist[] getPlaylists(int sort, boolean desc) {
        return null;
    }

    public APlaylist[] getPlaylists() {
        return null;
    }

    public APlaylist[] getPagedPlaylists(int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int getPlaylistsCount() {
        return 0;
    }

    public int getPlaylistsCount(String query) {
        return 0;
    }

    public APlaylist getPlaylist(long playlistId) {
        return null;
    }

    public APlaylist createPlaylist(String name) {
        return null;
    }

    public void pauseBackgroundOperations() {}
    public void resumeBackgroundOperations() {}
    public void reload() {}
    public void reload(String entrypoint) {}
    public void forceParserRetry() {}
    public void forceRescan() {}

    public AMediaWrapper[] lastMediaPlayed() {
        return null;
    }

    public AMediaWrapper[] lastStreamsPlayed() {
        return null;
    }

    public boolean clearHistory() {
        return true;
    }

    public boolean addToHistory(String mrl, String title) {
        return true;
    }

    public AMediaWrapper getMedia(long id) {
        return null;
    }

    public AMediaWrapper getMedia(Uri uri) {
        return null;
    }

    public AMediaWrapper getMedia(String mrl) {
        return null;
    }

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
        return true;
    }

    public AMediaWrapper findMedia(AMediaWrapper mw) {
        return null;
    }

    public void onMediaAdded(AMediaWrapper[] mediaList) {}
    public void onMediaUpdated(AMediaWrapper[] mediaList) {}
    public void onMediaDeleted() {}
    public void onArtistsAdded() {}
    public void onArtistsModified() {}
    public void onArtistsDeleted() {}
    public void onAlbumsAdded() {}
    public void onAlbumsModified() {}
    public void onAlbumsDeleted() {}
    public void onGenresAdded() {}
    public void onGenresModified() {}
    public void onGenresDeleted() {}
    public void onPlaylistsAdded() {}
    public void onPlaylistsModified() {}
    public void onPlaylistsDeleted() {}
    public void onDiscoveryStarted(String entryPoint) {}
    public void onDiscoveryProgress(String entryPoint) {}
    public void onDiscoveryCompleted(String entryPoint) {}
    public void onParsingStatsUpdated(int percent) {}
    public void onBackgroundTasksIdleChanged(boolean isIdle) {}
    public void onReloadStarted(String entryPoint) {}
    public void onReloadCompleted(String entryPoint) {}
    public void onEntryPointBanned(String entryPoint, boolean success) {}
    public void onEntryPointUnbanned(String entryPoint, boolean success) {}
    public void onEntryPointRemoved(String entryPoint, boolean success) {}
    public void onMediaThumbnailReady(AMediaWrapper Amedia, boolean success) {}
    public void addMediaCb(MediaCb mediaUpdated) {}
    public void removeMediaCb(MediaCb mediaUpdatedCb) {}
    public void addArtistsCb(ArtistsCb artictAddedCb) {}
    public void removeArtistsCb(ArtistsCb artistCb) {}
    public void addAlbumsCb(AlbumsCb albumsAddedCb) {}
    public void removeAlbumsCb(AlbumsCb albumsAddedCb) {}
    public void addGenreCb(GenresCb genresAddedCb) {}
    public void removeGenreCb(GenresCb genresAddedCb) {}
    public void addPlaylistCb(PlaylistsCb playlistsAddedCb) {}
    public void removePlaylistCb(PlaylistsCb playlistsAddedCb) {}

    public SearchAggregate search(String query) {
        return null;
    }

    public AMediaWrapper[] searchMedia(String query) {
        return null;
    }

    public AMediaWrapper[] searchMedia(String query, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int getMediaCount(String query) {
        return 0;
    }

    public AMediaWrapper[] searchAudio(String query, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int getAudioCount(String query) {
        return 0;
    }

    public AMediaWrapper[] searchVideo(String query, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public int getVideoCount(String query) {
        return 0;
    }

    public AArtist[] searchArtist(String query) {
        return null;
    }

    public AArtist[] searchArtist(String query, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public AAlbum[] searchAlbum(String query) {
        return null;

    }
    public AAlbum[] searchAlbum(String query, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public AGenre[] searchGenre(String query) {
        return null;
    }

    public AGenre[] searchGenre(String query, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public APlaylist[] searchPlaylist(String query) {
        return null;
    }

    public APlaylist[] searchPlaylist(String query, int sort, boolean desc, int nbItems, int offset) {
        return null;
    }

    public void addDeviceDiscoveryCb(DevicesDiscoveryCb cb) {}
    public void removeDeviceDiscoveryCb(DevicesDiscoveryCb cb) {}
    public void addOnMedialibraryReadyListener(OnMedialibraryReadyListener cb) {}
    public void removeOnMedialibraryReadyListener(OnMedialibraryReadyListener cb) {}
    public void addEntryPointsEventsCb(EntryPointsEventsCb cb) {}
    public void removeEntryPointsEventsCb(EntryPointsEventsCb cb) {}
    public void addOnDeviceChangeListener(OnDeviceChangeListener cb) {}
    public void removeOnDeviceChangeListener(OnDeviceChangeListener cb){}
}
