package org.videolan.vlc.gui.tv;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.TextView;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.helpers.UiTools;

public class AboutActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_main);
        UiTools.fillAboutView(getWindow().getDecorView().getRootView());
        TvUtil.INSTANCE.applyOverscanMargin(this);
        TimeUpdaterKt.registerTimeView(this, (TextView) findViewById(R.id.tv_time));
    }
}
