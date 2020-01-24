package org.videolan.television.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.television.R

import org.videolan.vlc.gui.helpers.UiTools

class AboutActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about_main)
        UiTools.fillAboutView(window.decorView.rootView)
        applyOverscanMargin(this)
        this.registerTimeView(findViewById(R.id.tv_time))
    }
}
