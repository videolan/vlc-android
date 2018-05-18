package org.videolan.vlc.gui.tv.browser

import android.arch.lifecycle.Observer
import android.os.Bundle
import android.view.MenuItem
import org.videolan.medialibrary.Medialibrary
import org.videolan.vlc.R
import org.videolan.vlc.interfaces.Sortable
import org.videolan.vlc.util.Constants
import org.videolan.vlc.viewmodels.VideosProvider


class VideosFragment : MediaLibBrowserFragment<VideosProvider>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val minGroupLengthValue = Integer.valueOf(preferences.getString("video_min_group_length", "6"))
        val group = arguments?.getString(Constants.KEY_GROUP)
        title = group ?: getString(R.string.videos)
        provider = VideosProvider.get(this, group, minGroupLengthValue, Medialibrary.SORT_DEFAULT)
        provider.dataset.observe(this, Observer { update(it!!) })
    }

    fun sort(sort: Int) = provider.sort(sort)
}