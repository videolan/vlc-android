/*
 * ************************************************************************
 *  PopupManager.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
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
 *
 *  *************************************************************************
 */

package org.videolan.vlc.gui.video

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.*
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.view.GestureDetectorCompat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.IVLCVout
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.view.PopupLayout
import org.videolan.vlc.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PopupManager constructor(private val mService: PlaybackService) : PlaybackService.Callback, GestureDetector.OnDoubleTapListener, View.OnClickListener, GestureDetector.OnGestureListener, IVLCVout.OnNewVideoLayoutListener, IVLCVout.Callback {

    private var rootView: PopupLayout? = null
    private lateinit var expandButton: ImageView
    private lateinit var closeButton: ImageView
    private lateinit var playPauseButton: ImageView
    private val alwaysOn: Boolean = Settings.getInstance(mService).getBoolean(POPUP_KEEPSCREEN, false)

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val visibility = if (msg.what == SHOW_BUTTONS) View.VISIBLE else View.GONE
            playPauseButton.visibility = visibility
            closeButton.visibility = visibility
            expandButton.visibility = visibility
        }
    }

    fun removePopup() {
        hideNotification()
        if (rootView == null) return
        mService.removeCallback(this)
        val vlcVout = mService.vout
        vlcVout?.detachViews()
        rootView!!.close()
        rootView = null
    }

    fun showPopup() {
        mService.addCallback(this)
        val li = mService.applicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                ?: return
        rootView = li.inflate(R.layout.video_popup, null) as PopupLayout
        if (alwaysOn) rootView!!.keepScreenOn = true
        playPauseButton = rootView!!.findViewById(R.id.video_play_pause)
        closeButton = rootView!!.findViewById(R.id.popup_close)
        expandButton = rootView!!.findViewById(R.id.popup_expand)
        playPauseButton.setOnClickListener(this)
        closeButton.setOnClickListener(this)
        expandButton.setOnClickListener(this)

        val gestureDetector = GestureDetectorCompat(mService, this)
        gestureDetector.setOnDoubleTapListener(this)
        rootView!!.setGestureDetector(gestureDetector)

        val vlcVout = mService.vout ?: return
        vlcVout.setVideoView(rootView!!.findViewById<View>(R.id.player_surface) as SurfaceView)
        vlcVout.addCallback(this)
        vlcVout.attachViews(this)
        rootView!!.setVLCVOut(vlcVout)
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (playPauseButton.visibility == View.VISIBLE) return false
        handler.sendEmptyMessage(SHOW_BUTTONS)
        handler.sendEmptyMessageDelayed(HIDE_BUTTONS, MSG_DELAY.toLong())
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        expandToVideoPlayer()
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        return false
    }

    override fun onDown(e: MotionEvent): Boolean {
        return false
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        return false
    }

    override fun onLongPress(e: MotionEvent) {}

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (Math.abs(velocityX) > FLING_STOP_VELOCITY || velocityY > FLING_STOP_VELOCITY) {
            stopPlayback()
            return true
        }
        return false
    }

    override fun onNewVideoLayout(vlcVout: IVLCVout, width: Int, height: Int,
                                  visibleWidth: Int, visibleHeight: Int, sarNum: Int, sarDen: Int) {
        var width = width
        var height = height
        if (rootView == null) return
        val displayW = rootView!!.width
        val displayH = rootView!!.height

        // sanity check
        if (displayW * displayH == 0) {
            Log.e(TAG, "Invalid surface size")
            return
        }

        if (width == 0 || height == 0) {
            rootView!!.setViewSize(displayW, displayH)
            return
        }

        // compute the aspect ratio
        var dw = displayW.toDouble()
        var dh = displayH.toDouble()
        val ar: Double
        ar = if (sarDen == sarNum) {
            /* No indication about the density, assuming 1:1 */
            visibleWidth.toDouble() / visibleHeight.toDouble()
        } else {
            /* Use the specified aspect ratio */
            val vw = visibleWidth * sarNum.toDouble() / sarDen
            vw / visibleHeight
        }

        // compute the display aspect ratio
        val dar = dw / dh
        if (dar < ar)
            dh = dw / ar
        else
            dw = dh * ar

        width = Math.floor(dw).toInt()
        height = Math.floor(dh).toInt()
        rootView!!.setViewSize(width, height)
    }

    override fun update() {}

    override fun onMediaEvent(event: Media.Event) {}

    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                if (rootView != null) {
                    if (!alwaysOn) rootView!!.keepScreenOn = true
                    playPauseButton.setImageResource(R.drawable.ic_popup_pause)
                }
                showNotification()
            }
            MediaPlayer.Event.Paused -> {
                if (rootView != null) {
                    if (!alwaysOn) rootView!!.keepScreenOn = false
                    playPauseButton.setImageResource(R.drawable.ic_popup_play)
                }
                showNotification()
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.video_play_pause -> if (mService.hasMedia()) {
                if (mService.isPlaying)
                    mService.pause()
                else
                    mService.play()
            }
            R.id.popup_close -> stopPlayback()
            R.id.popup_expand -> expandToVideoPlayer()
        }
    }

    private fun expandToVideoPlayer() {
        removePopup()
        if (mService.hasMedia() && !mService.isPlaying)
            mService.currentMediaWrapper!!.flags = AbstractMediaWrapper.MEDIA_PAUSED
        mService.switchToVideo()
    }

    private fun stopPlayback() {
        var time = mService.time
        if (time != -1L) {
            // remove saved position if in the last 5 seconds
            // else, go back 2 seconds, to compensate loading time
            time = (if (mService.length - time < 5000) 0 else 2000).toLong()
            // Save position
            if (mService.isSeekable)
                Settings.getInstance(mService).edit()
                        .putLong(VIDEO_RESUME_TIME, time).apply()
        }
        mService.stop(video = true)
    }

    private fun showNotification() {
        val piStop = mService.getPendingIntent(Intent(ACTION_REMOTE_STOP))
        val builder = NotificationCompat.Builder(mService, "misc")
                .setSmallIcon(R.drawable.ic_notif_video)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(mService.title)
                .setContentText(mService.getString(R.string.popup_playback))
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(mService.sessionPendingIntent)
                .setDeleteIntent(piStop)

        //Switch
        val notificationIntent = Intent(ACTION_REMOTE_SWITCH_VIDEO)
        val piExpand = mService.getPendingIntent(notificationIntent)
        //PLay Pause
        val piPlay = mService.getPendingIntent(Intent(ACTION_REMOTE_PLAYPAUSE))

        if (mService.isPlaying)
            builder.addAction(R.drawable.ic_popup_pause, mService.getString(R.string.pause), piPlay)
        else
            builder.addAction(R.drawable.ic_popup_play, mService.getString(R.string.play), piPlay)
        builder.addAction(R.drawable.ic_popup_expand_w, mService.getString(R.string.popup_expand), piExpand)
        mService.startForeground(42, builder.build())
    }

    private fun hideNotification() {
        mService.stopForeground(true)
    }

    override fun onSurfacesCreated(vlcVout: IVLCVout) {
        mService.setVideoAspectRatio(null)
        mService.setVideoScale(0f)
        mService.setVideoTrackEnabled(true)
        if (mService.hasMedia()) {
            mService.flush()
            playPauseButton!!.setImageResource(if (mService.isPlaying) R.drawable.ic_popup_pause else R.drawable.ic_popup_play)
        } else
            mService.playIndex(mService.currentMediaPosition)
        showNotification()
    }

    override fun onSurfacesDestroyed(vlcVout: IVLCVout) {
        vlcVout.removeCallback(this)
    }

    companion object {

        private val TAG = "VLC/PopupManager"

        private const val FLING_STOP_VELOCITY = 3000
        private const val MSG_DELAY = 3000

        private const val SHOW_BUTTONS = 0
        private const val HIDE_BUTTONS = 1
    }
}
