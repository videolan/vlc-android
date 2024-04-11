package org.videolan.medialibrary.interfaces;


public interface RootsEventsCb {
    void onRootBanned(String root, boolean success);
    void onRootUnbanned(String root, boolean success);
    void onRootAdded(String root, boolean success);
    void onRootRemoved(String root, boolean success);
    void onDiscoveryStarted();
    void onDiscoveryProgress(String root);
    void onDiscoveryCompleted();
    void onDiscoveryFailed(String root);
}
