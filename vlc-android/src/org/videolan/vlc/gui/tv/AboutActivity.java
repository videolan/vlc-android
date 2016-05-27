package org.videolan.vlc.gui.tv;

import android.app.Activity;
import android.os.Bundle;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.helpers.UiTools;

public class AboutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_main);
        UiTools.fillAboutView(getWindow().getDecorView().getRootView());
        TvUtil.applyOverscanMargin(this);
    }
}
