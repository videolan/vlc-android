package org.videolan.vlc.gui.tv.browser

import android.arch.lifecycle.Observer
import android.os.Bundle
import org.videolan.medialibrary.Medialibrary
import org.videolan.vlc.R
import org.videolan.vlc.util.KEY_GROUP
import org.videolan.vlc.viewmodels.VideosModel


class VideosFragment : MediaLibBrowserFragment<VideosModel>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val minGroupLengthValue = Integer.valueOf(preferences.getString("video_min_group_length", "6"))
        val group = arguments?.getString(KEY_GROUP)
        title = group ?: getString(R.string.videos)
        model = VideosModel.get(requireContext(), this, group, minGroupLengthValue, Medialibrary.SORT_DEFAULT)
        model.dataset.observe(this, Observer { update(it!!) })
    }

    fun sort(sort: Int) = model.sort(sort)
}