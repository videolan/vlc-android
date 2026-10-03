package org.videolan.vlc.gui.audio

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.videolan.tools.HttpImageLoader
import org.videolan.vlc.R
import org.videolan.vlc.discourse.Discourse
import org.videolan.vlc.discourse.DiscourseRepository
import org.videolan.vlc.discourse.resolveDiscourseUrl
import org.videolan.vlc.gui.helpers.UiTools

class RecentlyPlayedDiscoursesFragment : Fragment(R.layout.recently_played_discourses) {
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val view = view ?: return
        val list = DiscourseRepository(requireContext()).recentlyPlayedDiscourses
        val container = view.findViewById<ViewGroup>(R.id.recently_played_discourses_list)
        container.removeAllViews()
        if (list.isEmpty()) {
            view.isVisible = false
            return
        }
        view.isVisible = true

        list.forEach { discourse ->
            val card = layoutInflater.inflate(R.layout.recently_played_discourse_card, container, false)
            card.findViewById<TextView>(R.id.recently_played_discourse_title).text = discourse.title
            card.findViewById<TextView>(R.id.recently_played_discourse_meta).text = getString(
                R.string.discourse_counts,
                discourse.totalTracks,
                discourse.totalLikes
            )
            loadImage(card.findViewById(R.id.recently_played_discourse_image), discourse)
            container.addView(card)
        }
    }

    private fun loadImage(image: ImageView, discourse: Discourse) {
        image.setImageDrawable(UiTools.getDefaultAudioDrawable(requireContext()))
        val imageUrl = resolveDiscourseUrl(discourse.thumbnailUrl)
        image.tag = imageUrl
        if (imageUrl.isNullOrBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = HttpImageLoader.downloadBitmap(imageUrl)
            if (image.tag == imageUrl && bitmap != null) image.setImageBitmap(bitmap)
        }
    }
}
