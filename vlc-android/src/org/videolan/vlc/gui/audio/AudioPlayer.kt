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
import android.arch.lifecycle.Observer
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.support.annotation.MainThread
import android.support.annotation.RequiresPermission
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.helper.ItemTouchHelper
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
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.databinding.AudioPlayerBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.PlaybackServiceActivity
import org.videolan.vlc.gui.dialogs.AdvOptionsDialog
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.gui.view.AudioMediaSwitcher.AudioMediaSwitcherListener
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.Constants
import org.videolan.vlc.util.VLCIO
import org.videolan.vlc.viewmodels.PlaybackProgress
import org.videolan.vlc.viewmodels.PlaylistModel

private const val TAG = "VLC/AudioPlayer"
private const val SEARCH_TIMEOUT_MILLIS = 5000

@Suppress("UNUSED_PARAMETER")
class AudioPlayer : Fragment(), PlaylistAdapter.IPlayer, TextWatcher, PlaybackService.Client.Callback {

    private lateinit var binding: AudioPlayerBinding
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var settings: SharedPreferences
    private val handler by lazy(LazyThreadSafetyMode.NONE) { Handler() }
    private val updateActor = actor<Unit>(UI, capacity = Channel.CONFLATED) { for (entry in channel) doUpdate() }
    private lateinit var helper: PlaybackServiceActivity.Helper
    private var service: PlaybackService? = null
    private lateinit var playlistModel: PlaylistModel

    private var showRemainingTime = false
    private var previewingSeek = false
    private var advFuncVisible = false
    private var playlistSwitchVisible = false
    private var searchVisible = false
    private var headerPlayPauseVisible = false
    private var progressBarVisible = false
    private var headerTimeVisible = false
    private var playerState = 0
    private var currentCoverArt: String? = null

