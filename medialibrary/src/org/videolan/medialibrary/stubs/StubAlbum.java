package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.Album;
import org.videolan.medialibrary.interfaces.media.Artist;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

import java.util.ArrayList;

public class StubAlbum extends Album {

    private StubDataSource dt = StubDataSource.getInstance();

    public StubAlbum(long id, String title, int releaseYear, String artworkMrl, String albumArtist, long albumArtistId, int nbTracks, int nbPresentTracks, long duration, boolean isFavorite) {
        super(id, title, releaseYear, artworkMrl, albumArtist, albumArtistId, nbTracks, nbPresentTracks, duration, isFavorite);
    }

    public StubAlbum(Parcel in) {
        super(in);
    }

    public int getRealTracksCount() {
        int count = 0;
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getAlbum().equals(this.getTitle())) count++;
        }
        return count;
    }

    public Artist retrieveAlbumArtist() {
        Medialibrary ml = Medialibrary.getInstance();
        return ml.getArtist(this.albumArtistId);
    }

    private ArrayList<MediaWrapper> getAlbumTracks() {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getAlbum().equals(this.getTitle())
                    && media.getAlbumArtist().equals(this.retrieveAlbumArtist().getTitle())) {
                results.add(media);
            }
        }
        return results;
    }

    public MediaWrapper[] getTracks(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        return dt.sortMedia(getAlbumTracks(), sort, desc);
    }

    public MediaWrapper[] getPagedTracks(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        return dt.sortMedia(dt.secureSublist(getAlbumTracks(), offset, offset + nbItems), sort, desc);
    }

    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getAlbum().equals(this.getTitle()) ||
                    Tools.hasSubString(media.getTitle(), query)) {
                results.add(media);
            }
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int searchTracksCount(String query) {
        int count = 0;
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getAlbum().equals(this.getTitle()) ||
                    Tools.hasSubString(media.getTitle(), query)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean setFavorite(boolean favorite) { return true; }
}
