package vlc.android;

import android.app.Activity;
import android.os.Bundle;

public class PlayerActivity extends Activity {

	public final static String TAG = "VLC/PlayerActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.player);
		super.onCreate(savedInstanceState);
	}

}
