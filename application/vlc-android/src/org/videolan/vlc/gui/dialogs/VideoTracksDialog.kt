/*
 * ************************************************************************
 *  AddToGroupDialog.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.videolan.libvlc.MediaPlayer
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.DependencyProvider
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PlayerOverlayTracksBinding
import org.videolan.vlc.gui.dialogs.adapters.TrackAdapter

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class VideoTracksDialog : VLCBottomSheetDialogFragment() {
    override fun getDefaultState(): Int = STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = true

    private lateinit var binding: PlayerOverlayTracksBinding

    private val coroutineContextProvider: CoroutineContextProvider

    override fun initialFocusedView(): View = binding.subtitleTracks.emptyView

    lateinit var menuItemListener:(Int) -> Unit
    lateinit var trackSelectionListener:(Int, TrackType) -> Unit

    init {
        VideoTracksDialog.registerCreator { CoroutineContextProvider() }
        coroutineContextProvider = VideoTracksDialog.get(0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        PlaybackService.serviceFlow.onEach { onServiceChanged(it) }.launchIn(MainScope())
        super.onCreate(savedInstanceState)
    }

    private fun onServiceChanged(service: PlaybackService?) {
        service?.let { playbackService ->
            if (playbackService.videoTracksCount <= 2) {
                binding.videoTracks.trackContainer.setGone()
                binding.tracksSeparator3.setGone()
            }
            if (playbackService.audioTracksCount <= 0) {
                binding.audioTracks.trackContainer.setGone()
                binding.tracksSeparator2.setGone()
            }

            playbackService.videoTracks?.let { trackList ->
                val trackAdapter = TrackAdapter(trackList as Array<MediaPlayer.TrackDescription>, trackList.firstOrNull { it.id == playbackService.videoTrack })
                trackAdapter.setOnTrackSelectedListener { track ->
                    trackSelectionListener.invoke(track.id, TrackType.VIDEO)
                }
                binding.videoTracks.trackList.adapter = trackAdapter
            }
            playbackService.audioTracks?.let { trackList ->
                val trackAdapter = TrackAdapter(trackList as Array<MediaPlayer.TrackDescription>, trackList.firstOrNull { it.id == playbackService.audioTrack })
                trackAdapter.setOnTrackSelectedListener { track ->
                    trackSelectionListener.invoke(track.id, TrackType.AUDIO)
                }
                binding.audioTracks.trackList.adapter = trackAdapter
            }
            playbackService.spuTracks?.let { trackList ->
                val trackAdapter = TrackAdapter(trackList as Array<MediaPlayer.TrackDescription>, trackList.firstOrNull { it.id == playbackService.spuTrack })
                trackAdapter.setOnTrackSelectedListener { track ->
                    trackSelectionListener.invoke(track.id, TrackType.SPU)
                }
                binding.subtitleTracks.trackList.adapter = trackAdapter
                if (trackList.isEmpty()) binding.subtitleTracks.emptyView.setVisible()
            }
            if (playbackService.spuTracks == null) binding.subtitleTracks.emptyView.setVisible()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = PlayerOverlayTracksBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.audioTracks.trackTitle.text = getString(R.string.audio)
        binding.videoTracks.trackTitle.text = getString(R.string.video)
        binding.subtitleTracks.trackTitle.text = getString(R.string.subtitles)

        binding.audioTracks.trackList.layoutManager = LinearLayoutManager(requireActivity())
        binding.videoTracks.trackList.layoutManager = LinearLayoutManager(requireActivity())
        binding.subtitleTracks.trackList.layoutManager = LinearLayoutManager(requireActivity())

        binding.videoTracks.trackMore.setGone()

        //prevent focus
        binding.tracksSeparator3.isEnabled = false
        binding.tracksSeparator2.isEnabled = false

        binding.audioTracks.trackMore.setOnClickListener {
            val popup = PopupMenu(requireActivity(), binding.audioTracks.trackMore)
            popup.menuInflater.inflate(R.menu.audio_track_menu, popup.menu)
            popup.show()
            popup.setOnMenuItemClickListener {
                menuItemListener.invoke(it.itemId)
                dismiss()
                true
            }
        }

        binding.subtitleTracks.trackMore.setOnClickListener {
            val popup = PopupMenu(requireActivity(), binding.subtitleTracks.trackMore, Gravity.END)
            popup.menuInflater.inflate(R.menu.subtitle_track_menu, popup.menu)
            popup.show()
            popup.setOnMenuItemClickListener {
                menuItemListener.invoke(it.itemId)
                dismiss()
                true
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    companion object : DependencyProvider<Any>() {

        val TAG = "VLC/SavePlaylistDialog"
    }

    enum class TrackType {
        VIDEO, AUDIO, SPU
    }
}

