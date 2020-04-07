/*****************************************************************************
 * AudioPlayer.java
 *
 * Copyright © 2011-2014 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.audio

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.MainThread
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.tools.isStarted
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.databinding.AudioPlayerBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.InfoActivity
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.*
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.gui.view.AudioMediaSwitcher.AudioMediaSwitcherListener
import org.videolan.vlc.media.PlaylistManager.Companion.hasMedia
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.PlaybackProgress
import org.videolan.vlc.viewmodels.PlaylistModel

private const val TAG = "VLC/AudioPlayer"
private const val SEARCH_TIMEOUT_MILLIS = 10000L

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@Suppress("UNUSED_PARAMETER")
class AudioPlayer : Fragment(), PlaylistAdapter.IPlayer, TextWatcher, CoroutineScope by MainScope() {

    private lateinit var binding: AudioPlayerBinding
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var settings: SharedPreferences
    private val handler by lazy(LazyThreadSafetyMode.NONE) { Handler() }
    private val updateActor = actor<Unit>(capacity = Channel.CONFLATED) { for (entry in channel) doUpdate() }
    private lateinit var playlistModel: PlaylistModel
    private lateinit var optionsDelegate: PlayerOptionsDelegate

    private var showRemainingTime = false
    private var previewingSeek = false
    private var advFuncVisible = false
    private var playlistSwitchVisible = false
    private var searchVisible = false
    private var searchTextVisible = false
    private var headerPlayPauseVisible = false
    private var progressBarVisible = false
    private var headerTimeVisible = false
    private var playerState = 0
    private var currentCoverArt: String? = null
    private lateinit var pauseToPlay: AnimatedVectorDrawableCompat
    private lateinit var playToPause: AnimatedVectorDrawableCompat
    private lateinit var pauseToPlaySmall: AnimatedVectorDrawableCompat
    private lateinit var playToPauseSmall: AnimatedVectorDrawableCompat

    companion object {
        private var DEFAULT_BACKGROUND_DARKER_ID = 0
        private var DEFAULT_BACKGROUND_ID = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            playerState = it.getInt("player_state")
            wasPlaying = it.getBoolean("was_playing")
        }
        playlistAdapter = PlaylistAdapter(this)
        settings = Settings.getInstance(requireContext())
        playlistModel = PlaylistModel.get(this)
        playlistModel.progress.observe(this@AudioPlayer, Observer { it?.let { updateProgress(it) } })
        playlistModel.dataset.observe(this@AudioPlayer, playlistObserver)
        playlistAdapter.setModel(playlistModel)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = AudioPlayerBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DEFAULT_BACKGROUND_DARKER_ID = UiTools.getResourceFromAttribute(view.context, R.attr.background_default_darker)
        DEFAULT_BACKGROUND_ID = UiTools.getResourceFromAttribute(view.context, R.attr.background_default)
        binding.songsList.layoutManager = LinearLayoutManager(view.context)
        binding.songsList.adapter = playlistAdapter
        binding.audioMediaSwitcher.setAudioMediaSwitcherListener(headerMediaSwitcherListener)
        binding.coverMediaSwitcher.setAudioMediaSwitcherListener(mCoverMediaSwitcherListener)
        binding.playlistSearchText.editText?.addTextChangedListener(this)
        if (!AndroidUtil.isLolliPopOrLater) binding.songsList.itemAnimator = null

        val callback = SwipeDragItemTouchHelperCallback(playlistAdapter, true)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.songsList)

        setHeaderVisibilities(false, false, true, true, true, false)
        binding.fragment = this

        binding.next.setOnTouchListener(LongSeekListener(true,
                UiTools.getResourceFromAttribute(view.context, R.attr.ic_next),
                R.drawable.ic_next_pressed))
        binding.previous.setOnTouchListener(LongSeekListener(false,
                UiTools.getResourceFromAttribute(view.context, R.attr.ic_previous),
                R.drawable.ic_previous_pressed))

        registerForContextMenu(binding.songsList)
        userVisibleHint = true
        binding.showCover = settings.getBoolean("audio_player_show_cover", false)
        binding.playlistSwitch.setImageResource(UiTools.getResourceFromAttribute(view.context, if (binding.showCover) R.attr.ic_playlist else R.attr.ic_playlist_on))
        binding.timeline.setOnSeekBarChangeListener(timelineListener)

        //For resizing purpose, we have to cache this twice even if it's from the same resource
        playToPause = AnimatedVectorDrawableCompat.create(requireActivity(), R.drawable.anim_play_pause)!!
        pauseToPlay = AnimatedVectorDrawableCompat.create(requireActivity(), R.drawable.anim_pause_play)!!
        playToPauseSmall = AnimatedVectorDrawableCompat.create(requireActivity(), R.drawable.anim_play_pause)!!
        pauseToPlaySmall = AnimatedVectorDrawableCompat.create(requireActivity(), R.drawable.anim_pause_play)!!
    }

    override fun onResume() {
        onStateChanged(playerState)
        super.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("player_state", playerState)
        outState.putBoolean("was_playing", wasPlaying)
    }

    private val ctxReceiver: CtxActionReceiver = object : CtxActionReceiver {
        override fun onCtxAction(position: Int, option: Int) {
            if (position in 0 until playlistAdapter.itemCount) when (option) {
                CTX_SET_RINGTONE -> AudioUtil.setRingtone(playlistAdapter.getItem(position), requireActivity())
                CTX_ADD_TO_PLAYLIST -> {
                    val mw = playlistAdapter.getItem(position)
                    UiTools.addToPlaylist(requireActivity(), listOf(mw))
                }
                CTX_REMOVE_FROM_PLAYLIST -> view?.let {
                    val mw = playlistAdapter.getItem(position)
                    val cancelAction = Runnable { playlistModel.insertMedia(position, mw) }
                    val message = String.format(getString(R.string.remove_playlist_item), mw.title)
                    UiTools.snackerWithCancel(it, message, null, cancelAction)
                    playlistModel.remove(position)
                }
                CTX_STOP_AFTER_THIS -> playlistModel.stopAfter(position)
                CTX_INFORMATION -> showInfoDialog(playlistAdapter.getItem(position))
            }
        }
    }

    private fun showInfoDialog(media: AbstractMediaWrapper) {
        val i = Intent(requireActivity(), InfoActivity::class.java)
        i.putExtra(TAG_ITEM, media)
        startActivity(i)
    }

    override fun onPopupMenu(view: View, position: Int, item: AbstractMediaWrapper?) {
        val activity = activity
        if (activity === null || position >= playlistAdapter.itemCount) return
        val flags = CTX_REMOVE_FROM_PLAYLIST or CTX_SET_RINGTONE or CTX_ADD_TO_PLAYLIST or CTX_STOP_AFTER_THIS or CTX_INFORMATION
        showContext(activity, ctxReceiver, position, item?.title ?: "", flags)
    }

    private suspend fun doUpdate() {
        if (activity === null || (isVisible && playlistModel.switchToVideo())) return
        binding.playlistPlayasaudioOff.visibility = if (playlistModel.videoTrackCount > 0) View.VISIBLE else View.GONE
        binding.audioMediaSwitcher.updateMedia(this, playlistModel.service)
        binding.coverMediaSwitcher.updateMedia(this, playlistModel.service)

        updatePlayPause()
        updateShuffleMode()
        updateRepeatMode()
        updateBackground()
    }

    private var wasPlaying = true
    private fun updatePlayPause() {
        val ctx = context ?: return
        val playing = playlistModel.playing
        val text = ctx.getString(if (playing) R.string.pause else R.string.play)

        val drawable = if (playing) playToPause else pauseToPlay
        val drawableSmall = if (playing) playToPauseSmall else pauseToPlaySmall
        binding.playPause.setImageDrawable(drawable)
        binding.headerPlayPause.setImageDrawable(drawableSmall)
        if (playing != wasPlaying) {
            drawable.start()
            drawableSmall.start()
        }

        playlistAdapter.setCurrentlyPlaying(playing)
        binding.playPause.contentDescription = text
        binding.headerPlayPause.contentDescription = text
        wasPlaying = playing
    }

    private var wasShuffling = false
    private fun updateShuffleMode() {
        val ctx = context ?: return
        binding.shuffle.visibility = if (playlistModel.canShuffle) View.VISIBLE else View.INVISIBLE
        val shuffling = playlistModel.shuffling
        if (wasShuffling == shuffling) return
        binding.shuffle.setImageResource(if (shuffling) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle)
        binding.shuffle.contentDescription = ctx.getString(if (shuffling) R.string.shuffle_on else R.string.shuffle)
        wasShuffling = shuffling
    }

    private var previousRepeatType = -1
    private fun updateRepeatMode() {
        val ctx = context ?: return
        val repeatType = playlistModel.repeatType
        if (previousRepeatType == repeatType) return
        when (repeatType) {
            REPEAT_ONE -> {
                binding.repeat.setImageResource(R.drawable.ic_repeat_one)
                binding.repeat.contentDescription = ctx.getString(R.string.repeat_single)
            }
            REPEAT_ALL -> {
                binding.repeat.setImageResource(R.drawable.ic_repeat_all)
                binding.repeat.contentDescription = ctx.getString(R.string.repeat_all)
            }
            else -> {
                binding.repeat.setImageResource(R.drawable.ic_repeat)
                binding.repeat.contentDescription = ctx.getString(R.string.repeat)
            }
        }
        previousRepeatType = repeatType
    }

    private fun updateProgress(progress: PlaybackProgress) {
        binding.length.text = progress.lengthText
        binding.timeline.max = progress.length.toInt()
        binding.progressBar.max = progress.length.toInt()

        if (!previewingSeek) {
            val displayTime = if (showRemainingTime) Tools.millisToString(progress.time - progress.length) else progress.timeText
            binding.headerTime.text = displayTime
            binding.time.text = displayTime
            binding.timeline.progress = progress.time.toInt()
            binding.progressBar.progress = progress.time.toInt()
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private suspend fun updateBackground() {
        if (settings.getBoolean("blurred_cover_background", true)) {
            val mw = playlistModel.currentMediaWrapper
            if (!isStarted() || mw === null || TextUtils.equals(currentCoverArt, mw.artworkMrl)) return
            currentCoverArt = mw.artworkMrl
            if (TextUtils.isEmpty(mw.artworkMrl)) {
                setDefaultBackground()
            } else {
                val width = if (binding.contentLayout.width > 0) binding.contentLayout.width else activity?.getScreenWidth()
                        ?: return
                val blurredCover = withContext(Dispatchers.IO) { UiTools.blurBitmap(AudioUtil.readCoverBitmap(Uri.decode(mw.artworkMrl), width)) }
                if (!isStarted()) return
                if (blurredCover !== null) {
                    val activity = activity as? AudioPlayerContainerActivity ?: return
                    binding.backgroundView.setColorFilter(UiTools.getColorFromAttribute(activity, R.attr.audio_player_background_tint))
                    binding.backgroundView.setImageBitmap(blurredCover)
                    binding.backgroundView.visibility = View.VISIBLE
                    binding.songsList.setBackgroundResource(0)
                    binding.header.setBackgroundColor(UiTools.getColorFromAttribute(requireContext(), R.attr.audio_player_transparent))
                } else setDefaultBackground()
            }
        }
    }

    @MainThread
    private fun setDefaultBackground() {
        binding.songsList.setBackgroundResource(DEFAULT_BACKGROUND_ID)
        binding.header.setBackgroundResource(DEFAULT_BACKGROUND_ID)
        binding.backgroundView.visibility = View.INVISIBLE
    }

    override fun onSelectionSet(position: Int) {
        if (playerState != BottomSheetBehavior.STATE_COLLAPSED && playerState != com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN) {
            binding.songsList.scrollToPosition(position)
        }
    }

    override fun playItem(position: Int, item: AbstractMediaWrapper) {
        clearSearch()
        playlistModel.play(playlistModel.getPlaylistPosition(position, item))
    }

    fun onTimeLabelClick(view: View) {
        showRemainingTime = !showRemainingTime
        playlistModel.progress.value?.let { updateProgress(it) }
    }

    fun onPlayPauseClick(view: View) {
        playlistModel.togglePlayPause()
    }

    fun onStopClick(view: View): Boolean {
        playlistModel.stop()
        return true
    }

    fun onNextClick(view: View) {
        if (!playlistModel.next()) Snackbar.make(binding.root, R.string.lastsong, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
    }

    fun onPreviousClick(view: View) {
        if (!playlistModel.previous()) Snackbar.make(binding.root, R.string.firstsong, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
    }

    fun onRepeatClick(view: View) {
        when (playlistModel.repeatType) {
            REPEAT_NONE -> playlistModel.repeatType = REPEAT_ALL
            REPEAT_ALL -> playlistModel.repeatType = REPEAT_ONE
            else -> playlistModel.repeatType = REPEAT_NONE
        }
        updateRepeatMode()
    }

    fun onPlaylistSwitchClick(view: View) {
        binding.showCover = !binding.showCover
        settings.edit().putBoolean("audio_player_show_cover", binding.showCover).apply()
        binding.playlistSwitch.setImageResource(UiTools.getResourceFromAttribute(view.context, if (binding.showCover) R.attr.ic_playlist else R.attr.ic_playlist_on))
    }

    fun onShuffleClick(view: View) {
        playlistModel.shuffle()
        updateShuffleMode()
    }

    fun onResumeToVideoClick(v: View) {
        playlistModel.currentMediaWrapper?.let {
            if (PlaybackService.hasRenderer()) VideoPlayerActivity.startOpened(v.context,
                    it.uri, playlistModel.currentMediaPosition)
            else if (hasMedia()) {
                it.removeFlags(AbstractMediaWrapper.MEDIA_FORCE_AUDIO)
                launch(start = CoroutineStart.UNDISPATCHED) { playlistModel.switchToVideo() }
            }
        }
    }

    fun showAdvancedOptions(v: View) {
        if (!isVisible) return
        if (!this::optionsDelegate.isInitialized) {
            val service = playlistModel.service ?: return
            val activity = activity as? AppCompatActivity ?: return
            optionsDelegate = PlayerOptionsDelegate(activity, service)
        }
        optionsDelegate.show(PlayerOptionType.ADVANCED)
    }

    private fun setHeaderVisibilities(advFuncVisible: Boolean, playlistSwitchVisible: Boolean,
                                      headerPlayPauseVisible: Boolean, progressBarVisible: Boolean,
                                      headerTimeVisible: Boolean, searchVisible: Boolean,
                                      filter: Boolean = false) {
        this.advFuncVisible = !filter && advFuncVisible
        this.playlistSwitchVisible = !filter && playlistSwitchVisible && resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        this.headerPlayPauseVisible = !filter && headerPlayPauseVisible
        this.progressBarVisible = !filter && progressBarVisible
        this.headerTimeVisible = !filter && headerTimeVisible
        this.searchVisible = !filter && searchVisible
        this.searchTextVisible = filter
        restoreHeaderButtonVisibilities()
    }

    private fun restoreHeaderButtonVisibilities() {
        binding.progressBar.visibility = if (progressBarVisible) View.VISIBLE else View.INVISIBLE
        val cl = binding.header
        TransitionManager.beginDelayedTransition(cl, AutoTransition().setDuration(200))
        ConstraintSet().apply {
            clone(cl)
            setVisibility(R.id.playlist_search, if (searchVisible) ConstraintSet.VISIBLE else ConstraintSet.GONE)
            setVisibility(R.id.playlist_switch, if (playlistSwitchVisible) ConstraintSet.VISIBLE else ConstraintSet.GONE)
            setVisibility(R.id.adv_function, if (advFuncVisible) ConstraintSet.VISIBLE else ConstraintSet.GONE)
            setVisibility(R.id.header_play_pause, if (headerPlayPauseVisible) ConstraintSet.VISIBLE else ConstraintSet.GONE)
            setVisibility(R.id.header_time, if (headerTimeVisible) ConstraintSet.VISIBLE else ConstraintSet.GONE)
            setVisibility(R.id.playlist_search_text, if (searchTextVisible) ConstraintSet.VISIBLE else ConstraintSet.GONE)
            setVisibility(R.id.audio_media_switcher, if (searchTextVisible) ConstraintSet.GONE else ConstraintSet.VISIBLE)
            applyTo(cl)
        }
    }

    fun onABRepeat(v: View) {
        playlistModel.toggleABRepeat()
    }

    fun onSearchClick(v: View) {
        setHeaderVisibilities(false, false, false, false, false, false, true)
        binding.playlistSearchText.editText?.requestFocus()
        if (binding.showCover) onPlaylistSwitchClick(binding.playlistSwitch)
        val imm = v.context.applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.playlistSearchText.editText, InputMethodManager.SHOW_IMPLICIT)
        handler.postDelayed(hideSearchRunnable, SEARCH_TIMEOUT_MILLIS)
    }

    override fun beforeTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {}

    fun backPressed(): Boolean {
        if (this::optionsDelegate.isInitialized && optionsDelegate.isShowing()) {
            optionsDelegate.hide()
            return true
        }
        return clearSearch()
    }

    private fun clearSearch(): Boolean {
        if (this::playlistModel.isInitialized) playlistModel.filter(null)
        return hideSearchField()
    }

    private fun hideSearchField(): Boolean {
        if (binding.playlistSearchText.visibility != View.VISIBLE) return false
        binding.playlistSearchText.editText?.apply {
            removeTextChangedListener(this@AudioPlayer)
            setText("")
            addTextChangedListener(this@AudioPlayer)
        }
        UiTools.setKeyboardVisibility(binding.playlistSearchText, false)
        if (playerState == BottomSheetBehavior.STATE_COLLAPSED) setHeaderVisibilities(false, false, true, true, true, false)
        else setHeaderVisibilities(true, true, false, false, false, true)
        return true
    }

    override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
        val length = charSequence.length
        if (length > 0) {
            playlistModel.filter(charSequence)
            handler.removeCallbacks(hideSearchRunnable)
        } else {
            playlistModel.filter(null)
            hideSearchField()
        }
    }

    override fun afterTextChanged(editable: Editable) {}

    private val playlistObserver = Observer<MutableList<AbstractMediaWrapper>> {
        playlistAdapter.update(it!!)
        updateActor.offer(Unit)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::optionsDelegate.isInitialized) optionsDelegate.release()
        playlistModel.dataset.removeObserver(playlistObserver)
    }

    private inner class LongSeekListener(internal var forward: Boolean, internal var normal: Int, internal var pressed: Int) : View.OnTouchListener {
        internal var length = -1L

        internal var possibleSeek = 0
        internal var vibrated = false

        @RequiresPermission(Manifest.permission.VIBRATE)
        internal var seekRunnable: Runnable = object : Runnable {
            override fun run() {
                if (!vibrated) {
                    (VLCApplication.appContext.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator)
                            .vibrate(80)
                    vibrated = true
                }

                if (forward) {
                    if (length <= 0 || possibleSeek < length) possibleSeek += 4000
                } else {
                    if (possibleSeek > 4000) possibleSeek -= 4000
                    else if (possibleSeek <= 4000) possibleSeek = 0
                }

                binding.time.text = Tools.millisToString(if (showRemainingTime) possibleSeek - length else possibleSeek.toLong())
                binding.timeline.progress = possibleSeek
                binding.progressBar.progress = possibleSeek
                handler.postDelayed(this, 50)
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    (if (forward) binding.next else binding.previous).setImageResource(this.pressed)
                    possibleSeek = playlistModel.time.toInt()
                    previewingSeek = true
                    vibrated = false
                    length = playlistModel.length
                    handler.postDelayed(seekRunnable, 1000)
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    (if (forward) binding.next else binding.previous).setImageResource(this.normal)
                    handler.removeCallbacks(seekRunnable)
                    previewingSeek = false
                    if (event.eventTime - event.downTime < 1000) {
                        if (forward) onNextClick(v) else onPreviousClick(v)
                    } else {
                        if (forward) {
                            if (possibleSeek < playlistModel.length)
                                playlistModel.time = possibleSeek.toLong()
                            else
                                onNextClick(v)
                        } else {
                            if (possibleSeek > 0)
                                playlistModel.time = possibleSeek.toLong()
                            else
                                onPreviousClick(v)
                        }
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun showPlaylistTips() {
        val activity = activity as? AudioPlayerContainerActivity
        activity?.showTipViewIfNeeded(R.id.audio_playlist_tips, PREF_PLAYLIST_TIPS_SHOWN)
    }

    fun onStateChanged(newState: Int) {
        playerState = newState
        when (newState) {
            BottomSheetBehavior.STATE_COLLAPSED -> {
                backPressed()
                binding.header.setBackgroundResource(DEFAULT_BACKGROUND_DARKER_ID)
                setHeaderVisibilities(advFuncVisible = false, playlistSwitchVisible = false, headerPlayPauseVisible = true, progressBarVisible = true, headerTimeVisible = true, searchVisible = false)
            }
            BottomSheetBehavior.STATE_EXPANDED -> {
                binding.header.setBackgroundColor(UiTools.getColorFromAttribute(requireContext(), R.attr.audio_player_transparent))
                setHeaderVisibilities(advFuncVisible = true, playlistSwitchVisible = true, headerPlayPauseVisible = false, progressBarVisible = false, headerTimeVisible = false, searchVisible = true)
                showPlaylistTips()
                playlistAdapter.currentIndex = playlistModel.currentMediaPosition
            }
//            else -> binding.header.setBackgroundResource(0)
        }
    }

    private var timelineListener: OnSeekBarChangeListener = object : OnSeekBarChangeListener {

        override fun onStopTrackingTouch(seekBar: SeekBar) {}

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                playlistModel.time = progress.toLong()
                binding.time.text = Tools.millisToString(if (showRemainingTime) progress - playlistModel.length else progress.toLong())
                binding.headerTime.text = Tools.millisToString(progress.toLong())
            }
        }
    }

    private val headerMediaSwitcherListener = object : AudioMediaSwitcherListener {

        override fun onMediaSwitching() {}

        override fun onMediaSwitched(position: Int) {
            when (position) {
                AudioMediaSwitcherListener.PREVIOUS_MEDIA -> playlistModel.previous(true)
                AudioMediaSwitcherListener.NEXT_MEDIA -> playlistModel.next()
            }
        }

        override fun onTouchClick() {
            val activity = activity as AudioPlayerContainerActivity
            activity.slideUpOrDownAudioPlayer()
        }

        override fun onTouchDown() {}

        override fun onTouchUp() {}
    }

    private val mCoverMediaSwitcherListener = object : AudioMediaSwitcherListener {

        override fun onMediaSwitching() {
            (activity as? AudioPlayerContainerActivity)?.bottomSheetBehavior?.lock(true)
        }

        override fun onMediaSwitched(position: Int) {
            when (position) {
                AudioMediaSwitcherListener.PREVIOUS_MEDIA -> playlistModel.previous(true)
                AudioMediaSwitcherListener.NEXT_MEDIA -> playlistModel.next()
            }
            (activity as? AudioPlayerContainerActivity)?.bottomSheetBehavior?.lock(false)
        }

        override fun onTouchDown() {}

        override fun onTouchUp() {}

        override fun onTouchClick() {}
    }

    private val hideSearchRunnable by lazy(LazyThreadSafetyMode.NONE) {
        Runnable {
            hideSearchField()
            playlistModel.filter(null)
        }
    }
}
