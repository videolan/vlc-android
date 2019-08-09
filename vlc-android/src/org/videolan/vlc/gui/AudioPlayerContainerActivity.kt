/*
 * *************************************************************************
 *  AudioPlayerContainerActivity.kt
 * **************************************************************************
 *  Copyright © 2019 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui

import android.annotation.SuppressLint
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.ViewStubCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.tools.setVisibility
import org.videolan.vlc.*
import org.videolan.vlc.gui.audio.AudioPlayer
import org.videolan.vlc.gui.browser.StorageBrowserFragment
import org.videolan.vlc.gui.helpers.BottomSheetBehavior
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.interfaces.IRefreshable
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.*

private const val TAG = "VLC/APCActivity"

private const val ACTION_DISPLAY_PROGRESSBAR = 1339
private const val ACTION_SHOW_PLAYER = 1340
private const val ACTION_HIDE_PLAYER = 1341

@SuppressLint("Registered")
@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
open class AudioPlayerContainerActivity : BaseActivity() {

    protected lateinit var appBarLayout: AppBarLayout
    protected lateinit var toolbar: Toolbar
    private var tabLayout: TabLayout? = null
    protected var audioPlayer: AudioPlayer? = null
    private var audioPlayerContainer: FrameLayout? = null
    var bottomSheetBehavior: BottomSheetBehavior<*>? = null
    protected var fragmentContainer: View? = null
    protected var originalBottomPadding: Int = 0
    private var scanProgressLayout: View? = null
    private var scanProgressText: TextView? = null
    private var scanProgressBar: ProgressBar? = null
    private lateinit var resumeCard: Snackbar

    private var preventRescan = false

    protected val currentFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.fragment_placeholder)

    val menu: Menu
        get() = toolbar.menu

    @Suppress("LeakingThis")
    protected val activityHandler: Handler = ProgressHandler(this)
    private val audioPlayerBottomSheetCallback = AudioPlayerBottomSheetCallback()

    val isAudioPlayerReady: Boolean
        get() = audioPlayer != null

    val isAudioPlayerExpanded: Boolean
        get() = isAudioPlayerReady && bottomSheetBehavior!!.state == STATE_EXPANDED

    override fun onCreate(savedInstanceState: Bundle?) {
        //Init Medialibrary if KO
        if (savedInstanceState != null) {

            this.startMedialibrary(firstRun = false, upgrade = false, parse = true)
        }
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        registerLiveData()
    }

    protected open fun initAudioPlayerContainerActivity() {
        fragmentContainer = findViewById(R.id.fragment_placeholder)
        if (fragmentContainer != null) originalBottomPadding = fragmentContainer!!.paddingBottom
        toolbar = findViewById(R.id.main_toolbar)
        setSupportActionBar(toolbar)
        appBarLayout = findViewById(R.id.appbar)
        tabLayout = findViewById(R.id.sliding_tabs)
        appBarLayout.setExpanded(true)
        audioPlayerContainer = findViewById(R.id.audio_player_container)
    }

    fun setTabLayoutVisibility(show: Boolean) {
        tabLayout!!.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun initAudioPlayer() {
        findViewById<View>(R.id.audio_player_stub).visibility = View.VISIBLE
        audioPlayer = supportFragmentManager.findFragmentById(R.id.audio_player) as AudioPlayer?
        bottomSheetBehavior = from(audioPlayerContainer!!) as BottomSheetBehavior<*>
        bottomSheetBehavior!!.peekHeight = resources.getDimensionPixelSize(R.dimen.player_peek_height)
        bottomSheetBehavior!!.setBottomSheetCallback(audioPlayerBottomSheetCallback)
        showTipViewIfNeeded(R.id.audio_player_tips, PREF_AUDIOPLAYER_TIPS_SHOWN)
    }

    fun expandAppBar() {
        appBarLayout.setExpanded(true)
    }

    override fun onStart() {
        ExternalMonitor.subscribeStorageCb(this)
        super.onStart()
    }

    override fun onRestart() {
        super.onRestart()
        preventRescan = true
    }

    override fun onStop() {
        super.onStop()
        ExternalMonitor.unsubscribeStorageCb(this)
    }

    override fun onDestroy() {
        activityHandler.removeMessages(ACTION_SHOW_PLAYER)
        coroutineContext.cancelChildren()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (slideDownAudioPlayer()) return
        super.onBackPressed()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        // Handle item selection
        when (item.itemId) {
            android.R.id.home -> {
                // Current fragment loaded
                val current = currentFragment
                if (current is StorageBrowserFragment && current.goBack())
                    return true
                finish()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    fun updateLib() {
        if (preventRescan) {
            preventRescan = false
            return
        }
        val fm = supportFragmentManager
        val current = fm.findFragmentById(R.id.fragment_placeholder)
        if (current is IRefreshable) (current as IRefreshable).refresh()
    }

    /**
     * Show a tip view.
     * @param stubId the stub of the tip view
     * @param settingKey the setting key to check if the view must be displayed or not.
     */
    @SuppressLint("RestrictedApi")
    fun showTipViewIfNeeded(stubId: Int, settingKey: String) {
        if (BuildConfig.DEBUG || PlaybackService.hasRenderer()) return
        val vsc = findViewById<View>(stubId)
        if (vsc != null && !settings.getBoolean(settingKey, false) && !Settings.showTvUi) {
            val v = (vsc as ViewStubCompat).inflate()
            v.setOnClickListener { removeTipViewIfDisplayed() }
            val okGotIt = v.findViewById<TextView>(R.id.okgotit_button)
            okGotIt.setOnClickListener {
                removeTipViewIfDisplayed()
                val editor = settings.edit()
                editor.putBoolean(settingKey, true)
                editor.apply()
            }
        }
    }

    /**
     * Remove the current tip view if there is one displayed.
     */
    fun removeTipViewIfDisplayed() {
        findViewById<View>(R.id.audio_tips)?.let { (it.parent as ViewGroup).removeView(it) }
    }

    /**
     * Show the audio player.
     */
    fun showAudioPlayer() {
        if (isFinishing) return
        activityHandler.sendEmptyMessageDelayed(ACTION_SHOW_PLAYER, 100L)
    }

    private fun showAudioPlayerImpl() {
        if (isFinishing) return
        if (!isAudioPlayerReady) initAudioPlayer()
        if (audioPlayerContainer?.visibility != View.VISIBLE) {
            audioPlayerContainer?.visibility = View.VISIBLE
        }
        bottomSheetBehavior?.run {
            if (state == STATE_HIDDEN) state = STATE_COLLAPSED
            isHideable = false
            lock(false)
        }
    }

    /**
     * Slide down the audio player.
     * @return true on success else false.
     */
    fun slideDownAudioPlayer(): Boolean {
        if (isAudioPlayerReady && bottomSheetBehavior?.state == STATE_EXPANDED) {
            bottomSheetBehavior?.state = STATE_COLLAPSED
            return true
        }
        return false
    }

    /**
     * Slide up and down the audio player depending on its current state.
     */
    fun slideUpOrDownAudioPlayer() {
        if (!isAudioPlayerReady || bottomSheetBehavior!!.state == STATE_HIDDEN) return
        bottomSheetBehavior?.state = if (bottomSheetBehavior!!.state == STATE_EXPANDED)
            STATE_COLLAPSED
        else
            STATE_EXPANDED
    }

    /**
     * Hide the audio player.
     */
    fun hideAudioPlayer() {
        if (isFinishing) return
        activityHandler.removeMessages(ACTION_SHOW_PLAYER)
        activityHandler.sendEmptyMessage(ACTION_HIDE_PLAYER)
    }

    private fun hideAudioPlayerImpl() {
        if (!isAudioPlayerReady) return
        bottomSheetBehavior!!.isHideable = true
        bottomSheetBehavior!!.state = STATE_HIDDEN
    }

    private fun updateProgressVisibility(show: Boolean) {
        val visibility = if (show) View.VISIBLE else View.GONE
        if (scanProgressLayout?.visibility == visibility) return
        if (show)
            activityHandler.sendEmptyMessageDelayed(ACTION_DISPLAY_PROGRESSBAR, 1000)
        else {
            activityHandler.removeMessages(ACTION_DISPLAY_PROGRESSBAR)
            scanProgressLayout.setVisibility(visibility)
        }
    }

    private fun showProgressBar() {
        if (!AbstractMedialibrary.getInstance().isWorking) return
        MediaParsingService.progress.value?.run {
            val vsc = findViewById<View>(R.id.scan_viewstub)
            if (vsc != null) {
                vsc.visibility = View.VISIBLE
                scanProgressLayout = findViewById(R.id.scan_progress_layout)
                scanProgressText = findViewById(R.id.scan_progress_text)
                scanProgressBar = findViewById(R.id.scan_progress_bar)
            } else scanProgressLayout?.visibility = View.VISIBLE
            scanProgressText?.text = discovery
            scanProgressBar?.progress = parsing
        }
    }

    protected fun updateContainerPadding(show: Boolean) {
        if (fragmentContainer == null) return
        val factor = if (show) 1 else 0
        val peekHeight = if (show && bottomSheetBehavior != null) bottomSheetBehavior!!.peekHeight else 0
        fragmentContainer!!.setPadding(fragmentContainer!!.paddingLeft,
                fragmentContainer!!.paddingTop, fragmentContainer!!.paddingRight,
                originalBottomPadding + factor * peekHeight)
    }

    private fun applyMarginToProgressBar(marginValue: Int) {
        if (scanProgressLayout != null && scanProgressLayout?.visibility == View.VISIBLE) {
            val lp = scanProgressLayout!!.layoutParams as CoordinatorLayout.LayoutParams
            lp.bottomMargin = marginValue
            scanProgressLayout?.layoutParams = lp
        }
    }

    private inner class AudioPlayerBottomSheetCallback : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            this@AudioPlayerContainerActivity.onPlayerStateChanged(bottomSheet, newState)
            audioPlayer!!.onStateChanged(newState)
            if (newState == STATE_COLLAPSED || newState == STATE_HIDDEN) removeTipViewIfDisplayed()
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    }

    protected open fun onPlayerStateChanged(bottomSheet: View, newState: Int) {}

    private fun registerLiveData() {
        PlaylistManager.showAudioPlayer.observe(this, Observer { showPlayer ->
            if (showPlayer == true) showAudioPlayer()
            else {
                hideAudioPlayer()
                if (bottomSheetBehavior != null) bottomSheetBehavior!!.lock(true)
            }
        })
        MediaParsingService.progress.observe(this, Observer { scanProgress ->
            if (scanProgress == null || !AbstractMedialibrary.getInstance().isWorking) {
                updateProgressVisibility(false)
                return@Observer
            }
            updateProgressVisibility(true)
            if (scanProgressText != null) scanProgressText!!.text = scanProgress.discovery
            if (scanProgressBar != null) scanProgressBar!!.progress = scanProgress.parsing
        })
        AbstractMedialibrary.getState().observe(this, Observer { started -> if (started != null) updateProgressVisibility(started) })
        MediaParsingService.newStorages.observe(this, Observer<List<String>> { devices ->
            if (devices == null) return@Observer
            for (device in devices) UiTools.newStorageDetected(this@AudioPlayerContainerActivity, device)
            MediaParsingService.newStorages.setValue(null)
        })
    }

    @SuppressLint("RestrictedApi")
    fun proposeCard() = launch {
        delay(1000L)
        if (PlaylistManager.showAudioPlayer.value == true) return@launch
        val song = settings.getString("current_song", null) ?: return@launch
        val media = getFromMl { getMedia(Uri.parse(song)) }
        val title = media?.title
                ?: Uri.decode(FileUtils.getFileNameFromPath(song)).substringBeforeLast('.')
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
        resumeCard = Snackbar.make(appBarLayout, getString(R.string.resume_card_message, title), Snackbar.LENGTH_LONG)
                .setAction(R.string.play) { PlaybackService.loadLastAudio(it.context) }
        resumeCard.show()
    }

    private class ProgressHandler internal constructor(owner: AudioPlayerContainerActivity) : WeakHandler<AudioPlayerContainerActivity>(owner) {

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            val owner = owner ?: return
            when (msg.what) {
                ACTION_DISPLAY_PROGRESSBAR -> {
                    removeMessages(ACTION_DISPLAY_PROGRESSBAR)
                    owner.showProgressBar()
                }
                ACTION_SHOW_PLAYER -> owner.run {
                    if (this::resumeCard.isInitialized && resumeCard.isShown) resumeCard.dismiss()
                    showAudioPlayerImpl()
                    updateContainerPadding(true)
                    bottomSheetBehavior?.let { owner.applyMarginToProgressBar(it.peekHeight) }
                }
                ACTION_HIDE_PLAYER -> owner.run {
                    hideAudioPlayerImpl()
                    updateContainerPadding(false)
                    applyMarginToProgressBar(0)
                }
            }
        }
    }
}
