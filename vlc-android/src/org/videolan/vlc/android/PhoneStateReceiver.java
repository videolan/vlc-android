package org.videolan.vlc.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class PhoneStateReceiver extends BroadcastReceiver {

	private PhoneStateListener mListener = new PhoneStateListener() {
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			if (state == TelephonyManager.CALL_STATE_RINGING) {
				LibVLC libVLC = LibVLC.getExistingInstance();
				if (libVLC != null && libVLC.isPlaying())
					libVLC.pause();
			}
		}
	};

	@Override
	public void onReceive(Context context, Intent intent) {
		TelephonyManager tm = (TelephonyManager)
				context.getSystemService(Context.TELEPHONY_SERVICE);
		tm.listen(mListener, PhoneStateListener.LISTEN_CALL_STATE);
	}

}
