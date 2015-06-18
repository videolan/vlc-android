/*****************************************************************************
 * PlaybackServiceClient.java
 *****************************************************************************
 * Copyright © 2011-2015 VLC authors and VideoLAN
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.MainThread;
import android.util.Log;

import org.videolan.vlc.interfaces.IPlaybackService;
import org.videolan.vlc.interfaces.IPlaybackServiceCallback;

import java.util.ArrayList;
import java.util.List;

public class PlaybackServiceClient implements ServiceConnection {
    public static final String TAG = "PlaybackServiceClient";

    @MainThread
    public interface Callback {
        void onConnected();
        void onDisconnected();
        void update();
        void updateProgress();
        void onMediaPlayedAdded(MediaWrapper media, int index);
        void onMediaPlayedRemoved(int index);
    }

    @MainThread
    public interface ResultCallback<T> {
        void onResult(PlaybackServiceClient client, T result);
        void onError(PlaybackServiceClient client);
    }

    private boolean mBound = false;
    private IPlaybackService mIService = null;
    private final Callback mCallback;
    private final Context mContext;

    private final IPlaybackServiceCallback mICallback = new IPlaybackServiceCallback.Stub() {
        @Override
        public void update() throws RemoteException {
            mCallback.update();
        }

        @Override
        public void updateProgress() throws RemoteException {
            mCallback.updateProgress();
        }

        @Override
        public void onMediaPlayedAdded(MediaWrapper media, int index) throws RemoteException {
            mCallback.onMediaPlayedAdded(media, index);
        }

        @Override
        public void onMediaPlayedRemoved(int index) throws RemoteException {
            mCallback.onMediaPlayedRemoved(index);
        }
    };

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "Service Disconnected");
        onDisconnected(false);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "Service Connected");
        if (!mBound)
            return;
        mIService = IPlaybackService.Stub.asInterface(service);

        if (mCallback != null) {
            try {
                mIService.addAudioCallback(mICallback);
                mCallback.onConnected();
                mCallback.update();
            } catch (RemoteException e) {
                Log.e(TAG, "remote procedure send failed: addAudioCallback()");
                onDisconnected(true);
            }
        }
    }

    private static Intent getServiceIntent(Context context) {
        return new Intent(context, PlaybackService.class);
    }

    private static void startService(Context context) {
        context.startService(getServiceIntent(context));
    }

    private static void stopService(Context context) {
        context.stopService(getServiceIntent(context));
    }

    public PlaybackServiceClient(Context context, Callback callback) {
        if (context == null)
            throw new IllegalArgumentException("Context can't be null");
        mContext = context;
        mCallback = callback;
    }

    @MainThread
    public void connect() {
        if (mBound)
            throw new IllegalStateException("already connected");
        startService(mContext);
        mBound = mContext.bindService(getServiceIntent(mContext), this, Context.BIND_AUTO_CREATE);
    }

    @MainThread
    public void disconnect() {
        if (mBound) {
            try {
                if (mIService != null && mCallback != null)
                    mIService.removeAudioCallback(mICallback);
            } catch (RemoteException e) {
                Log.e(TAG, "remote procedure send failed: removeAudioCallback()");
            }
            mIService = null;
            mBound = false;
            mContext.unbindService(this);
        }
    }

    private void onDisconnected(boolean error) {
        if (error && mBound && mCallback != null)
            mCallback.onDisconnected();
        disconnect();

        if (error)
            stopService(mContext);
        else
            connect();
    }

    @MainThread
    public void restartService() {
        disconnect();
        stopService(mContext);
        startService(mContext);
        connect();
    }

    public static void restartService(Context context) {
        stopService(context);
        startService(context);
    }

    @MainThread
    public boolean isConnected() {
        return mBound && mIService != null;
    }

    private static abstract class Command<T> {
        public Command() {
        }

        protected abstract T run(IPlaybackService iService) throws RemoteException;

        public T send(IPlaybackService iService, T defaultValue) {
            if (iService == null)
                throw new IllegalStateException("can't send remote methods without being connected");
            try {
                return run(iService);
            } catch (RemoteException e) {
                Log.e(TAG, "remote send failed", e);
                return defaultValue;
            }
        }

        @MainThread
        public T send(IPlaybackService iService) {
            return send(iService, null);
        }

        public void sendAsync(Context context, final ResultCallback<T> asyncCb) {
            class Holder {
                PlaybackServiceClient client;
            }

            final Holder holder = new Holder();
            holder.client = new PlaybackServiceClient(context, new PlaybackServiceClient.Callback() {

                @Override
                public void onConnected() {
                    try {
                        final T result = run(holder.client.mIService);
                        if (asyncCb != null)
                            asyncCb.onResult(holder.client, result);
                    } catch (RemoteException e) {
                        if (asyncCb != null)
                            asyncCb.onError(holder.client);
                    }
                    holder.client.disconnect();
                    holder.client = null;
                }

                @Override
                public void onDisconnected() {
                    if (asyncCb != null)
                        asyncCb.onError(holder.client);
                    holder.client.disconnect();
                    holder.client = null;
                }

                @Override
                public void update() {}
                @Override
                public void updateProgress() {}
                @Override
                public void onMediaPlayedAdded(MediaWrapper media, int index) {}
                @Override
                public void onMediaPlayedRemoved(int index) {}
            });
            holder.client.connect();
        }
    }

    private static class LoadCmd extends Command<Void> {
        final List<MediaWrapper> mediaList; final int position; final boolean forceAudio;

        private LoadCmd(List<MediaWrapper> mediaList, int position, boolean forceAudio) {
            this.mediaList = mediaList;
            this.position = position;
            this.forceAudio = forceAudio;
        }

        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.load(mediaList, position, forceAudio); return null;
        }
    }

    private static class LoadLocationsCmd extends Command<Void> {
        final List<String> mediaPathList; final int position;

        private LoadLocationsCmd(List<String> mediaPathList, int position) {
            this.mediaPathList = mediaPathList;
            this.position = position;
        }

        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.loadLocations(mediaPathList, position); return null;
        }
    }

    private static class AppendCmd extends Command<Void> {
        final List<MediaWrapper> mediaList;

        private AppendCmd(List<MediaWrapper> mediaList) {
            this.mediaList = mediaList;
        }

        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.append(mediaList); return null;
        }
    }

    private static class MoveItemCmd extends Command<Void> {
        final int positionStart, positionEnd;

        private MoveItemCmd(int positionStart, int positionEnd) {
            this.positionStart = positionStart;
            this.positionEnd = positionEnd;
        }

        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.moveItem(positionStart, positionEnd); return null;
        }
    }

    private static class RemoveCmd extends Command<Void> {
        final int position;

        private RemoveCmd(int position) {
            this.position = position;
        }

        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.remove(position); return null;
        }
    }

    private static class RemoveLocationCmd extends Command<Void> {
        final String location;

        private RemoveLocationCmd(String location) {
            this.location = location;
        }

        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.removeLocation(location); return null;
        }
    }

    private static class GetMediasCmd extends Command<List<MediaWrapper>> {
        @Override
        protected List<MediaWrapper> run(IPlaybackService iService) throws RemoteException {
            return iService.getMedias();
        }
    }

    private static class GetMediaLocationsCmd extends Command<List<String>> {
        @Override
        protected List<String> run(IPlaybackService iService) throws RemoteException {
            return iService.getMediaLocations();
        }
    }

    private static class GetCurrentMediaLocationCmd extends Command<String> {
        @Override
        protected String run(IPlaybackService iService) throws RemoteException {
            return iService.getCurrentMediaLocation();
        }
    }

    private static class GetCurrentMediaWrapperCmd extends Command<MediaWrapper> {
        @Override
        protected MediaWrapper run(IPlaybackService iService) throws RemoteException {
            return iService.getCurrentMediaWrapper();
        }
    }

    private static class StopCmd extends Command<Void> {
        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.stop(); return null;
        }
    }

    private static class ShowWithoutParseCmd extends Command<Void> {
        final int index;

        private ShowWithoutParseCmd(int index) {
            this.index = index;
        }

        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.showWithoutParse(index); return null;
        }
    }

    private static class PlayIndexCmd extends Command<Void> {
        final int index;

        private PlayIndexCmd(int index) {
            this.index = index;
        }

        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.playIndex(index); return null;
        }
    }

    private static class GetAlbumCmd extends Command<String> {
        @Override
        protected String run(IPlaybackService iService) throws RemoteException {
            return iService.getAlbum();
        }
    }

    private static class GetArtistCmd extends Command<String> {
        @Override
        protected String run(IPlaybackService iService) throws RemoteException {
            return iService.getArtist();
        }
    }

    private static class GetArtistPrevCmd extends Command<String> {
        @Override
        protected String run(IPlaybackService iService) throws RemoteException {
            return iService.getArtistPrev();
        }
    }

    private static class GetArtistNextCmd extends Command<String> {
        @Override
        protected String run(IPlaybackService iService) throws RemoteException {
            return iService.getArtistNext();
        }
    }

    private static class GetTitleCmd extends Command<String> {
        @Override
        protected String run(IPlaybackService iService) throws RemoteException {
            return iService.getTitle();
        }
    }

    private static class GetTitlePrevCmd extends Command<String> {
        @Override
        protected String run(IPlaybackService iService) throws RemoteException {
            return iService.getTitlePrev();
        }
    }

    private static class GetTitleNextCmd extends Command<String> {
        @Override
        protected String run(IPlaybackService iService) throws RemoteException {
            return iService.getTitleNext();
        }
    }

    private static class IsPlayingCmd extends Command<Boolean> {
        @Override
        protected Boolean run(IPlaybackService iService) throws RemoteException {
            return iService.isPlaying();
        }
    }

    private static class PauseCmd extends Command<Void> {
        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.pause(); return null;
        }
    }

    private static class PlayCmd extends Command<Void> {
        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.play(); return null;
        }
    }

    private static class HasMediasCmd extends Command<Boolean> {
        @Override
        protected Boolean run(IPlaybackService iService) throws RemoteException {
            return iService.hasMedia();
        }
    }

    private static class GetLengthCmd extends Command<Integer> {
        @Override
        protected Integer run(IPlaybackService iService) throws RemoteException {
            return iService.getLength();
        }
    }

    private static class GetTimeCmd extends Command<Integer> {
        @Override
        protected Integer run(IPlaybackService iService) throws RemoteException {
            return iService.getTime();
        }
    }

    private static class GetCoverCmd extends Command<Bitmap> {
        @Override
        protected Bitmap run(IPlaybackService iService) throws RemoteException {
            return iService.getCover();
        }
    }

    private static class GetCoverPrevCmd extends Command<Bitmap> {
        @Override
        protected Bitmap run(IPlaybackService iService) throws RemoteException {
            return iService.getCoverPrev();
        }
    }

    private static class GetCoverNextCmd extends Command<Bitmap> {
        @Override
        protected Bitmap run(IPlaybackService iService) throws RemoteException {
            return iService.getCoverNext();
        }
    }

    private static class NextCmd extends Command<Void> {
        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.next(); return null;
        }
    }

    private static class PreviousCmd extends Command<Void> {
        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.previous(); return null;
        }
    }

    private static class SetTimeCmd extends Command<Void> {
        final long time;

        private SetTimeCmd(long time) {
            this.time = time;
        }

        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.setTime(time); return null;
        }
    }

    private static class HasNextCmd extends Command<Boolean> {
        @Override
        protected Boolean run(IPlaybackService iService) throws RemoteException {
            return iService.hasNext();
        }
    }

    private static class HasPreviousCmd extends Command<Boolean> {
        @Override
        protected Boolean run(IPlaybackService iService) throws RemoteException {
            return iService.hasPrevious();
        }
    }

    private static class ShuffleCmd extends Command<Void> {
        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.shuffle(); return null;
        }
    }

    private static class IsShufflingCmd extends Command<Boolean> {
        @Override
        protected Boolean run(IPlaybackService iService) throws RemoteException {
            return iService.isShuffling();
        }
    }

    private static class SetRepeatTypeCmd extends Command<Void> {
        final PlaybackService.RepeatType type;

        private SetRepeatTypeCmd(PlaybackService.RepeatType type) {
            this.type = type;
        }

        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.setRepeatType(type.ordinal()); return null;
        }
    }

    private static class GetRepeatTypeCmd extends Command<PlaybackService.RepeatType> {
        @Override
        protected PlaybackService.RepeatType run(IPlaybackService iService) throws RemoteException {
            return PlaybackService.RepeatType.values()[iService.getRepeatType()];
        }
    }

    private static class DetectHeadsetCmd extends Command<Void> {
        final boolean enable;

        private DetectHeadsetCmd(boolean enable) {
            this.enable = enable;
        }

        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.detectHeadset(enable); return null;
        }
    }

    private static class GetRateCmd extends Command<Float> {
        @Override
        protected Float run(IPlaybackService iService) throws RemoteException {
            return iService.getRate();
        }
    }

    private static class HandleVout extends Command<Void> {
        @Override
        protected Void run(IPlaybackService iService) throws RemoteException {
            iService.handleVout(); return null;
        }
    }

    public void load(List<MediaWrapper> mediaList, int position) {
        load(mediaList, position, false);
    }
    public void load(List<MediaWrapper> mediaList, int position, boolean forceAudio) {
        new LoadCmd(mediaList, position, forceAudio).send(mIService);
    }
    public void load(MediaWrapper media, boolean forceAudio) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        load(arrayList, 0, forceAudio);
    }
    public void load(MediaWrapper media) {
        load(media, false);
    }
    public void loadLocation(String mediaPath) {
        ArrayList <String> arrayList = new ArrayList<String>();
        arrayList.add(mediaPath);
        loadLocations(arrayList, 0);
    }
    public void loadLocations(List<String> mediaPathList, int position) {
        new LoadLocationsCmd(mediaPathList, position).send(mIService);
    }
    public void append(MediaWrapper media) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        append(arrayList);
    }
    public void append(List<MediaWrapper> mediaList) {
        new AppendCmd(mediaList).send(mIService);
    }
    public void moveItem(int positionStart, int positionEnd) {
        new MoveItemCmd(positionStart, positionEnd).send(mIService);
    }
    public void remove(int position) {
        new RemoveCmd(position).send(mIService);
    }
    public void removeLocation(String location) {
        new RemoveLocationCmd(location).send(mIService);
    }
    public List<MediaWrapper> getMedias() {
        return new GetMediasCmd().send(mIService);
    }
    public List<String> getMediaLocations() {
        return new GetMediaLocationsCmd().send(mIService);
    }
    public String getCurrentMediaLocation() {
        return new GetCurrentMediaLocationCmd().send(mIService);
    }
    public MediaWrapper getCurrentMediaWrapper() {
        return new GetCurrentMediaWrapperCmd().send(mIService);
    }
    public void stop() {
        new StopCmd().send(mIService);
    }
    public void showWithoutParse(int u) {
        new ShowWithoutParseCmd(u).send(mIService);
    }
    public void playIndex(int i) {
        new PlayIndexCmd(i).send(mIService);
    }
    public String getAlbum() {
        return new GetAlbumCmd().send(mIService);
    }
    public String getArtist() {
        return new GetArtistCmd().send(mIService);
    }
    public String getArtistPrev() {
        return new GetArtistPrevCmd().send(mIService);
    }
    public String getArtistNext() {
        return new GetArtistNextCmd().send(mIService);
    }
    public String getTitle() {
        return new GetTitleCmd().send(mIService);
    }
    public String getTitlePrev() {
        return new GetTitlePrevCmd().send(mIService);
    }
    public String getTitleNext() {
        return new GetTitleNextCmd().send(mIService);
    }
    public boolean isPlaying() {
        return new IsPlayingCmd().send(mIService, false);
    }
    public void pause() {
        new PauseCmd().send(mIService);
    }
    public void play() {
        new PlayCmd().send(mIService);
    }
    public boolean hasMedia() {
        return new HasMediasCmd().send(mIService, false);
    }
    public int getLength() {
        return new GetLengthCmd().send(mIService, 0);
    }
    public int getTime() {
        return new GetTimeCmd().send(mIService, 0);
    }
    public Bitmap getCover() {
        return new GetCoverCmd().send(mIService);
    }
    public Bitmap getCoverPrev() {
        return new GetCoverPrevCmd().send(mIService);
    }
    public Bitmap getCoverNext() {
        return new GetCoverNextCmd().send(mIService);
    }
    public void next() {
        new NextCmd().send(mIService);
    }
    public void previous() {
        new PreviousCmd().send(mIService);
    }
    public void setTime(long time) {
        new SetTimeCmd(time).send(mIService);
    }
    public boolean hasNext() {
        return new HasNextCmd().send(mIService, false);
    }
    public boolean hasPrevious() {
        return new HasPreviousCmd().send(mIService, false);
    }
    public void shuffle() {
        new ShuffleCmd().send(mIService);
    }
    public boolean isShuffling() {
        return new IsShufflingCmd().send(mIService, false);
    }
    public void setRepeatType(PlaybackService.RepeatType t) {
        new SetRepeatTypeCmd(t).send(mIService);
    }
    public PlaybackService.RepeatType getRepeatType() {
        return new GetRepeatTypeCmd().send(mIService, PlaybackService.RepeatType.None);
    }
    public void detectHeadset(boolean enable) {
        new DetectHeadsetCmd(enable).send(mIService);
    }
    public float getRate() {
        return new GetRateCmd().send(mIService, 1.0f);
    }
    public void handleVout() {
        new HandleVout().send(mIService);
    }

    /* Static commands: can be run without a PlaybackServiceClient instance */
    public static void load(Context context, ResultCallback<Void> asyncCb, MediaWrapper media, boolean forceAudio) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        load(context, asyncCb, arrayList, 0, forceAudio);
    }
    public static void load(Context context, ResultCallback<Void> asyncCb, MediaWrapper media) {
        load(context, asyncCb, media, false);
    }
    public static void load(Context context, ResultCallback<Void> asyncCb, List<MediaWrapper> mediaList, int position) {
        load(context, asyncCb, mediaList, position, false);
    }
    public static void load(Context context, ResultCallback<Void> asyncCb, List<MediaWrapper> mediaList, int position, boolean forceAudio) {
        new LoadCmd(mediaList, position, forceAudio).sendAsync(context, asyncCb);
    }
    public static void loadLocation(Context context, ResultCallback<Void> asyncCb, String mediaPath) {
        ArrayList <String> arrayList = new ArrayList<String>();
        arrayList.add(mediaPath);
        loadLocations(context, asyncCb, arrayList, 0);
    }
    public static void loadLocations(Context context, ResultCallback<Void> asyncCb, List<String> mediaPathList, int position) {
        new LoadLocationsCmd(mediaPathList, position).sendAsync(context, asyncCb);
    }
    public static void append(Context context, ResultCallback<Void> asyncCb, MediaWrapper media) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        append(context, asyncCb, arrayList);
    }
    public static void append(Context context, ResultCallback<Void> asyncCb, List<MediaWrapper> mediaList) {
        new AppendCmd(mediaList).sendAsync(context, asyncCb);
    }
    public static void moveItem(Context context, ResultCallback<Void> asyncCb, int positionStart, int positionEnd) {
        new MoveItemCmd(positionStart, positionEnd).sendAsync(context, asyncCb);
    }
    public static void remove(Context context, ResultCallback<Void> asyncCb, int position) {
        new RemoveCmd(position).sendAsync(context, asyncCb);
    }
    public static void removeLocation(Context context, ResultCallback<Void> asyncCb, String location) {
        new RemoveLocationCmd(location).sendAsync(context, asyncCb);
    }
    public static void getMedias(Context context, ResultCallback<List<MediaWrapper>> asyncCb) {
        new GetMediasCmd().sendAsync(context, asyncCb);
    }
    public static void getMediaLocations(Context context, ResultCallback<List<String>> asyncCb) {
        new GetMediaLocationsCmd().sendAsync(context, asyncCb);
    }
    public static void getCurrentMediaLocation(Context context, ResultCallback<String> asyncCb) {
        new GetCurrentMediaLocationCmd().sendAsync(context, asyncCb);
    }
    public static void getCurrentMediaWrapper(Context context, ResultCallback<MediaWrapper> asyncCb) {
        new GetCurrentMediaWrapperCmd().sendAsync(context, asyncCb);
    }
    public static void stop(Context context, ResultCallback<Void> asyncCb) {
        new StopCmd().sendAsync(context, asyncCb);
    }
    public static void showWithoutParse(Context context, ResultCallback<Void> asyncCb, int u) {
        new ShowWithoutParseCmd(u).sendAsync(context, asyncCb);
    }
    public static void playIndex(Context context, ResultCallback<Void> asyncCb, int i) {
        new PlayIndexCmd(i).sendAsync(context, asyncCb);
    }
    public static void getAlbum(Context context, ResultCallback<String> asyncCb) {
        new GetAlbumCmd().sendAsync(context, asyncCb);
    }
    public static void getArtist(Context context, ResultCallback<String> asyncCb) {
        new GetArtistCmd().sendAsync(context, asyncCb);
    }
    public static void getArtistPrev(Context context, ResultCallback<String> asyncCb) {
        new GetArtistPrevCmd().sendAsync(context, asyncCb);
    }
    public static void getArtistNext(Context context, ResultCallback<String> asyncCb) {
        new GetArtistNextCmd().sendAsync(context, asyncCb);
    }
    public static void getTitle(Context context, ResultCallback<String> asyncCb) {
        new GetTitleCmd().sendAsync(context, asyncCb);
    }
    public static void getTitlePrev(Context context, ResultCallback<String> asyncCb) {
        new GetTitlePrevCmd().sendAsync(context, asyncCb);
    }
    public static void getTitleNext(Context context, ResultCallback<String> asyncCb) {
        new GetTitleNextCmd().sendAsync(context, asyncCb);
    }
    public static void isPlaying(Context context, ResultCallback<Boolean> asyncCb) {
        new IsPlayingCmd().sendAsync(context, asyncCb);
    }
    public static void pause(Context context, ResultCallback<Void> asyncCb) {
        new PauseCmd().sendAsync(context, asyncCb);
    }
    public static void play(Context context, ResultCallback<Void> asyncCb) {
        new PlayCmd().sendAsync(context, asyncCb);
    }
    public static void hasMedia(Context context, ResultCallback<Boolean> asyncCb) {
        new HasMediasCmd().sendAsync(context, asyncCb);
    }
    public static void getLength(Context context, ResultCallback<Integer> asyncCb) {
        new GetLengthCmd().sendAsync(context, asyncCb);
    }
    public static void getTime(Context context, ResultCallback<Integer> asyncCb) {
        new GetTimeCmd().sendAsync(context, asyncCb);
    }
    public static void getCover(Context context, ResultCallback<Bitmap> asyncCb) {
        new GetCoverCmd().sendAsync(context, asyncCb);
    }
    public static void getCoverPrev(Context context, ResultCallback<Bitmap> asyncCb) {
        new GetCoverPrevCmd().sendAsync(context, asyncCb);
    }
    public static void getCoverNext(Context context, ResultCallback<Bitmap> asyncCb) {
        new GetCoverNextCmd().sendAsync(context, asyncCb);
    }
    public static void next(Context context, ResultCallback<Void> asyncCb) {
        new NextCmd().sendAsync(context, asyncCb);
    }
    public static void previous(Context context, ResultCallback<Void> asyncCb) {
        new PreviousCmd().sendAsync(context, asyncCb);
    }
    public static void setTime(Context context, ResultCallback<Void> asyncCb, long time) {
        new SetTimeCmd(time).sendAsync(context, asyncCb);
    }
    public static void hasNext(Context context, ResultCallback<Boolean> asyncCb) {
        new HasNextCmd().sendAsync(context, asyncCb);
    }
    public static void hasPrevious(Context context, ResultCallback<Boolean> asyncCb) {
        new HasPreviousCmd().sendAsync(context, asyncCb);
    }
    public static void shuffle(Context context, ResultCallback<Void> asyncCb) {
        new ShuffleCmd().sendAsync(context, asyncCb);
    }
    public static void isShuffling(Context context, ResultCallback<Boolean> asyncCb) {
        new IsShufflingCmd().sendAsync(context, asyncCb);
    }
    public static void setRepeatType(Context context, ResultCallback<Void> asyncCb, PlaybackService.RepeatType t) {
        new SetRepeatTypeCmd(t).sendAsync(context, asyncCb);
    }
    public static void getRepeatType(Context context, ResultCallback<PlaybackService.RepeatType> asyncCb) {
        new GetRepeatTypeCmd().sendAsync(context, asyncCb);
    }
    public static void detectHeadset(Context context, ResultCallback<Void> asyncCb, boolean enable) {
        new DetectHeadsetCmd(enable).sendAsync(context, asyncCb);
    }
    public static void getRate(Context context, ResultCallback<Float> asyncCb) {
        new GetRateCmd().sendAsync(context, asyncCb);
    }
    public static void handleVout(Context context, ResultCallback<Void> asyncCb) {
        new HandleVout().sendAsync(context, asyncCb);
    }
}
