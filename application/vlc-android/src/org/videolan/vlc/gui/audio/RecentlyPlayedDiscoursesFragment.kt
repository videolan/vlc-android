package org.videolan.vlc.gui.audio

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.HttpImageLoader
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.discourse.Discourse
import org.videolan.vlc.discourse.DiscourseRepository
import org.videolan.vlc.discourse.resolveDiscourseUrl
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.getAudioIconDrawable
import org.videolan.vlc.gui.helpers.loadImage
import org.videolan.vlc.media.MediaSessionBrowser
import org.videolan.vlc.media.MediaUtils

class RecentlyPlayedDiscoursesFragment : Fragment(R.layout.recently_played_discourses) {
    private fun render() {
        val view = view ?: return
        val list = DiscourseRepository(requireContext()).recentlyPlayedDiscourses
        val section = view.findViewById<View>(R.id.recently_played_discourses_section)
        val container = view.findViewById<ViewGroup>(R.id.recently_played_discourses_list)
        container.removeAllViews()
        if (list.isEmpty()) {
            section.isVisible = false
        } else {
            section.isVisible = true
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
    }

    private fun loadTracks() {
        val view = view ?: return
        val section = view.findViewById<View>(R.id.recently_played_tracks_section)
        val container = view.findViewById<ViewGroup>(R.id.recently_played_tracks_list)
        if (!Settings.getInstance(requireContext()).getBoolean(PLAYBACK_HISTORY, true)) {
            section.isVisible = false
            return
        }
        lifecycleScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                Medialibrary.getInstance().history(Medialibrary.HISTORY_TYPE_LOCAL)
                    ?.toList()
                    ?.filter { MediaSessionBrowser.isMediaAudio(it) }
                    ?.take(MAX_TRACKS)
                    .orEmpty()
            }
            if (!isAdded || view !== this@RecentlyPlayedDiscoursesFragment.view) return@launch
            container.removeAllViews()
            section.isVisible = tracks.isNotEmpty()
            tracks.forEach { addTrackCard(container, it) }
        }
    }

    private fun addTrackCard(container: ViewGroup, track: MediaWrapper) {
        val card = layoutInflater.inflate(R.layout.recently_played_track_card, container, false)
        val image = card.findViewById<ImageView>(R.id.recently_played_track_image)
        image.setImageDrawable(getAudioIconDrawable(requireContext(), MediaLibraryItem.TYPE_MEDIA, true))
        loadImage(image, track, card = true)
        card.findViewById<TextView>(R.id.recently_played_track_title).text = track.title
        card.findViewById<TextView>(R.id.recently_played_track_meta).text =
            MediaUtils.getDisplaySubtitle(requireContext(), track) ?: track.albumName ?: track.artistName.orEmpty()
        card.setOnClickListener { MediaUtils.openMedia(requireContext(), track) }
        container.addView(card)
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

    override fun onResume() {
        super.onResume()
        render()
        loadTracks()
    }

    private companion object {
        const val MAX_TRACKS = 24
    }
}
