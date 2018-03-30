package org.videolan.vlc.gui.tv.browser

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import org.videolan.vlc.R
import org.videolan.vlc.viewmodels.audio.ArtistProvider


class ArtistsFragment : MediaLibBrowserFragment<ArtistProvider>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = currentItem?.title ?: getString(R.string.artists)
        provider = ViewModelProviders.of(this, ArtistProvider.Factory(false)).get(ArtistProvider::class.java)
        provider.dataset.observe(this, Observer { update(it!!) })
    }
}