package org.videolan.medialibrary.interfaces;

public interface DevicesDiscoveryCb {
    void onDiscoveryStarted();
    void onDiscoveryProgress(String entryPoint);
    void onDiscoveryCompleted();
    void onDiscoveryFailed(String entryPoint);
    void onParsingStatsUpdated(int done, int scheduled);
    void onReloadStarted(String entryPoint);
    void onReloadCompleted(String entryPoint);
}