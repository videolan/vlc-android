package org.videolan.vlc.gui.tv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

import org.videolan.vlc.R;
import org.videolan.vlc.util.Util;

public class LicenceActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String revision = getString(R.string.build_revision);
        WebView licence = new WebView(this);
        licence.loadData(Util.readAsset("licence.htm", "").replace("!COMMITID!", revision), "text/html", "UTF8");
        setContentView(licence);
        ((View)licence.getParent()).setBackgroundColor(Color.LTGRAY);
        TvUtil.applyOverscanMargin(this);
    }
}
