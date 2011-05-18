package org.videolan.vlc.android;

import android.util.Log;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class Aout {
    /**
     * Java side of the audio output module for Android.
     * Uses an AudioTrack to play decoded audio buffers.
     *
     * TODO Use MODE_STATIC instead of MODE_STREAM with a MemoryFile (ashmem)
     */

	public Aout()
	{
	}

    private AudioTrack mAudioTrack;
    private static final String TAG = "LibVLC/aout";

    public void init(int sampleRateInHz, int channels, int samples) {
        Log.d(TAG, sampleRateInHz + ", " + channels + ", " + samples + "=>" + channels*samples);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                                     sampleRateInHz,
                                     AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                                     AudioFormat.ENCODING_PCM_16BIT,
                                     channels * samples * 2,
                                     AudioTrack.MODE_STREAM);
    }

    public void release() {
        Log.d(TAG, "Stopping audio playback");
        // mAudioTrack.stop();
        mAudioTrack.release();
        mAudioTrack = null;
    }

    public void playBuffer(byte[] audioData, int bufferSize, int nbSamples) {
        if (mAudioTrack.write(audioData, 0, bufferSize) != bufferSize)
        {
            Log.w(TAG, "Could not write all the samples to the audio device");
        }
        mAudioTrack.play();
    }
}
