/*****************************************************************************
 * AudioPlayer.kt
 *
 * Copyright © 2011-2019 VLC authors and VideoLAN
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
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Vibrator
import android.support.v4.media.session.PlaybackStateCompat
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.*
import org.videolan.tools.*
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.AudioPlayerBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.InfoActivity
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.PlaybackSpeedDialog
import org.videolan.vlc.gui.dialogs.SleepTimerDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.AudioUtil.setRingtone
import org.videolan.vlc.gui.helpers.BookmarkListDelegate
import org.videolan.vlc.gui.helpers.PlayerOptionsDelegate
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.helpers.UiTools.isTablet
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.gui.view.AudioMediaSwitcher
import org.videolan.vlc.gui.view.AudioMediaSwitcher.AudioMediaSwitcherListener
import org.videolan.vlc.manageAbRepeatStep
import org.videolan.vlc.media.PlaylistManager.Companion.hasMedia
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.launchWhenStarted
import org.videolan.vlc.util.share
import org.videolan.vlc.viewmodels.BookmarkModel
import org.videolan.vlc.viewmodels.PlaybackProgress
import org.videolan.vlc.viewmodels.PlaylistModel
import java.text.DateFormat.getTimeInstance
import kotlin.math.absoluteValue

private const val TAG = "VLC/AudioPlayer"
private const val SEARCH_TIMEOUT_MILLIS = 10000L

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@Suppress("UNUSED_PARAMETER")
class AudioPlayer : Fragment(), PlaylistAdapter.IPlayer, TextWatcher, IAudioPlayerAnimator by AudioPlayerAnimator() {

    private lateinit var binding: AudioPlayerBinding
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var settings: SharedPreferences
    private val handler by lazy(LazyThreadSafetyMode.NONE) { Handler() }
    lateinit var playlistModel: PlaylistModel
    lateinit var bookmarkModel: BookmarkModel
    private lateinit var optionsDelegate: PlayerOptionsDelegate
    private lateinit var bookmarkListDelegate: BookmarkListDelegate

    private var showRemainingTime = false
    private var previewingSeek = false
    private var playerState = 0
    private lateinit var pauseToPlay: AnimatedVectorDrawableCompat
    private lateinit var playToPause: AnimatedVectorDrawableCompat
    private lateinit var pauseToPlaySmall: AnimatedVectorDrawableCompat
    private lateinit var playToPauseSmall: AnimatedVectorDrawableCompat

    lateinit var abRepeatAddMarker: Button
    private var audioPlayProgressMode:Boolean = false
    private var lastEndsAt = -1L
    private var isDragging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            playerState = it.getInt("player_state")
            wasPlaying = it.getBoolean("was_playing")
            showRemainingTime = it.getBoolean("show_remaining_time")
        }
        playlistAdapter = PlaylistAdapter(this)
        settings = Settings.getInstance(requireContext())
        playlistModel = PlaylistModel.get(this)
        playlistModel.service?.let {
            if (it.videoTracksCount > 0)
                UiTools.snacker(requireActivity(), R.string.return_to_video)
        }
        playlistModel.progress.observe(this@AudioPlayer) { it?.let { updateProgress(it) } }
        playlistModel.speed.observe(this@AudioPlayer) { showChips() }
        playlistAdapter.setModel(playlistModel)
        playlistModel.dataset.asFlow().conflate().onEach {
            doUpdate()
            playlistAdapter.update(it)
            delay(50L)
        }.launchWhenStarted(lifecycleScope)
        bookmarkModel = BookmarkModel.get(requireActivity())
        PlaybackService.playerSleepTime.observe(this@AudioPlayer) {
            showChips()
        }
        Settings.setAudioControlsChangeListener {
            lifecycleScope.launchWhenStarted {
                doUpdate()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = AudioPlayerBinding.inflate(inflater)
        setupAnimator(binding)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.songsList.layoutManager = LinearLayoutManager(view.context)
        binding.songsList.adapter = playlistAdapter
        binding.audioMediaSwitcher.setAudioMediaSwitcherListener(headerMediaSwitcherListener)
        binding.coverMediaSwitcher.setAudioMediaSwitcherListener(coverMediaSwitcherListener)
        binding.playlistSearchText.editText?.addTextChangedListener(this)
        binding.header.setOnClickListener {
            val activity = activity as AudioPlayerContainerActivity
            activity.slideUpOrDownAudioPlayer()
        }

        val callback = SwipeDragItemTouchHelperCallback(playlistAdapter, true)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.songsList)

        binding.fragment = this

        binding.next.setOnTouchListener(LongSeekListener(true))
        binding.previous.setOnTouchListener(LongSeekListener(false))

        registerForContextMenu(binding.songsList)
        userVisibleHint = true
        binding.timeline.setOnSeekBarChangeListener(timelineListener)

        //For resizing purpose, we have to cache this twice even if it's from the same resource
        playToPause = AnimatedVectorDrawableCompat.create(requireActivity(), R.drawable.anim_play_pause_video)!!
        pauseToPlay = AnimatedVectorDrawableCompat.create(requireActivity(), R.drawable.anim_pause_play_video)!!
        playToPauseSmall = AnimatedVectorDrawableCompat.create(requireActivity(), R.drawable.anim_play_pause_video)!!
        pauseToPlaySmall = AnimatedVectorDrawableCompat.create(requireActivity(), R.drawable.anim_pause_play_video)!!
        onSlide(0f)
        abRepeatAddMarker = binding.abRepeatContainer.findViewById<Button>(R.id.ab_repeat_add_marker)
        playlistModel.service?.playlistManager?.abRepeat?.observe(viewLifecycleOwner) { abvalues ->
            binding.abRepeatA = if (abvalues.start == -1L) -1F else abvalues.start / playlistModel.service!!.playlistManager.player.getLength().toFloat()
            binding.abRepeatB = if (abvalues.stop == -1L) -1F else abvalues.stop / playlistModel.service!!.playlistManager.player.getLength().toFloat()
            binding.abRepeatMarkerA.visibility = if (abvalues.start == -1L) View.GONE else View.VISIBLE
            binding.abRepeatMarkerB.visibility = if (abvalues.stop == -1L) View.GONE else View.VISIBLE
            playlistModel.service?.manageAbRepeatStep(binding.abRepeatReset, binding.abRepeatStop, binding.abRepeatContainer, abRepeatAddMarker)
        }
        playlistModel.service?.playlistManager?.abRepeatOn?.observe(viewLifecycleOwner) {
            binding.abRepeatMarkerGuidelineContainer.visibility = if (it) View.VISIBLE else View.GONE
            abRepeatAddMarker.visibility = if (it) View.VISIBLE else View.GONE
            binding.audioPlayProgress.visibility = if (!shouldHidePlayProgress()) View.VISIBLE else View.GONE

            playlistModel.service?.manageAbRepeatStep(binding.abRepeatReset, binding.abRepeatStop, binding.abRepeatContainer, abRepeatAddMarker)
        }

        abRepeatAddMarker.setOnClickListener {
            playlistModel.service?.playlistManager?.setABRepeatValue(binding.timeline.progress.toLong())
        }

        audioPlayProgressMode = Settings.getInstance(requireActivity()).getBoolean(AUDIO_PLAY_PROGRESS_MODE, false)
        binding.audioPlayProgress.setOnClickListener {
            audioPlayProgressMode = !audioPlayProgressMode
            Settings.getInstance(requireActivity()).putSingle(AUDIO_PLAY_PROGRESS_MODE, audioPlayProgressMode)
            playlistModel.progress.value?.let { updateProgress(it) }
        }
        binding.playbackSpeedQuickAction.setOnClickListener {
            val newFragment = PlaybackSpeedDialog.newInstance()
            newFragment.show(requireActivity().supportFragmentManager, "playback_speed")
        }
        binding.playbackSpeedQuickAction.setOnLongClickListener {
            playlistModel.service?.setRate(1F, true)
            showChips()
            true
        }
        binding.sleepQuickAction.setOnClickListener {
            val newFragment = SleepTimerDialog.newInstance()
            newFragment.show(requireActivity().supportFragmentManager, "time")
        }
        binding.sleepQuickAction.setOnLongClickListener {
            playlistModel.service?.setSleepTimer(null)
            showChips()
            true
        }

        binding.songTitle?.setOnClickListener { coverMediaSwitcherListener.onTextClicked() }
        binding.songSubtitle?.setOnClickListener { coverMediaSwitcherListener.onTextClicked() }

        setBottomMargin()
    }

    fun setBottomMargin() {
        (binding.playPause.layoutParams as? ConstraintLayout.LayoutParams)?.let {
            val audioPlayerContainerActivity = (requireActivity() as AudioPlayerContainerActivity)
            if (audioPlayerContainerActivity.needsTopInset()) it.bottomMargin = it.bottomMargin + audioPlayerContainerActivity.bottomInset
        }
    }

    fun isTablet() = requireActivity().isTablet()

    fun showChips() {
        if (playlistModel.speed.value == 1.0F && PlaybackService.playerSleepTime.value == null) {
            binding.playbackChips.setGone()
        } else {
            binding.playbackChips.setVisible()
            binding.playbackSpeedQuickAction.setGone()
            binding.sleepQuickAction.setGone()
            playlistModel.speed.value?.let {
                if (it != 1.0F) binding.playbackSpeedQuickAction.setVisible()
                binding.playbackSpeedQuickAction.text = it.formatRateString()
            }
            PlaybackService.playerSleepTime.value?.let {
                binding.sleepQuickAction.setVisible()
                binding.sleepQuickAction.text = DateFormat.getTimeFormat(requireContext()).format(it.time)
            }
        }
    }

    override fun onResume() {
        onStateChanged(playerState)
        showRemainingTime = Settings.getInstance(requireContext()).getBoolean(SHOW_REMAINING_TIME, false)
        super.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("player_state", playerState)
        outState.putBoolean("was_playing", wasPlaying)
        outState.putBoolean("show_remaining_time", showRemainingTime)
    }

    private val ctxReceiver: CtxActionReceiver = object : CtxActionReceiver {
        override fun onCtxAction(position: Int, option: Long) {
            if (position in 0 until playlistAdapter.itemCount) when (option) {
                CTX_SET_RINGTONE -> activity?.setRingtone(playlistAdapter.getItem(position))
                CTX_ADD_TO_PLAYLIST -> {
                    val mw = playlistAdapter.getItem(position)
                    requireActivity().addToPlaylist(listOf(mw))
                }
                CTX_REMOVE_FROM_PLAYLIST -> view?.let {
                    val mw = playlistAdapter.getItem(position)
                    val message = String.format(getString(R.string.remove_playlist_item), mw.title)
                    UiTools.snackerWithCancel(requireActivity(), message, true, { })  {
                        playlistModel.insertMedia(position, mw)
                    }
                    playlistModel.remove(position)
                }
                CTX_STOP_AFTER_THIS -> playlistModel.stopAfter(position)
                CTX_INFORMATION -> showInfoDialog(playlistAdapter.getItem(position))
                CTX_SHARE -> lifecycleScope.launch { (requireActivity() as AppCompatActivity).share(playlistAdapter.getItem(position)) }
            }
        }
    }

    private fun showInfoDialog(media: MediaWrapper) {
        val i = Intent(requireActivity(), InfoActivity::class.java)
        i.putExtra(TAG_ITEM, media)
        startActivity(i)
    }

    override fun onPopupMenu(view: View, position: Int, item: MediaWrapper?) {
        val activity = activity
        if (activity === null || position >= playlistAdapter.itemCount) return
        var flags = CTX_REMOVE_FROM_PLAYLIST or CTX_STOP_AFTER_THIS or CTX_INFORMATION
        if (item?.uri?.scheme != "content") flags = flags or CTX_ADD_TO_PLAYLIST or CTX_SHARE or CTX_SET_RINGTONE
        showContext(activity, ctxReceiver, position, item?.title ?: "", flags)
    }

    private suspend fun doUpdate() {
        if (isVisible && playlistModel.switchToVideo()) return
        updatePlayPause()
        updateShuffleMode()
        updateRepeatMode()
        binding.audioMediaSwitcher.updateMedia(playlistModel.service)
        binding.coverMediaSwitcher.updateMedia(playlistModel.service)

        val chapter = playlistModel.service?.getCurrentChapter(true)
        binding.songTitle?.text = if (!chapter.isNullOrEmpty()) chapter else  playlistModel.title
        binding.songSubtitle?.text = if (!chapter.isNullOrEmpty()) TextUtils.separatedString(playlistModel.title, playlistModel.artist) else TextUtils.separatedString(playlistModel.artist, playlistModel.album)
        binding.songTitle?.isSelected = true
        binding.songSubtitle?.isSelected = true
        binding.songTrackInfo?.text = playlistModel.service?.trackInfo()
        binding.songTrackInfo?.visibility = if (Settings.showAudioTrackInfo) View.VISIBLE else View.GONE
        binding.songTrackInfo?.isSelected = true

        binding.audioRewindText.text = "${Settings.audioJumpDelay}"
        binding.audioForwardText.text = "${Settings.audioJumpDelay}"
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
        binding.headerLargePlayPause.setImageDrawable(drawable)
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
        val shuffleButtons = arrayOf(binding.shuffle, binding.headerShuffle)
        binding.canShuffle = playlistModel.canShuffle
        val shuffling = playlistModel.shuffling
        if (wasShuffling == shuffling) return
        shuffleButtons.forEach {
            it.setImageResource(if (shuffling) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_audio)
            it.contentDescription = ctx.getString(if (shuffling) R.string.shuffle_on else R.string.shuffle)
        }
        wasShuffling = shuffling
    }

    private var previousRepeatType = -1
    private fun updateRepeatMode() {
        val ctx = context ?: return
        val repeatType = playlistModel.repeatType
        if (previousRepeatType == repeatType) return
        when (repeatType) {
            PlaybackStateCompat.REPEAT_MODE_ONE -> {
                arrayOf(binding.repeat, binding.headerRepeat).forEach {
                    it.setImageResource(R.drawable.ic_repeat_one_audio)
                    it.contentDescription = ctx.getString(R.string.repeat_single)
                }
            }
            PlaybackStateCompat.REPEAT_MODE_ALL -> {
                arrayOf(binding.repeat, binding.headerRepeat).forEach {
                    it.setImageResource(R.drawable.ic_repeat_all_audio)
                    it.contentDescription = ctx.getString(R.string.repeat_all)
                }
            }
            else -> {
                arrayOf(binding.repeat, binding.headerRepeat).forEach {
                    it.setImageResource(R.drawable.ic_repeat_audio)
                    it.contentDescription = ctx.getString(R.string.repeat)
                }
            }
        }
        previousRepeatType = repeatType
    }

    private fun updateProgress(progress: PlaybackProgress) {
        if (playlistModel.currentMediaPosition == -1) return
        binding.length.text = progress.lengthText
        binding.timeline.max = progress.length.toInt()
        binding.progressBar.max = progress.length.toInt()

        if (!previewingSeek) {
            val displayTime = if (showRemainingTime) Tools.millisToString(progress.time - progress.length) else progress.timeText
            binding.headerTime.text = displayTime
            binding.time.text = displayTime
            if (!isDragging) binding.timeline.progress = progress.time.toInt()
            binding.progressBar.progress = progress.time.toInt()
        }

        lifecycleScope.launchWhenStarted {
            val text = withContext(Dispatchers.Default) {
                val medias = playlistModel.medias ?: return@withContext ""
                withContext(Dispatchers.Main) { if (!shouldHidePlayProgress()) binding.audioPlayProgress.setVisible() else binding.audioPlayProgress.setGone() }
                if (playlistModel.currentMediaPosition == -1) return@withContext ""
                val elapsedTracksTime = playlistModel.previousTotalTime ?: return@withContext ""
                val progressTime = elapsedTracksTime + progress.time
                val totalTime = playlistModel.getTotalTime()
                val progressTimeText = Tools.millisToString(
                    if (showRemainingTime && totalTime > 0) totalTime - progressTime else progressTime,
                    false,
                    true,
                    false
                )
                val totalTimeText = Tools.millisToString(totalTime, false, false, false)
                val currentProgressText = if (progressTimeText.isNullOrEmpty()) "0:00" else progressTimeText

                val textTrack = getString(R.string.track_index, "${playlistModel.currentMediaPosition + 1} / ${medias.size}")

                val textProgress = if (audioPlayProgressMode) {
                    val endsAt = System.currentTimeMillis() + totalTime - progressTime
                    if ((lastEndsAt - endsAt).absoluteValue > 1) lastEndsAt = endsAt
                    getString(
                        R.string.audio_queue_progress_finished,
                        getTimeInstance(java.text.DateFormat.MEDIUM).format(lastEndsAt)
                    )
                } else if (showRemainingTime && totalTime > 0) getString(
                    R.string.audio_queue_progress_remaining,
                        currentProgressText
                )
                else getString(
                        R.string.audio_queue_progress,
                        if (totalTimeText.isNullOrEmpty()) currentProgressText else "$currentProgressText / $totalTimeText"
                    )
                "$textTrack  •  $textProgress"
            }
            binding.audioPlayProgress.text = text
        }
    }

    private fun shouldHidePlayProgress() = abRepeatAddMarker.visibility != View.GONE || (::bookmarkListDelegate.isInitialized && bookmarkListDelegate.visible) || playlistModel.medias?.size ?: 0 < 2

    override fun onSelectionSet(position: Int) {
        binding.songsList.scrollToPosition(position)
    }

    override fun playItem(position: Int, item: MediaWrapper) {
        clearSearch()
        playlistModel.play(playlistModel.getPlaylistPosition(position, item))
    }

    fun onTimeLabelClick(view: View) {
        showRemainingTime = !showRemainingTime
        Settings.getInstance(requireContext()).edit().putBoolean(SHOW_REMAINING_TIME, showRemainingTime).apply()
        playlistModel.progress.value?.let { updateProgress(it) }
    }

    fun onJumpBack(view: View) {
        jump(forward = false, long = false)
    }

    fun onJumpBackLong(view: View):Boolean {
        jump(forward = false, long = true)
        return true
    }

    fun onJumpForward(view: View) {
        jump(forward = true, long = false)
    }

    fun onJumpForwardLong(view: View):Boolean {
        jump(forward = true, long = true)
        return true
    }

    /**
     * Jump backward or forward, with a long or small delay
     * depending on the audio control setting chosen by the user
     *
     * @param forward is the jump forward?
     * @param long has it been triggered by a long tap?
     */
    private fun jump(forward:Boolean, long:Boolean) {
        playlistModel.service ?.let { service ->
            val jumpDelay = if (long) Settings.audioLongJumpDelay else Settings.audioJumpDelay
            val delay = if (forward) jumpDelay * 1000 else -(jumpDelay * 1000)
            var position = service.getTime() + delay
            if (position < 0) position = 0
            if (position > service.length) position = service.length
            service.seek(position, service.length.toDouble(), true)
            if (service.playlistManager.player.lastPosition == 0.0f && (forward || service.getTime() > 0))
                UiTools.snacker(requireActivity(), getString(R.string.unseekable_stream))
        }
    }

    fun onPlayPauseClick(view: View?) {
        if (playlistModel.service?.isPausable == false) {
            UiTools.snackerConfirm(requireActivity(), getString(R.string.stop_unpaubale), true) {
                playlistModel.stop()
            }
            return
        }
        playlistModel.togglePlayPause()
    }

    fun onStopClick(view: View?): Boolean {
        playlistModel.stop()
        if (activity is AudioPlayerContainerActivity)
            (activity as AudioPlayerContainerActivity).closeMiniPlayer()
        return true
    }

    fun onNextClick(view: View?) {
        if (!playlistModel.next()) UiTools.snacker(requireActivity(), R.string.lastsong, true)
    }

    fun onPreviousClick(view: View?) {
        if (!playlistModel.previous()) UiTools.snacker(requireActivity(),  R.string.firstsong)
    }

    fun onRepeatClick(view: View) {
        playlistModel.repeatType = when (playlistModel.repeatType) {
            PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
            PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
            else -> PlaybackStateCompat.REPEAT_MODE_NONE
        }
        updateRepeatMode()
    }

    fun onPlaylistSwitchClick(view: View) {
        switchShowCover()
        settings.putSingle("audio_player_show_cover", isShowingCover())
        lifecycleScope.launch { doUpdate() }
    }

    fun onShuffleClick(view: View) {
        playlistModel.shuffle()
        updateShuffleMode()
    }

    fun onResumeToVideoClick() {
        playlistModel.currentMediaWrapper?.let {
            if (PlaybackService.hasRenderer()) VideoPlayerActivity.startOpened(requireActivity(),
                    it.uri, playlistModel.currentMediaPosition)
            else if (hasMedia()) {
                it.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { playlistModel.switchToVideo() }
            }
        }
    }

    fun showAdvancedOptions(v: View?) {
        if (!isVisible) return
        if (!this::optionsDelegate.isInitialized) {
            val service = playlistModel.service ?: return
            val activity = activity as? AppCompatActivity ?: return
            optionsDelegate = PlayerOptionsDelegate(activity, service)
            optionsDelegate.setBookmarkClickedListener {
                showBookmarks()
            }
        }
        optionsDelegate.show()
    }

    /**
     * Show bookmark and initialize the delegate if needed
     */
    fun showBookmarks() {
        val service = playlistModel.service ?: return
        if (!this::bookmarkListDelegate.isInitialized) {
            bookmarkListDelegate = BookmarkListDelegate(requireActivity(), service, bookmarkModel)
            bookmarkListDelegate.visibilityListener = {
                binding.audioPlayProgress.visibility = if (shouldHidePlayProgress()) View.GONE else View.VISIBLE
            }
            bookmarkListDelegate.markerContainer = binding.bookmarkMarkerContainer
        }
        bookmarkListDelegate.show()
        bookmarkListDelegate.setProgressHeight(binding.time.y)
    }

    fun onSearchClick(v: View) {
        manageSearchVisibilities(true)
        binding.playlistSearchText.editText?.requestFocus()
        if (isShowingCover()) onPlaylistSwitchClick(binding.playlistSwitch)
        val imm = v.context.applicationContext.getSystemService<InputMethodManager>()!!
        imm.showSoftInput(binding.playlistSearchText.editText, InputMethodManager.SHOW_IMPLICIT)
        handler.postDelayed(hideSearchRunnable, SEARCH_TIMEOUT_MILLIS)
    }

    fun onABRepeatStopClick(v: View) {
        playlistModel.service?.playlistManager?.clearABRepeat()
    }

    fun onABRepeatResetClick(v: View) {
        playlistModel.service?.playlistManager?.resetABRepeatValues()
    }

    override fun beforeTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {}

    fun backPressed(): Boolean {
        if (this::optionsDelegate.isInitialized && optionsDelegate.isShowing()) {
            optionsDelegate.hide()
            return true
        }
        if (::bookmarkListDelegate.isInitialized && bookmarkListDelegate.visible) {
            bookmarkListDelegate.hide()
            return true
        }
        return clearSearch()
    }

    fun clearSearch(): Boolean {
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
        manageSearchVisibilities(false)
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

    private inner class LongSeekListener(var forward: Boolean) : View.OnTouchListener {
        var length = -1L

        var possibleSeek = 0
        var vibrated = false

        @RequiresPermission(Manifest.permission.VIBRATE)
        var seekRunnable: Runnable = object : Runnable {
            override fun run() {
                if (!vibrated) {
                    AppContextProvider.appContext.getSystemService<Vibrator>()?.vibrate(80)
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
                    possibleSeek = playlistModel.getTime().toInt()
                    previewingSeek = true
                    vibrated = false
                    length = playlistModel.length
                    handler.postDelayed(seekRunnable, 1000)
                    return false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(seekRunnable)
                    previewingSeek = false
                    if (event.eventTime - event.downTime >= 1000L) {
                        playlistModel.setTime(possibleSeek.toLong().coerceAtLeast(0L).coerceAtMost(playlistModel.length))
                        v.isPressed = false
                        return true
                    }
                    return false
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
                onSlide(0f)
            }
            BottomSheetBehavior.STATE_EXPANDED -> {
                onSlide(1f)
                showPlaylistTips()
                playlistAdapter.currentIndex = playlistModel.currentMediaPosition
            }
        }
    }

    private var timelineListener: OnSeekBarChangeListener = object : OnSeekBarChangeListener {

        override fun onStopTrackingTouch(seekBar: SeekBar) {
             playlistModel.setTime(seekBar.progress.toLong())
            isDragging = false
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            isDragging = true
        }

        override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                playlistModel.setTime(progress.toLong(), true)
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

        override fun onTouchLongClick() {
            val trackInfo = playlistModel.title ?: return

            if (playlistModel.videoTrackCount > 0) onResumeToVideoClick()
            else {
                requireActivity().copy("VLC - song name", trackInfo)
                UiTools.snacker(requireActivity(), R.string.track_info_copied_to_clipboard)
            }
        }

        override fun onTouchDown() {}

        override fun onTouchUp() {}

        override fun onTextClicked() { }
    }

    private val coverMediaSwitcherListener = object : AudioMediaSwitcherListener by AudioMediaSwitcher.EmptySwitcherListener {

        override fun onMediaSwitching() {
            (activity as? AudioPlayerContainerActivity)?.playerBehavior?.lock(true)
        }

        override fun onMediaSwitched(position: Int) {
            when (position) {
                AudioMediaSwitcherListener.PREVIOUS_MEDIA -> playlistModel.previous(true)
                AudioMediaSwitcherListener.NEXT_MEDIA -> playlistModel.next()
            }
            (activity as? AudioPlayerContainerActivity)?.playerBehavior?.lock(false)
        }

        override fun onTextClicked() {
            Settings.getInstance(requireActivity()).putSingle(KEY_SHOW_TRACK_INFO, !Settings.showAudioTrackInfo)
            Settings.showAudioTrackInfo = !Settings.showAudioTrackInfo
            lifecycleScope.launch { doUpdate() }
        }
    }

    fun retrieveAbRepeatAddMarker():Button? {
        if (!::abRepeatAddMarker.isInitialized) return null
        return abRepeatAddMarker
    }

    private val hideSearchRunnable by lazy(LazyThreadSafetyMode.NONE) {
        Runnable {
            hideSearchField()
            playlistModel.filter(null)
        }
    }
}
