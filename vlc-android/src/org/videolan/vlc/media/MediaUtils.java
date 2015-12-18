package org.videolan.vlc.media;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;

import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;

import java.util.List;

public class MediaUtils {
    public static final String ACTION_SCAN_START = Strings.buildPkgString("gui.ScanStart");
    public static final String ACTION_SCAN_STOP = Strings.buildPkgString("gui.ScanStop");

    public static void actionScanStart() {
        Intent intent = new Intent();
        intent.setAction(ACTION_SCAN_START);
        LocalBroadcastManager.getInstance(VLCApplication.getAppContext()).sendBroadcast(intent);
    }

    public static void actionScanStop() {
        Intent intent = new Intent();
        intent.setAction(ACTION_SCAN_STOP);
        LocalBroadcastManager.getInstance(VLCApplication.getAppContext()).sendBroadcast(intent);
    }

    public static void appendMedia(final Context context, final MediaWrapper media){
        if (media == null)
            return;
        new DialogCallback(context, new DialogCallback.Runnable() {
                @Override
                public void run(PlaybackService service) {
                    service.append(media);
                }
        });
    }

    public static void openMedia(final Context context, final MediaWrapper media){
        if (media == null)
            return;
        new DialogCallback(context, new DialogCallback.Runnable() {
                @Override
                public void run(PlaybackService service) {
                    service.load(media);
                }
        });
    }

    public static void openMediaNoUi(final Context context, final MediaWrapper media){
        if (media == null)
            return;
        if (media.getType() == MediaWrapper.TYPE_VIDEO)
            VideoPlayerActivity.start(context, media.getUri(), media.getTitle());
        else
            new BaseCallBack(context) {
                @Override
                public void onConnected(PlaybackService service) {
                    service.load(media);
                }
            };
    }

    public static void openList(final Context context, final List<MediaWrapper> list, final int position){
        new DialogCallback(context, new DialogCallback.Runnable() {
            @Override
            public void run(PlaybackService service) {
                service.load(list, position);
            }
        });
    }

    public static void openUri(final Context context, final Uri uri){
        new DialogCallback(context, new DialogCallback.Runnable() {
            @Override
            public void run(PlaybackService service) {
                service.loadUri(uri);
            }
        });
    }

    public static void openStream(final Context context, final String uri){
        new DialogCallback(context, new DialogCallback.Runnable() {
            @Override
            public void run(PlaybackService service) {
                service.loadLocation(uri);
            }
        });
    }

    public static String getMediaArtist(Context ctx, MediaWrapper media) {
        final String artist = media.getArtist();
        return artist != null ? artist : getMediaString(ctx, R.string.unknown_artist);
    }

    public static String getMediaReferenceArtist(Context ctx, MediaWrapper media) {
        final String artist = media.getReferenceArtist();
        return artist != null ? artist : getMediaString(ctx, R.string.unknown_artist);
    }

    public static String getMediaAlbumArtist(Context ctx, MediaWrapper media) {
        final String albumArtist = media.getAlbumArtist();
        return albumArtist != null ? albumArtist : getMediaString(ctx, R.string.unknown_artist);
    }

    public static String getMediaAlbum(Context ctx, MediaWrapper media) {
        final String album = media.getAlbum();
        return album != null ? album : getMediaString(ctx, R.string.unknown_album);

    }

    public static String getMediaGenre(Context ctx, MediaWrapper media) {
        final String genre = media.getGenre();
        return genre != null ? genre : getMediaString(ctx, R.string.unknown_genre);
    }

    public static String getMediaSubtitle(Context ctx, MediaWrapper media) {
        if (media.getType() == MediaWrapper.TYPE_AUDIO)
            return media.getNowPlaying() != null
                    ? media.getNowPlaying()
                    : getMediaArtist(ctx, media) + " - " + getMediaAlbum(ctx, media);
        else
            return "";
    }

    public static String getMediaTitle(MediaWrapper mediaWrapper){
        String title = mediaWrapper.getTitle();
        if (title == null)
            title = FileUtils.getFileNameFromPath(mediaWrapper.getLocation());
        return title;
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
        private final ProgressDialog dialog;
        final private Runnable mRunnable;

        private interface Runnable {
            void run(PlaybackService service);
        }

        private DialogCallback(Context context, Runnable runnable) {
            mClient = new PlaybackService.Client(context, this);
            mRunnable = runnable;
            this.dialog = ProgressDialog.show(
                    context,
                    context.getApplicationContext().getString(R.string.loading) + "…",
                    context.getApplicationContext().getString(R.string.please_wait), true);
            dialog.setCancelable(true);
            dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    mClient.disconnect();
                }
            });
            mClient.connect();
        }

        @Override
        public void onConnected(PlaybackService service) {
            mRunnable.run(service);
            dialog.dismiss();
        }

        @Override
        public void onDisconnected() {
            dialog.dismiss();
        }
    }
}
