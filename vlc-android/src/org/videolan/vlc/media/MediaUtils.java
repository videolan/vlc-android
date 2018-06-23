package org.videolan.vlc.media;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Playlist;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.SubtitlesDownloader;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.WorkersKt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MediaUtils {

    private static final String TAG = "VLC/MediaUtils";

    private static SubtitlesDownloader sSubtitlesDownloader;

    public static void getSubs(Activity activity, List<MediaWrapper> mediaList) {
        getSubs(activity, mediaList, null);
    }

    public static void getSubs(Activity activity, List<MediaWrapper> mediaList, SubtitlesDownloader.Callback cb) {
        if (sSubtitlesDownloader == null)
            sSubtitlesDownloader = new SubtitlesDownloader();
        sSubtitlesDownloader.downloadSubs(activity, mediaList, cb);
    }

    public static void loadlastPlaylist(final Context context, final int type){
        if (context == null) return;
        new DialogCallback(context, new DialogCallback.Runnable() {
                @Override
                public void run(PlaybackService service) {
                    service.loadLastPlaylist(type);
                }
        });
    }

    public static void getSubs(Activity activity, MediaWrapper media, SubtitlesDownloader.Callback cb) {
        final List<MediaWrapper> mediaList = new ArrayList<>();
        mediaList.add(media);
        getSubs(activity, mediaList, cb);
    }

    public static void getSubs(Activity activity, MediaWrapper media) {
        final List<MediaWrapper> mediaList = new ArrayList<>();
        mediaList.add(media);
        getSubs(activity, mediaList);
    }

    public static void appendMedia(final Context context, final List<MediaWrapper> media){
        if (media == null || context == null) return;
        new DialogCallback(context, new DialogCallback.Runnable() {
                @Override
                public void run(PlaybackService service) {
                    service.append(media);
                }
        });
    }

    public static void appendMedia(final Context context, final MediaWrapper media){
        if (media == null || context == null) return;
        new DialogCallback(context, new DialogCallback.Runnable() {
                @Override
                public void run(PlaybackService service) {
                    service.append(media);
                }
        });
    }

    public static void appendMedia(final Context context, final MediaWrapper[] array){
        appendMedia(context, Arrays.asList(array));
    }

    public static void insertNext(final Context context, final MediaWrapper[] media){
        if (media == null || context == null) return;
        new DialogCallback(context, new DialogCallback.Runnable() {
                @Override
                public void run(PlaybackService service) {
                    service.insertNext(media);
                }
        });
    }

    public static void insertNext(final Context context, final MediaWrapper media){
        if (media == null || context == null) return;
        new DialogCallback(context, new DialogCallback.Runnable() {
                @Override
                public void run(PlaybackService service) {
                    service.insertNext(media);
                }
        });
    }

    public static void openMedia(final Context context, final MediaWrapper media){
        if (media == null || context == null) return;
        new DialogCallback(context, new DialogCallback.Runnable() {
                @Override
                public void run(PlaybackService service) {
                    service.load(media);
                }
        });
    }

    public static void openMediaNoUi(Uri uri){
        final MediaWrapper media = new MediaWrapper(uri);
        openMediaNoUi(VLCApplication.getAppContext(), media);
    }

    public static void openMediaNoUi(final Context context, final MediaWrapper media){
        if (media == null || context == null) return;
        new BaseCallBack(context) {
            @Override
            public void onConnected(PlaybackService service) {
                service.load(media);
                mClient.disconnect();
            }
        };
    }

    public static void openArray(final Context context, final MediaWrapper[] array, final int position){
        openList(context, Arrays.asList(array), position);
    }

    public static void openList(final Context context, final List<MediaWrapper> list, final int position){
        openList(context, list, position, false);
    }

    public static void openList(final Context context, final List<MediaWrapper> list, final int position, final boolean shuffle){
        if (Util.isListEmpty(list) || context == null) return;
        new DialogCallback(context, new DialogCallback.Runnable() {
            @Override
            public void run(PlaybackService service) {
                service.load(list, position);
                if (shuffle && !service.isShuffling()) service.shuffle();
            }
        });
    }

    public static void openUri(final Context context, final Uri uri){
        if (uri == null || context == null) return;
        new DialogCallback(context, new DialogCallback.Runnable() {
            @Override
            public void run(PlaybackService service) {
                service.loadUri(uri);
            }
        });
    }

    public static void openStream(final Context context, final String uri){
        if (uri == null || context == null) return;
        new DialogCallback(context, new DialogCallback.Runnable() {
            @Override
            public void run(PlaybackService service) {
                service.loadLocation(uri);
            }
        });
    }

    public static String getMediaArtist(Context ctx, MediaWrapper media) {
        final String artist = media != null ? media.getArtist() : null;
        return artist != null ? artist : getMediaString(ctx, R.string.unknown_artist);
    }

    public static String getMediaReferenceArtist(Context ctx, MediaWrapper media) {
        final String artist = media != null ? media.getReferenceArtist() : null;
        return artist != null ? artist : getMediaString(ctx, R.string.unknown_artist);
    }

    public static String getMediaAlbumArtist(Context ctx, MediaWrapper media) {
        final String albumArtist = media != null ? media.getAlbumArtist() : null;
        return albumArtist != null ? albumArtist : getMediaString(ctx, R.string.unknown_artist);
    }

    public static String getMediaAlbum(Context ctx, MediaWrapper media) {
        final String album = media != null ? media.getAlbum() : null;
        return album != null ? album : getMediaString(ctx, R.string.unknown_album);

    }

    public static String getMediaGenre(Context ctx, MediaWrapper media) {
        final String genre = media != null ? media.getGenre() : null;
        return genre != null ? genre : getMediaString(ctx, R.string.unknown_genre);
    }

    public static String getMediaSubtitle(MediaWrapper media) {
        String subtitle = media.getNowPlaying() != null
                ? media.getNowPlaying()
                : media.getArtist();
        if (media.getLength() > 0L) {
            if (TextUtils.isEmpty(subtitle))
                subtitle = Tools.millisToString(media.getLength());
            else
                subtitle = subtitle + "  -  " +  Tools.millisToString(media.getLength());
        }
        return subtitle;
    }

    public static String getMediaTitle(MediaWrapper mediaWrapper){
        String title = mediaWrapper.getTitle();
        if (title == null)
            title = FileUtils.getFileNameFromPath(mediaWrapper.getLocation());
        return title;
    }

    public static Uri getContentMediaUri(Uri data) {
        Uri uri = null;
        try {
            Cursor cursor = VLCApplication.getAppContext().getContentResolver().query(data,
                    new String[]{ MediaStore.Video.Media.DATA }, null, null, null);
            if (cursor != null) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                if (cursor.moveToFirst())
                    uri = AndroidUtil.PathToUri(cursor.getString(column_index));
                cursor.close();
            } else // other content-based URI (probably file pickers)
                uri = data;
        } catch (Exception e) {
            uri = data;
            if (uri.getScheme() == null)
                uri = AndroidUtil.PathToUri(uri.getPath());
        }
        return uri != null ? uri : data;
    }
    private static String getMediaString(Context ctx, int id) {
        if (ctx != null)
            return ctx.getResources().getString(id);
        else {
            switch (id) {
                case R.string.unknown_artist:
                    return "Unknown Artist";
                case R.string.unknown_album:
                    return "Unknown Album";
                case R.string.unknown_genre:
                    return "Unknown Genre";
                default:
                    return "";
            }
        }
    }

    private static abstract class BaseCallBack implements PlaybackService.Client.Callback {
        protected PlaybackService.Client mClient;

        private BaseCallBack(Context context) {
            mClient = new PlaybackService.Client(context, this);
            mClient.connect();
        }

        protected BaseCallBack() {}

        @Override
        public void onDisconnected() {}
    }

    private static class DialogCallback extends BaseCallBack {
        private ProgressDialog dialog;
        private final Runnable mRunnable;
        private final Handler handler = new Handler(Looper.getMainLooper());

        private interface Runnable {
            void run(PlaybackService service);
        }

        private DialogCallback(final Context context, Runnable runnable) {
            mClient = new PlaybackService.Client(context, this);
            mRunnable = runnable;
            handler.postDelayed(new java.lang.Runnable() {
                @Override
                public void run() {
                    dialog = ProgressDialog.show(
                            context,
                            context.getApplicationContext().getString(R.string.loading) + "…",
                            context.getApplicationContext().getString(R.string.please_wait), true);
                    dialog.setCancelable(true);
                    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            synchronized (this) {
                                mClient.disconnect();
                            }
                        }
                    });
                }
            }, 300);
            synchronized (this) {
                mClient.connect();
            }
        }

        @Override
        public void onConnected(PlaybackService service) {
            synchronized (this) {
                mRunnable.run(service);
            }
            handler.removeCallbacksAndMessages(null);
            if (dialog != null) dialog.cancel();
        }

        @Override
        public void onDisconnected() {
            dialog.dismiss();
        }
    }

    public static void retrieveMediaTitle(MediaWrapper mw) {
        Cursor cursor = null;
        try {
            cursor = VLCApplication.getAppContext().getContentResolver().query(mw.getUri(), null, null, null, null);
            if (cursor == null) return;
            final int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex > -1 && cursor.getCount() > 0) {
                cursor.moveToFirst();
                if (!cursor.isNull(nameIndex)) mw.setTitle(cursor.getString(nameIndex));
            }
        } catch (SecurityException|IllegalArgumentException|UnsupportedOperationException e) { // We may not have storage access permission yet
            Log.w(TAG, "retrieveMediaTitle: fail to resolve file from "+mw.getUri(), e);
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
    }

    public static void deletePlaylist(final Playlist playlist) {
        WorkersKt.runBackground(new Runnable() {
            @Override
            public void run() {
                playlist.delete();
            }
        });
    }
}
