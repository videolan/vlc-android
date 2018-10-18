package org.videolan.vlc.gui.tv

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import android.view.View
import android.webkit.WebView

import org.videolan.vlc.R
import org.videolan.vlc.util.Util

class LicenceActivity : androidx.fragment.app.FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val revision = getString(R.string.build_revision)
        val licence = WebView(this)
        licence.loadData(Util.readAsset("licence.htm", "").replace("!COMMITID!", revision), "text/html", "UTF8")
        setContentView(licence)
        (licence.parent as View).setBackgroundColor(Color.LTGRAY)
        TvUtil.applyOverscanMargin(this)
    }
}
