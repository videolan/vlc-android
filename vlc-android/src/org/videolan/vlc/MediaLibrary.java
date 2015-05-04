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

package org.videolan.vlc;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.Extensions;
import org.videolan.vlc.gui.audio.AudioBrowserListAdapter;
import org.videolan.vlc.interfaces.IBrowser;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

public class MediaLibrary {
    public final static String TAG = "VLC/MediaLibrary";

    public static final int MEDIA_ITEMS_UPDATED = 100;

    private static MediaLibrary mInstance;
    private final ArrayList<MediaWrapper> mItemList;
    private final ArrayList<Handler> mUpdateHandler;
    private final ReadWriteLock mItemListLock;
    private boolean isStopping = false;
    private boolean mRestart = false;
    protected Thread mLoadingThread;
    private IBrowser mBrowser = null;

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
                "/Android/data/" };

        FOLDER_BLACKLIST = new HashSet<String>();
        for (String item : folder_blacklist)
            FOLDER_BLACKLIST.add(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + item);
    }

    private MediaLibrary() {
        mInstance = this;
        mItemList = new ArrayList<MediaWrapper>();
        mUpdateHandler = new ArrayList<Handler>();
        mItemListLock = new ReentrantReadWriteLock();
    }

    public void loadMediaItems(Context context, boolean restart) {
        if (restart && isWorking()) {
            /* do a clean restart if a scan is ongoing */
            mRestart = true;
            isStopping = true;
        } else {
            loadMediaItems();
        }
    }

    public void loadMediaItems() {
        if (mLoadingThread == null || mLoadingThread.getState() == State.TERMINATED) {
            isStopping = false;
            Util.actionScanStart();
            mLoadingThread = new Thread(new GetMediaItemsRunnable());
            mLoadingThread.start();
        }
    }

    public void stop() {
        isStopping = true;
    }

    public boolean isWorking() {
        if (mLoadingThread != null &&
            mLoadingThread.isAlive() &&
            mLoadingThread.getState() != State.TERMINATED &&
            mLoadingThread.getState() != State.NEW)
            return true;
        return false;
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
        ArrayList<MediaWrapper> mediaList = new ArrayList<MediaWrapper>();
        ArrayList<String> pathList = MediaDatabase.getInstance().searchMedia(query);
        if (!pathList.isEmpty()){
            for (String path : pathList) {
                mediaList.add(getMediaItem(path));
            }
        }
        return mediaList;
    }

    public ArrayList<MediaWrapper> getVideoItems() {
        ArrayList<MediaWrapper> videoItems = new ArrayList<MediaWrapper>();
        mItemListLock.readLock().lock();
        for (int i = 0; i < mItemList.size(); i++) {
            MediaWrapper item = mItemList.get(i);
            if (item != null && item.getType() == MediaWrapper.TYPE_VIDEO) {
                videoItems.add(item);
            }
        }
        mItemListLock.readLock().unlock();
        return videoItems;
    }

    public ArrayList<MediaWrapper> getAudioItems() {
        ArrayList<MediaWrapper> audioItems = new ArrayList<MediaWrapper>();
        mItemListLock.readLock().lock();
        for (int i = 0; i < mItemList.size(); i++) {
            MediaWrapper item = mItemList.get(i);
            if (item.getType() == MediaWrapper.TYPE_AUDIO) {
                audioItems.add(item);
            }
        }
        mItemListLock.readLock().unlock();
        return audioItems;
    }

    public ArrayList<MediaWrapper> getPlaylistFilesItems() {
        ArrayList<MediaWrapper> playlistItems = new ArrayList<MediaWrapper>();
        mItemListLock.readLock().lock();
        for (int i = 0; i < mItemList.size(); i++) {
            MediaWrapper item = mItemList.get(i);
            if (item.getType() == MediaWrapper.TYPE_PLAYLIST) {
                playlistItems.add(item);
            }
        }
        mItemListLock.readLock().unlock();
        return playlistItems;
    }

    public ArrayList<AudioBrowserListAdapter.ListItem> getPlaylistDbItems() {
        ArrayList<AudioBrowserListAdapter.ListItem> playlistItems = new ArrayList<AudioBrowserListAdapter.ListItem>();
        AudioBrowserListAdapter.ListItem playList;
        MediaDatabase db = MediaDatabase.getInstance();
        String[] items, playlistNames = db.getPlaylists();
        for (String playlistName : playlistNames){
            items = db.playlistGetItems(playlistName);
            playList = new AudioBrowserListAdapter.ListItem(playlistName, null, null, false);
            for (String track : items){
                playList.mMediaList.add(new MediaWrapper(track));
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

    public ArrayList<MediaWrapper> getMediaItems(List<String> pathList) {
        ArrayList<MediaWrapper> items = new ArrayList<MediaWrapper>();
        for (int i = 0; i < pathList.size(); i++) {
            MediaWrapper item = getMediaItem(pathList.get(i));
            items.add(item);
        }
        return items;
    }

    private class GetMediaItemsRunnable implements Runnable {

        private final Stack<File> directories = new Stack<File>();
        private final HashSet<String> directoriesScanned = new HashSet<String>();

        public GetMediaItemsRunnable() {
        }

        @Override
        public void run() {
            LibVLC libVlcInstance = VLCInstance.get();

            // Initialize variables
            final MediaDatabase mediaDatabase = MediaDatabase.getInstance();

            // show progressbar in footer
            if (mBrowser != null)
                mBrowser.showProgressBar();

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
            HashMap<String, MediaWrapper> existingMedias = mediaDatabase.getMedias();

            // list of all added files
            HashSet<String> addedLocations = new HashSet<String>();

            // clear all old items
            mItemListLock.writeLock().lock();
            mItemList.clear();
            mItemListLock.writeLock().unlock();

            MediaItemFilter mediaFileFilter = new MediaItemFilter();

            int count = 0;

            LinkedList<File> mediaToScan = new LinkedList<File>();
            try {
                LinkedList<String> dirsToIgnore = new LinkedList<String>();
                // Count total files, and stack them
                while (!directories.isEmpty()) {
                    File dir = directories.pop();
                    String dirPath = dir.getAbsolutePath();
                    File[] f = null;

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
                        File file;
                        if (files != null){
                            for (String fileName : files){
                                file = new File(dirPath, fileName);
                                if (mediaFileFilter.accept(file)){
                                    if (file.isFile())
                                        mediaToScan.add(file);
                                    else if (file.isDirectory())
                                        directories.push(file);
                                }
                                file = null;
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
                HashSet<String> mediasToRemove = new HashSet<String>();
                outloop:
                for (String path : existingMedias.keySet()){
                    for (String dirPath : dirsToIgnore) {
                        if (path.startsWith(dirPath)) {
                            mediasToRemove.add(path);
                            mItemList.remove(existingMedias.get(path));
                            continue outloop;
                        }
                    }
                }
                mediaDatabase.removeMedias(mediasToRemove);

                // Process the stacked items
                for (File file : mediaToScan) {
                    String fileURI = LibVLC.PathToURI(file.getPath());
                    if (mBrowser != null)
                        mBrowser.sendTextInfo(file.getName(), count,
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
                            addedLocations.add(fileURI);
                        }
                    } else {
                        mItemListLock.writeLock().lock();
                        // create new media item
                        final Media media = new Media(libVlcInstance, fileURI);
                        media.parse();
                        media.release();
                        /* skip files with .mod extension and no duration */
                        if ((media.getDuration() == 0 || (media.getTrackCount() != 0 && TextUtils.isEmpty(media.getTrack(0).codec))) &&
                            fileURI.endsWith(".mod")) {
                            mItemListLock.writeLock().unlock();
                            continue;
                        }
                        MediaWrapper mw = new MediaWrapper(media);
                        mw.setLastModified(file.lastModified());
                        mItemList.add(mw);
                        // Add this item to database
                        mediaDatabase.addMedia(mw);
                        mItemListLock.writeLock().unlock();
                    }
                    if (isStopping) {
                        Log.d(TAG, "Stopping scan");
                        return;
                    }
                }
            } finally {
                // update the video and audio activities
                for (int i = 0; i < mUpdateHandler.size(); i++) {
                    Handler h = mUpdateHandler.get(i);
                    h.sendEmptyMessage(MEDIA_ITEMS_UPDATED);
                }

                // remove old files & folders from database if storage is mounted
                if (!isStopping && Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    for (String fileURI : addedLocations) {
                        existingMedias.remove(fileURI);
                    }
                    mediaDatabase.removeMedias(existingMedias.keySet());

                    /*
                     * In case of file matching path of a folder from another removable storage
                     */
                    for (File file : mediaDatabase.getMediaDirs())
                        if (!file.isDirectory())
                            mediaDatabase.removeDir(file.getAbsolutePath());
                }

                // hide progressbar in footer
                if (mBrowser != null) {
                    mBrowser.clearTextInfo();
                    mBrowser.hideProgressBar();
                }

                Util.actionScanStop();

                if (mRestart) {
                    Log.d(TAG, "Restarting scan");
                    mRestart = false;
                    restartHandler.sendEmptyMessageDelayed(1, 200);
                }
            }
        }
    };

    private Handler restartHandler = new RestartHandler(this);

    private static class RestartHandler extends WeakHandler<MediaLibrary> {
        public RestartHandler(MediaLibrary owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            MediaLibrary owner = getOwner();
            if(owner == null) return;
            owner.loadMediaItems();
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
        mBrowser = browser;
    }
}
