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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.support.annotation.MainThread
import android.support.annotation.RequiresPermission
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.Snackbar
import android.support.v4.app.FragmentActivity
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
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.launch
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.databinding.AudioPlayerBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.PlaybackServiceFragment
import org.videolan.vlc.gui.dialogs.AdvOptionsDialog
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.gui.view.AudioMediaSwitcher.AudioMediaSwitcherListener
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.Constants

@Suppress("UNUSED_PARAMETER")
class AudioPlayer : PlaybackServiceFragment(), PlaybackService.Callback, PlaylistAdapter.IPlayer, TextWatcher {

    private lateinit var mBinding: AudioPlayerBinding
    private lateinit var mPlaylistAdapter: PlaylistAdapter
    private lateinit var mSettings: SharedPreferences
    private val mHandler by lazy(LazyThreadSafetyMode.NONE) { Handler() }
    private val updateActor = actor<Unit>(UI, capacity = Channel.CONFLATED) { for (entry in channel) doUpdate() }

    private var mShowRemainingTime = false
    private var mPreviewingSeek = false
    private var mAdvFuncVisible = false
    private var mPlaylistSwitchVisible = false
    private var mSearchVisible = false
    private var mHeaderPlayPauseVisible = false
    private var mProgressBarVisible = false
    private var mHeaderTimeVisible = false
    private var mPlayerState = 0
    private var mCurrentCoverArt: String? = null

    companion object {
        const val TAG = "VLC/AudioPlayer"

        private var DEFAULT_BACKGROUND_DARKER_ID = 0
        private var DEFAULT_BACKGROUND_ID = 0
        const private val SEARCH_TIMEOUT_MILLIS = 5000
        /**
         * Show the audio player from an intent
         *
         * @param context The context of the activity
         */
        fun start(context: Context) {
            context.applicationContext.sendBroadcast(Intent(Constants.ACTION_SHOW_PLAYER))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { mPlayerState = it.getInt("player_state")}
        mPlaylistAdapter = PlaylistAdapter(this)
        mSettings = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mBinding = AudioPlayerBinding.inflate(inflater)
        return mBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (AndroidUtil.isJellyBeanMR1OrLater) {
            DEFAULT_BACKGROUND_DARKER_ID = UiTools.getResourceFromAttribute(view.context, R.attr.background_default_darker)
            DEFAULT_BACKGROUND_ID = UiTools.getResourceFromAttribute(view.context, R.attr.background_default)
        }
        mBinding.songsList.layoutManager = LinearLayoutManager(view.context)
        mBinding.songsList.adapter = mPlaylistAdapter
        mBinding.audioMediaSwitcher.setAudioMediaSwitcherListener(mHeaderMediaSwitcherListener)
        mBinding.coverMediaSwitcher.setAudioMediaSwitcherListener(mCoverMediaSwitcherListener)
        mBinding.playlistSearchText.editText?.addTextChangedListener(this)

        val callback = SwipeDragItemTouchHelperCallback(mPlaylistAdapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(mBinding.songsList)

        setHeaderVisibilities(false, false, true, true, true, false)
        mBinding.fragment = this

        mBinding.next.setOnTouchListener(LongSeekListener(true,
                UiTools.getResourceFromAttribute(view.context, R.attr.ic_next),
                R.drawable.ic_next_pressed))
        mBinding.previous.setOnTouchListener(LongSeekListener(false,
                UiTools.getResourceFromAttribute(view.context, R.attr.ic_previous),
                R.drawable.ic_previous_pressed))

        registerForContextMenu(mBinding.songsList)
        userVisibleHint = true
        mBinding.showCover = mSettings.getBoolean("audio_player_show_cover", false)
        mBinding.playlistSwitch.setImageResource(UiTools.getResourceFromAttribute(view.context, if (mBinding.showCover) R.attr.ic_playlist else R.attr.ic_playlist_on))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("player_state", mPlayerState)
    }

    override fun onPopupMenu(anchor: View, position: Int) {
        val activity = activity
        if (activity === null || position >= mPlaylistAdapter.itemCount) return
        val mw = mPlaylistAdapter.getItem(position)
        val popupMenu = PopupMenu(activity, anchor)
        popupMenu.menuInflater.inflate(R.menu.audio_player, popupMenu.menu)

        popupMenu.menu.setGroupVisible(R.id.phone_only, mw!!.type != MediaWrapper.TYPE_VIDEO
                && TextUtils.equals(mw.uri.scheme, "file")
                && AndroidDevices.isPhone)

        popupMenu.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
            if (item.itemId == R.id.audio_player_mini_remove) {
                if (mService != null) {
                    mService.remove(position)
                    return@OnMenuItemClickListener true
                }
            } else if (item.itemId == R.id.audio_player_set_song) {
                AudioUtil.setRingtone(mw, activity as FragmentActivity)
                return@OnMenuItemClickListener true
            }
            false
        })
        popupMenu.show()
    }

