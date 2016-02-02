package org.videolan.vlc.extensions.api;

import android.app.Activity;
import android.os.Bundle;

import org.videolan.vlc.extensions.api.tools.Dialogs;

public class WarningActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Dialogs.showInstallVlc(this);
    }
}
