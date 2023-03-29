package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.media.Album;
import org.videolan.medialibrary.interfaces.media.Artist;
import org.videolan.medialibrary.interfaces.media.Genre;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

import java.util.ArrayList;
import java.util.Arrays;

public class StubGenre extends Genre {

    private StubDataSource dt = StubDataSource.getInstance();

    public StubGenre(long id, String title, int nbTracks, int nbPresentTracks, boolean isFavorite) { super(id, title, nbTracks, nbPresentTracks, isFavorite); }
    public StubGenre(Parcel in) { super(in); }

    public Album[] getAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        ArrayList<Album> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) {
                for (Album album : dt.mAlbums) {
                    if (album.getTitle().equals(media.getAlbum()) &&
                            !results.contains(album)) {
                        results.add(album);
                    }
                }
            }
        }
        return dt.sortAlbum(results, sort, desc);
    }

    public Album[] getPagedAlbums(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<Album> results = new ArrayList<>(Arrays.asList(getAlbums(sort, desc, includeMissing, onlyFavorites)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new Album[0]);
    }

    @Override
    public int getAlbumsCount() {
        return getAlbums().length;
    }

    public Album[] searchAlbums(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<Album> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle()) &&
                    Tools.hasSubString(media.getTitle(), query)) {
                for (Album album : dt.mAlbums) {
                    if (album.getTitle().equals(media.getAlbum()) &&
                            !results.contains(album)) {
                        results.add(album);
                    }
                }
            }
        }
        results = new ArrayList<>(Arrays.asList(dt.sortAlbum(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new Album[0]);
    }

    public int searchAlbumsCount(String query) {
        ArrayList<Album> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle()) &&
                    Tools.hasSubString(media.getTitle(), query)) {
                for (Album album : dt.mAlbums) {
                    if (album.getTitle().equals(media.getAlbum()) &&
                            !results.contains(album)) {
                        results.add(album);
                    }
                }
            }
        }
        return results.size();
    }

    public Artist[] getArtists(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        ArrayList<Artist> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) {
                for (Artist artist : dt.mArtists) {
                    if ((artist.getTitle().equals(media.getArtist()) ||
                            artist.getTitle().equals(media.getAlbumArtist())) &&
                            !results.contains(artist)) {
                        results.add(artist);
                        break;
                    }
                }
            }
        }
        return dt.sortArtist(results, sort, desc);
    }

    public MediaWrapper[] getTracks(boolean withThumbnail, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) results.add(media);
        }
        return dt.sortMedia(results, sort, desc);
    }

    public MediaWrapper[] getPagedTracks(boolean withThumbnail, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) results.add(media);
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getTracksCount() {
        int count = 0;
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) count++;
        }
        return count;
    }


    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle()) &&
                    Tools.hasSubString(media.getTitle(), query)) {
                results.add(media);
            }
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int searchTracksCount(String query) {
        int count = 0;
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle()) &&
                    Tools.hasSubString(media.getTitle(), query)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean setFavorite(boolean favorite) { return true; }
}
