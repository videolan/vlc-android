package org.videolan.medialibrary.interfaces;


public interface EntryPointsEventsCb {
    void onEntryPointBanned(String entryPoint, boolean success);
    void onEntryPointUnbanned(String entryPoint, boolean success);
    void onEntryPointRemoved(String entryPoint, boolean success);
    void onDiscoveryStarted(String entryPoint);
    void onDiscoveryProgress(String entryPoint);
    void onDiscoveryCompleted(String entryPoint);
}
