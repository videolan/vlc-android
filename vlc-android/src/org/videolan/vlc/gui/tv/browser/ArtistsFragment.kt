package org.videolan.vlc.gui.tv.browser

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import org.videolan.vlc.R
import org.videolan.vlc.viewmodels.audio.ArtistModel


class ArtistsFragment : MediaLibBrowserFragment<ArtistModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = currentItem?.title ?: getString(R.string.artists)
        model = ViewModelProviders.of(this, ArtistModel.Factory(requireContext(), false)).get(ArtistModel::class.java)
        model.dataset.observe(this, Observer { update(it!!) })
    }
}