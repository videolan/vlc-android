/*****************************************************************************
 * AudioServiceController.java
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.videolan.vlc.interfaces.IAudioPlayer;
import org.videolan.vlc.interfaces.IAudioPlayerControl;
import org.videolan.vlc.interfaces.IAudioService;
import org.videolan.vlc.interfaces.IAudioServiceCallback;

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

public class AudioServiceController implements IAudioPlayerControl {
    public static final String TAG = "VLC/AudioServiceContoller";

    private static AudioServiceController mInstance;
    private static boolean mIsBound = false;
    private IAudioService mAudioServiceBinder;
    private ServiceConnection mAudioServiceConnection;
    private final ArrayList<IAudioPlayer> mAudioPlayer;
    private final IAudioServiceCallback mCallback = new IAudioServiceCallback.Stub() {
        @Override
        public void update() throws RemoteException {
            updateAudioPlayer();
        }

        @Override
        public void updateProgress() throws RemoteException {
            updateProgressAudioPlayer();
        }
    };

    private AudioServiceController() {
        mAudioPlayer = new ArrayList<IAudioPlayer>();
    }

    public static AudioServiceController getInstance() {
        if (mInstance == null) {
            mInstance = new AudioServiceController();
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
            Intent service = new Intent(context, AudioService.class);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final boolean enableHS = prefs.getBoolean("enable_headset_detection", true);

            // Setup audio service connection
            mAudioServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.d(TAG, "Service Disconnected");
                    mAudioServiceBinder = null;
                    mIsBound = false;
                }

                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    if (!mIsBound) // Can happen if unbind is called quickly before this callback
                        return;
                    Log.d(TAG, "Service Connected");
                    mAudioServiceBinder = IAudioService.Stub.asInterface(service);

                    // Register controller to the service
                    try {
                        mAudioServiceBinder.addAudioCallback(mCallback);
                        mAudioServiceBinder.detectHeadset(enableHS);
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

            mIsBound = context.bindService(service, mAudioServiceConnection, Context.BIND_AUTO_CREATE);
        } else {
            // Register controller to the service
            try {
                if (mAudioServiceBinder != null)
                    mAudioServiceBinder.addAudioCallback(mCallback);
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
                if (mAudioServiceBinder != null)
                    mAudioServiceBinder.removeAudioCallback(mCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "remote procedure call failed: removeAudioCallback()");
            }
            context.unbindService(mAudioServiceConnection);
            mAudioServiceBinder = null;
            mAudioServiceConnection = null;
        }
    }

    /**
     * Add a AudioPlayer
     * @param ap
     */
    public void addAudioPlayer(IAudioPlayer ap) {
        if (!mAudioPlayer.contains(ap))
            mAudioPlayer.add(ap);
    }

    /**
     * Remove AudioPlayer from list
     * @param ap
     */
    public void removeAudioPlayer(IAudioPlayer ap) {
        if (mAudioPlayer.contains(ap))
            mAudioPlayer.remove(ap);
    }

    /**
     * Update all AudioPlayer
     */
    private void updateAudioPlayer() {
        for (IAudioPlayer player : mAudioPlayer)
            player.update();
    }

    /**
     * Update the progress of all AudioPlayers
     */
    private void updateProgressAudioPlayer() {
        for (IAudioPlayer player : mAudioPlayer)
            player.updateProgress();
    }

    /**
     * This is a handy utility function to call remote procedure calls from mAudioServiceBinder
     * to reduce code duplication across methods of AudioServiceController.
     *
     * @param instance The instance of IAudioService to call, usually mAudioServiceBinder
     * @param returnType Return type of the method being called
     * @param defaultValue Default value to return in case of null or exception
     * @param functionName The function name to call, e.g. "stop"
     * @param parameterTypes List of parameter types. Pass null if none.
     * @param parameters List of parameters. Must be in same order as parameterTypes. Pass null if none.
     * @return The results of the RPC or defaultValue if error
     */
    private <T> T remoteProcedureCall(IAudioService instance, Class<T> returnType, T defaultValue, String functionName, Class<?> parameterTypes[], Object parameters[]) {
        if(instance == null) {
            return defaultValue;
        }

        try {
            Method m = IAudioService.class.getMethod(functionName, parameterTypes);
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

    public void load(List<String> mediaPathList, int position) {
        load(mediaPathList, position, false);
    }

    public void load(String mediaPath, boolean noVideo) {
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add(mediaPath);
        load(arrayList, 0, noVideo);
    }

    public void load(List<String> mediaPathList, int position, boolean noVideo) {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "load",
                new Class<?>[] { List.class, int.class, boolean.class },
                new Object[] { mediaPathList, position, noVideo } );
    }

    public void append(String mediaPath) {
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add(mediaPath);
        append(arrayList);
    }

    public void append(List<String> mediaPathList) {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "append",
                new Class<?>[] { List.class },
                new Object[] { mediaPathList } );
    }

    public void moveItem(int positionStart, int positionEnd) {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "moveItem",
                new Class<?>[] { int.class, int.class },
                new Object[] { positionStart, positionEnd } );
    }

    public void remove(int position) {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "remove",
                new Class<?>[] { int.class },
                new Object[] { position } );
    }

    public void removeLocation(String location) {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "removeLocation",
                new Class<?>[] { String.class },
                new Object[] { location } );
    }

    @SuppressWarnings("unchecked")
    public List<String> getMediaLocations() {
        List<String> def = new ArrayList<String>();
        return remoteProcedureCall(mAudioServiceBinder, List.class, def, "getMediaLocations", null, null);
    }

    public String getCurrentMediaLocation() {
        return remoteProcedureCall(mAudioServiceBinder, String.class, (String)null, "getCurrentMediaLocation", null, null);
    }

    public void stop() {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "stop", null, null);
        updateAudioPlayer();
    }

    public void showWithoutParse(int u) {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "showWithoutParse",
                new Class<?>[] { int.class },
                new Object[] { u } );
        updateAudioPlayer();
    }

    public void playIndex(int i) {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "playIndex",
                new Class<?>[] { int.class },
                new Object[] { i } );
        updateAudioPlayer();
    }

    @Override
    public String getAlbum() {
        return remoteProcedureCall(mAudioServiceBinder, String.class, (String)null, "getAlbum", null, null);
    }

    @Override
    public String getArtist() {
        return remoteProcedureCall(mAudioServiceBinder, String.class, (String)null, "getArtist", null, null);
    }

    @Override
    public String getArtistPrev() {
        return remoteProcedureCall(mAudioServiceBinder, String.class, (String)null, "getArtistPrev", null, null);
    }

    @Override
    public String getArtistNext() {
        return remoteProcedureCall(mAudioServiceBinder, String.class, (String)null, "getArtistNext", null, null);
    }

    @Override
    public String getTitle() {
        return remoteProcedureCall(mAudioServiceBinder, String.class, (String)null, "getTitle", null, null);
    }

    @Override
    public String getTitlePrev() {
        return remoteProcedureCall(mAudioServiceBinder, String.class, (String)null, "getTitlePrev", null, null);
    }

    @Override
    public String getTitleNext() {
        return remoteProcedureCall(mAudioServiceBinder, String.class, (String)null, "getTitleNext", null, null);
    }

    @Override
    public boolean isPlaying() {
        return hasMedia() && remoteProcedureCall(mAudioServiceBinder, boolean.class, false, "isPlaying", null, null);
    }

    @Override
    public void pause() {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "pause", null, null);
        updateAudioPlayer();
    }

    @Override
    public void play() {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "play", null, null);
        updateAudioPlayer();
    }

    @Override
    public boolean hasMedia() {
        return remoteProcedureCall(mAudioServiceBinder, boolean.class, false, "hasMedia", null, null);
    }

    @Override
    public int getLength() {
        return remoteProcedureCall(mAudioServiceBinder, int.class, 0, "getLength", null, null);
    }

    @Override
    public int getTime() {
        return remoteProcedureCall(mAudioServiceBinder, int.class, 0, "getTime", null, null);
    }

    @Override
    public Bitmap getCover() {
        return remoteProcedureCall(mAudioServiceBinder, Bitmap.class, (Bitmap)null, "getCover", null, null);
    }

    @Override
    public Bitmap getCoverPrev() {
        return remoteProcedureCall(mAudioServiceBinder, Bitmap.class, (Bitmap)null, "getCoverPrev", null, null);
    }

    @Override
    public Bitmap getCoverNext() {
        return remoteProcedureCall(mAudioServiceBinder, Bitmap.class, (Bitmap)null, "getCoverNext", null, null);
    }

    @Override
    public void next() {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "next", null, null);
    }

    @Override
    public void previous() {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "previous", null, null);
    }

    public void setTime(long time) {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "setTime",
                new Class<?>[] { long.class },
                new Object[] { time } );
    }

    @Override
    public boolean hasNext() {
        return remoteProcedureCall(mAudioServiceBinder, boolean.class, false, "hasNext", null, null);
    }

    @Override
    public boolean hasPrevious() {
        return remoteProcedureCall(mAudioServiceBinder, boolean.class, false, "hasPrevious", null, null);
    }

    @Override
    public void shuffle() {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "shuffle", null, null);
    }

    @Override
    public void setRepeatType(RepeatType t) {
        remoteProcedureCall(mAudioServiceBinder, Void.class, (Void)null, "setRepeatType",
                new Class<?>[] { int.class },
                new Object[] { t.ordinal() } );
    }

    @Override
    public boolean isShuffling() {
        return remoteProcedureCall(mAudioServiceBinder, boolean.class, false, "isShuffling", null, null);
    }

    @Override
    public RepeatType getRepeatType() {
        return RepeatType.values()[
            remoteProcedureCall(mAudioServiceBinder, int.class, RepeatType.None.ordinal(), "getRepeatType", null, null)
        ];
    }

    @Override
    public void detectHeadset(boolean enable) {
        remoteProcedureCall(mAudioServiceBinder, Void.class, null, "detectHeadset",
                new Class<?>[] { boolean.class },
                new Object[] { enable } );
    }

    @Override
    public float getRate() {
        return remoteProcedureCall(mAudioServiceBinder, Float.class, (float) 1.0, "getRate", null, null);
    }
}
