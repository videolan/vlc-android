package org.videolan.vlc.gui.tv.browser

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import org.videolan.vlc.R
import org.videolan.vlc.viewmodels.audio.AlbumModel


class AlbumsFragment : MediaLibBrowserFragment<AlbumModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = currentItem?.title ?: getString(R.string.albums)
        model = ViewModelProviders.of(this, AlbumModel.Factory(currentItem)).get(AlbumModel::class.java)
        model.dataset.observe(this, Observer { update(it!!) })
    }
}