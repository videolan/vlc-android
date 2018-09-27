package org.videolan.vlc.gui.tv

import android.os.Bundle
import android.support.v4.app.FragmentActivity

import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools

class AboutActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about_main)
        UiTools.fillAboutView(window.decorView.rootView)
        TvUtil.applyOverscanMargin(this)
        this.registerTimeView(findViewById(R.id.tv_time))
    }
}
