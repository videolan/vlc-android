package org.videolan.medialibrary.interfaces;


public interface EntryPointsEventsCb {
    void onEntryPointBanned(String entryPoint, boolean success);
    void onEntryPointUnbanned(String entryPoint, boolean success);
    void onEntryPointAdded(String entryPoint, boolean success);
    void onEntryPointRemoved(String entryPoint, boolean success);
    void onDiscoveryStarted();
    void onDiscoveryProgress(String entryPoint);
    void onDiscoveryCompleted();
    void onDiscoveryFailed(String entryPoint);
}
