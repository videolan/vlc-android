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
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.ViewStubCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.net.toUri
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.KEY_CURRENT_AUDIO
import org.videolan.resources.util.getFromMl
import org.videolan.resources.util.startMedialibrary
import org.videolan.tools.*
import org.videolan.vlc.*
import org.videolan.vlc.gui.audio.AudioPlayer
import org.videolan.vlc.gui.audio.AudioPlaylistTipsDelegate
import org.videolan.vlc.gui.audio.AudioTipsDelegate
import org.videolan.vlc.gui.audio.EqualizerFragment
import org.videolan.vlc.gui.helpers.*
import org.videolan.vlc.gui.helpers.UiTools.isTablet
import org.videolan.vlc.interfaces.IRefreshable
import org.videolan.vlc.media.PlaylistManager
import kotlin.math.max
import kotlin.math.min


private const val TAG = "VLC/APCActivity"

private const val ACTION_DISPLAY_PROGRESSBAR = 1339
private const val ACTION_SHOW_PLAYER = 1340
private const val ACTION_HIDE_PLAYER = 1341
private const val BOTTOM_IS_HIDDEN = "bottom_is_hidden"
private const val PLAYER_OPENED = "player_opened"
private const val SHOWN_TIPS = "shown_tips"

@SuppressLint("Registered")
@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
open class AudioPlayerContainerActivity : BaseActivity(), KeycodeListener {

    private var bottomBar: BottomNavigationView? = null
    lateinit var appBarLayout: AppBarLayout
    protected lateinit var toolbar: Toolbar
    private var tabLayout: TabLayout? = null
    private var navigationRail: NavigationRailView? = null
    lateinit var audioPlayer: AudioPlayer
    private lateinit var audioPlayerContainer: FrameLayout
    lateinit var playerBehavior: PlayerBehavior<*>
    protected lateinit var fragmentContainer: View
    protected var originalBottomPadding: Int = 0
    private var scanProgressLayout: View? = null
    private var scanProgressText: TextView? = null
    private var scanProgressBar: ProgressBar? = null
    private lateinit var resumeCard: Snackbar
    private var preventRescan = false

