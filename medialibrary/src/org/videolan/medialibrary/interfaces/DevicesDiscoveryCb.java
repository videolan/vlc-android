package org.videolan.medialibrary.interfaces;

public interface DevicesDiscoveryCb {
    void onDiscoveryStarted(String entryPoint);
    void onDiscoveryProgress(String entryPoint);
    void onDiscoveryCompleted(String entryPoint);
    void onParsingStatsUpdated(int percent);
    void onReloadStarted(String entryPoint);
    void onReloadCompleted(String entryPoint);
//    void onEntryPointBanned(String entryPoint, boolean success);
//    void onEntryPointUnbanned(String entryPoint, boolean success);
//    void onEntryPointRemoved(String entryPoint, boolean success);
}