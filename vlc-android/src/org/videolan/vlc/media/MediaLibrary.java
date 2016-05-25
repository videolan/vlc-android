/*****************************************************************************
 * MediaLibrary.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.media;

import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;
import android.util.Log;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.libvlc.util.Extensions;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioBrowserListAdapter;
import org.videolan.vlc.interfaces.IBrowser;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.Thread.State;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MediaLibrary {
    public final static String TAG = "VLC/MediaLibrary";

    public static final int UPDATE_ITEM = 0;
    public static final int MEDIA_ITEMS_UPDATED = 100;

    private static MediaLibrary mInstance;
    private final ArrayList<MediaWrapper> mItemList;
    private final ArrayList<Handler> mUpdateHandler;
    private final ReadWriteLock mItemListLock;
    private boolean isStopping = false;
    private boolean mRestart = false;
    protected Thread mLoadingThread;
    private WeakReference<IBrowser> mBrowser = null;

    public final static HashSet<String> FOLDER_BLACKLIST;
    static {
        final String[] folder_blacklist = {
                "/alarms",
                "/notifications",
                "/ringtones",
                "/media/alarms",
                "/media/notifications",
                "/media/ringtones",
                "/media/audio/alarms",
                "/media/audio/notifications",
                "/media/audio/ringtones",
                "/android/data",
                "/android/media",
        };

        FOLDER_BLACKLIST = new HashSet<>();
        for (String item : folder_blacklist)
            FOLDER_BLACKLIST.add(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + item);
    }

    private MediaLibrary() {
        mInstance = this;
        mItemList = new ArrayList<>();
        mUpdateHandler = new ArrayList<>();
        mItemListLock = new ReentrantReadWriteLock();
    }

    public void scanMediaItems(boolean restart) {
        if (restart && isWorking()) {
            /* do a clean restart if a scan is ongoing */
            mRestart = true;
            isStopping = true;
        } else {
            scanMediaItems();
        }
    }

    public void scanMediaItems() {
        if (mLoadingThread == null || mLoadingThread.getState() == State.TERMINATED) {
            isStopping = false;
            MediaUtils.actionScanStart();
            mLoadingThread = new Thread(new GetMediaItemsRunnable());
            mLoadingThread.setPriority(Process.THREAD_PRIORITY_DEFAULT+Process.THREAD_PRIORITY_LESS_FAVORABLE);
            mLoadingThread.start();
        }
    }

    public void loadMediaItems(){
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                mItemListLock.writeLock().lock();
                mItemList.clear();
                mItemList.addAll(getStoredMedias(MediaDatabase.getInstance()).values());
                mItemListLock.writeLock().unlock();
                notifyMediaUpdated();
            }
        });
    }

    public void stop() {
        isStopping = true;
    }

    public boolean isWorking() {
        return mLoadingThread != null &&
                mLoadingThread.isAlive() &&
                mLoadingThread.getState() != State.TERMINATED &&
                mLoadingThread.getState() != State.NEW;
    }

    public synchronized static MediaLibrary getInstance() {
        if (mInstance == null)
            mInstance = new MediaLibrary();
        return mInstance;
    }

    public void addUpdateHandler(Handler handler) {
        mUpdateHandler.add(handler);
    }

    public void removeUpdateHandler(Handler handler) {
        mUpdateHandler.remove(handler);
    }

    public ArrayList<MediaWrapper> searchMedia(String query){
        ArrayList<MediaWrapper> mediaList = new ArrayList<>();
        ArrayList<String> pathList = MediaDatabase.getInstance().searchMedia(query);
        if (!pathList.isEmpty()){
            for (String path : pathList) {
                mediaList.add(getMediaItem(path));
            }
        }
        return mediaList;
    }

    public ArrayList<MediaWrapper> getVideoItems() {
        ArrayList<MediaWrapper> videoItems = new ArrayList<>();
        MediaWrapper item;
        int count = mItemList.size();
        for (int i = 0; i < count; ++i) {
            item = mItemList.get(i);
            if (item != null && item.getType() == MediaWrapper.TYPE_VIDEO) {
                videoItems.add(item);
            }
        }
        return videoItems;
    }

    public ArrayList<MediaWrapper> getAudioItems() {
        ArrayList<MediaWrapper> audioItems = new ArrayList<>();
        MediaWrapper item;
        int count = mItemList.size();
        for (int i = 0; i < count; ++i) {
            item = mItemList.get(i);
            if (item.getType() == MediaWrapper.TYPE_AUDIO) {
                audioItems.add(item);
            }
        }
        return audioItems;
    }

    public ArrayList<MediaWrapper> getPlaylistFilesItems() {
        ArrayList<MediaWrapper> playlistItems = new ArrayList<>();
        MediaWrapper item;
        int count = mItemList.size();
        for (int i = 0; i < count; ++i) {
            item = mItemList.get(i);
            if (item.getType() == MediaWrapper.TYPE_PLAYLIST)
                playlistItems.add(item);
        }
        return playlistItems;
    }

    public ArrayList<AudioBrowserListAdapter.ListItem> getPlaylistDbItems() {
        ArrayList<AudioBrowserListAdapter.ListItem> playlistItems = new ArrayList<>();
        AudioBrowserListAdapter.ListItem playList;
        MediaDatabase db = MediaDatabase.getInstance();
        String[] items, playlistNames = db.getPlaylists();
        for (String playlistName : playlistNames){
            items = db.playlistGetItems(playlistName);
            if (items == null)
                continue;
            playList = new AudioBrowserListAdapter.ListItem(playlistName, null, null, false);
            for (String track : items){
                playList.mMediaList.add(new MediaWrapper(AndroidUtil.LocationToUri(track)));
            }
            playlistItems.add(playList);
        }
        return playlistItems;
    }

    public ArrayList<MediaWrapper> getMediaItems() {
        return mItemList;
    }

    public MediaWrapper getMediaItem(String location) {
        mItemListLock.readLock().lock();
        for (int i = 0; i < mItemList.size(); i++) {
            MediaWrapper item = mItemList.get(i);
            if (item.getLocation().equals(location)) {
                mItemListLock.readLock().unlock();
                return item;
            }
        }
        mItemListLock.readLock().unlock();
        return null;
    }

    private class GetMediaItemsRunnable implements Runnable {

        private final Stack<File> directories = new Stack<>();
        private final HashSet<String> directoriesScanned = new HashSet<>();

        public GetMediaItemsRunnable() {
        }

        @Override
        public void run() {
            LibVLC libVlcInstance = VLCInstance.get();

            // Initialize variables
            final MediaDatabase mediaDatabase = MediaDatabase.getInstance();

            // show progressbar in footer
            if (mBrowser != null && mBrowser.get() != null)
                mBrowser.get().showProgressBar();

            List<File> mediaDirs = mediaDatabase.getMediaDirs();
            if (mediaDirs.size() == 0) {
                // Use all available storage directories as our default
                String storageDirs[] = AndroidDevices.getMediaDirectories();
                for (String dir: storageDirs) {
                    File f = new File(dir);
                    if (f.exists())
                        mediaDirs.add(f);
                }
            }
            directories.addAll(mediaDirs);

            // get all existing media items
            ArrayMap<String, MediaWrapper> existingMedias = getStoredMedias(mediaDatabase);

            // list of all added files
            HashSet<String> addedLocations = new HashSet<>();

            // clear all old items
            mItemListLock.writeLock().lock();
            mItemList.clear();
            mItemListLock.writeLock().unlock();

            MediaItemFilter mediaFileFilter = new MediaItemFilter();

            int count = 0;

            LinkedList<File> mediaToScan = new LinkedList<>();
            try {
                LinkedList<String> dirsToIgnore = new LinkedList<>();
                // Count total files, and stack them
                while (!directories.isEmpty()) {
                    File dir = directories.pop();
                    String dirPath = dir.getAbsolutePath();

                    // Skip some system folders
                    if (dirPath.startsWith("/proc/") || dirPath.startsWith("/sys/") || dirPath.startsWith("/dev/"))
                        continue;

                    // Do not scan again if same canonical path
                    try {
                        dirPath = dir.getCanonicalPath();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (directoriesScanned.contains(dirPath))
                        continue;
                    else
                        directoriesScanned.add(dirPath);

                    // Do no scan media in .nomedia folders
                    if (new File(dirPath + "/.nomedia").exists()) {
                        dirsToIgnore.add("file://"+dirPath);
                        continue;
                    }

                    // Filter the extensions and the folders
                    try {
                        String[] files = dir.list();
                        if (files != null) {
                            for (String fileName : files) {
                                File file = new File(dirPath, fileName);
                                if (mediaFileFilter.accept(file)){
                                    if (file.isFile())
                                        mediaToScan.add(file);
                                    else if (file.isDirectory())
                                        directories.push(file);
                                }
                            }
                        }
                    } catch (Exception e){
                        // listFiles can fail in OutOfMemoryError, go to the next folder
                        continue;
                    }

                    if (isStopping) {
                        Log.d(TAG, "Stopping scan");
                        return;
                    }
                }

                //Remove ignored files
                HashSet<Uri> mediasToRemove = new HashSet<>();
                String path;
                outloop:
                for (Map.Entry<String, MediaWrapper> entry : existingMedias.entrySet()){
                    path = entry.getKey();
                    for (String dirPath : dirsToIgnore) {
                        if (path.startsWith(dirPath)) {
                            mediasToRemove.add(entry.getValue().getUri());
                            mItemListLock.writeLock().lock();
                            mItemList.remove(existingMedias.get(path));
                            mItemListLock.writeLock().unlock();
                            continue outloop;
                        }
                    }
                }
                mediaDatabase.removeMedias(mediasToRemove);

                // Process the stacked items
                for (File file : mediaToScan) {
                    String fileURI = AndroidUtil.FileToUri(file).toString();
                    if (mBrowser != null && mBrowser.get() != null)
                        mBrowser.get().sendTextInfo(file.getName(), count,
                                mediaToScan.size());
                    count++;
                    if (existingMedias.containsKey(fileURI)) {
                        /**
                         * only add file if it is not already in the list. eg. if
                         * user select an subfolder as well
                         */
                        if (!addedLocations.contains(fileURI)) {
                            mItemListLock.writeLock().lock();
                            // get existing media item from database
                            mItemList.add(existingMedias.get(fileURI));
                            mItemListLock.writeLock().unlock();
                            notifyMediaUpdated(existingMedias.get(fileURI));
                            addedLocations.add(fileURI);
                        }
                    } else {
                        // create new media item
                        final Media media = new Media(libVlcInstance, Uri.parse(fileURI));
                        media.parse();
                        /* skip files with .mod extension and no duration */
                        if ((media.getDuration() == 0 || (media.getTrackCount() != 0 && TextUtils.isEmpty(media.getTrack(0).codec))) &&
                            fileURI.endsWith(".mod")) {
                            media.release();
                            continue;
                        }
                        MediaWrapper mw = new MediaWrapper(media);
                        media.release();
                        mw.setLastModified(file.lastModified());
                        mItemListLock.writeLock().lock();
                        mItemList.add(mw);
                        mItemListLock.writeLock().unlock();
                        notifyMediaUpdated(mw);
                        // Add this item to database
                        mediaDatabase.addMedia(mw);
                    }
                    if (isStopping) {
                        Log.d(TAG, "Stopping scan");
                        return;
                    }
                }
            } finally {
                notifyMediaUpdated();

                // remove old files & folders from database if storage is mounted
                if (!isStopping && Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    for (String fileURI : addedLocations) {
                        existingMedias.remove(fileURI);
                    }
                    mediaDatabase.removeMediaWrappers(existingMedias.values());

                    /*
                     * In case of file matching path of a folder from another removable storage
                     */
                    for (File file : mediaDatabase.getMediaDirs())
                        if (!file.isDirectory())
                            mediaDatabase.removeDir(file.getAbsolutePath());
                }

                // hide progressbar in footer
                if (mBrowser != null && mBrowser.get() != null) {
                    mBrowser.get().clearTextInfo();
                    mBrowser.get().hideProgressBar();
                }

                MediaUtils.actionScanStop();

                if (mRestart) {
                    Log.d(TAG, "Restarting scan");
                    mRestart = false;
                    restartHandler.sendEmptyMessageDelayed(1, 200);
                }
            }
        }
    }

    private void notifyMediaUpdated(MediaWrapper mw) {
        // update the video and audio activities
        for (int i = 0; i < mUpdateHandler.size(); i++) {
            Handler h = mUpdateHandler.get(i);
            h.obtainMessage(UPDATE_ITEM, mw).sendToTarget();
        }
    }

    private void notifyMediaUpdated() {
        // update the video and audio activities
        for (int i = 0; i < mUpdateHandler.size(); i++) {
            Handler h = mUpdateHandler.get(i);
            h.sendEmptyMessage(MEDIA_ITEMS_UPDATED);
        }
    }

    private ArrayMap<String, MediaWrapper> getStoredMedias(MediaDatabase mediaDatabase) {
        return mediaDatabase.getMedias();
    }

    private Handler restartHandler = new RestartHandler(this);

    private static class RestartHandler extends WeakHandler<MediaLibrary> {
        public RestartHandler(MediaLibrary owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            MediaLibrary owner = getOwner();
            if(owner == null) return;
            owner.scanMediaItems();
        }
    }

    /**
     * Filters all irrelevant files
     */
    private static class MediaItemFilter implements FileFilter {

        @Override
        public boolean accept(File f) {
            boolean accepted = false;
            if (!f.isHidden()) {
                if (f.isDirectory() && !FOLDER_BLACKLIST.contains(f.getPath().toLowerCase(Locale.ENGLISH))) {
                    accepted = true;
                } else {
                    String fileName = f.getName().toLowerCase(Locale.ENGLISH);
                    int dotIndex = fileName.lastIndexOf(".");
                    if (dotIndex != -1) {
                        String fileExt = fileName.substring(dotIndex);
                        accepted = Extensions.AUDIO.contains(fileExt) ||
                                   Extensions.VIDEO.contains(fileExt) ||
                                   Extensions.PLAYLIST.contains(fileExt);
                    }
                }
            }
            return accepted;
        }
    }

    public void setBrowser(IBrowser browser) {
        if (browser != null)
            mBrowser = new WeakReference<>(browser);
        else
            mBrowser.clear();
    }
}
