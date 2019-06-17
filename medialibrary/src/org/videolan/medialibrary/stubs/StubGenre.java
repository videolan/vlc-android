package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.AAlbum;
import org.videolan.medialibrary.interfaces.media.AArtist;
import org.videolan.medialibrary.interfaces.media.AGenre;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;

import java.util.ArrayList;
import java.util.Arrays;

public class StubGenre extends AGenre {

    private StubDataSource dt = StubDataSource.getInstance();

    public StubGenre(long id, String title) { super(id, title); }
    public StubGenre(Parcel in) { super(in); }

    public AAlbum[] getAlbums(int sort, boolean desc) {
        ArrayList<AAlbum> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) {
                for (AAlbum album : dt.mAlbums) {
                    if (album.getTitle().equals(media.getAlbum()) &&
                            !results.contains(album)) {
                        results.add(album);
                    }
                }
            }
        }
        return dt.sortAlbum(results, sort, desc);
    }

    public AAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AAlbum> results = new ArrayList<>(Arrays.asList(getAlbums(sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new AAlbum[0]);
    }

    @Override
    public int getAlbumsCount() {
        return getAlbums().length;
    }

    public AAlbum[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AAlbum> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle()) &&
                    media.getTitle().contains(query)) {
                for (AAlbum album : dt.mAlbums) {
                    if (album.getTitle().equals(media.getAlbum()) &&
                            !results.contains(album)) {
                        results.add(album);
                    }
                }
            }
        }
        results = new ArrayList<>(Arrays.asList(dt.sortAlbum(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new AAlbum[0]);
    }

    public int searchAlbumsCount(String query) {
        ArrayList<AAlbum> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle()) &&
                    media.getTitle().contains(query)) {
                for (AAlbum album : dt.mAlbums) {
                    if (album.getTitle().equals(media.getAlbum()) &&
                            !results.contains(album)) {
                        results.add(album);
                    }
                }
            }
        }
        return results.size();
    }

    public AArtist[] getArtists(int sort, boolean desc) {
        ArrayList<AArtist> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) {
                for (AArtist artist : dt.mArtists) {
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

    public AMediaWrapper[] getTracks(int sort, boolean desc) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) results.add(media);
        }
        return dt.sortMedia(results, sort, desc);
    }

    public AMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) results.add(media);
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getTracksCount() {
        int count = 0;
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) count++;
        }
        return count;
    }


    public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle()) &&
                    media.getTitle().contains(query)) {
                results.add(media);
            }
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int searchTracksCount(String query) {
        int count = 0;
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle()) &&
                    media.getTitle().contains(query)) {
                count++;
            }
        }
        return count;
    }
}
