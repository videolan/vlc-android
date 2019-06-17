package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.AAlbum;
import org.videolan.medialibrary.interfaces.media.AArtist;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;

import java.util.ArrayList;
import java.util.Arrays;

public class StubArtist extends AArtist {

    private StubDataSource dt = StubDataSource.getInstance();

    public StubArtist(long id, String name, String shortBio, String artworkMrl, String musicBrainzId) {
        super(id, name, shortBio, artworkMrl, musicBrainzId);
    }

    public StubArtist(Parcel in) {
        super(in);
    }

    public AAlbum[] getAlbums(int sort, boolean desc) {
        ArrayList<AAlbum> results = new ArrayList<>();
        for (AAlbum album : dt.mAlbums) {
            if (album.getDescription().equals(this.getTitle())) results.add(album);
        }
        return dt.sortAlbum(results, sort, desc);
    }

    public int getAlbumsCount() {
        int count = 0;
        for (AAlbum album : dt.mAlbums) {
            if (album.getDescription().equals(this.getTitle())) count++;
        }
        return count;
    }

    public AAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AAlbum> results = new ArrayList<>(Arrays.asList(getAlbums(sort, desc)));
        return results.toArray(new AAlbum[0]);
    }

    public AAlbum[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AAlbum> results = new ArrayList<>();
        for (AAlbum album : dt.mAlbums) {
            if (album.getDescription().equals(this.getTitle()) ||
                    album.getTitle().equals(query)) {
                results.add(album);
            }
        }
        return dt.sortAlbum(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int searchAlbumsCount(String query) {
        int count = 0;
        for (AAlbum album : dt.mAlbums) {
            if (album.getDescription().equals(this.getTitle()) ||
                    album.getTitle().equals(query)) {
                count++;
            }
        }
        return count;
    }

    //TODO checkout if query is on artist or albumArtist or both
    public int searchTracksCount(String query) {
        int count = 0;
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) ||
                    media.getAlbumArtist().equals(this.getTitle()) ||
                    media.getTitle().contains(query)) {
                count++;
            }
        }
        return count;
    }

    //TODO checkout if query is on artist or albumArtist or both (same as above)
    public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) ||
                    media.getAlbumArtist().equals(this.getTitle()) ||
                    media.getTitle().contains(query)) {
                results.add(media);
            }
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public AMediaWrapper[] getTracks(int sort, boolean desc) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) ||
                    media.getAlbumArtist().equals(this.getTitle())) {
                results.add(media);
            }
        }
        return dt.sortMedia(results, sort, desc);
    }

    public AMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) ||
                    media.getAlbumArtist().equals(this.getTitle())) {
                results.add(media);
            }
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getTracksCount() {
        int count = 0;
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) ||
                    media.getAlbumArtist().equals(this.getTitle())) {
                count++;
            }
        }
        return count;
    }
}
