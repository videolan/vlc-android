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
import java.util.List;
import java.util.Stack;

import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.audio.AudioBrowserFragment;
import org.videolan.vlc.gui.video.VideoGridFragment;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class MediaLibrary {
    public final static String TAG = "VLC/MediaLibrary";

    public static final int MEDIA_ITEMS_UPDATED = 100;

    private static MediaLibrary mInstance;
    private final ArrayList<Media> mItemList;
    private final ArrayList<Handler> mUpdateHandler;
    private boolean isStopping = false;
    private boolean mRestart = false;
    private Context mRestartContext;
    protected Thread mLoadingThread;

    private MediaLibrary(Context context) {
        mInstance = this;
        mItemList = new ArrayList<Media>();
        mUpdateHandler = new ArrayList<Handler>();
    }

    public void loadMediaItems(Context context, boolean restart) {
        if (restart && isWorking()) {
            /* do a clean restart if a scan is ongoing */
            mRestart = true;
            isStopping = true;
            mRestartContext = context;
        } else {
            loadMediaItems(context);
        }
    }

    public void loadMediaItems(Context context) {
        if (mLoadingThread == null || mLoadingThread.getState() == State.TERMINATED) {
            isStopping = false;
            VideoGridFragment.actionScanStart(context.getApplicationContext());
            mLoadingThread = new Thread(new GetMediaItemsRunnable(context.getApplicationContext()));
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

    public static MediaLibrary getInstance(Context context) {
        if (mInstance == null)
            mInstance = new MediaLibrary(context);
        return mInstance;
    }

    public void addUpdateHandler(Handler handler) {
        mUpdateHandler.add(handler);
    }

    public void removeUpdateHandler(Handler handler) {
        mUpdateHandler.remove(handler);
    }

    public ArrayList<Media> getVideoItems() {
        ArrayList<Media> videoItems = new ArrayList<Media>();
        for (int i = 0; i < mItemList.size(); i++) {
            Media item = mItemList.get(i);
            if (item != null && item.getType() == Media.TYPE_VIDEO) {
                videoItems.add(item);
            }
        }
        return videoItems;
    }

    public ArrayList<Media> getAudioItems() {
        ArrayList<Media> audioItems = new ArrayList<Media>();
        for (int i = 0; i < mItemList.size(); i++) {
            Media item = mItemList.get(i);
            if (item.getType() == Media.TYPE_AUDIO) {
                audioItems.add(item);
            }
        }
        return audioItems;
    }

    public ArrayList<Media> getAudioItems(String name, String name2, int mode) {
        ArrayList<Media> audioItems = new ArrayList<Media>();
        for (int i = 0; i < mItemList.size(); i++) {
            Media item = mItemList.get(i);
            if (item.getType() == Media.TYPE_AUDIO) {

                boolean valid = false;
                switch (mode) {
                    case AudioBrowserFragment.MODE_ARTIST:
                        valid = name.equals(item.getArtist()) && (name2 == null || name2.equals(item.getAlbum()));
                        break;
                    case AudioBrowserFragment.MODE_ALBUM:
                        valid = name.equals(item.getAlbum());
                        break;
                    case AudioBrowserFragment.MODE_GENRE:
                        valid = name.equals(item.getGenre()) && (name2 == null || name2.equals(item.getAlbum()));
                        break;
                    default:
                        break;
                }
                if (valid)
                    audioItems.add(item);

            }
        }
        return audioItems;
    }

    public ArrayList<Media> getMediaItems() {
        return mItemList;
    }

    public Media getMediaItem(String location) {
        for (int i = 0; i < mItemList.size(); i++) {
            Media item = mItemList.get(i);
            if (item.getLocation().equals(location)) {
                return item;
            }
        }
        return null;
    }

    public ArrayList<Media> getMediaItems(List<String> pathList) {
        ArrayList<Media> items = new ArrayList<Media>();
        for (int i = 0; i < pathList.size(); i++) {
            Media item = getMediaItem(pathList.get(i));
            items.add(item);
        }
        return items;
    }

    private class GetMediaItemsRunnable implements Runnable {

        private final Stack<File> directories = new Stack<File>();
        private final HashSet<String> directoriesScanned = new HashSet<String>();
        private Context mContext;

        public GetMediaItemsRunnable(Context context) {
            mContext = context;
        }

        @Override
        public void run() {
            // Initialize variables
            final DatabaseManager DBManager = DatabaseManager.getInstance(VLCApplication.getAppContext());

            // show progressbar in footer
            MainActivity.showProgressBar(mContext);

            List<File> mediaDirs = DBManager.getMediaDirs();
            if (mediaDirs.size() == 0) {
                // Use all available storage directories as our default
                String storageDirs[] = Util.getStorageDirectories();
                for (String dir: storageDirs) {
                    File f = new File(dir);
                    if (f.exists())
                        mediaDirs.add(f);
                }
            }
            directories.addAll(mediaDirs);

            // get all existing media items
            HashMap<String, Media> existingMedias = DBManager.getMedias(mContext);

            // list of all added files
            HashSet<String> addedLocations = new HashSet<String>();

            // clear all old items
            mItemList.clear();

            MediaItemFilter mediaFileFilter = new MediaItemFilter();

            int count = 0;

            ArrayList<File> mediaToScan = new ArrayList<File>();
            try {
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
                        continue;
                    }

                    // Filter the extensions and the folders
                    try {
                        if ((f = dir.listFiles(mediaFileFilter)) != null) {
                            for (File file : f) {
                                if (file.isFile()) {
                                    mediaToScan.add(file);
                                } else if (file.isDirectory()) {
                                    directories.push(file);
                                }
                            }
                        }
                    } catch (Exception e)
                    {
                        // listFiles can fail in OutOfMemoryError, go to the next folder
                        continue;
                    }

                    if (isStopping) {
                        Log.d(TAG, "Stopping scan");
                        return;
                    }
                }

                // Process the stacked items
                for (File file : mediaToScan) {
                    String fileURI = Util.PathToURI(file.getPath());
                    MainActivity.sendTextInfo(mContext, file.getName(), count,
                            mediaToScan.size());
                    count++;
                    if (existingMedias.containsKey(fileURI)) {
                        /**
                         * only add file if it is not already in the list. eg. if
                         * user select an subfolder as well
                         */
                        if (!addedLocations.contains(fileURI)) {
                            // get existing media item from database
                            mItemList.add(existingMedias.get(fileURI));
                            addedLocations.add(fileURI);
                        }
                    } else {
                        // create new media item
                        mItemList.add(new Media(fileURI, true));
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
                    DBManager.removeMedias(existingMedias.keySet());

                    for (File file : DBManager.getMediaDirs())
                        if (!file.isDirectory())
                            DBManager.removeDir(file.getAbsolutePath());
                }

                // hide progressbar in footer
                MainActivity.clearTextInfo(mContext);
                MainActivity.hideProgressBar(mContext);

                VideoGridFragment.actionScanStop(mContext);

                if (mRestart) {
                    Log.d(TAG, "Restarting scan");
                    mRestart = false;
                    restartHandler.sendEmptyMessage(1);
                } else {
                    mRestartContext = null;
                    mContext = null;
                }
            }
        }
    };

    private Handler restartHandler = new Handler() {
        @Override
        public void handleMessage(final Message msgs) {
            restartHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mRestartContext != null)
                        loadMediaItems(mRestartContext);
                    else
                        Log.e(TAG, "Context lost in a black hole");
                }
            }, 200);
        }
    };

    /**
     * Filters all irrelevant files
     */
    private class MediaItemFilter implements FileFilter {

        @Override
        public boolean accept(File f) {
            boolean accepted = false;
            if (!f.isHidden()) {
                if (f.isDirectory() && !Media.FOLDER_BLACKLIST.contains(f.getPath().toLowerCase())) {
                    accepted = true;
                } else {
                    String fileName = f.getName().toLowerCase();
                    int dotIndex = fileName.lastIndexOf(".");
                    if (dotIndex != -1) {
                        String fileExt = fileName.substring(dotIndex);
                        accepted = Media.AUDIO_EXTENSIONS.contains(fileExt) ||
                                   Media.VIDEO_EXTENSIONS.contains(fileExt);
                    }
                }
            }
            return accepted;
        }
    }
}
