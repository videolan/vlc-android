package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.Playlist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StubPlaylist extends Playlist {

    private ArrayList<Long> mTracksId = new ArrayList<>();
    private StubDataSource dt = StubDataSource.getInstance();

    public StubPlaylist(long id, String name, int trackCount, long duration, int nbVideo, int nbAudio, int nbUnknown, int nbDurationUnknown, boolean isFavorite) {
        super(id, name, trackCount, duration, nbVideo, nbAudio, nbUnknown, nbDurationUnknown, isFavorite);
    }

    public StubPlaylist(Parcel in) {
        super(in);
    }

    @Override
    public MediaWrapper[] getTracks() {
        return getTracks(true, false);
    }

    public MediaWrapper[] getTracks(boolean includeMissing, boolean onlyFavorites) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (mTracksId.contains(media.getId())) results.add(media);
        }
        return results.toArray(new MediaWrapper[0]);
    }

    public MediaWrapper[] getPagedTracks(int nbItems, int offset, boolean includeMissing, boolean onlyFavorites) {
        ArrayList<MediaWrapper> results = new ArrayList<>(Arrays.asList(getTracks()));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new MediaWrapper[0]);
    }

    public int getRealTracksCount(boolean includeMissing, boolean onlyFavorites) {
        int count = 0;
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (mTracksId.contains(media.getId())) count++;
        }
        return count;
    }

    public boolean append(long mediaId) {
        mTracksId.add(mediaId);
        mTracksCount++;
        return true;
    }

    public boolean append(long[] mediaIds) {
        for (long id : mediaIds) {
            append(id);
        }
        return true;
    }

    public boolean append(List<Long> mediaIds) {
        for (long id : mediaIds) {
            append(id);
        }
        return true;
    }

    public boolean add(long mediaId, int position) {
        mTracksId.add(position, mediaId);
        return true;
    }

    public boolean move(int oldPosition, int newPosition) {
        long id = mTracksId.get(oldPosition);
        mTracksId.remove(oldPosition);
        mTracksId.add(newPosition, id);
        return true;
    }

    public boolean remove(int position) {
        mTracksId.remove(position);
        mTracksCount--;
        return true;
    }

    public boolean delete() {
        for (int i = 0 ; i <  dt.mPlaylists.size() ; i++) {
            if (dt.mPlaylists.get(i).getId() == this.getId()) {
                dt.mPlaylists.remove(i);
                return true;
            }
        }
        return false;
    }

    public MediaWrapper[] searchTracks(String query, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (mTracksId.contains(media.getId()) &&
                    Tools.hasSubString(media.getTitle(), query)) results.add(media);
        }
        results = new ArrayList<>(Arrays.asList(dt.sortMedia(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new MediaWrapper[0]);

    }

    public int searchTracksCount(String query) {
        int count = 0;
        for (MediaWrapper media : dt.mAudioMediaWrappers) {
            if (mTracksId.contains(media.getId()) &&
                    Tools.hasSubString(media.getTitle(), query)) count++;
        }
        return count;
    }
}
