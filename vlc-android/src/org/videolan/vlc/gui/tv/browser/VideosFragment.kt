package org.videolan.vlc.gui.tv.browser

import androidx.lifecycle.Observer
import android.os.Bundle
import org.videolan.vlc.R
import org.videolan.vlc.util.KEY_GROUP
import org.videolan.vlc.viewmodels.VideosModel


class VideosFragment : MediaLibBrowserFragment<VideosModel>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val group = arguments?.getString(KEY_GROUP)
        title = group ?: getString(R.string.videos)
        model = VideosModel.get(requireContext(), this, group)
        model.dataset.observe(this, Observer { update(it!!) })
    }

    fun sort(sort: Int) = model.sort(sort)
}