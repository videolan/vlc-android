/*****************************************************************************
 * Aout.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.libvlc;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class AudioOutput {
    /**
     * Java side of the audio output module for Android.
     * Uses an AudioTrack to play decoded audio buffers.
     *
     * TODO Use MODE_STATIC instead of MODE_STREAM with a MemoryFile (ashmem)
     */

    public AudioOutput() {
    }

    private AudioTrack mAudioTrack;
    private static final String TAG = "LibVLC/aout";

    public void init(int sampleRateInHz, int channels, int samples) {
        Log.d(TAG, sampleRateInHz + ", " + channels + ", " + samples + "=>" + channels * samples);
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz,
                                                        AudioFormat.CHANNEL_OUT_STEREO,
                                                        AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                                     sampleRateInHz,
                                     AudioFormat.CHANNEL_OUT_STEREO,
                                     AudioFormat.ENCODING_PCM_16BIT,
                                     Math.max(minBufferSize, channels * samples * 2),
                                     AudioTrack.MODE_STREAM);
    }

    public void release() {
        if (mAudioTrack != null) {
            mAudioTrack.release();
        }
        mAudioTrack = null;
    }

    public void playBuffer(byte[] audioData, int bufferSize) {
        if (mAudioTrack.getState() == AudioTrack.STATE_UNINITIALIZED)
            return;
        if (mAudioTrack.write(audioData, 0, bufferSize) != bufferSize) {
            Log.w(TAG, "Could not write all the samples to the audio device");
        }
        mAudioTrack.play();
    }

    public void pause() {
        mAudioTrack.pause();
    }
}
