package org.videolan.medialibrary.stubs;

import android.os.Parcel;

import org.videolan.medialibrary.interfaces.media.AbstractFolder;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class StubFolder extends AbstractFolder {

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
    public AbstractMediaWrapper[] media(int type, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        ArrayList<AbstractMediaWrapper> source;
        if (type == TYPE_FOLDER_VIDEO) source = dt.mVideoMediaWrappers;
        else if (type == TYPE_FOLDER_AUDIO) source = dt.mAudioMediaWrappers;
        else return null;
        for (AbstractMediaWrapper media : source) {
            if (isParentFolder(this.mMrl, media.getUri().getPath())) results.add(media);
        }
        results = new ArrayList<>(Arrays.asList(dt.sortMedia(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new AbstractMediaWrapper[0]);
    }

    public int mediaCount(int type) {
        int count = 0;
        ArrayList<AbstractMediaWrapper> source;
        if (type == TYPE_FOLDER_VIDEO) source = dt.mVideoMediaWrappers;
        else if (type == TYPE_FOLDER_AUDIO) source = dt.mAudioMediaWrappers;
        else return 0;
        for (AbstractMediaWrapper media : source) {
            if (isParentFolder(this.mMrl, media.getUri().getPath())) count++;
        }
        return count;
    }

    public AbstractFolder[] subfolders(int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractFolder> results = new ArrayList<>();
        for (AbstractFolder folder : dt.mFolders) {
            if (isParentFolder(this.mMrl, folder.mMrl)) results.add(folder);
        }
        results = new ArrayList<>(Arrays.asList(dt.sortFolder(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new AbstractFolder[0]);
    }

    public int subfoldersCount(int type) {
        int count = 0;
        for (AbstractFolder folder : dt.mFolders) {
            if (isParentFolder(this.mMrl, folder.mMrl)) count++;
        }
        return count;
    }

    public AbstractMediaWrapper[] searchTracks(String query, int mediaType, int sort, boolean desc, int nbItems, int offset) {
        ArrayList<AbstractMediaWrapper> results = new ArrayList<>();
        ArrayList<AbstractMediaWrapper> source;
        if (mediaType == TYPE_FOLDER_VIDEO) source = dt.mVideoMediaWrappers;
        else if (mediaType == TYPE_FOLDER_AUDIO) source = dt.mAudioMediaWrappers;
        else return null;
        for (AbstractMediaWrapper media : source) {
            if (media.getTitle().contains(query) &&
                    isParentFolder(this.mMrl, media.getUri().getPath())) {
                results.add(media);
            }
        }
        results = new ArrayList<>(Arrays.asList(dt.sortMedia(results, sort, desc)));
        return dt.secureSublist(results, offset, offset + nbItems).toArray(new AbstractMediaWrapper[0]);
    }

    public int searchTracksCount(String query, int mediaType) {
        int count = 0;
        ArrayList<AbstractMediaWrapper> source;
        if (mediaType == TYPE_FOLDER_VIDEO) source = dt.mVideoMediaWrappers;
        else if (mediaType == TYPE_FOLDER_AUDIO) source = dt.mAudioMediaWrappers;
        else return 0;
        for (AbstractMediaWrapper media : source) {
            if (media.getTitle().contains(query) &&
                    isParentFolder(this.mMrl, media.getUri().getPath())) count++;
        }
        return count;
    }
}
