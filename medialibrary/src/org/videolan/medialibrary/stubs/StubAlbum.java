package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.AMedialibrary;
import org.videolan.medialibrary.interfaces.media.AAlbum;
import org.videolan.medialibrary.interfaces.media.AArtist;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;

import java.util.ArrayList;

public class StubAlbum extends AAlbum {

    private StubDataSource dt = StubDataSource.getInstance();

    public StubAlbum(long id, String title, int releaseYear, String artworkMrl, String albumArtist, long albumArtistId, int nbTracks, int duration) {
        super(id, title, releaseYear, artworkMrl, albumArtist, albumArtistId, nbTracks, duration);
    }

    public StubAlbum(Parcel in) {
        super(in);
    }

    public int getRealTracksCount() {
        int count = 0;
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getAlbum().equals(this.getTitle())) count++;
        }
        return count;
    }

    public AArtist getAlbumArtist() {
        AMedialibrary ml = AMedialibrary.getInstance();
        return ml.getArtist(this.albumArtistId);
    }

    private ArrayList<AMediaWrapper> getAlbumTracks() {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getAlbum().equals(this.getTitle())) results.add(media);
        }
        return results;
    }

    public AMediaWrapper[] getTracks(int sort, boolean desc) {
        return dt.sortMedia(getAlbumTracks(), sort, desc);
    }

    public AMediaWrapper[] getPagedTracks(int sort, boolean desc, int nbItems, int offset) {
        return dt.sortMedia(dt.secureSublist(getAlbumTracks(), offset, offset + nbItems), sort, desc);
    }

    public AMediaWrapper[] searchTracks(String query, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getAlbum().equals(this.getTitle()) ||
                    media.getTitle().contains(query)) {
                results.add(media);
            }
        }
        return dt.sortMedia(dt.secureSublist(results, offset, offset + nbItems), sort, desc);
    }

    public int searchTracksCount(String query) {
        int count = 0;
        for (AMediaWrapper media : dt.mAudioMediaWrappers) {
            if (media.getAlbum().equals(this.getTitle()) ||
                    media.getTitle().contains(query)) {
                count++;
            }
        }
        return count;
    }
}