    private var playerShown = false
    val tipsDelegate: AudioTipsDelegate by lazy(LazyThreadSafetyMode.NONE) { AudioTipsDelegate(this) }
    val playlistTipsDelegate: AudioPlaylistTipsDelegate by lazy(LazyThreadSafetyMode.NONE) { AudioPlaylistTipsDelegate(this) }
    private val playerKeyListenerDelegate: PlayerKeyListenerDelegate by lazy(LazyThreadSafetyMode.NONE) { PlayerKeyListenerDelegate(this@AudioPlayerContainerActivity) }
    val shownTips = ArrayList<Int>()
    protected val currentFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.fragment_placeholder)

    val menu: Menu
        get() = toolbar.menu

    @Suppress("LeakingThis")
    protected val handler: Handler = ProgressHandler(this)

    private var topInset: Int = 0

    val isAudioPlayerReady: Boolean
        get() = ::audioPlayer.isInitialized

    val isAudioPlayerExpanded: Boolean
        get() = isAudioPlayerReady && playerBehavior.state == STATE_EXPANDED

    var bottomIsHiddden: Boolean = false
    open val managePlayerTopMargin = false

    override fun getSnackAnchorView(overAudioPlayer:Boolean): View? {
      return  if (::audioPlayerContainer.isInitialized && audioPlayerContainer.visibility != View.GONE && ::playerBehavior.isInitialized && playerBehavior.state == STATE_COLLAPSED)
          audioPlayerContainer else if (::playerBehavior.isInitialized && playerBehavior.state == STATE_EXPANDED) findViewById(android.R.id.content) else if (::playerBehavior.isInitialized) findViewById(R.id.coordinator) else findViewById(android.R.id.content)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //Init Medialibrary if KO
        if (savedInstanceState != null) {
            this.startMedialibrary(firstRun = false, upgrade = false, parse = true)
            bottomIsHiddden = savedInstanceState.getBoolean(BOTTOM_IS_HIDDEN, false) && !savedInstanceState.getBoolean(PLAYER_OPENED, false)
            savedInstanceState.getIntegerArrayList(SHOWN_TIPS)?.let { shownTips.addAll(it) }
        }
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        registerLiveData()
        if (managePlayerTopMargin) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updateLayoutParams<ViewGroup.MarginLayoutParams>{
                    leftMargin = insets.left
                    rightMargin = insets.right
                    bottomMargin = insets.bottom
                    (findViewById<Toolbar>(R.id.main_toolbar).layoutParams as CollapsingToolbarLayout.LayoutParams).topMargin = insets.top
                }

                WindowInsetsCompat.CONSUMED
            }
        }
    }

    protected open fun initAudioPlayerContainerActivity() {
        findViewById<View>(R.id.fragment_placeholder)?.let {
            fragmentContainer = it
            originalBottomPadding = fragmentContainer.paddingBottom
        }
        toolbar = findViewById(R.id.main_toolbar)
        setSupportActionBar(toolbar)
        appBarLayout = findViewById(R.id.appbar)
        tabLayout = findViewById(R.id.sliding_tabs)
        navigationRail = findViewById(R.id.navigation_rail)
        tabLayout?.visibility = View.VISIBLE
        appBarLayout.setExpanded(true)
        bottomBar = findViewById(R.id.navigation)
        tabLayout?.viewTreeObserver?.addOnGlobalLayoutListener {
            //add a shadow if there are tabs
            val needToElevate = (tabLayout?.layoutParams?.height != 0) || navigationRail?.visibility ?: View.GONE != View.GONE
            if (AndroidUtil.isLolliPopOrLater) appBarLayout.elevation = if (needToElevate) 8.dp.toFloat() else 0.dp.toFloat()
        }
        audioPlayerContainer = findViewById(R.id.audio_player_container)
    }

    fun setTabLayoutVisibility(show: Boolean) {
        tabLayout?.layoutParams?.height = if (show) ViewGroup.LayoutParams.WRAP_CONTENT else 0
        tabLayout?.requestLayout()
    }

    private fun initAudioPlayer() {
        findViewById<View>(R.id.audio_player_stub).visibility = View.VISIBLE
        audioPlayer = supportFragmentManager.findFragmentById(R.id.audio_player) as AudioPlayer
        playerBehavior = from(audioPlayerContainer) as PlayerBehavior<*>
        val bottomBehavior = bottomBar?.let { BottomNavigationBehavior.from(it) as BottomNavigationBehavior<View> }
            ?: null
        if (bottomIsHiddden)  bottomBehavior?.setCollapsed()
        playerBehavior.peekHeight = resources.getDimensionPixelSize(R.dimen.player_peek_height)
        updateFragmentMargins()
        playerBehavior.setPeekHeightListener {
            applyMarginToProgressBar(playerBehavior.peekHeight)
        }
        playerBehavior.setLayoutListener {
            // [AudioPlayer.showCover] applies a new [ConstraintSet]. It cannot be done in [AudioPlayer.onCreate] because it would compete with
            // [BottomSheetBehavior.onLayoutChild] and prevent any scroll event to be forwarded by the ConstraintLayout views (the bookmark list for example)
            // That why we wait that the layout has been done to perform this. See https://code.videolan.org/videolan/vlc-android/-/issues/2241#note_291050
            audioPlayer.showCover(settings.getBoolean("audio_player_show_cover", false))
            if (playerBehavior.state == STATE_COLLAPSED) audioPlayer.onSlide(0f)
        }
        playerBehavior.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                audioPlayer.onSlide(slideOffset)
                val translationpercent = min(1f, max(0f, slideOffset))
                bottomBehavior?.let { bottomBehavior ->
                    bottomBar?.let { bottomBar ->
                        val translation = min((translationpercent * audioPlayerContainer.height / 2), bottomBar.height.toFloat()) - if (managePlayerTopMargin) topInset else 0
                        bottomBehavior.translate(bottomBar, translation)
                    }
                }
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                onPlayerStateChanged(bottomSheet, newState)
                if (managePlayerTopMargin) {
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        systemBarsBehavior = if (newState == STATE_EXPANDED)  WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE else WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
                        if (newState == STATE_EXPANDED) hide( WindowInsetsCompat.Type.statusBars()) else show( WindowInsetsCompat.Type.statusBars())
                    }
                }
                audioPlayer.onStateChanged(newState)
                if (newState == STATE_COLLAPSED || newState == STATE_HIDDEN) removeTipViewIfDisplayed()
                updateFragmentMargins(newState)
                applyMarginToProgressBar(playerBehavior.peekHeight)
            }
        })
        showTipViewIfNeeded(R.id.audio_player_tips, PREF_AUDIOPLAYER_TIPS_SHOWN)
        if (playlistTipsDelegate.currentTip != null) lockPlayer(true)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (playerKeyListenerDelegate.onKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun showAdvancedOptions() {
        audioPlayer.showAdvancedOptions(null)
    }

    override fun togglePlayPause() {
        audioPlayer.onPlayPauseClick(null)
    }

    override fun next() {
        audioPlayer.onNextClick(null)
    }

    override fun previous() {
        audioPlayer.onPreviousClick(null)
    }

    override fun stop() {
        audioPlayer.onStopClick(null)
    }

    override fun seek(delta: Int) {
        val time = audioPlayer.playlistModel.getTime().toInt() + delta
        if (time < 0 || time > audioPlayer.playlistModel.length) return
        audioPlayer.playlistModel.setTime(time.toLong())
    }

    override fun showEqualizer() {
        EqualizerFragment().show(supportFragmentManager, "equalizer")
    }

    override fun increaseRate() {
        audioPlayer.playlistModel.service?.increaseRate()
    }

    override fun decreaseRate() {
        audioPlayer.playlistModel.service?.decreaseRate()
    }

    override fun resetRate() {
        audioPlayer.playlistModel.service?.resetRate()
    }

    override fun bookmark() {
        audioPlayer.bookmarkModel.addBookmark(this)
        UiTools.snackerConfirm(this, getString(R.string.bookmark_added), overAudioPlayer = true, confirmMessage = R.string.show) {
            audioPlayer.showBookmarks()
        }
    }

    fun updateFragmentMargins(state: Int = STATE_COLLAPSED) {
        playerShown = state != STATE_HIDDEN
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment is BaseFragment) fragment.updateAudioPlayerMargin()
        }
    }

    fun getAudioMargin() = if (playerShown) resources.getDimensionPixelSize(R.dimen.player_peek_height) else 0

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(BOTTOM_IS_HIDDEN, bottomBar?.let { it.translationY != 0F }
                ?: false)
        outState.putBoolean(PLAYER_OPENED,  if (::playerBehavior.isInitialized) playerBehavior.state == STATE_EXPANDED else false)
        outState.putIntegerArrayList(SHOWN_TIPS, shownTips)
        super.onSaveInstanceState(outState)
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
        handler.removeMessages(ACTION_SHOW_PLAYER)
        super.onDestroy()
    }

    override fun onResume() {
        if (playerShown)
            applyMarginToProgressBar(playerBehavior.peekHeight)
        else
            applyMarginToProgressBar(0)
        super.onResume()
    }

    override fun onBackPressed() {
        if (slideDownAudioPlayer()) return
        super.onBackPressed()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        // Handle item selection
        when (item.itemId) {
            android.R.id.home -> {
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
        val vsc = findViewById<ViewStubCompat>(stubId)
        if (vsc != null && !settings.getBoolean(settingKey, false) && !Settings.showTvUi) {
            when (stubId) {
                R.id.audio_player_tips -> if (tipsDelegate.currentTip == null && !shownTips.contains(stubId)) tipsDelegate.init(vsc)
                R.id.audio_playlist_tips -> if (playlistTipsDelegate.currentTip == null && !shownTips.contains(stubId)) playlistTipsDelegate.init(vsc)
            }
            if (::audioPlayer.isInitialized) audioPlayer.playlistModel.service?.pause()
        }
    }

    fun onClickDismissTips(@Suppress("UNUSED_PARAMETER") v: View?) {
        tipsDelegate.close()
    }
    fun onClickNextTips(@Suppress("UNUSED_PARAMETER") v: View?) {
        tipsDelegate.next()
    }

    fun onClickDismissPlaylistTips(@Suppress("UNUSED_PARAMETER") v: View?) {
        playlistTipsDelegate.close()
    }

    fun onClickNextPlaylistTips(@Suppress("UNUSED_PARAMETER") v: View?) {
        playlistTipsDelegate.next()
    }

    /**
     * Remove the current tip view if there is one displayed.
     */
    fun removeTipViewIfDisplayed() {
        findViewById<View>(R.id.audio_player_tips)?.let { (it.parent as ViewGroup).removeView(it) }
    }

    /**
     * Show the audio player.
     */
    private fun showAudioPlayer() {
        if (isFinishing) return
        handler.sendEmptyMessageDelayed(ACTION_SHOW_PLAYER, 100L)
    }

    private fun showAudioPlayerImpl() {
        if (isFinishing) return
        if (!isAudioPlayerReady) initAudioPlayer()
        if (audioPlayerContainer.visibility != View.VISIBLE) {
            audioPlayerContainer.visibility = View.VISIBLE
        }
        playerBehavior.run {
            if (state == STATE_HIDDEN) state = STATE_COLLAPSED
            isHideable = false
            if (tipsDelegate.currentTip == null && playlistTipsDelegate.currentTip == null) lock(false)
        }
    }

    /**
     * Slide down the audio player.
     * @return true on success else false.
     */
    fun slideDownAudioPlayer(): Boolean {
        if (isAudioPlayerReady && playerBehavior.state == STATE_EXPANDED) {
            playerBehavior.state = STATE_COLLAPSED
            return true
        }
        return false
    }

    /**
     * Slide up and down the audio player depending on its current state.
     */
    fun slideUpOrDownAudioPlayer() {
        if (!isAudioPlayerReady || playerBehavior.state == STATE_HIDDEN) return
        playerBehavior.state = if (playerBehavior.state == STATE_EXPANDED)
            STATE_COLLAPSED
        else
            STATE_EXPANDED
    }

    /**
     * Hide the audio player.
     */
    fun hideAudioPlayer() {
        if (isFinishing) return
        handler.removeMessages(ACTION_SHOW_PLAYER)
        handler.sendEmptyMessage(ACTION_HIDE_PLAYER)
    }

    private fun hideAudioPlayerImpl() {
        if (!isAudioPlayerReady) return
        playerBehavior.isHideable = true
        playerBehavior.state = STATE_HIDDEN
    }

    private fun updateProgressVisibility(show: Boolean, discovery: String? = null) {
        val visibility = if (show) View.VISIBLE else View.GONE
        if (scanProgressLayout?.visibility == visibility) return
        if (show) {
            val msg = handler.obtainMessage(ACTION_DISPLAY_PROGRESSBAR, 0, 0, discovery)
            handler.sendMessageDelayed(msg, 1000L)
        } else {
            handler.removeMessages(ACTION_DISPLAY_PROGRESSBAR)
            scanProgressLayout.setVisibility(visibility)
        }
    }

    private fun showProgressBar(discovery: String) {
        if (!Medialibrary.getInstance().isWorking) return
        val vsc = findViewById<View>(R.id.scan_viewstub)
        if (vsc != null) {
            vsc.visibility = View.VISIBLE
            scanProgressLayout = findViewById(R.id.scan_progress_layout)
            scanProgressText = findViewById(R.id.scan_progress_text)
            scanProgressBar = findViewById(R.id.scan_progress_bar)
        } else scanProgressLayout?.visibility = View.VISIBLE
        vsc?.let {
            val lp = it.layoutParams as CoordinatorLayout.LayoutParams
            if (this is MainActivity) {
                lp.anchorId =if (isTablet()) R.id.fragment_placeholder else  R.id.navigation
                lp.anchorGravity = if (isTablet()) Gravity.BOTTOM else Gravity.TOP
                lp.marginStart = if (isTablet()) 72.dp else 0.dp
            }
            it.layoutParams = lp
        }
        scanProgressText?.text = discovery
    }

    fun closeMiniPlayer() {
        hideAudioPlayerImpl()
    }

    private fun applyMarginToProgressBar(marginValue: Int) {
        if (scanProgressLayout != null && scanProgressLayout?.visibility == View.VISIBLE) {
            val lp = scanProgressLayout!!.layoutParams as CoordinatorLayout.LayoutParams
            lp.bottomMargin = if (playerShown) marginValue else 0
            scanProgressLayout?.layoutParams = lp
        }
    }

    protected open fun onPlayerStateChanged(bottomSheet: View, newState: Int) {}

    private fun registerLiveData() {
        PlaylistManager.showAudioPlayer.observe(this) { showPlayer ->
            if (showPlayer == true) showAudioPlayer()
            else {
                hideAudioPlayer()
                if (isAudioPlayerReady) playerBehavior.lock(true)
            }
        }
        MediaParsingService.progress.observe(this) { scanProgress ->
            if (scanProgress == null || !Medialibrary.getInstance().isWorking) {
                updateProgressVisibility(false)
                return@observe
            }
            updateProgressVisibility(true, scanProgress.progressText)
            scanProgressText?.text = scanProgress.progressText
            scanProgressBar?.progress = scanProgress.parsing.toInt()
            if (scanProgress.inDiscovery && scanProgressBar?.isIndeterminate == false) {
                scanProgressBar?.isVisible = false
                scanProgressBar?.isIndeterminate = true
                scanProgressBar?.isVisible = true
            }

            if (!scanProgress.inDiscovery && scanProgressBar?.isIndeterminate == true) {
                scanProgressBar?.isVisible = false
                scanProgressBar?.isIndeterminate = false
                scanProgressBar?.isVisible = true
            }
        }
        MediaParsingService.discoveryError.observe(this) {
            UiTools.snacker(this, getString(R.string.discovery_failed, it.entryPoint))
        }
        MediaParsingService.newStorages.observe(this) { devices ->
            if (devices == null) return@observe
            for (device in devices) UiTools.newStorageDetected(this@AudioPlayerContainerActivity, device)
            MediaParsingService.newStorages.setValue(null)
        }
    }

    @SuppressLint("RestrictedApi")
    fun proposeCard() = lifecycleScope.launchWhenStarted {
        delay(1000L)
        if (PlaylistManager.showAudioPlayer.value == true) return@launchWhenStarted
        val song = settings.getString(KEY_CURRENT_AUDIO, null) ?: return@launchWhenStarted
        val media = getFromMl { getMedia(song.toUri()) } ?: return@launchWhenStarted
        val title = media.title
        resumeCard = Snackbar.make(getSnackAnchorView() ?: appBarLayout, getString(R.string.resume_card_message, title), Snackbar.LENGTH_LONG)
                .setAction(R.string.play) { PlaybackService.loadLastAudio(it.context) }
        resumeCard.show()
    }

    fun lockPlayer(lock: Boolean) {
        if (::playerBehavior.isInitialized) playerBehavior.lock(lock)
    }

    private class ProgressHandler(owner: AudioPlayerContainerActivity) : WeakHandler<AudioPlayerContainerActivity>(owner) {

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            val owner = owner ?: return
            when (msg.what) {
                ACTION_DISPLAY_PROGRESSBAR -> {
                    removeMessages(ACTION_DISPLAY_PROGRESSBAR)
                    owner.showProgressBar(msg.obj as String)
                }
                ACTION_SHOW_PLAYER -> owner.run {
                    if (this::resumeCard.isInitialized && resumeCard.isShown) resumeCard.dismiss()
                    showAudioPlayerImpl()
                    if (::playerBehavior.isInitialized) owner.applyMarginToProgressBar(playerBehavior.peekHeight)
                }
                ACTION_HIDE_PLAYER -> owner.run {
                    hideAudioPlayerImpl()
                    applyMarginToProgressBar(0)
                }
            }
        }
    }
}
