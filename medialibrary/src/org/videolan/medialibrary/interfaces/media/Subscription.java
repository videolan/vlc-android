package org.videolan.medialibrary.interfaces.media;

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
    public abstract long getMaxCacheSize();
    public abstract boolean setMaxCacheSize(long size);
    public abstract int getNbUnplayedMedia();
    public abstract Subscription[] getChildSubscriptions(int sortingCriteria, boolean desc, boolean includeMissing, boolean onlyFavorites);
    public abstract Subscription getParent();
    public abstract MediaWrapper[] getMedia(int sortingCriteria, boolean desc, boolean includeMissing, boolean onlyFavorites);
    public abstract boolean refresh();
    public abstract int getNbMedia();
}