    companion object {
        private var DEFAULT_BACKGROUND_DARKER_ID = 0
        private var DEFAULT_BACKGROUND_ID = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { playerState = it.getInt("player_state")}
        playlistAdapter = PlaylistAdapter(this)
        settings = PreferenceManager.getDefaultSharedPreferences(activity)
        helper = PlaybackServiceActivity.Helper(activity, this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = AudioPlayerBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (AndroidUtil.isJellyBeanMR1OrLater) {
            DEFAULT_BACKGROUND_DARKER_ID = UiTools.getResourceFromAttribute(view.context, R.attr.background_default_darker)
            DEFAULT_BACKGROUND_ID = UiTools.getResourceFromAttribute(view.context, R.attr.background_default)
        }
        binding.songsList.layoutManager = LinearLayoutManager(view.context)
        binding.songsList.adapter = playlistAdapter
        binding.audioMediaSwitcher.setAudioMediaSwitcherListener(headerMediaSwitcherListener)
        binding.coverMediaSwitcher.setAudioMediaSwitcherListener(mCoverMediaSwitcherListener)
        binding.playlistSearchText.editText?.addTextChangedListener(this)

        val callback = SwipeDragItemTouchHelperCallback(playlistAdapter)
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
    }

    override fun onStart() {
        super.onStart()
        helper.onStart()
    }

    override fun onStop() {
        super.onStop()
        helper.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("player_state", playerState)
    }

    override fun onPopupMenu(anchor: View, position: Int, media: MediaWrapper) {
        val activity = activity
        if (activity === null || position >= playlistAdapter.itemCount) return
        val pos = playlistModel.getItemPosition(position, media)
        if (pos == -1) return
        val mw = playlistAdapter.getItem(pos)
        val popupMenu = PopupMenu(activity, anchor)
        popupMenu.menuInflater.inflate(R.menu.audio_player, popupMenu.menu)

        popupMenu.menu.setGroupVisible(R.id.phone_only, mw!!.type != MediaWrapper.TYPE_VIDEO
                && TextUtils.equals(mw.uri.scheme, "file")
                && AndroidDevices.isPhone)

        popupMenu.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
            if (item.itemId == R.id.audio_player_mini_remove) {
                service?.apply {
                    remove(pos)
                    return@OnMenuItemClickListener true
                }
            } else if (item.itemId == R.id.audio_player_set_song) {
                AudioUtil.setRingtone(mw, activity)
                return@OnMenuItemClickListener true
            }
            false
        })
        popupMenu.show()
    }

    private fun doUpdate() {
        if (activity === null) return
        service?.apply {
            if (hasMedia() && !isVideoPlaying && isVisible
                    && settings.getBoolean(PreferencesActivity.VIDEO_RESTORE, false)) {
                settings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, false).apply()
                currentMediaWrapper?.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                switchToVideo()
                return
            }
        }

        binding.audioMediaSwitcher.updateMedia(service)
        binding.coverMediaSwitcher.updateMedia(service)

        binding.playlistPlayasaudioOff.visibility = if (service?.videoTracksCount ?: 0 > 0) View.VISIBLE else View.GONE

        updatePlayPause()
        updateShuffleMode()
        updateRepeatMode()
        binding.timeline.setOnSeekBarChangeListener(timelineListener)
        updateBackground()
    }

    private fun updatePlayPause() {
        val playing = service?.isPlaying ?: false
        val imageResId = UiTools.getResourceFromAttribute(activity, if (playing) R.attr.ic_pause else R.attr.ic_play)
        val text = getString(if (playing) R.string.pause else R.string.play)
        binding.playPause.setImageResource(imageResId)
        binding.playPause.contentDescription = text
        binding.headerPlayPause.setImageResource(imageResId)
        binding.headerPlayPause.contentDescription = text
    }

    private fun updateShuffleMode() {
        service?.let {
            binding.shuffle.setImageResource(UiTools.getResourceFromAttribute(activity, if (it.isShuffling) R.attr.ic_shuffle_on else R.attr.ic_shuffle))
            binding.shuffle.contentDescription = resources.getString(if (it.isShuffling) R.string.shuffle_on else R.string.shuffle)
            binding.shuffle.visibility = if (it.canShuffle()) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun updateRepeatMode() {
        when (service?.repeatType) {
            Constants.REPEAT_ONE -> {
                binding.repeat.setImageResource(UiTools.getResourceFromAttribute(activity, R.attr.ic_repeat_one))
                binding.repeat.contentDescription = resources.getString(R.string.repeat_single)
            }
            Constants.REPEAT_ALL -> {
                binding.repeat.setImageResource(UiTools.getResourceFromAttribute(activity, R.attr.ic_repeat_all))
                binding.repeat.contentDescription = resources.getString(R.string.repeat_all)
            }
            else -> {
                binding.repeat.setImageResource(UiTools.getResourceFromAttribute(activity, R.attr.ic_repeat))
                binding.repeat.contentDescription = resources.getString(R.string.repeat)
            }
        }
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
    private fun updateBackground() {
        if (AndroidUtil.isJellyBeanMR1OrLater && settings.getBoolean("blurred_cover_background", true)) {
            launch(UI, CoroutineStart.UNDISPATCHED) {
                val mw = service?.currentMediaWrapper
                if (mw === null || TextUtils.equals(currentCoverArt, mw.artworkMrl)) return@launch
                currentCoverArt = mw.artworkMrl
                if (TextUtils.isEmpty(mw.artworkMrl)) {
                    setDefaultBackground()
                } else {
                    val blurredCover = withContext(VLCIO) { UiTools.blurBitmap(AudioUtil.readCoverBitmap(Uri.decode(mw.artworkMrl), binding.contentLayout.width)) }
                    if (blurredCover !== null) {
                        val activity = activity as? AudioPlayerContainerActivity
                        if (activity === null) return@launch
                        binding.backgroundView.setColorFilter(UiTools.getColorFromAttribute(activity, R.attr.audio_player_background_tint))
                        binding.backgroundView.setImageBitmap(blurredCover)
                        binding.backgroundView.visibility = View.VISIBLE
                        binding.songsList.setBackgroundResource(0)
                        if (playerState == BottomSheetBehavior.STATE_EXPANDED) binding.header.setBackgroundResource(0)
                    } else setDefaultBackground()
                }
            }
        }
        if ((activity as AudioPlayerContainerActivity).isAudioPlayerExpanded)
            setHeaderVisibilities(true, true, false, false, false, true)
    }

    @MainThread
    private fun setDefaultBackground() {
        binding.songsList.setBackgroundResource(DEFAULT_BACKGROUND_ID)
        binding.header.setBackgroundResource(DEFAULT_BACKGROUND_ID)
        binding.backgroundView.visibility = View.INVISIBLE
    }

    override fun onSelectionSet(position: Int) {
        if (playerState != BottomSheetBehavior.STATE_COLLAPSED && playerState != BottomSheetBehavior.STATE_HIDDEN) {
            binding.songsList.scrollToPosition(position)
        }
    }

    override fun playItem(position: Int, item: MediaWrapper) {
         service?.playIndex(playlistModel.getItemPosition(position, item))
    }

    fun onTimeLabelClick(view: View) {
        showRemainingTime = !showRemainingTime
        playlistModel.progress.value?.let { updateProgress(it) }
    }

    fun onPlayPauseClick(view: View) {
        service?.run { if (isPlaying) pause() else play() }
    }

    fun onStopClick(view: View): Boolean {
        service?.stop()
        return true
    }

    fun onNextClick(view: View) {
        service?.run {
            if (hasNext()) next()
            else Snackbar.make(binding.root, R.string.lastsong, Snackbar.LENGTH_SHORT).show()
        }
    }

    fun onPreviousClick(view: View) {
        service?.run {
            if (hasPrevious() || isSeekable) previous(false)
            else Snackbar.make(binding.root, R.string.firstsong, Snackbar.LENGTH_SHORT).show()
        }
    }

    fun onRepeatClick(view: View) {
        if (service === null) return
        when (service?.repeatType) {
            Constants.REPEAT_NONE -> service?.repeatType = Constants.REPEAT_ALL
            Constants.REPEAT_ALL -> service?.repeatType = Constants.REPEAT_ONE
            else -> service?.repeatType = Constants.REPEAT_NONE
        }
        updateRepeatMode()
    }

    fun onPlaylistSwitchClick(view: View) {
        binding.showCover = !binding.showCover
        settings.edit().putBoolean("audio_player_show_cover", binding.showCover).apply()
        binding.playlistSwitch.setImageResource(UiTools.getResourceFromAttribute(view.context, if (binding.showCover) R.attr.ic_playlist else R.attr.ic_playlist_on))
    }

    fun onShuffleClick(view: View) {
        service?.apply {
            shuffle()
            updateShuffleMode()
        }
    }

    fun onResumeToVideoClick(v: View) {
        service?.apply {
            currentMediaWrapper?.let {
                if (hasRenderer()) VideoPlayerActivity.startOpened(VLCApplication.getAppContext(),
                        it.uri, currentMediaPosition)
                else if (hasMedia()) {
                    it.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                    switchToVideo()
                }
            }
        }
    }


    fun showAdvancedOptions(v: View) {
        if (!isVisible) return
        activity?.let {
            val advOptionsDialog = AdvOptionsDialog()
            advOptionsDialog.arguments = Bundle().apply { putInt(AdvOptionsDialog.MODE_KEY, AdvOptionsDialog.MODE_AUDIO) }
            advOptionsDialog.show(it.supportFragmentManager, "fragment_adv_options")
        }
    }

    private fun setHeaderVisibilities(advFuncVisible: Boolean, playlistSwitchVisible: Boolean,
                                      headerPlayPauseVisible: Boolean, progressBarVisible: Boolean,
                                      headerTimeVisible: Boolean, searchVisible: Boolean) {
        this.advFuncVisible = advFuncVisible
        this.playlistSwitchVisible = playlistSwitchVisible
        this.headerPlayPauseVisible = headerPlayPauseVisible
        this.progressBarVisible = progressBarVisible
        this.headerTimeVisible = headerTimeVisible
        this.searchVisible = searchVisible
        restoreHeaderButtonVisibilities()
    }

    private fun restoreHeaderButtonVisibilities() {
        binding.advFunction.visibility = if (advFuncVisible) View.VISIBLE else View.GONE
        binding.playlistSwitch.visibility = if (playlistSwitchVisible) View.VISIBLE else View.GONE
        binding.playlistSearch.visibility = if (searchVisible) View.VISIBLE else View.GONE
        binding.headerPlayPause.visibility = if (headerPlayPauseVisible) View.VISIBLE else View.GONE
        binding.progressBar.visibility = if (progressBarVisible) View.VISIBLE else View.GONE
        binding.headerTime.visibility = if (headerTimeVisible) View.VISIBLE else View.GONE
    }

    private fun hideHeaderButtons() {
        binding.advFunction.visibility = View.GONE
        binding.playlistSwitch.visibility = View.GONE
        binding.playlistSearch.visibility = View.GONE
        binding.headerPlayPause.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.headerTime.visibility = View.GONE
    }

    fun onSearchClick(v: View) {
        binding.playlistSearch.visibility = View.GONE
        binding.playlistSearchText.visibility = View.VISIBLE
        binding.playlistSearchText.editText?.requestFocus()
        val imm = VLCApplication.getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.playlistSearchText.editText, InputMethodManager.SHOW_IMPLICIT)
        handler.postDelayed(hideSearchRunnable, SEARCH_TIMEOUT_MILLIS.toLong())
    }

    override fun beforeTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {}

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
        binding.playlistSearch.visibility = View.VISIBLE
        binding.playlistSearchText.visibility = View.GONE
        return true
    }

    override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
        val length = charSequence.length
        if (length > 1) {
            playlistModel.filter(charSequence)
            handler.removeCallbacks(hideSearchRunnable)
        } else if (length == 0) {
            playlistModel.filter(null)
            hideSearchField()
        }
    }

    override fun afterTextChanged(editable: Editable) {}

    override fun onConnected(service: PlaybackService) {
        this.service = service
        playlistModel = PlaylistModel.get(this, service).apply { setup() }
        playlistModel.progress.observe(this,  Observer { it?.let { updateProgress(it) } })
        playlistModel.dataset.observe(this, Observer {
            playlistAdapter.update(it!!)
            updateActor.offer(Unit)
        })
        playlistAdapter.setService(service)
    }

    override fun onDisconnected() {
        playlistModel.onCleared()
        service = null
    }

    private inner class LongSeekListener(internal var forward: Boolean, internal var normal: Int, internal var pressed: Int) : View.OnTouchListener {
        internal var length = -1L

        internal var possibleSeek = 0
        internal var vibrated = false

        @RequiresPermission(Manifest.permission.VIBRATE)
        internal var seekRunnable: Runnable = object : Runnable {
            override fun run() {
                if (!vibrated) {
                    (VLCApplication.getAppContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator)
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
            if (service === null) return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    (if (forward) binding.next else binding.previous).setImageResource(this.pressed)
                    possibleSeek = service?.time?.toInt() ?: 0
                    previewingSeek = true
                    vibrated = false
                    length = service?.length ?: 0L
                    handler.postDelayed(seekRunnable, 1000)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    (if (forward) binding.next else binding.previous).setImageResource(this.normal)
                    handler.removeCallbacks(seekRunnable)
                    previewingSeek = false
                    if (event.eventTime - event.downTime < 1000) {
                        if (forward) onNextClick(v) else onPreviousClick(v)
                    } else {
                        if (forward) {
                            if (possibleSeek < service?.length ?: 0L)
                                service?.time = possibleSeek.toLong()
                            else
                                onNextClick(v)
                        } else {
                            if (possibleSeek > 0)
                                service?.time = possibleSeek.toLong()
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
        activity?.showTipViewIfNeeded(R.id.audio_playlist_tips, Constants.PREF_PLAYLIST_TIPS_SHOWN)
    }

    fun onStateChanged(newState: Int) {
        playerState = newState
        when (newState) {
            BottomSheetBehavior.STATE_COLLAPSED -> {
                hideSearchField()
                binding.header.setBackgroundResource(DEFAULT_BACKGROUND_DARKER_ID)
                setHeaderVisibilities(false, false, true, true, true, false)
            }
            BottomSheetBehavior.STATE_EXPANDED -> {
                binding.header.setBackgroundResource(0)
                setHeaderVisibilities(true, true, false, false, false, true)
                showPlaylistTips()
                service?.apply { playlistAdapter.currentIndex = currentMediaPosition }
            }
            else -> binding.header.setBackgroundResource(0)
        }
    }

    private var timelineListener: OnSeekBarChangeListener = object : OnSeekBarChangeListener {

        override fun onStopTrackingTouch(seekBar: SeekBar) {}

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) service?.apply {
                time = progress.toLong()
                binding.time.text = Tools.millisToString(if (showRemainingTime) progress - length else progress.toLong())
                binding.headerTime.text = Tools.millisToString(progress.toLong())
            }
        }
    }

    private val headerMediaSwitcherListener = object : AudioMediaSwitcherListener {

        override fun onMediaSwitching() {}

        override fun onMediaSwitched(position: Int) {
           service?.apply {
               when (position) {
                   AudioMediaSwitcherListener.PREVIOUS_MEDIA -> previous(true)
                   AudioMediaSwitcherListener.NEXT_MEDIA ->  next()
               }
           }
        }

        override fun onTouchDown() = hideHeaderButtons()

        override fun onTouchUp() = restoreHeaderButtonVisibilities()

        override fun onTouchClick() {
            val activity = activity as AudioPlayerContainerActivity
            activity.slideUpOrDownAudioPlayer()
        }
    }

    private val mCoverMediaSwitcherListener = object : AudioMediaSwitcherListener {

        override fun onMediaSwitching() {}

        override fun onMediaSwitched(position: Int) {
            service?.apply {
                when (position) {
                    AudioMediaSwitcherListener.PREVIOUS_MEDIA -> previous(true)
                    AudioMediaSwitcherListener.NEXT_MEDIA -> next()
                }
            }
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
