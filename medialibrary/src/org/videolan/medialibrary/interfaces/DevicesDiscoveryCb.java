package org.videolan.medialibrary.interfaces;

public interface DevicesDiscoveryCb {
    void onDiscoveryStarted();
    void onDiscoveryProgress(String root);
    void onDiscoveryCompleted();
    void onDiscoveryFailed(String root);
    void onParsingStatsUpdated(int done, int scheduled);
    void onReloadStarted(String root);
    void onReloadCompleted(String root);
}