package org.videolan.vlc.gui.tv.browser

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import org.videolan.vlc.R
import org.videolan.vlc.viewmodels.audio.ArtistModel


class ArtistsFragment : MediaLibBrowserFragment<ArtistModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = currentItem?.title ?: getString(R.string.artists)
        model = ViewModelProviders.of(this, ArtistModel.Factory(false)).get(ArtistModel::class.java)
        model.dataset.observe(this, Observer { update(it!!) })
    }
}