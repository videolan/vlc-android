package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.AFolder;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class StubFolder extends AFolder {

    private StubDataSource dt = StubDataSource.getInstance();

    public StubFolder(long id, String name, String mrl) {
        super(id, name, mrl);
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
    public AMediaWrapper[] media(int type, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        ArrayList<AMediaWrapper> source;
        if (type == TYPE_FOLDER_VIDEO) source = dt.mVideoMediaWrappers;
        else if (type == TYPE_FOLDER_AUDIO) source = dt.mAudioMediaWrappers;
        else return null;
        for (AMediaWrapper media : source) {
            if (isParentFolder(this.mMrl, media.getUri().getPath())) results.add(media);
        }
        results = new ArrayList<>(Arrays.asList(dt.sortMedia(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new AMediaWrapper[0]);
    }

    public int mediaCount(int type) {
        int count = 0;
        ArrayList<AMediaWrapper> source;
        if (type == TYPE_FOLDER_VIDEO) source = dt.mVideoMediaWrappers;
        else if (type == TYPE_FOLDER_AUDIO) source = dt.mAudioMediaWrappers;
        else return 0;
        for (AMediaWrapper media : source) {
            if (isParentFolder(this.mMrl, media.getUri().getPath())) count++;
        }
        return count;
    }

    public AFolder[] subfolders(int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AFolder> results = new ArrayList<>();
        for (AFolder folder : dt.mFolders) {
            if (isParentFolder(this.mMrl, folder.mMrl)) results.add(folder);
        }
        results = new ArrayList<>(Arrays.asList(dt.sortFolder(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new AFolder[0]);
    }

    public int subfoldersCount(int type) {
        int count = 0;
        for (AFolder folder : dt.mFolders) {
            if (isParentFolder(this.mMrl, folder.mMrl)) count++;
        }
        return count;
    }

    public AMediaWrapper[] searchTracks(String query, int mediaType, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AMediaWrapper> results = new ArrayList<>();
        ArrayList<AMediaWrapper> source;
        if (mediaType == TYPE_FOLDER_VIDEO) source = dt.mVideoMediaWrappers;
        else if (mediaType == TYPE_FOLDER_AUDIO) source = dt.mAudioMediaWrappers;
        else return null;
        for (AMediaWrapper media : source) {
            if (media.getTitle().contains(query) &&
                    isParentFolder(this.mMrl, media.getUri().getPath())) {
                results.add(media);
            }
        }
        results = new ArrayList<>(Arrays.asList(dt.sortMedia(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new AMediaWrapper[0]);
    }

    public int searchTracksCount(String query, int mediaType) {
        int count = 0;
        ArrayList<AMediaWrapper> source;
        if (mediaType == TYPE_FOLDER_VIDEO) source = dt.mVideoMediaWrappers;
        else if (mediaType == TYPE_FOLDER_AUDIO) source = dt.mAudioMediaWrappers;
        else return 0;
        for (AMediaWrapper media : source) {
            if (media.getTitle().contains(query) &&
                    isParentFolder(this.mMrl, media.getUri().getPath())) count++;
        }
        return count;
    }
}