    override fun update() {
        if (!updateActor.isClosedForSend) updateActor.offer(Unit)
    }

    private fun doUpdate() {
        if (mService === null || activity === null) return
        if (mService.hasMedia() && !mService.isVideoPlaying) {
            //Check fragment resumed to not restore video on device turning off
            if (isVisible && mSettings.getBoolean(PreferencesActivity.VIDEO_RESTORE, false)) {
                mSettings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, false).apply()
                mService.currentMediaWrapper.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                mService.switchToVideo()
                return
            } else
                show()
        } else {
            hide()
            return
        }

        mBinding.audioMediaSwitcher.updateMedia(mService)
        mBinding.coverMediaSwitcher.updateMedia(mService)

        mBinding.playlistPlayasaudioOff.visibility = if (mService.videoTracksCount > 0) View.VISIBLE else View.GONE

        val playing = mService.isPlaying
        val imageResId = UiTools.getResourceFromAttribute(activity, if (playing) R.attr.ic_pause else R.attr.ic_play)
        val text = getString(if (playing) R.string.pause else R.string.play)
        mBinding.playPause.setImageResource(imageResId)
        mBinding.playPause.contentDescription = text
        mBinding.headerPlayPause.setImageResource(imageResId)
        mBinding.headerPlayPause.contentDescription = text
        mBinding.shuffle.setImageResource(UiTools.getResourceFromAttribute(activity, if (mService.isShuffling) R.attr.ic_shuffle_on else R.attr.ic_shuffle))
        mBinding.shuffle.contentDescription = resources.getString(if (mService.isShuffling) R.string.shuffle_on else R.string.shuffle)
        when (mService.repeatType) {
            Constants.REPEAT_ONE -> {
                mBinding.repeat.setImageResource(UiTools.getResourceFromAttribute(activity, R.attr.ic_repeat_one))
                mBinding.repeat.contentDescription = resources.getString(R.string.repeat_single)
            }
            Constants.REPEAT_ALL -> {
                mBinding.repeat.setImageResource(UiTools.getResourceFromAttribute(activity, R.attr.ic_repeat_all))
                mBinding.repeat.contentDescription = resources.getString(R.string.repeat_all)
            }
            else -> {
                mBinding.repeat.setImageResource(UiTools.getResourceFromAttribute(activity, R.attr.ic_repeat))
                mBinding.repeat.contentDescription = resources.getString(R.string.repeat)
            }
        }
        mBinding.shuffle.visibility = if (mService.canShuffle()) View.VISIBLE else View.INVISIBLE
        mBinding.timeline.setOnSeekBarChangeListener(mTimelineListner)
        updateList()
        updateBackground()
    }

    override fun updateProgress() {
        if (mService === null) return
        val time = mService.time
        val length = mService.length

        mBinding.headerTime.text = Tools.millisToString(time)
        mBinding.length.text = Tools.millisToString(length)
        mBinding.timeline.max = length.toInt()
        mBinding.progressBar.max = length.toInt()

        if (!mPreviewingSeek) {
            mBinding.time.text = Tools.millisToString((if (mShowRemainingTime) time - length else time))
            mBinding.timeline.progress = time.toInt()
            mBinding.progressBar.progress = time.toInt()
        }
    }

    override fun onMediaEvent(event: Media.Event) {}

    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Opening -> hideSearchField()
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun updateBackground() {
        if (AndroidUtil.isJellyBeanMR1OrLater) {
            launch(UI, CoroutineStart.UNDISPATCHED) {
                val mw = mService.currentMediaWrapper
                if (mw === null || TextUtils.equals(mCurrentCoverArt, mw.artworkMrl)) return@launch
                mCurrentCoverArt = mw.artworkMrl
                if (TextUtils.isEmpty(mw.artworkMrl)) {
                    setDefaultBackground()
                } else {
                    val blurredCover = async { UiTools.blurBitmap(AudioUtil.readCoverBitmap(Uri.decode(mw.artworkMrl), mBinding.contentLayout.width)) }.await()
                    if (blurredCover !== null) {
                        val activity = activity as? AudioPlayerContainerActivity
                        if (activity === null) return@launch
                        mBinding.backgroundView.setColorFilter(UiTools.getColorFromAttribute(activity, R.attr.audio_player_background_tint))
                        mBinding.backgroundView.setImageBitmap(blurredCover)
                        mBinding.backgroundView.visibility = View.VISIBLE
                        mBinding.songsList.setBackgroundResource(0)
                        if (mPlayerState == BottomSheetBehavior.STATE_EXPANDED) mBinding.header.setBackgroundResource(0)
                    } else setDefaultBackground()
                }
            }
        }
        if ((activity as AudioPlayerContainerActivity).isAudioPlayerExpanded)
            setHeaderVisibilities(true, true, false, false, false, true)
    }

    @MainThread
    private fun setDefaultBackground() {
        mBinding.songsList.setBackgroundResource(DEFAULT_BACKGROUND_ID)
        mBinding.header.setBackgroundResource(DEFAULT_BACKGROUND_ID)
        mBinding.backgroundView.visibility = View.INVISIBLE
    }

    override fun updateList() {
        hideSearchField()
        if (mService !== null) mPlaylistAdapter.update(mService.medias)
    }

    override fun onSelectionSet(position: Int) {
        if (mPlayerState != BottomSheetBehavior.STATE_COLLAPSED && mPlayerState != BottomSheetBehavior.STATE_HIDDEN) {
            mBinding.songsList.scrollToPosition(position)
        }
    }

    fun onTimeLabelClick(view: View) {
        mShowRemainingTime = !mShowRemainingTime
        update()
    }

    fun onPlayPauseClick(view: View) {
        mService?.run { if (isPlaying) pause() else play() }
    }

    fun onStopClick(view: View): Boolean {
        if (mService === null) return false
        mService.stop()
        return true
    }

    fun onNextClick(view: View) {
        if (mService === null) return
        if (mService.hasNext())
            mService.next()
        else
            Snackbar.make(mBinding.root, R.string.lastsong, Snackbar.LENGTH_SHORT).show()
    }

    fun onPreviousClick(view: View) {
        if (mService === null) return
        if (mService.hasPrevious() || mService.isSeekable)
            mService.previous(false)
        else
            Snackbar.make(mBinding.root, R.string.firstsong, Snackbar.LENGTH_SHORT).show()
    }

    fun onRepeatClick(view: View) {
        if (mService === null) return
        when (mService.repeatType) {
            Constants.REPEAT_NONE -> mService.repeatType = Constants.REPEAT_ALL
            Constants.REPEAT_ALL -> mService.repeatType = Constants.REPEAT_ONE
            else -> mService.repeatType = Constants.REPEAT_NONE
        }
        update()
    }

    fun onPlaylistSwitchClick(view: View) {
        mBinding.showCover = !mBinding.showCover
        mSettings.edit().putBoolean("audio_player_show_cover", mBinding.showCover).apply()
        mBinding.playlistSwitch.setImageResource(UiTools.getResourceFromAttribute(view.context, if (mBinding.showCover) R.attr.ic_playlist else R.attr.ic_playlist_on))
    }

    fun onShuffleClick(view: View) {
        if (mService === null) return
        mService.shuffle()
        update()
    }

    fun onResumeToVideoClick(v: View) {
        if (mService == null) return
        if (mService.hasRenderer()) VideoPlayerActivity.startOpened(VLCApplication.getAppContext(),
            mService.currentMediaWrapper.uri, mService.currentMediaPosition)
        else if (mService.hasMedia()) {
            mService.currentMediaWrapper.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
            mService.switchToVideo()
        }
    }


    fun showAdvancedOptions(v: View) {
        if (!isVisible) return
        val advOptionsDialog = AdvOptionsDialog()
        advOptionsDialog.arguments = Bundle().apply { putInt(AdvOptionsDialog.MODE_KEY, AdvOptionsDialog.MODE_AUDIO) }
        advOptionsDialog.show(activity.supportFragmentManager, "fragment_adv_options")
    }

    fun show() {
        val activity = activity as? AudioPlayerContainerActivity
        if (activity?.isAudioPlayerReady == true) activity.showAudioPlayer()
    }

    fun hide() {
        val activity = activity as? AudioPlayerContainerActivity
        activity?.hideAudioPlayer()
    }

    private fun setHeaderVisibilities(advFuncVisible: Boolean, playlistSwitchVisible: Boolean,
                                      headerPlayPauseVisible: Boolean, progressBarVisible: Boolean,
                                      headerTimeVisible: Boolean, searchVisible: Boolean) {
        mAdvFuncVisible = advFuncVisible
        mPlaylistSwitchVisible = playlistSwitchVisible
        mHeaderPlayPauseVisible = headerPlayPauseVisible
        mProgressBarVisible = progressBarVisible
        mHeaderTimeVisible = headerTimeVisible
        mSearchVisible = searchVisible
        restoreHeaderButtonVisibilities()
    }

    private fun restoreHeaderButtonVisibilities() {
        mBinding.advFunction.visibility = if (mAdvFuncVisible) View.VISIBLE else View.GONE
        mBinding.playlistSwitch.visibility = if (mPlaylistSwitchVisible) View.VISIBLE else View.GONE
        mBinding.playlistSearch.visibility = if (mSearchVisible) View.VISIBLE else View.GONE
        mBinding.headerPlayPause.visibility = if (mHeaderPlayPauseVisible) View.VISIBLE else View.GONE
        mBinding.progressBar.visibility = if (mProgressBarVisible) View.VISIBLE else View.GONE
        mBinding.headerTime.visibility = if (mHeaderTimeVisible) View.VISIBLE else View.GONE
    }

    private fun hideHeaderButtons() {
        mBinding.advFunction.visibility = View.GONE
        mBinding.playlistSwitch.visibility = View.GONE
        mBinding.playlistSearch.visibility = View.GONE
        mBinding.headerPlayPause.visibility = View.GONE
        mBinding.progressBar.visibility = View.GONE
        mBinding.headerTime.visibility = View.GONE
    }

    fun onSearchClick(v: View) {
        mBinding.playlistSearch.visibility = View.GONE
        mBinding.playlistSearchText.visibility = View.VISIBLE
        if (mBinding.playlistSearchText.editText != null)
            mBinding.playlistSearchText.editText!!.requestFocus()
        val imm = VLCApplication.getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(mBinding.playlistSearchText.editText, InputMethodManager.SHOW_IMPLICIT)
        mHandler.postDelayed(hideSearchRunnable, SEARCH_TIMEOUT_MILLIS.toLong())
    }

    override fun beforeTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {}

    fun clearSearch(): Boolean {
        mPlaylistAdapter.restoreList()
        return hideSearchField()
    }

    private fun hideSearchField(): Boolean {
        if (mBinding.playlistSearchText.visibility != View.VISIBLE) return false
        mBinding.playlistSearchText.editText?.apply {
            removeTextChangedListener(this@AudioPlayer)
            setText("")
            addTextChangedListener(this@AudioPlayer)
        }
        UiTools.setKeyboardVisibility(mBinding.playlistSearchText, false)
        mBinding.playlistSearch.visibility = View.VISIBLE
        mBinding.playlistSearchText.visibility = View.GONE
        return true
    }

    override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
        val length = charSequence.length
        if (length > 1) {
            mPlaylistAdapter.filter.filter(charSequence)
            mHandler.removeCallbacks(hideSearchRunnable)
        } else if (length == 0) {
            mPlaylistAdapter.restoreList()
            hideSearchField()
        }
    }

    override fun afterTextChanged(editable: Editable) {}

    override fun onConnected(service: PlaybackService) {
        super.onConnected(service)
        mService.addCallback(this)
        mPlaylistAdapter.setService(service)
        update()
    }

    override fun onStop() {
        /* unregister before super.onStop() since mService is set to null from this call */
        mService?.removeCallback(this)
        super.onStop()
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

                mBinding.time.text = Tools.millisToString(if (mShowRemainingTime) possibleSeek - length else possibleSeek.toLong())
                mBinding.timeline.progress = possibleSeek
                mBinding.progressBar.progress = possibleSeek
                mHandler.postDelayed(this, 50)
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (mService === null) return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    (if (forward) mBinding.next else mBinding.previous).setImageResource(this.pressed)
                    possibleSeek = mService.time.toInt()
                    mPreviewingSeek = true
                    vibrated = false
                    length = mService.length
                    mHandler.postDelayed(seekRunnable, 1000)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    (if (forward) mBinding.next else mBinding.previous).setImageResource(this.normal)
                    mHandler.removeCallbacks(seekRunnable)
                    mPreviewingSeek = false
                    if (event.eventTime - event.downTime < 1000) {
                        if (forward) onNextClick(v) else onPreviousClick(v)
                    } else {
                        if (forward) {
                            if (possibleSeek < mService.length)
                                mService.time = possibleSeek.toLong()
                            else
                                onNextClick(v)
                        } else {
                            if (possibleSeek > 0)
                                mService.time = possibleSeek.toLong()
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
        mPlayerState = newState
        when (newState) {
            BottomSheetBehavior.STATE_COLLAPSED -> {
                mBinding.header.setBackgroundResource(DEFAULT_BACKGROUND_DARKER_ID)
                setHeaderVisibilities(false, false, true, true, true, false)
            }
            BottomSheetBehavior.STATE_EXPANDED -> {
                mBinding.header.setBackgroundResource(0)
                setHeaderVisibilities(true, true, false, false, false, true)
                showPlaylistTips()
                if (mService != null) mPlaylistAdapter.currentIndex = mService.currentMediaPosition
            }
            else -> mBinding.header.setBackgroundResource(0)
        }
    }

    private var mTimelineListner: OnSeekBarChangeListener = object : OnSeekBarChangeListener {

        override fun onStopTrackingTouch(seekBar: SeekBar) {}

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser && mService !== null) {
                mService.time = progress.toLong()
                mBinding.time.text = Tools.millisToString(if (mShowRemainingTime) progress - mService.length else progress.toLong())
                mBinding.headerTime.text = Tools.millisToString(progress.toLong())
            }
        }
    }

    private val mHeaderMediaSwitcherListener = object : AudioMediaSwitcherListener {

        override fun onMediaSwitching() {}

        override fun onMediaSwitched(position: Int) {
            if (mService === null) return
            when (position) {
                AudioMediaSwitcherListener.PREVIOUS_MEDIA -> mService.previous(true)
                AudioMediaSwitcherListener.NEXT_MEDIA ->  mService.next()
            }
        }

        override fun onTouchDown() {
            hideHeaderButtons()
        }

        override fun onTouchUp() {
            restoreHeaderButtonVisibilities()
        }

        override fun onTouchClick() {
            val activity = activity as AudioPlayerContainerActivity
            activity.slideUpOrDownAudioPlayer()
        }
    }

    private val mCoverMediaSwitcherListener = object : AudioMediaSwitcherListener {

        override fun onMediaSwitching() {}

        override fun onMediaSwitched(position: Int) {
            if (mService === null) return
            when (position) {
                AudioMediaSwitcherListener.PREVIOUS_MEDIA -> mService.previous(true)
                AudioMediaSwitcherListener.NEXT_MEDIA -> mService.next()
            }
        }

        override fun onTouchDown() {}

        override fun onTouchUp() {}

        override fun onTouchClick() {}
    }

    private val hideSearchRunnable by lazy(LazyThreadSafetyMode.NONE) {
        Runnable {
            hideSearchField()
            mPlaylistAdapter.restoreList()
        }
    }
}
