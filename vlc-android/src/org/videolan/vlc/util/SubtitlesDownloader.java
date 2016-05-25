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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaWrapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import de.timroes.axmlrpc.XMLRPCClient;
import de.timroes.axmlrpc.XMLRPCException;

public class SubtitlesDownloader {

    private static final int DIALOG_SHOW = 1;
    private static final int DIALOG_HIDE = 2;
    private static final int DIALOG_UPDATE_PROGRESS = 3;
    private static final int DIALOG_UPDATE_MSG = 4;
    private final String TAG = "VLC/SubtitlesDownloader";

    private final String OpenSubtitlesAPIUrl = "http://api.opensubtitles.org/xml-rpc";
    private final String HTTP_USER_AGENT = "VLSub";
    private final String USER_AGENT = "VLSub 0.9";

    private HashMap<String, Object> map = null;
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

    public void setActivity(Activity activity) {
        mContext = activity;
    }

    public void downloadSubs(final List<MediaWrapper> mediaList, Callback cb) {
        stop = false;
        mCallback = cb;
        Set<String> languages =  Collections.singleton(Locale.getDefault().getISO3Language().toLowerCase());
        if (AndroidUtil.isHoneycombOrLater()) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext());
            languages =  pref.getStringSet("languages_download_list", languages);
        }
        final ArrayList<String> finalLanguages = new ArrayList<>(languages);
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                FileUtils.SUBTITLES_DIRECTORY.mkdirs();
                if (logIn()){
                    getSubtitles(mediaList, finalLanguages);
                }
                mHandler.sendEmptyMessage(DIALOG_HIDE);
            }
        });
    }

    private void exit() {
        logOut();
    }

    @SuppressWarnings("unchecked")
    private boolean logIn() {
        mHandler.sendEmptyMessage(DIALOG_SHOW);
        try {
            mClient = new XMLRPCClient(new URL(OpenSubtitlesAPIUrl));
            map = ((HashMap<String, Object>) mClient.call("LogIn","","","fre",USER_AGENT));
            mToken = (String) map.get("token");
        } catch (XMLRPCException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "logIn", e);
            showSnackBar(R.string.service_unavailable);
            mHandler.sendEmptyMessage(DIALOG_HIDE);
            stop = true;
            return false;
        } catch (Throwable e){ //for various service outages
            if (BuildConfig.DEBUG) Log.e(TAG, "logIn", e);
            e.printStackTrace();
            showSnackBar(R.string.service_unavailable);
            mHandler.sendEmptyMessage(DIALOG_HIDE);
            stop = true;
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
            map = ((HashMap<String, Object>)  mClient.call("LogOut", mToken));
        } catch (Throwable e){ //for various service outages
            Log.w("subtitles", "XMLRPCException", e);
        }
        mToken = null;
        map = null;
    }

    @SuppressWarnings("unchecked")
    private void getSubtitles(final List<MediaWrapper> mediaList, List<String> languages) {
        mHandler.obtainMessage(DIALOG_UPDATE_MSG,R.string.downloading_subtitles, 0).sendToTarget();
        for (String language : languages)
            language = getCompliantLanguageID(language);
        final boolean single = mediaList.size() == 1;
        final ArrayList<MediaWrapper> notFoundFiles = new ArrayList<>();
        final HashMap<String, String> index = new HashMap<>();
        final HashMap<String, ArrayList<String>> success = new HashMap<>();
        final HashMap<String, ArrayList<String>> fails = new HashMap<>();
        ArrayList<HashMap<String, String>> videoSearchList = prepareRequestList(mediaList, languages, index, true);
        Object[] subtitleMaps;
        if (!videoSearchList.isEmpty()) {
            try {
                map = (HashMap<String, Object>) mClient.call("SearchSubtitles", mToken, videoSearchList);
            } catch (Throwable e) { //for various service outages
                stop = true;
                showSnackBar(R.string.service_unavailable);
                return;
            }
            if(!stop && map.get("data") instanceof Object[]) {
                subtitleMaps = ((Object[]) map.get("data"));
                if (!single)
                    mHandler.obtainMessage(DIALOG_UPDATE_PROGRESS, mediaList.size(), 0).sendToTarget();
                if (subtitleMaps.length > 0){
                    String srtFormat, movieHash, fileUrl, fileName, subLanguageID, subDownloadLink;
                    for (Object map : subtitleMaps){
                        if (stop)
                            break;
                        srtFormat = ((HashMap<String, String>) map).get("SubFormat");
                        movieHash = ((HashMap<String, String>) map).get("MovieHash");
                        subLanguageID = ((HashMap<String, String>) map).get("SubLanguageID");
                        subDownloadLink = ((HashMap<String, String>) map).get("SubDownloadLink");
                        fileUrl = index.get(movieHash);

                        fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
                        if (success.containsKey(fileName) && success.get(fileName).contains(subLanguageID)){
                            continue;
                        } else {
                            if (success.containsKey(fileName))
                                success.get(fileName).add(subLanguageID);
                            else {
                                ArrayList<String> newLanguage = new ArrayList<>();
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
            if (videoSearchList != null)
                videoSearchList.clear();
            index.clear();
        }

        //Second pass
        for (MediaWrapper media : mediaList){
            String fileName = media.getUri().getLastPathSegment();
            if (!success.containsKey(fileName))
                notFoundFiles.add(media);
        }
        if (!stop && !notFoundFiles.isEmpty() &&
                !(videoSearchList = prepareRequestList(notFoundFiles, languages, index, false)).isEmpty()) {
            try {
                map = (HashMap<String, Object>) mClient.call("SearchSubtitles", mToken,
                        videoSearchList);
            } catch (Throwable e) { //for various service outages
                stop = true;
                showSnackBar(R.string.service_unavailable);
                return;
            }
            if (map.get("data") instanceof Object[]) {
                subtitleMaps = ((Object[]) map.get("data"));
                if (subtitleMaps.length > 0) {
                    String srtFormat, fileUrl, fileName, subLanguageID, subDownloadLink;
                    HashMap<String, Object> resultMap;
                    Object[] paths = index.values().toArray();
                    for (int i = 0; i < subtitleMaps.length; ++i) {
                        if (stop)
                            break;
                        resultMap = (HashMap<String, Object>) subtitleMaps[i];
                        srtFormat = (String) resultMap.get("SubFormat");
                        Integer queryNumber = (Integer) resultMap.get("QueryNumber");
                        subLanguageID = (String) resultMap.get("SubLanguageID");
                        subDownloadLink = (String) resultMap.get("SubDownloadLink");
                        fileUrl = (String) paths[queryNumber.intValue()/languages.size()];
                        if (fileUrl == null) //we keep only result for exact matching name
                            continue;
                        fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
                        if (success.containsKey(fileName) && success.get(fileName).contains(subLanguageID)){
                            continue;
                        } else {
                            if (success.containsKey(fileName))
                                success.get(fileName).add(subLanguageID);
                            else {
                                ArrayList<String> newLanguage = new ArrayList<>();
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
        }
        //fill fails list
        for (MediaWrapper media : mediaList){
            String fileName;
            fileName = media.getUri().getLastPathSegment();
            if (!success.containsKey(fileName)){
                ArrayList<String> langs = new ArrayList<>();
                for (String language : languages) {
                    langs.add(getCompliantLanguageID(language));
                }
                fails.put(fileName, langs);
            } else {
                ArrayList<String> langs = new ArrayList<>();
                for (String language : languages) {
                    String langID = getCompliantLanguageID(language);
                    if (!success.get(fileName).contains(langID)){
                        langs.add(langID);
                    }
                }
                if (!langs.isEmpty())
                    fails.put(fileName, langs);
            }
        }
        if (mCallback != null)
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onRequestEnded(!success.isEmpty());
                }
            });
        if (!stop) {
            if (single){
                stop = true;
                showSnackBar(buildSumup(success, fails, true));
            } else {
                showSumup(buildSumup(success, fails, false));
            }
        }
        logOut();
    }

    private void downloadSubtitles(String subUrl, String path, String fileName, String subFormat, String language){
        if (mToken == null || path == null)
            return;

        StringBuilder sb = new StringBuilder();
        String name = fileName.lastIndexOf('.') > 0 ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        sb.append(FileUtils.SUBTITLES_DIRECTORY.getPath()).append('/').append(name).append('.').append(language).append('.').append(subFormat);
        String srtUrl = sb.toString();
        FileOutputStream f = null;
        InputStream in = null;
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
        } catch (IOException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "download fail", e);
        } catch (Throwable e) { //for various service outages
            if (BuildConfig.DEBUG) Log.e(TAG, "download fail", e);
        }finally{
            Util.close(f);
            Util.close(in);
            Util.close(gzIS);
        }
        return;
    }

    private ArrayList<HashMap<String, String>> prepareRequestList(List<MediaWrapper> mediaList, List<String> languages, HashMap<String, String> index, boolean firstPass) {
        ArrayList<HashMap<String, String>> videoSearchList = new ArrayList<>();
        for (MediaWrapper media : mediaList) {
            if (stop)
                break;
            String hash = null, tag = null;
            long fileLength = 0;
            Uri mediaUri = media.getUri();
            if (firstPass) {
                if (FileUtils.canWrite(mediaUri)) {
                    File videoFile = new File(mediaUri.getPath());
                    hash = FileUtils.computeHash(videoFile);
                    fileLength = videoFile.length();
                } //TODO network files
                if (hash == null)
                    continue;
            } else { //Second pass, search by TAG (filename)
                tag = mediaUri.getLastPathSegment();
            }
            HashMap<String, String> video;
            for (String item : languages){
                if (stop)
                    break;
                String languageID = getCompliantLanguageID(item);

                video = new HashMap<>();
                video.put("sublanguageid", languageID);
                if (firstPass){
                    index.put(hash, mediaUri.getPath());
                    video.put("moviehash", hash);
                    video.put("moviebytesize", String.valueOf(fileLength));
                } else {
                    video.put("tag", tag);
                    int dotPos = tag.lastIndexOf('.');
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
        if (mContext == null)
            return;
        mHandler.post(new Runnable(){
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
                try{
                    mSumUpDialog.show();
                } catch (IllegalArgumentException | WindowManager.BadTokenException e){
                    e.printStackTrace();
                }
            }
        });
    }

    private String buildSumup(HashMap<String, ArrayList<String>> success, HashMap<String, ArrayList<String>> fails, boolean single) {
        StringBuilder textToDisplay = new StringBuilder();
        if (single){ // Text for the toast
            if (!success.isEmpty()) {
                for (Entry<String, ArrayList<String>> entry : success.entrySet()) {
                    textToDisplay.append(VLCApplication.getAppResources().getString(R.string.snack_subloader_sub_found));
                    if (entry.getValue().size() > 1)
                        textToDisplay.append(" ").append(entry.getValue().toString()).append("\n");
                }
            }
            if (!fails.isEmpty()) {
                for (Entry<String, ArrayList<String>> entry : fails.entrySet()){
                    textToDisplay.append(VLCApplication.getAppResources().getString(R.string.snack_subloader_sub_not_found));
                    if (entry.getValue().size() > 1)
                        textToDisplay.append(" ").append(entry.getValue().toString()).append("\n");
                }
            }
        } else { // Text for the dialog box
            if (!success.isEmpty()){
                textToDisplay.append(VLCApplication.getAppResources().getString(R.string.dialog_subloader_success)).append("\n");

                for (Entry<String, ArrayList<String>> entry : success.entrySet()){
                    ArrayList<String> langs = entry.getValue();
                    String filename = entry.getKey();
                    textToDisplay.append("\n- ").append(filename).append(" ").append(langs.toString());
                }

                if (fails.size()>0)
                    textToDisplay.append("\n\n");
            }

            if (!fails.isEmpty()){
                textToDisplay.append(VLCApplication.getAppResources().getString(R.string.dialog_subloader_fails)).append("\n");

                for (Entry<String, ArrayList<String>> entry : fails.entrySet()){
                    ArrayList<String> langs = entry.getValue();
                    String filename = entry.getKey();
                    textToDisplay.append("\n- ").append(filename).append(" ").append(langs.toString());
                }
            }
        }
        return textToDisplay.toString();
    }

    // Locale ID Control, because of OpenSubtitle support of ISO639-2 codes
    // e.g. French ID can be 'fra' or 'fre', OpenSubtitles considers 'fre' but Android Java Locale provides 'fra'
    private String getCompliantLanguageID(String language){
        if (language.equals("system"))
            return getCompliantLanguageID(Locale.getDefault().getISO3Language());
        if (language.equals("fra"))
            return "fre";
        if (language.equals("deu"))
            return "ger";
        if (language.equals("zho"))
            return "chi";
        if (language.equals("ces"))
            return "cze";
        if (language.equals("fas"))
            return "per";
        if (language.equals("nld"))
            return "dut";
        if (language.equals("ron"))
            return "rum";
        if (language.equals("slk"))
            return "slo";
        return language;
    }

    private void showSnackBar(int stringId) {
        if (mContext == null)
            return;
        showSnackBar(VLCApplication.getAppResources().getString(stringId));
    }

    private void showSnackBar(final String text) {
        if (mContext == null)
            return;
        if (mContext instanceof AppCompatActivity && !(mContext instanceof VideoPlayerActivity)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    View v = mContext.findViewById(R.id.fragment_placeholder);
                    if (v == null)
                        v = mContext.getWindow().getDecorView();
                    UiTools.snacker(v, text);
                }
            });
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    Handler mHandler = new Handler(Looper.getMainLooper()) {
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
