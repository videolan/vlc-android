package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.media.Folder;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class StubFolder extends Folder {

    private StubDataSource dt = StubDataSource.getInstance();

    public StubFolder(long id, String name, String mrl, int count, boolean isFavorite) {
        super(id, name, mrl, count, isFavorite);
    }
    public StubFolder(Parcel in) {
        super(in);
    }

    //TODO checkout results not sure att all ...
    private boolean isParentFolder(String parentMrl, String childMrl) {
        if (!childMrl.contains(parentMrl)) return false;
        File mediaFile = new File(childMrl);
        String parentPath = mediaFile.getParent();
        return parentPath.equals(parentMrl);
    }

    //TODO WTF would media be null ??
    public MediaWrapper[] media(int type, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        ArrayList<MediaWrapper> source;
        if (type == TYPE_FOLDER_VIDEO) source = dt.mVideoMediaWrappers;
        else if (type == TYPE_FOLDER_AUDIO) source = dt.mAudioMediaWrappers;
        else return null;
        for (MediaWrapper media : source) {
            if (isParentFolder(this.mMrl, media.getUri().getPath())) results.add(media);
        }
        results = new ArrayList<>(Arrays.asList(dt.sortMedia(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new MediaWrapper[0]);
    }

    public int mediaCount(int type) {
        int count = 0;
        ArrayList<MediaWrapper> source;
        if (type == TYPE_FOLDER_VIDEO) source = dt.mVideoMediaWrappers;
        else if (type == TYPE_FOLDER_AUDIO) source = dt.mAudioMediaWrappers;
        else return 0;
        for (MediaWrapper media : source) {
            if (isParentFolder(this.mMrl, media.getUri().getPath())) count++;
        }
        return count;
    }

    public Folder[] subfolders(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<Folder> results = new ArrayList<>();
        for (Folder folder : dt.mFolders) {
            if (isParentFolder(this.mMrl, folder.mMrl)) results.add(folder);
        }
        results = new ArrayList<>(Arrays.asList(dt.sortFolder(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new Folder[0]);
    }

    public int subfoldersCount(int type) {
        int count = 0;
        for (Folder folder : dt.mFolders) {
            if (isParentFolder(this.mMrl, folder.mMrl)) count++;
        }
        return count;
    }

    public MediaWrapper[] searchTracks(String query, int mediaType, int sort, boolean desc, boolean includeMissing, boolean onlyFavorites, int nbItems, int offset) {
        ArrayList<MediaWrapper> results = new ArrayList<>();
        ArrayList<MediaWrapper> source;
        if (mediaType == TYPE_FOLDER_VIDEO) source = dt.mVideoMediaWrappers;
        else if (mediaType == TYPE_FOLDER_AUDIO) source = dt.mAudioMediaWrappers;
        else return null;
        for (MediaWrapper media : source) {
            if (Tools.hasSubString(media.getTitle(), query) &&
                    isParentFolder(this.mMrl, media.getUri().getPath())) {
                results.add(media);
            }
        }
        results = new ArrayList<>(Arrays.asList(dt.sortMedia(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new MediaWrapper[0]);
    }

    public int searchTracksCount(String query, int mediaType) {
        int count = 0;
        ArrayList<MediaWrapper> source;
        if (mediaType == TYPE_FOLDER_VIDEO) source = dt.mVideoMediaWrappers;
        else if (mediaType == TYPE_FOLDER_AUDIO) source = dt.mAudioMediaWrappers;
        else return 0;
        for (MediaWrapper media : source) {
            if (Tools.hasSubString(media.getTitle(), query) &&
                    isParentFolder(this.mMrl, media.getUri().getPath())) count++;
        }
        return count;
    }

    @Override
    public boolean equals(Object obj) {
        boolean result = super.equals(obj);
        if (!result && obj instanceof Folder) {
            Folder other = ((Folder) obj);
            return other.mMrl.equals(this.mMrl);
        }
        return result;
    }
}
