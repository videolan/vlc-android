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
import android.os.Handler
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.television.R
import org.videolan.television.databinding.TvAudioPlayerBinding
import org.videolan.television.ui.browser.BaseTvActivity
import org.videolan.tools.Settings
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.MediaComparators
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.getScreenWidth
import org.videolan.vlc.viewmodels.PlayerState
import org.videolan.vlc.viewmodels.PlaylistModel
import java.lang.Runnable
import kotlin.math.abs

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class AudioPlayerActivity : BaseTvActivity() {

    private lateinit var binding: TvAudioPlayerBinding
    private lateinit var adapter: PlaylistAdapter
    private val handler = Handler()
    private var lastMove: Long = 0
    private var shuffling = false
    private var currentCoverArt: String? = null
    private lateinit var model: PlaylistModel
    private var settings: SharedPreferences? = null
    private lateinit var pauseToPlay: AnimatedVectorDrawableCompat
    private lateinit var playToPause: AnimatedVectorDrawableCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.tv_audio_player)
        settings = Settings.getInstance(this)

        model = ViewModelProviders.of(this).get(PlaylistModel::class.java)
        binding.playlist.layoutManager = LinearLayoutManager(this)
        binding.playlist.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        adapter = PlaylistAdapter(this, model)
        binding.playlist.adapter = adapter
        binding.lifecycleOwner = this
        binding.progress = model.progress
        model.dataset.observe(this, Observer<List<MediaWrapper>> { mediaWrappers ->
            if (mediaWrappers != null) {
                adapter.setSelection(-1)
                adapter.update(mediaWrappers)
            }
        })
        model.playerState.observe(this, Observer { playerState -> update(playerState) })
        val medialist = intent.getParcelableArrayListExtra<MediaWrapper>(MEDIA_LIST)
        val position = intent.getIntExtra(MEDIA_POSITION, 0)
        if (medialist != null) MediaUtils.openList(this, medialist, position)
        playToPause = AnimatedVectorDrawableCompat.create(this, R.drawable.anim_play_pause)!!
        pauseToPlay = AnimatedVectorDrawableCompat.create(this, R.drawable.anim_pause_play)!!
    }

    override fun refresh() {}

    private var wasPlaying = false
    fun update(state: PlayerState?) {
        if (state == null) return

        val drawable = if (state.playing) playToPause else pauseToPlay
        binding.buttonPlay.setImageDrawable(drawable)
        if (state.playing != wasPlaying) {
            drawable.start()
        }

        wasPlaying = state.playing

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
                R.drawable.ic_shuffle)
            if (mw == null || TextUtils.equals(currentCoverArt, mw.artworkMrl)) return@launch
            currentCoverArt = mw.artworkMrl
            updateBackground()
        }
    }

    private fun updateBackground() = lifecycleScope.launchWhenStarted {
        val width = if (binding.albumCover.width > 0) binding.albumCover.width else this@AudioPlayerActivity.getScreenWidth()
        val cover = withContext(Dispatchers.IO) { AudioUtil.readCoverBitmap(Uri.decode(currentCoverArt), width) }
        val blurredCover = if (cover != null) withContext(Dispatchers.Default) { UiTools.blurBitmap(cover) } else null
        if (cover == null) {
            binding.albumCover.setImageResource(R.drawable.ic_no_artwork_big)
            binding.background.clearColorFilter()
            binding.background.setImageResource(0)
        } else {
            binding.albumCover.setImageBitmap(cover)
            binding.background.setColorFilter(UiTools.getColorFromAttribute(binding.background.context, R.attr.audio_player_background_tint))
            binding.background.setImageBitmap(blurredCover)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            /*
             * Playback control
             */
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                model.stop()
                finish()
                return true
            }
            KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_BUTTON_R1 -> {
                goNext()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> return if (binding.mediaProgress.hasFocus()) {
                seek(10000)
                true
            } else
                false
            KeyEvent.KEYCODE_DPAD_LEFT -> return if (binding.mediaProgress.hasFocus()) {
                seek(-10000)
                true
            } else
                false
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seek(10000)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seek(-10000)
                return true
            }
            KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_BUTTON_L1 -> {
                goPrevious()
                return true
            }
            else -> return super.onKeyDown(keyCode, event)
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

    private fun seek(delta: Int) {
        val time = model.time.toInt() + delta
        if (time < 0 || time > model.length) return
        model.time = time.toLong()
    }

    fun onClick(v: View) {
        when (v.id) {
            R.id.button_play -> togglePlayPause()
            R.id.button_next -> goNext()
            R.id.button_previous -> goPrevious()
            R.id.button_repeat -> updateRepeatMode()
            R.id.button_shuffle -> setShuffleMode(!shuffling)
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
            PlaybackStateCompat.REPEAT_MODE_NONE -> {
                model.repeatType = PlaybackStateCompat.REPEAT_MODE_ALL
                binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_all)
            }
            PlaybackStateCompat.REPEAT_MODE_ALL -> {
                model.repeatType = PlaybackStateCompat.REPEAT_MODE_ONE
                binding.buttonRepeat.setImageResource(R.drawable.ic_repeat_one)
            }
            PlaybackStateCompat.REPEAT_MODE_ONE -> {
                model.repeatType = PlaybackStateCompat.REPEAT_MODE_NONE
                binding.buttonRepeat.setImageResource(R.drawable.ic_repeat)
            }
        }
    }

    private fun goPrevious() {
        model.previous(false)
    }

    private fun goNext() {
        model.next()
    }

    private fun togglePlayPause() {
        model.togglePlayPause()
    }

    fun onUpdateFinished() {
        handler.post(Runnable {
            val position = model.currentMediaPosition
            if (position < 0) return@Runnable
            adapter.setSelection(position)
            val first = (binding.playlist.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
            val last = (binding.playlist.layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()
            if (position < first || position > last) binding.playlist.smoothScrollToPosition(position)
        })
    }

    companion object {
        const val TAG = "VLC/AudioPlayerActivity"

        const val MEDIA_LIST = "media_list"
        const val MEDIA_POSITION = "media_position"

        //PAD navigation
        private const val JOYSTICK_INPUT_DELAY = 300
    }
}
