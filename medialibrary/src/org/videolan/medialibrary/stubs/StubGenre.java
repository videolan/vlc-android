package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractGenre;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;

import java.util.ArrayList;
import java.util.Arrays;

public class StubGenre extends AbstractGenre {

    private StubDataSource dt = StubDataSource.getInstance();

    public StubGenre(long id, String title) { super(id, title); }
    public StubGenre(Parcel in) { super(in); }

    public AbstractAlbum[] getAlbums(int sort, boolean desc) {
        ArrayList<AbstractAlbum> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) {
                for (AbstractAlbum album : dt.mAlbums) {
                    if (album.getTitle().equals(media.getAlbum()) &&
                            !results.contains(album)) {
                        results.add(album);
                    }
                }
            }
        }
        return dt.sortAlbum(results, sort, desc);
    }

    public AbstractAlbum[] getPagedAlbums(int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractAlbum> results = new ArrayList<>(Arrays.asList(getAlbums(sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new AbstractAlbum[0]);
    }

    @Override
    public int getAlbumsCount() {
        return getAlbums().length;
    }

    public AbstractAlbum[] searchAlbums(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractAlbum> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle()) &&
                    media.getTitle().contains(query)) {
                for (AbstractAlbum album : dt.mAlbums) {
                    if (album.getTitle().equals(media.getAlbum()) &&
                            !results.contains(album)) {
                        results.add(album);
                    }
                }
            }
        }
        results = new ArrayList<>(Arrays.asList(dt.sortAlbum(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new AbstractAlbum[0]);
    }

    public int searchAlbumsCount(String query) {
        ArrayList<AbstractAlbum> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle()) &&
                    media.getTitle().contains(query)) {
                for (AbstractAlbum album : dt.mAlbums) {
                    if (album.getTitle().equals(media.getAlbum()) &&
                            !results.contains(album)) {
                        results.add(album);
                    }
                }
            }
        }
        return results.size();
    }

    public AbstractArtist[] getArtists(int sort, boolean desc) {
        ArrayList<AbstractArtist> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) {
                for (AbstractArtist artist : dt.mArtists) {
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

    public AbstractMediaWrapper[] getTracks(boolean withThumbnail, int sort, boolean desc) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) results.add(media);
        }
        return dt.sortMedia(results, sort, desc);
    }

    public AbstractMediaWrapper[] getPagedTracks(boolean withThumbnail, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) results.add(media);
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int getTracksCount() {
        int count = 0;
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle())) count++;
        }
        return count;
    }


    public AbstractMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle()) &&
                    media.getTitle().contains(query)) {
                results.add(media);
            }
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int searchTracksCount(String query) {
        int count = 0;
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getGenre().equals(this.getTitle()) &&
                    media.getTitle().contains(query)) {
                count++;
            }
        }
        return count;
    }
}
