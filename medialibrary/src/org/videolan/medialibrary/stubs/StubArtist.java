package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;

import java.util.ArrayList;
import java.util.Arrays;

public class StubArtist extends AbstractArtist {

    private StubDataSource dt = StubDataSource.getInstance();

    public StubArtist(long id, String name, String shortBio, String artworkMrl, String musicBrainzId) {
        super(id, name, shortBio, artworkMrl, musicBrainzId);
    }

    public StubArtist(Parcel in) {
        super(in);
    }

    public AbstractAlbum[] getAlbums(int sort, boolean desc) {
        ArrayList<AbstractAlbum> results = new ArrayList<>();
        for (AbstractAlbum album : dt.mAlbums) {
            if (album.getDescription().equals(this.getTitle())) results.add(album);
        }
        return dt.sortAlbum(results, sort, desc);
    }

    public int getAlbumsCount() {
        int count = 0;
        for (AbstractAlbum album : dt.mAlbums) {
            if (album.getDescription().equals(this.getTitle())) count++;
        }
        return count;
    }

    public AbstractAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractAlbum> results = new ArrayList<>(Arrays.asList(getAlbums(sort, desc)));
        return results.toArray(new AbstractAlbum[0]);
    }

    public AbstractAlbum[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractAlbum> results = new ArrayList<>();
        for (AbstractAlbum album : dt.mAlbums) {
            if (album.getDescription().equals(this.getTitle()) ||
                    album.getTitle().equals(query)) {
                results.add(album);
            }
        }
        return dt.sortAlbum(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int searchAlbumsCount(String query) {
        int count = 0;
        for (AbstractAlbum album : dt.mAlbums) {
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
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) ||
                    media.getAlbumArtist().equals(this.getTitle()) ||
                    media.getTitle().contains(query)) {
                count++;
            }
        }
        return count;
    }

    //TODO checkout if query is on artist or albumArtist or both (same as above)
    public AbstractMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) ||
                    media.getAlbumArtist().equals(this.getTitle()) ||
                    media.getTitle().contains(query)) {
                results.add(media);
            }
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public AbstractMediaWrapper[] getTracks(int sort, boolean desc) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) ||
                    media.getAlbumArtist().equals(this.getTitle())) {
                results.add(media);
            }
        }
        return dt.sortMedia(results, sort, desc);
    }

    public AbstractMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) ||
                    media.getAlbumArtist().equals(this.getTitle())) {
                results.add(media);
            }
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getTracksCount() {
        int count = 0;
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getArtist().equals(this.getTitle()) ||
                    media.getAlbumArtist().equals(this.getTitle())) {
                count++;
            }
        }
        return count;
    }
}
