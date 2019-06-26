package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.AbstractMedialibrary;
import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;

import java.util.ArrayList;

public class StubAlbum extends AbstractAlbum {

    private StubDataSource dt = StubDataSource.getInstance();

    public StubAlbum(long id, String title, int releaseYear, String artworkMrl, String albumArtist, long albumArtistId, int nbTracks, int duration) {
        super(id, title, releaseYear, artworkMrl, albumArtist, albumArtistId, nbTracks, duration);
    }

    public StubAlbum(Parcel in) {
        super(in);
    }

    public int getRealTracksCount() {
        int count = 0;
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getAlbum().equals(this.getTitle())) count++;
        }
        return count;
    }

    public AbstractArtist getAlbumArtist() {
        AbstractMedialibrary ml = AbstractMedialibrary.getInstance();
        return ml.getArtist(this.albumArtistId);
    }

    private ArrayList<AbstractMediaWrapper> getAlbumTracks() {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getAlbum().equals(this.getTitle())) results.add(media);
        }
        return results;
    }

    public AbstractMediaWrapper[] getTracks(int sort, boolean desc) {
        return dt.sortMedia(getAlbumTracks(), sort, desc);
    }

    public AbstractMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        return dt.sortMedia(dt.secureSublist(getAlbumTracks(), offset, offset + nbItems), sort, desc);
    }

    public AbstractMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getAlbum().equals(this.getTitle()) ||
                    media.getTitle().contains(query)) {
                results.add(media);
            }
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int searchTracksCount(String query) {
        int count = 0;
        for (AbstractMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getAlbum().equals(this.getTitle()) ||
                    media.getTitle().contains(query)) {
                count++;
            }
        }
        return count;
    }
}
