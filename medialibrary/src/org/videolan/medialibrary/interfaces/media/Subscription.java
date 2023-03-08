package org.videolan.medialibrary.interfaces.media;

import org.videolan.medialibrary.media.MediaLibraryItem;

public abstract class Subscription {

    public Subscription(long id, MlService.Type type, String name, long parentId) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.parentId = parentId;
    }
    
    public Subscription(long id, int type, String name, long parentId) {
        this.id = id;
        this.type = MlService.Type.getValue(type);
        this.name = name;
        this.parentId = parentId;
    }

    public MlService.Type type;
    protected long parentId;
    public String name;
    public long id;


    public abstract int getNewMediaNotification();
    public abstract boolean setNewMediaNotification(int value);
    public abstract long getCachedSize();
    public abstract long getMaxCachedSize();
    public abstract boolean setMaxCachedSize(long size);
    public abstract int getNbUnplayedMedia();
    public abstract Subscription[] getChildSubscriptions(int sortingCriteria, boolean desc, boolean includeMissing);
    public abstract Subscription getParent();
    public abstract MediaWrapper[] getMedia(int sortingCriteria, boolean desc, boolean includeMissing);
    public abstract boolean refresh();
    public abstract int getNbMedia();
}
