/*****************************************************************************
 * AudioPlayerActivity.java
 *
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
 *
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
 */
package org.videolan.television.ui.audioplayer

import android.annotation.TargetApi
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateFormat
import android.view.*
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.resources.util.parcelableList
import org.videolan.television.R
import org.videolan.television.databinding.TvAudioPlayerBinding
import org.videolan.television.ui.browser.BaseTvActivity
import org.videolan.tools.Settings
import org.videolan.tools.formatRateString
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.audio.EqualizerFragment
import org.videolan.vlc.gui.dialogs.PlaybackSpeedDialog
import org.videolan.vlc.gui.dialogs.SleepTimerDialog
import org.videolan.vlc.gui.helpers.*
import org.videolan.vlc.gui.helpers.UiTools.showPinIfNeeded
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.getScreenWidth
import org.videolan.vlc.viewmodels.BookmarkModel
import org.videolan.vlc.viewmodels.PlayerState
import org.videolan.vlc.viewmodels.PlaylistModel
import kotlin.math.abs

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class AudioPlayerActivity : BaseTvActivity(),KeycodeListener  {

    private lateinit var binding: TvAudioPlayerBinding
    private lateinit var adapter: PlaylistAdapter
    private var lastMove: Long = 0
    private var shuffling = false
    private var currentCoverArt: String? = null
    private lateinit var model: PlaylistModel
    private var settings: SharedPreferences? = null
    private lateinit var pauseToPlay: AnimatedVectorDrawableCompat
    private lateinit var playToPause: AnimatedVectorDrawableCompat
    private var optionsDelegate: PlayerOptionsDelegate? = null
    lateinit var bookmarkModel: BookmarkModel
    private lateinit var bookmarkListDelegate: BookmarkListDelegate
    private val playerKeyListenerDelegate: PlayerKeyListenerDelegate by lazy(LazyThreadSafetyMode.NONE) { PlayerKeyListenerDelegate(this@AudioPlayerActivity) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.tv_audio_player)
        settings = Settings.getInstance(this)

        model = ViewModelProvider(this).get(PlaylistModel::class.java)
        binding.playlist.layoutManager = LinearLayoutManager(this)
        adapter = PlaylistAdapter(this, model)
        binding.playlist.adapter = adapter
        binding.lifecycleOwner = this
        binding.progress = model.progress
        model.dataset.observe(this) { mediaWrappers ->
            if (mediaWrappers != null) {
                adapter.setSelection(-1)
                adapter.update(mediaWrappers)
            }
            updateRepeatMode()
        }
        model.speed.observe(this) { showChips() }
        PlaybackService.playerSleepTime.observe(this) {
            showChips()
        }
        binding.mediaProgress.setOnSeekBarChangeListener(timelineListener)
        model.playerState.observe(this) { playerState -> update(playerState) }
        val position = intent.getIntExtra(MEDIA_POSITION, 0)
        if (intent.hasExtra(MEDIA_PLAYLIST))
            intent.getLongExtra(MEDIA_PLAYLIST, -1L).let { MediaUtils.openPlaylist(this, it, position) }
        else
            intent.parcelableList<MediaWrapper>(MEDIA_LIST)?.let { MediaUtils.openList(this, it, position) }
        playToPause = AnimatedVectorDrawableCompat.create(this, R.drawable.anim_play_pause_video)!!
        pauseToPlay = AnimatedVectorDrawableCompat.create(this, R.drawable.anim_pause_play_video)!!
        binding.playbackSpeedQuickAction.setOnClickListener {
            val newFragment = PlaybackSpeedDialog.newInstance()
            newFragment.show(supportFragmentManager, "playback_speed")
        }
        binding.playbackSpeedQuickAction.setOnLongClickListener {
            model.service?.setRate(1F, true)
            showChips()
            true
        }
        binding.sleepQuickAction.setOnClickListener {
            val newFragment = SleepTimerDialog.newInstance()
            newFragment.show(supportFragmentManager, "time")
        }
        binding.sleepQuickAction.setOnLongClickListener {
            model.service?.setSleepTimer(null)
            showChips()
            true
        }
        bookmarkModel = BookmarkModel.get(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (optionsDelegate?.isShowing() == true) {
                    optionsDelegate?.hide()
                    return
                }
                if (::bookmarkListDelegate.isInitialized && bookmarkListDelegate.visible) {
                    bookmarkListDelegate.hide()
                    return
                }
                finish()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        optionsDelegate = null
    }

    private var timelineListener: SeekBar.OnSeekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {

        override fun onStopTrackingTouch(seekBar: SeekBar) {}

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                model.setTime(progress.toLong())
            }
        }
    }

    private fun showChips() {
        binding.playbackSpeedQuickAction.setGone()
        binding.sleepQuickAction.setGone()
        model.speed.value?.let {
            if (it != 1.0F) binding.playbackSpeedQuickAction.setVisible()
            binding.playbackSpeedQuickActionText.text = it.formatRateString()
        }
        PlaybackService.playerSleepTime.value?.let {
            binding.sleepQuickAction.setVisible()
            binding.sleepQuickActionText.text = DateFormat.getTimeFormat(this).format(it.time)
        }
    }

    override fun refresh() {}

    private var wasPlaying = false
    fun update(state: PlayerState?) {
        if (state == null) return

        val drawable = if (state.playing) playToPause else pauseToPlay
        binding.buttonPlay.setImageDrawable(drawable)
        if (state.playing != wasPlaying) {
            binding.buttonPlay.post { drawable.start() }
        }

        wasPlaying = state.playing
        binding.buttonPlay.contentDescription = getString(if (state.playing) org.videolan.vlc.R.string.pause else org.videolan.vlc.R.string.play)

        val mw = model.currentMediaWrapper
        lifecycleScope.launch {
            if (model.switchToVideo()) {
                finish()
                return@launch
            }
            binding.mediaTitle.text = state.title
            binding.mediaArtist.text = state.artist
            binding.buttonShuffle.setImageResource(if (shuffling)
                R.drawable.ic_shuffle_on
            else
                R.drawable.ic_shuffle_audio)
            binding.buttonShuffle.contentDescription = getString(if (shuffling) org.videolan.vlc.R.string.shuffle_on else org.videolan.vlc.R.string.shuffle)
            if (mw == null || currentCoverArt == mw.artworkMrl) return@launch
            currentCoverArt = mw.artworkMrl
            updateBackground()
        }
    }

    private fun updateBackground() = lifecycleScope.launchWhenStarted {
        val width = if (binding.albumCover.width > 0) binding.albumCover.width else this@AudioPlayerActivity.getScreenWidth()
        val cover = withContext(Dispatchers.IO) { AudioUtil.readCoverBitmap(Uri.decode(currentCoverArt), width) }
        if (cover == null) {
            binding.albumCover.setImageResource(R.drawable.ic_song_big)
            binding.background.clearColorFilter()
            binding.background.setImageResource(0)
        } else {
            UiTools.blurView(binding.background, cover, 15F, UiTools.getColorFromAttribute(binding.background.context, R.attr.audio_player_background_tint))
            binding.albumCover.setImageBitmap(cover)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (playerKeyListenerDelegate.onKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun stop() {
        model.stop()
        finish()
    }

    override fun seek(delta: Int) {
        val time = model.getTime().toInt() + delta
        if (time < 0 || time > model.length) return
        model.setTime(time.toLong())
    }

    override fun isReady() = true

    override fun showAdvancedOptions() {
        showAdvancedOptions(null)
    }

    override fun previous() {
        model.previous(false)
    }

    override fun next() {
        model.next()
    }

    override fun togglePlayPause() {
        model.togglePlayPause()
    }

    override fun showEqualizer() {
        EqualizerFragment().show(supportFragmentManager, "equalizer")
    }

    override fun increaseRate() {
        model.service?.increaseRate()
    }

    override fun decreaseRate() {
        model.service?.decreaseRate()
    }

    override fun resetRate() {
        model.service?.resetRate()
    }

    override fun bookmark() {
        bookmarkModel.addBookmark(this)
        UiTools.snackerConfirm(this, getString(org.videolan.vlc.R.string.bookmark_added), confirmMessage = org.videolan.vlc.R.string.show) {
            showBookmarks()
        }
    }

    fun playSelection() {
        model.play(adapter.selectedItem)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        //Check for a joystick event
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK || event.action != MotionEvent.ACTION_MOVE)
            return false

        val inputDevice = event.device

        val dpadx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val dpady = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (inputDevice == null || abs(dpadx) == 1.0f || abs(dpady) == 1.0f) return false

        val x = AndroidDevices.getCenteredAxis(event, inputDevice,
                MotionEvent.AXIS_X)

        if (abs(x) > 0.3 && System.currentTimeMillis() - lastMove > JOYSTICK_INPUT_DELAY) {
            seek(if (x > 0.0f) 10000 else -10000)
            lastMove = System.currentTimeMillis()
            return true
        }
        return true
    }

    fun onClick(v: View) {
        when (v.id) {
            R.id.button_play -> togglePlayPause()
            R.id.button_next -> next()
            R.id.button_previous -> previous()
            R.id.button_repeat -> switchRepeatMode()
            R.id.button_shuffle -> setShuffleMode(!shuffling)
            R.id.button_more -> showAdvancedOptions(v)
        }
    }

    private fun showAdvancedOptions(@Suppress("UNUSED_PARAMETER") v: View?) {
        if (optionsDelegate == null) {
            val service = model.service ?: return
            optionsDelegate = PlayerOptionsDelegate(this, service, false)
            optionsDelegate?.setBookmarkClickedListener {
                lifecycleScope.launch { if (!showPinIfNeeded()) showBookmarks() }
            }
        }
        optionsDelegate?.show()
    }

    /**
     * Show the bookmarks and initialize the delegate if needed
     */
    private fun showBookmarks() {
        model.service?.let {
            if (!this::bookmarkListDelegate.isInitialized) {
                bookmarkListDelegate = BookmarkListDelegate(this, it, bookmarkModel)
                bookmarkListDelegate.visibilityListener = {
                    if (bookmarkListDelegate.visible) bookmarkListDelegate.rootView.requestFocus()
                    binding.playlist.descendantFocusability = if (bookmarkListDelegate.visible) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
                    binding.playlist.isFocusable = !bookmarkListDelegate.visible
                    binding.sleepQuickAction.isFocusable = !bookmarkListDelegate.visible
                    binding.playbackSpeedQuickAction.isFocusable = !bookmarkListDelegate.visible
                }
                bookmarkListDelegate.markerContainer = binding.bookmarkMarkerContainer
            }
            bookmarkListDelegate.show()
        }
    }

    private fun setShuffleMode(shuffle: Boolean) {
        shuffling = shuffle
        val medias = model.medias?.toMutableList() ?: return
        if (shuffle)
            medias.shuffle()
        else
            medias.sortWith(MediaComparators.BY_TRACK_NUMBER)
        model.load(medias, 0)
    }

    private fun updateRepeatMode() {
        when (model.repeatType) {
            PlaybackStateCompat.REPEAT_MODE_ALL -> {
                binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_all_audio)
                binding.buttonRepeat.contentDescription = getString(R.string.repeat_all)
            }
            PlaybackStateCompat.REPEAT_MODE_ONE -> {
                binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_one_audio)
                binding.buttonRepeat.contentDescription = getString(R.string.repeat_single)
            }
            PlaybackStateCompat.REPEAT_MODE_NONE -> {
                model.repeatType = PlaybackStateCompat.REPEAT_MODE_NONE
                binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_audio)
                binding.buttonRepeat.contentDescription = getString(R.string.repeat_none)
            }
        }
    }

    private fun switchRepeatMode() {
        when (model.repeatType) {
            PlaybackStateCompat.REPEAT_MODE_NONE -> {
                model.repeatType = PlaybackStateCompat.REPEAT_MODE_ALL
                binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_all_audio)
                binding.buttonRepeat.contentDescription = getString(R.string.repeat_all)
            }
            PlaybackStateCompat.REPEAT_MODE_ALL -> {
                model.repeatType = PlaybackStateCompat.REPEAT_MODE_ONE
                binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_one_audio)
                binding.buttonRepeat.contentDescription = getString(R.string.repeat_single)
            }
            PlaybackStateCompat.REPEAT_MODE_ONE -> {
                model.repeatType = PlaybackStateCompat.REPEAT_MODE_NONE
                binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_audio)
                binding.buttonRepeat.contentDescription = getString(R.string.repeat_none)
            }
        }
    }

    fun onUpdateFinished() {
        binding.root.post {
            val position = model.currentMediaPosition
            if (position < 0) return@post
            adapter.setSelection(position)
            val first = (binding.playlist.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
            val last = (binding.playlist.layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()
            if (position < first || position > last) binding.playlist.smoothScrollToPosition(position)
        }
    }

    companion object {
        const val TAG = "VLC/AudioPlayerActivity"

        const val MEDIA_LIST = "media_list"
        const val MEDIA_PLAYLIST = "media_playlist"
        const val MEDIA_POSITION = "media_position"

        //PAD navigation
        private const val JOYSTICK_INPUT_DELAY = 300
    }
}
