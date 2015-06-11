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
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import org.videolan.vlc.interfaces.IPlaybackService;
import org.videolan.vlc.interfaces.IPlaybackServiceCallback;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class PlaybackServiceClient {
    public static final String TAG = "PlaybackServiceClient";

    public interface Callback {
        void update();
        void updateProgress();
        void onMediaPlayedAdded(MediaWrapper media, int index);
        void onMediaPlayedRemoved(int index);
    }

    private static PlaybackServiceClient mInstance;
    private static boolean mIsBound = false;
    private IPlaybackService mIService;
    private ServiceConnection mServiceConnection;
    private final ArrayList<Callback> mCallbacks;

    private final IPlaybackServiceCallback mCallback = new IPlaybackServiceCallback.Stub() {
        @Override
        public void update() throws RemoteException {
            updateAudioPlayer();
        }

        @Override
        public void updateProgress() throws RemoteException {
            updateProgressAudioPlayer();
        }

        @Override
        public void onMediaPlayedAdded(MediaWrapper media, int index) throws RemoteException {
            updateMediaPlayedAdded(media, index);
        }

        @Override
        public void onMediaPlayedRemoved(int index) throws RemoteException {
            updateMediaPlayedRemoved(index);
        }
    };

    private PlaybackServiceClient() {
        mCallbacks = new ArrayList<Callback>();
    }

    public static PlaybackServiceClient getInstance() {
        if (mInstance == null) {
            mInstance = new PlaybackServiceClient();
        }
        return mInstance;
    }

    /**
     * The connection listener interface for the audio service
     */
    public interface AudioServiceConnectionListener {
        public void onConnectionSuccess();
        public void onConnectionFailed();
    }

    /**
     * Bind to audio service if it is running
     */
    public void bindAudioService(Context context) {
        bindAudioService(context, null);
    }

    public void bindAudioService(Context context, final AudioServiceConnectionListener connectionListerner) {
        if (context == null) {
            Log.w(TAG, "bindAudioService() with null Context. Ooops" );
            return;
        }
        context = context.getApplicationContext();

        if (!mIsBound) {
            Intent service = new Intent(context, PlaybackService.class);


            // Setup audio service connection
            mServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.d(TAG, "Service Disconnected");
                    mIService = null;
                    mIsBound = false;
                }

                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    if (!mIsBound) // Can happen if unbind is called quickly before this callback
                        return;
                    Log.d(TAG, "Service Connected");
                    mIService = IPlaybackService.Stub.asInterface(service);

                    // Register controller to the service
                    try {
                        mIService.addAudioCallback(mCallback);
                        if (connectionListerner != null)
                            connectionListerner.onConnectionSuccess();
                    } catch (RemoteException e) {
                        Log.e(TAG, "remote procedure call failed: addAudioCallback()");
                        if (connectionListerner != null)
                            connectionListerner.onConnectionFailed();
                    }
                    updateAudioPlayer();
                }
            };

            mIsBound = context.bindService(service, mServiceConnection, Context.BIND_AUTO_CREATE);
        } else {
            // Register controller to the service
            try {
                if (mIService != null)
                    mIService.addAudioCallback(mCallback);
                if (connectionListerner != null)
                    connectionListerner.onConnectionSuccess();
            } catch (RemoteException e) {
                Log.e(TAG, "remote procedure call failed: addAudioCallback()");
                if (connectionListerner != null)
                    connectionListerner.onConnectionFailed();
            }
        }
    }

    public void unbindAudioService(Context context) {
        if (context == null) {
            Log.w(TAG, "unbindAudioService() with null Context. Ooops" );
            return;
        }
        context = context.getApplicationContext();

        if (mIsBound) {
            mIsBound = false;
            try {
                if (mIService != null)
                    mIService.removeAudioCallback(mCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "remote procedure call failed: removeAudioCallback()");
            }
            context.unbindService(mServiceConnection);
            mIService = null;
            mServiceConnection = null;
        }
    }

    /**
     * Add a Callback
     * @param callback
     */
    public void addCallback(Callback callback) {
        if (!mCallbacks.contains(callback))
            mCallbacks.add(callback);
    }

    /**
     * Remove Callback from list
     * @param callback
     */
    public void removeCallback(Callback callback) {
        if (mCallbacks.contains(callback))
            mCallbacks.remove(callback);
    }

    /**
     * Update all AudioPlayer
     */
    private void updateAudioPlayer() {
        for (Callback player : mCallbacks)
            player.update();
    }

    /**
     * Update the progress of all AudioPlayers
     */
    private void updateProgressAudioPlayer() {
        for (Callback player : mCallbacks)
            player.updateProgress();
    }

    private void updateMediaPlayedAdded(MediaWrapper media, int index) {
        for (Callback listener : mCallbacks) {
            listener.onMediaPlayedAdded(media, index);
        }
    }

    private void updateMediaPlayedRemoved(int index) {
        for (Callback listener : mCallbacks) {
            listener.onMediaPlayedRemoved(index);
        }
    }

    /**
     * This is a handy utility function to call remote procedure calls from mIService
     * to reduce code duplication across methods of AudioServiceController.
     *
     * @param instance The instance of IPlaybackService to call, usually mIService
     * @param returnType Return type of the method being called
     * @param defaultValue Default value to return in case of null or exception
     * @param functionName The function name to call, e.g. "stop"
     * @param parameterTypes List of parameter types. Pass null if none.
     * @param parameters List of parameters. Must be in same order as parameterTypes. Pass null if none.
     * @return The results of the RPC or defaultValue if error
     */
    private <T> T remoteProcedureCall(IPlaybackService instance, Class<T> returnType, T defaultValue, String functionName, Class<?> parameterTypes[], Object parameters[]) {
        if(instance == null) {
            return defaultValue;
        }

        try {
            Method m = IPlaybackService.class.getMethod(functionName, parameterTypes);
            @SuppressWarnings("unchecked")
            T returnVal = (T) m.invoke(instance, parameters);
            return returnVal;
        } catch(NoSuchMethodException e) {
            e.printStackTrace();
            return defaultValue;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return defaultValue;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return defaultValue;
        } catch (InvocationTargetException e) {
            if(e.getTargetException() instanceof RemoteException) {
                Log.e(TAG, "remote procedure call failed: " + functionName + "()");
            }
            return defaultValue;
        }
    }

    public void loadLocation(String mediaPath) {
        ArrayList < String > arrayList = new ArrayList<String>();
        arrayList.add(mediaPath);
        loadLocations(arrayList, 0);
    }


    public void load(MediaWrapper media, boolean forceAudio) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        load(arrayList, 0, forceAudio);
    }

    public void load(MediaWrapper media) {
        load(media, false);
    }

    public void loadLocations(List<String> mediaPathList, int position) {
        remoteProcedureCall(mIService, Void.class, (Void) null, "loadLocations",
                new Class<?>[]{List.class, int.class},
                new Object[]{mediaPathList, position});
    }

    public void load(List<MediaWrapper> mediaList, int position) {
        load(mediaList, position, false);
    }

    public void load(List<MediaWrapper> mediaList, int position, boolean forceAudio) {
        remoteProcedureCall(mIService, Void.class, (Void) null, "load",
                new Class<?>[]{List.class, int.class, boolean.class},
                new Object[]{mediaList, position, forceAudio});
    }

    public void append(MediaWrapper media) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        append(arrayList);
    }

    public void append(List<MediaWrapper> mediaList) {
        remoteProcedureCall(mIService, Void.class, (Void) null, "append",
                new Class<?>[]{List.class},
                new Object[]{mediaList});
    }

    public void moveItem(int positionStart, int positionEnd) {
        remoteProcedureCall(mIService, Void.class, (Void)null, "moveItem",
                new Class<?>[] { int.class, int.class },
                new Object[] { positionStart, positionEnd } );
    }

    public void remove(int position) {
        remoteProcedureCall(mIService, Void.class, (Void)null, "remove",
                new Class<?>[] { int.class },
                new Object[] { position } );
    }

    public void removeLocation(String location) {
        remoteProcedureCall(mIService, Void.class, (Void)null, "removeLocation",
                new Class<?>[] { String.class },
                new Object[] { location } );
    }

    @SuppressWarnings("unchecked")
    public List<MediaWrapper> getMedias() {
        return remoteProcedureCall(mIService, List.class, null, "getMedias", null, null);
    }

    @SuppressWarnings("unchecked")
    public List<String> getMediaLocations() {
        List<String> def = new ArrayList<String>();
        return remoteProcedureCall(mIService, List.class, def, "getMediaLocations", null, null);
    }

    public String getCurrentMediaLocation() {
        return remoteProcedureCall(mIService, String.class, (String)null, "getCurrentMediaLocation", null, null);
    }

    public MediaWrapper getCurrentMediaWrapper() {
        return remoteProcedureCall(mIService, MediaWrapper.class, (MediaWrapper)null, "getCurrentMediaWrapper", null, null);
    }

    public void stop() {
        remoteProcedureCall(mIService, Void.class, (Void)null, "stop", null, null);
    }

    public void showWithoutParse(int u) {
        remoteProcedureCall(mIService, Void.class, (Void)null, "showWithoutParse",
                new Class<?>[] { int.class },
                new Object[] { u } );
    }

    public void playIndex(int i) {
        remoteProcedureCall(mIService, Void.class, (Void)null, "playIndex",
                new Class<?>[] { int.class },
                new Object[] { i } );
    }

    public String getAlbum() {
        return remoteProcedureCall(mIService, String.class, (String)null, "getAlbum", null, null);
    }

    public String getArtist() {
        return remoteProcedureCall(mIService, String.class, (String)null, "getArtist", null, null);
    }

    public String getArtistPrev() {
        return remoteProcedureCall(mIService, String.class, (String)null, "getArtistPrev", null, null);
    }

    public String getArtistNext() {
        return remoteProcedureCall(mIService, String.class, (String)null, "getArtistNext", null, null);
    }

    public String getTitle() {
        return remoteProcedureCall(mIService, String.class, (String)null, "getTitle", null, null);
    }

    public String getTitlePrev() {
        return remoteProcedureCall(mIService, String.class, (String)null, "getTitlePrev", null, null);
    }

    public String getTitleNext() {
        return remoteProcedureCall(mIService, String.class, (String)null, "getTitleNext", null, null);
    }

    public boolean isPlaying() {
        return hasMedia() && remoteProcedureCall(mIService, boolean.class, false, "isPlaying", null, null);
    }

    public void pause() {
        remoteProcedureCall(mIService, Void.class, (Void)null, "pause", null, null);
    }

    public void play() {
        remoteProcedureCall(mIService, Void.class, (Void)null, "play", null, null);
    }

    public boolean hasMedia() {
        return remoteProcedureCall(mIService, boolean.class, false, "hasMedia", null, null);
    }

    public int getLength() {
        return remoteProcedureCall(mIService, int.class, 0, "getLength", null, null);
    }

    public int getTime() {
        return remoteProcedureCall(mIService, int.class, 0, "getTime", null, null);
    }

    public Bitmap getCover() {
        return remoteProcedureCall(mIService, Bitmap.class, (Bitmap)null, "getCover", null, null);
    }

    public Bitmap getCoverPrev() {
        return remoteProcedureCall(mIService, Bitmap.class, (Bitmap)null, "getCoverPrev", null, null);
    }

    public Bitmap getCoverNext() {
        return remoteProcedureCall(mIService, Bitmap.class, (Bitmap)null, "getCoverNext", null, null);
    }

    public void next() {
        remoteProcedureCall(mIService, Void.class, (Void)null, "next", null, null);
    }

    public void previous() {
        remoteProcedureCall(mIService, Void.class, (Void)null, "previous", null, null);
    }

    public void setTime(long time) {
        remoteProcedureCall(mIService, Void.class, (Void)null, "setTime",
                new Class<?>[] { long.class },
                new Object[] { time } );
    }

    public boolean hasNext() {
        return remoteProcedureCall(mIService, boolean.class, false, "hasNext", null, null);
    }

    public boolean hasPrevious() {
        return remoteProcedureCall(mIService, boolean.class, false, "hasPrevious", null, null);
    }

    public void shuffle() {
        remoteProcedureCall(mIService, Void.class, (Void)null, "shuffle", null, null);
    }

    public void setRepeatType(PlaybackService.RepeatType t) {
        remoteProcedureCall(mIService, Void.class, (Void)null, "setRepeatType",
                new Class<?>[] { int.class },
                new Object[] { t.ordinal() } );
    }

    public boolean isShuffling() {
        return remoteProcedureCall(mIService, boolean.class, false, "isShuffling", null, null);
    }

    public PlaybackService.RepeatType getRepeatType() {
        return PlaybackService.RepeatType.values()[
            remoteProcedureCall(mIService, int.class, PlaybackService.RepeatType.None.ordinal(), "getRepeatType", null, null)
        ];
    }

    public void detectHeadset(boolean enable) {
        remoteProcedureCall(mIService, Void.class, null, "detectHeadset",
                new Class<?>[] { boolean.class },
                new Object[] { enable } );
    }

    public float getRate() {
        return remoteProcedureCall(mIService, Float.class, (float) 1.0, "getRate", null, null);
    }

    public void handleVout() {
        remoteProcedureCall(mIService, Void.class, (Void)null, "handleVout", null, null);
    }
}
