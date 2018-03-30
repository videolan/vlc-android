package org.videolan.vlc.gui.tv.browser

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import org.videolan.vlc.viewmodels.audio.AlbumProvider


class AlbumsFragment : MediaLibBrowserFragment<AlbumProvider>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        provider = ViewModelProviders.of(this, AlbumProvider.Factory(currentItem)).get(AlbumProvider::class.java)
        provider.dataset.observe(this, Observer { update(it!!) })
    }
}