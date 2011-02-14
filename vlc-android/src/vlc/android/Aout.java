package vlc.android;

import android.util.Log;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class Aout {
	private AudioTrack mAudioTrack;
	private static final String TAG = "LibVLC";

	public void Init(int sampleRateInHz, int channels, int samples)
	{
		Log.d(TAG, sampleRateInHz + ", " + channels + ", " + samples + "=>" + channels*samples);
		mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz,
									 AudioFormat.CHANNEL_CONFIGURATION_STEREO,
									 AudioFormat.ENCODING_PCM_16BIT, channels * samples * 2, AudioTrack.MODE_STREAM);
	}
	
	public void playAudio(byte[] audioData, int bufferSize, int nbSamples)
	{
		Log.d(TAG, "Buffer size: " + bufferSize + " nb samples: " + nbSamples);
		Log.d(TAG, "Write audio data in Java: " + mAudioTrack.write(audioData, 0, bufferSize));
		mAudioTrack.play();
	}
}
