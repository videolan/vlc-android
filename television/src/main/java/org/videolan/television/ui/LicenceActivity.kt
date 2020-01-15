package org.videolan.television.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.vlc.R
import org.videolan.vlc.util.Util

class LicenceActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val revision = getString(R.string.build_revision)
        val licence = WebView(this)
        licence.loadData(Util.readAsset("licence.htm", "").replace("!COMMITID!", revision), "text/html", "UTF8")
        setContentView(licence)
        (licence.parent as View).setBackgroundColor(Color.LTGRAY)
        applyOverscanMargin(this)
    }
}
