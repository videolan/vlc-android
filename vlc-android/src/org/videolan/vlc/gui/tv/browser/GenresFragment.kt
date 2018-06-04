package org.videolan.vlc.gui.tv.browser

import android.arch.lifecycle.Observer
import android.os.Bundle
import org.videolan.vlc.R
import org.videolan.vlc.util.getModel
import org.videolan.vlc.viewmodels.audio.GenresModel


class GenresFragment : MediaLibBrowserFragment<GenresModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = currentItem?.title ?: getString(R.string.genres)
        model = getModel()
        model.dataset.observe(this, Observer { update(it!!) })
    }
}