package org.videolan.vlc.gui.tv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools

class AboutActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about_main)
        UiTools.fillAboutView(window.decorView.rootView)
        TvUtil.applyOverscanMargin(this)
        this.registerTimeView(findViewById(R.id.tv_time))
    }
}
