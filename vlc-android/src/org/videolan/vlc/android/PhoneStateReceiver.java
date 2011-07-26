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
				try {
					LibVLC libVLC = LibVLC.getInstance();
					if (libVLC.isPlaying())
						libVLC.pause();
				} catch (LibVlcException e) {
					return;
				}
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
