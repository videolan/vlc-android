/*
 * ************************************************************************
 *  SubtatlesDownloader.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */

package org.videolan.vlc.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.media.MediaDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import de.timroes.axmlrpc.XMLRPCClient;
import de.timroes.axmlrpc.XMLRPCException;

public class SubtitlesDownloader {

    private static final int DIALOG_SHOW = 1;
    private static final int DIALOG_HIDE = 2;
    private static final int DIALOG_UPDATE_PROGRESS = 3;
    private static final int DIALOG_UPDATE_MSG = 4;
    private static final String TAG = "VLC/SubtitlesDownloader";

    private static final String OpenSubtitlesAPIUrl = "http://api.opensubtitles.org/xml-rpc";
    private static final String HTTP_USER_AGENT = "VLSub";
    private static final String USER_AGENT = "VLSub 0.9";
    private static final File SUBTITLES_DIRECTORY = new File(VLCApplication.getAppContext().getExternalFilesDir(null), "subs");

    private Map<String, Object> map = null;
    private XMLRPCClient mClient;
    private String mToken = null;
    private Activity mContext;
    private ProgressDialog mDialog;
    private volatile boolean stop = false;

    private AlertDialog mSumUpDialog;
    private Callback mCallback;

    public interface Callback {
        void onRequestEnded(boolean success);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void downloadSubs(Activity context, final List<MediaWrapper> mediaList, Callback cb) {
        mContext = context;
        stop = false;
        mCallback = cb;
        Set<String> languages = null;
        try {
            languages = Collections.singleton(Locale.getDefault().getISO3Language().toLowerCase());
        } catch (MissingResourceException ignored) {}
        languages = VLCApplication.getSettings().getStringSet("languages_download_list", languages);
        if (languages == null) { // In case getDefault().getISO3Language() fails
            Toast.makeText(mContext, R.string.subs_dl_lang_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> finalLanguages = new ArrayList<>(languages);
        WorkersKt.runBackground(new Runnable() {
            @Override
            public void run() {
                SUBTITLES_DIRECTORY.mkdirs();
                startDownload(mediaList, finalLanguages);
                mHandler.sendEmptyMessage(DIALOG_HIDE);
            }
        });
    }

    @WorkerThread
    private void startDownload(List<MediaWrapper> mediaList, List<String> finalLanguages) {
        int loginCount = 3;
        mHandler.sendEmptyMessage(DIALOG_SHOW);
        while (--loginCount >= 0) {
            if (logIn()) {
                getSubtitles(mediaList, finalLanguages);
                return;
            }
        }
        showSnackBar(R.string.service_unavailable);
        mHandler.sendEmptyMessage(DIALOG_HIDE);
        stop = true;
    }

    private void exit() {
        logOut();
    }

    @SuppressWarnings("unchecked")
    @WorkerThread
    private boolean logIn() {
        try {
            mClient = new XMLRPCClient(new URL(OpenSubtitlesAPIUrl));
            map = ((Map<String, Object>) mClient.call("LogIn","","","fre",USER_AGENT));
            mToken = (String) map.get("token");
        } catch (XMLRPCException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "logIn", e);
            return false;
        } catch (Throwable e){ //for various service outages
            if (BuildConfig.DEBUG) Log.e(TAG, "logIn", e);
            e.printStackTrace();
            return false;
        }
        map = null;
        return true;
    }

    @SuppressWarnings("unchecked")
    private void logOut() {
        if (mToken == null)
            return;
        try {
            map = ((Map<String, Object>)  mClient.call("LogOut", mToken));
        } catch (Throwable e){ //for various service outages
            Log.w("subtitles", "XMLRPCException", e);
        }
        mToken = null;
        map = null;
    }

    @SuppressWarnings("unchecked")
    private void getSubtitles(final List<MediaWrapper> mediaList, List<String> languages) {
        mHandler.obtainMessage(DIALOG_UPDATE_MSG,R.string.downloading_subtitles, 0).sendToTarget();
        final ListIterator<String> iter = languages.listIterator();
        while (iter.hasNext()) {
            final String language = iter.next();
            final String compliant = getCompliantLanguageID(language);
            if (!language.equals(compliant)) iter.set(compliant);
        }
        final boolean single = mediaList.size() == 1;
        final List<MediaWrapper> notFoundFiles = new ArrayList<>();
        final Map<String, String> index = new HashMap<>();
        final Map<String, List<String>> success = new HashMap<>();
        final Map<String, List<String>> fails = new HashMap<>();
        List<Map<String, String>> videoSearchList = prepareRequestList(mediaList, languages, index, true);
        if (!videoSearchList.isEmpty()) {
            int retryCount = 3;
            while (--retryCount >= 0) {
                try {
                    map = (Map<String, Object>) mClient.call("SearchSubtitles", mToken, videoSearchList);
                } catch (Throwable e) { //for various service outages
                    map = null;
                }
            }
            if (map == null) {
                stop = true;
                showSnackBar(R.string.service_unavailable);
                return;
            }
            if(!stop && map.get("data") instanceof Object[]) {
                final Object[] subtitleMaps = ((Object[]) map.get("data"));
                if (!single) mHandler.obtainMessage(DIALOG_UPDATE_PROGRESS, mediaList.size(), 0).sendToTarget();
                if (subtitleMaps.length > 0){
                    for (Object map : subtitleMaps){
                        if (stop) break;
                        final String srtFormat = ((Map<String, String>) map).get("SubFormat");
                        final String movieHash = ((Map<String, String>) map).get("MovieHash");
                        final String subLanguageID = ((Map<String, String>) map).get("SubLanguageID");
                        final String subDownloadLink = ((Map<String, String>) map).get("SubDownloadLink");
                        final String fileUrl = index.get(movieHash);
                        if (fileUrl == null) return;

                        final String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
                        if (success.containsKey(fileName) && success.get(fileName).contains(subLanguageID)) {
                            continue;
                        } else {
                            if (success.containsKey(fileName))
                                success.get(fileName).add(subLanguageID);
                            else {
                                List<String> newLanguage = new ArrayList<>();
                                newLanguage.add(subLanguageID);
                                success.put(fileName, newLanguage);
                            }
                        }
                        downloadSubtitles(subDownloadLink, fileUrl, fileName, srtFormat, subLanguageID);
                        if (!single)
                            mHandler.obtainMessage(DIALOG_UPDATE_PROGRESS, mediaList.size(), success.size()).sendToTarget();
                    }
                }
            }
            videoSearchList.clear();
            index.clear();
        }

        //Second pass
        for (MediaWrapper media : mediaList) {
            if (media == null) return;
            final String fileName = media.getUri().getLastPathSegment();
            if (!success.containsKey(fileName)) notFoundFiles.add(media);
        }
        if (!stop && !notFoundFiles.isEmpty() &&
                !(videoSearchList = prepareRequestList(notFoundFiles, languages, index, false)).isEmpty()) {
            try {
                map = (Map<String, Object>) mClient.call("SearchSubtitles", mToken,
                        videoSearchList);
            } catch (Throwable e) { //for various service outages
                stop = true;
                showSnackBar(R.string.service_unavailable);
                return;
            }
            if (map.get("data") instanceof Object[]) {
                final Object[] subtitleMaps = ((Object[]) map.get("data"));
                if (subtitleMaps.length > 0) {
                    final Object[] paths = index.values().toArray();
                    for (Object subtitleMap : subtitleMaps) {
                        if (stop) break;
                        final Map<String, Object> resultMap = (Map<String, Object>) subtitleMap;
                        final String srtFormat = (String) resultMap.get("SubFormat");
                        final String queryNumber = (String) resultMap.get("QueryNumber");
                        final String subLanguageID = (String) resultMap.get("SubLanguageID");
                        final String subDownloadLink = (String) resultMap.get("SubDownloadLink");
                        final String fileUrl = (String) paths[Integer.getInteger(queryNumber, 0) / languages.size()];
                        if (fileUrl == null) //we keep only result for exact matching name
                            continue;
                        final String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
                        if (success.containsKey(fileName) && success.get(fileName).contains(subLanguageID)) {
                            continue;
                        } else {
                            if (success.containsKey(fileName))
                                success.get(fileName).add(subLanguageID);
                            else {
                                final List<String> newLanguage = new ArrayList<>();
                                newLanguage.add(subLanguageID);
                                success.put(fileName, newLanguage);
                            }
                        }
                        downloadSubtitles(subDownloadLink, fileUrl, fileName, srtFormat, subLanguageID);
                        if (!single) mHandler.obtainMessage(DIALOG_UPDATE_PROGRESS, mediaList.size(), success.size()).sendToTarget();
                    }
                }
            }
        }
        //fill fails list
        for (MediaWrapper media : mediaList) {
            if (media == null) continue;
            final String fileName = media.getUri().getLastPathSegment();
            if (!success.containsKey(fileName)) {
                final List<String> langs = new ArrayList<>();
                for (String language : languages)
                    langs.add(language);
                fails.put(fileName, langs);
            } else {
                final List<String> langs = new ArrayList<>();
                for (String language : languages) {
                    if (!success.get(fileName).contains(language))
                        langs.add(language);
                }
                if (!langs.isEmpty())
                    fails.put(fileName, langs);
            }
        }
        if (mCallback != null)
            WorkersKt.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onRequestEnded(!success.isEmpty());
                }
            });
        if (!stop) {
            if (single){
                stop = true;
                showSnackBar(buildSumup(success, fails, true));
            } else
                showSumup(buildSumup(success, fails, false));
        }
        logOut();
    }

    private void downloadSubtitles(String subUrl, String path, String fileName, String subFormat, String language){
        if (mToken == null || path == null) return;
        final StringBuilder sb = new StringBuilder();
        final String name = fileName.lastIndexOf('.') > 0 ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        sb.append(SUBTITLES_DIRECTORY.getPath()).append('/').append(name).append('.').append(language).append('.').append(subFormat);
        final String srtUrl = sb.toString();
        FileOutputStream f = null;
        GZIPInputStream gzIS = null;
        URL url;
        HttpURLConnection urlConnection;
        try {
            url  = new URL(subUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            // We get the first matching subtitle
            f = new FileOutputStream(new File(srtUrl));
            //Base64 then gunzip uncompression
            gzIS = new GZIPInputStream(urlConnection.getInputStream());
            int length;
            byte[] buffer = new byte[1024];
            while ((length = gzIS.read(buffer)) != -1) {
                f.write(buffer, 0, length);
            }
            MediaDatabase.getInstance().saveSubtitle(srtUrl, fileName);
        } catch (Throwable e) { //for various service outages
            if (BuildConfig.DEBUG) Log.e(TAG, "download fail", e);
        } finally {
            Util.close(f);
            Util.close(gzIS);
        }
    }

    private List<Map<String, String>> prepareRequestList(List<MediaWrapper> mediaList, List<String> languages, Map<String, String> index, boolean firstPass) {
        final List<Map<String, String>> videoSearchList = new ArrayList<>();
        for (MediaWrapper media : mediaList) {
            if (stop) break;
            if (media == null) continue;
            String hash = null, tag = null;
            long fileLength = 0;
            final Uri mediaUri = media.getUri();
            if (firstPass) {
                if ("file".equals(mediaUri.getScheme())) {
                    File videoFile = new File(mediaUri.getPath());
                    hash = FileUtils.computeHash(videoFile);
                    fileLength = videoFile.length();
                } //TODO network files
                if (hash == null) continue;
            } else { //Second pass, search by TAG (filename)
                tag = mediaUri.getLastPathSegment();
            }
            for (String language : languages){
                if (stop) break;
                final Map<String, String> video = new HashMap<>();
                video.put("sublanguageid", language);
                if (firstPass) {
                    index.put(hash, mediaUri.getPath());
                    video.put("moviehash", hash);
                    video.put("moviebytesize", String.valueOf(fileLength));
                } else if (!TextUtils.isEmpty(tag)) {
                    video.put("tag", tag);
                    final int dotPos = tag.lastIndexOf('.');
                    if (dotPos > -1)
                        index.put(tag.substring(0, dotPos), mediaUri.getPath());
                    else
                        index.put(tag, mediaUri.getPath());
                }
                videoSearchList.add(video);
            }
        }
        return videoSearchList;
    }

    private void showSumup(final String displayText) {
        if (mContext == null) return;
        WorkersKt.runOnMainThread(new Runnable(){
            public void run() {
                mSumUpDialog = new AlertDialog.Builder(mContext).setTitle(R.string.dialog_subloader_sumup)
                        .setMessage(displayText)
                        .setCancelable(true)
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            public void onCancel(DialogInterface dialog) {
                                exit();
                            }
                        })
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                exit();
                            }
                        }).create();
                //We catch these exceptions because context might disappear while loading/showing the dialog, no matter if we wipe it in onPause()
                try {
                    mSumUpDialog.show();
                } catch (IllegalArgumentException | WindowManager.BadTokenException e){
                    e.printStackTrace();
                }
            }
        });
    }

    private String buildSumup(Map<String, List<String>> success, Map<String, List<String>> fails, boolean single) {
        final StringBuilder textToDisplay = new StringBuilder();
        if (single) { // Text for the toast
            if (!success.isEmpty()) {
                for (Entry<String, List<String>> entry : success.entrySet()) {
                    textToDisplay.append(VLCApplication.getAppResources().getString(R.string.snack_subloader_sub_found));
                    if (entry.getValue().size() > 1)
                        textToDisplay.append(" ").append(entry.getValue().toString()).append("\n");
                }
            } else if (!fails.isEmpty()) {
                for (Entry<String, List<String>> entry : fails.entrySet()){
                    textToDisplay.append(VLCApplication.getAppResources().getString(R.string.snack_subloader_sub_not_found));
                    if (entry.getValue().size() > 1)
                        textToDisplay.append(" ").append(entry.getValue().toString()).append("\n");
                }
            }
        } else { // Text for the dialog box
            if (!success.isEmpty()) {
                textToDisplay.append(VLCApplication.getAppResources().getString(R.string.dialog_subloader_success)).append("\n");
                for (Entry<String, List<String>> entry : success.entrySet()){
                    final List<String> langs = entry.getValue();
                    final String filename = entry.getKey();
                    textToDisplay.append("\n- ").append(filename).append(" ").append(langs.toString());
                }
            } else if (!fails.isEmpty()) {
                textToDisplay.append(VLCApplication.getAppResources().getString(R.string.dialog_subloader_fails)).append("\n");
                for (Entry<String, List<String>> entry : fails.entrySet()){
                    final List<String> langs = entry.getValue();
                    final String filename = entry.getKey();
                    textToDisplay.append("\n- ").append(filename).append(" ").append(langs.toString());
                }
            }
        }
        return textToDisplay.toString();
    }

    // Locale ID Control, because of OpenSubtitle support of ISO639-2 codes
    // e.g. French ID can be 'fra' or 'fre', OpenSubtitles considers 'fre' but Android Java Locale provides 'fra'
    private String getCompliantLanguageID(String language){
        if (language.equals("system")) return getCompliantLanguageID(Locale.getDefault().getISO3Language());
        if (language.equals("fra")) return "fre";
        if (language.equals("deu")) return "ger";
        if (language.equals("zho")) return "chi";
        if (language.equals("ces")) return "cze";
        if (language.equals("fas")) return "per";
        if (language.equals("nld")) return "dut";
        if (language.equals("ron")) return "rum";
        if (language.equals("slk")) return "slo";
        return language;
    }

    private void showSnackBar(int stringId) {
        showSnackBar(VLCApplication.getAppResources().getString(stringId));
    }

    private void showSnackBar(final String text) {
        if (mContext instanceof AppCompatActivity && !(mContext instanceof VideoPlayerActivity) && !mContext.isFinishing()) {
            WorkersKt.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    View v = mContext.findViewById(R.id.fragment_placeholder);
                    if (v == null)
                        v = mContext.getWindow().getDecorView();
                    UiTools.snacker(v, text);
                }
            });
        } else {
            WorkersKt.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(VLCApplication.getAppContext(), text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (mContext == null || mContext.isFinishing())
                return;
            if (mContext instanceof VideoPlayerActivity) {
                if (msg.what == DIALOG_SHOW)
                    showSnackBar(R.string.downloading_subtitles);
                return;
            }
            switch (msg.what) {
                case DIALOG_SHOW:
                    mDialog = ProgressDialog.show(mContext, mContext.getString(R.string.subtitles_download_title), mContext.getString(R.string.connecting), true);
                    mDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            stop = true;
                        }
                    });
                    break;
                case DIALOG_HIDE:
                    if (mDialog != null && mDialog.isShowing())
                        try {
                            mDialog.dismiss();
                        } catch (IllegalArgumentException e) {
                            //Activity is lost, we remove references to prevent leaks
                            mDialog = null;
                            mContext = null;
                        }
                    break;
                case DIALOG_UPDATE_PROGRESS:
                    if (mDialog != null && mDialog.isShowing()) {
                        try {
                            mDialog.setIndeterminate(false);
                            mDialog.setMax(msg.arg1);
                            mDialog.setProgress(msg.arg2);
                        } catch (IllegalArgumentException e) {
                            //Activity is lost, we remove references to prevent leaks
                            mDialog = null;
                            mContext = null;
                        }
                    }
                    break;
                case DIALOG_UPDATE_MSG:
                    if (mDialog != null && mDialog.isShowing()) {
                        mDialog.setMessage(VLCApplication.getAppResources().getString(msg.arg1));
                    }
                    break;
            }
        }
    };
}
