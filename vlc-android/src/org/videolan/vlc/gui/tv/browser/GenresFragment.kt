package org.videolan.vlc.gui.tv.browser

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import org.videolan.vlc.viewmodels.audio.Genresprovider


class GenresFragment : MediaLibBrowserFragment<Genresprovider>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        provider = ViewModelProviders.of(this, Genresprovider.Factory(false)).get(Genresprovider::class.java)
        provider.dataset.observe(this, Observer { update(it!!) })
    }
}