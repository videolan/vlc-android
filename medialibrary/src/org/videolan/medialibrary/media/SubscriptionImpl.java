package org.videolan.medialibrary.media;

import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.MlService;
import org.videolan.medialibrary.interfaces.media.Subscription;

public class SubscriptionImpl extends Subscription {

    SubscriptionImpl(long id, MlService.Type type, String name, long parentId) {
        super(id, type, name, parentId);
    }

    SubscriptionImpl(long id, int type, String name, long parentId) {
        super(id, type, name, parentId);
    }

    @Override
    public int getNewMediaNotification() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeSubscriptionNewMediaNotification(ml, this.id);
    }

    @Override
    public boolean setNewMediaNotification(int value) {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeSetSubscriptionNewMediaNotification(ml, this.id, value);
    }

    @Override
    public long getCachedSize() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetSubscriptionCachedSize(ml, this.id);
    }

    @Override
    public long getMaxCachedSize() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetSubscriptionMaxCachedSize(ml, this.id);
    }

    @Override
    public boolean setMaxCachedSize(long size) {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeSetSubscriptionMaxCachedSize(ml, this.id, size);
    }

    @Override
    public int getNbUnplayedMedia() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetSubscriptionNbUnplayedMedia(ml, this.id);
    }

    @Override
    public Subscription[] getChildSubscriptions(int sortingCriteria, boolean desc, boolean includeMissing) {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetChildSubscriptions(ml, id, sortingCriteria, desc, includeMissing);
    }

    @Override
    public Subscription getParent() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetParent(ml, id);
    }

    @Override
    public boolean refresh() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeSubscriptionRefresh(ml, id);
    }

    @Override
    public MediaWrapper[] getMedia(int sortingCriteria, boolean desc, boolean includeMissing) {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetSubscriptionMedia(ml, id, sortingCriteria, desc, includeMissing);
    }

    @Override
    public int getNbMedia() {
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetSubscriptionNbMedia(ml, id);
    }

    private native int nativeSubscriptionNewMediaNotification(Medialibrary ml, long id);
    private native boolean nativeSetSubscriptionNewMediaNotification(Medialibrary ml, long id, int value);
    private native long nativeGetSubscriptionCachedSize(Medialibrary ml, long id);
    private native long nativeGetSubscriptionMaxCachedSize(Medialibrary ml, long id);
    private native boolean nativeSetSubscriptionMaxCachedSize(Medialibrary ml, long id, long size);
    private native int nativeGetSubscriptionNbUnplayedMedia(Medialibrary ml, long id);
    private native Subscription[] nativeGetChildSubscriptions(Medialibrary ml, long id, int sortingCriteria, boolean desc, boolean includeMissing);
    private native Subscription nativeGetParent(Medialibrary ml, long id);
    private native boolean nativeSubscriptionRefresh(Medialibrary ml, long id);
    private native MediaWrapper[] nativeGetSubscriptionMedia(Medialibrary ml, long id, int sortingCriteria, boolean desc, boolean includeMissing);
    private native int nativeGetSubscriptionNbMedia(Medialibrary ml, long id);
}
