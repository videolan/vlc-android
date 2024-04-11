package org.videolan.medialibrary.interfaces.media;

public abstract class MlService {

    public enum Type {
        PODCAST(1);

        public final int value;

        Type(int val) {
            this.value = val;
        }

        public static Type getValue(int val) {
            for (Type type : Type.values()) {
                if (type.value == val) {
                    return type;
                }
            }
            return null;
        }
    }

    protected MlService(Type type) {
        this.type = type;
    }
    protected MlService(int type) {this.type = Type.getValue(type);}

    public Type type;

    public abstract boolean addSubscription(String mrl);
    public abstract boolean isAutoDownloadEnabled();
    public abstract boolean setAutoDownloadEnabled(boolean enabled);
    public abstract boolean isNewMediaNotificationEnabled();
    public abstract boolean setNewMediaNotificationEnabled(boolean enabled);
    public abstract long getMaxCacheSize();
    public abstract boolean setMaxCacheSize(long size);
    public abstract int getNbSubscriptions();
    public abstract int getNbUnplayedMedia();
    public abstract Subscription[] getSubscriptions(int sort, boolean desc, boolean includeMissing, boolean onlyFavorites);
    public abstract int getNbMedia();
    public abstract MediaWrapper[] getMedia(int sortingCriteria, boolean desc, boolean includeMissing, boolean onlyFavorites);
    public abstract boolean refresh();
}
