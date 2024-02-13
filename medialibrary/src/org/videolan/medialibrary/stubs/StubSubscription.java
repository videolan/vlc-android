package org.videolan.medialibrary.stubs;

import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.MlService;
import org.videolan.medialibrary.interfaces.media.Subscription;

public class StubSubscription extends Subscription {

    StubSubscription(long id, MlService.Type type, String name, long parentId) {
        super(id, type, name, parentId);
    }

    StubSubscription(long id, int type, String name, long parentId) {
        super(id, type, name, parentId);
    }

    @Override
    public int getNewMediaNotification() {
        return -1;
    }

    @Override
    public boolean setNewMediaNotification(int value) {
        return false;
    }

    @Override
    public long getCachedSize() {
        return 0;
    }

    @Override
    public long getMaxCacheSize() {
        return 0;
    }

    @Override
    public boolean setMaxCacheSize(long size) {
        return false;
    }

    @Override
    public int getNbUnplayedMedia() {
        return 0;
    }

    @Override
    public Subscription[] getChildSubscriptions(int sortingCriteria, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        return null;
    }

    @Override
    public Subscription getParent() {
        return null;
    }

    @Override
    public MediaWrapper[] getMedia(int sortingCriteria, boolean desc, boolean includeMissing, boolean onlyFavorites) {
        return null;
    }

    @Override
    public boolean refresh() {
        return false;
    }

    @Override
    public int getNbMedia() {
        return 0;
    }
}
