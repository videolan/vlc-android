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
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.ACTION_REMOTE_PLAYPAUSE
import org.videolan.resources.ACTION_REMOTE_STOP
import org.videolan.resources.ACTION_REMOTE_SWITCH_VIDEO
import org.videolan.tools.POPUP_KEEPSCREEN
import org.videolan.tools.Settings
import org.videolan.tools.VIDEO_RESUME_TIME
import org.videolan.tools.putSingle
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.MISC_CHANNEL_ID
import org.videolan.vlc.gui.view.PopupLayout
import org.videolan.vlc.util.getPendingIntent

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PopupManager constructor(private val service: PlaybackService) : PlaybackService.Callback, GestureDetector.OnDoubleTapListener, View.OnClickListener, GestureDetector.OnGestureListener, IVLCVout.OnNewVideoLayoutListener, IVLCVout.Callback {

    private var rootView: PopupLayout? = null
    private lateinit var expandButton: ImageView
    private lateinit var closeButton: ImageView
    private lateinit var playPauseButton: ImageView
    private val alwaysOn: Boolean = Settings.getInstance(service).getBoolean(POPUP_KEEPSCREEN, false)

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
        service.removeCallback(this)
        val vlcVout = service.vout
        vlcVout?.detachViews()
        rootView!!.close()
        rootView = null
    }

    fun showPopup() {
        service.addCallback(this)
        val li = service.applicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                ?: return
        rootView = li.inflate(R.layout.video_popup, null) as PopupLayout
        if (alwaysOn) rootView!!.keepScreenOn = true
        playPauseButton = rootView!!.findViewById(R.id.video_play_pause)
        closeButton = rootView!!.findViewById(R.id.popup_close)
        expandButton = rootView!!.findViewById(R.id.popup_expand)
        playPauseButton.setOnClickListener(this)
        closeButton.setOnClickListener(this)
        expandButton.setOnClickListener(this)

        val gestureDetector = GestureDetectorCompat(service, this)
        gestureDetector.setOnDoubleTapListener(this)
        rootView!!.setGestureDetector(gestureDetector)

        val vlcVout = service.vout ?: return
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

    override fun onMediaEvent(event: IMedia.Event) {}

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
            R.id.video_play_pause -> if (service.hasMedia()) {
                if (service.isPlaying)
                    service.pause()
                else
                    service.play()
            }
            R.id.popup_close -> stopPlayback()
            R.id.popup_expand -> expandToVideoPlayer()
        }
    }

    private fun expandToVideoPlayer() {
        removePopup()
        if (service.hasMedia() && !service.isPlaying)
            service.currentMediaWrapper!!.flags = MediaWrapper.MEDIA_PAUSED
        service.switchToVideo()
    }

    private fun stopPlayback() {
        var time = service.time
        if (time != -1L) {
            // remove saved position if in the last 5 seconds
            // else, go back 2 seconds, to compensate loading time
            time = (if (service.length - time < 5000) 0 else 2000).toLong()
            // Save position
            if (service.isSeekable)
                Settings.getInstance(service).putSingle(VIDEO_RESUME_TIME, time)
        }
        service.stop()
    }

    private fun showNotification() {
        val piStop = service.getPendingIntent(Intent(ACTION_REMOTE_STOP))
        val builder = NotificationCompat.Builder(service, MISC_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif_video)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(service.title)
                .setContentText(service.getString(R.string.popup_playback))
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(service.sessionPendingIntent)
                .setDeleteIntent(piStop)

        //Switch
        val notificationIntent = Intent(ACTION_REMOTE_SWITCH_VIDEO)
        val piExpand = service.getPendingIntent(notificationIntent)
        //PLay Pause
        val piPlay = service.getPendingIntent(Intent(ACTION_REMOTE_PLAYPAUSE))

        if (service.isPlaying)
            builder.addAction(R.drawable.ic_popup_pause, service.getString(R.string.pause), piPlay)
        else
            builder.addAction(R.drawable.ic_popup_play, service.getString(R.string.play), piPlay)
        builder.addAction(R.drawable.ic_popup_expand_w, service.getString(R.string.popup_expand), piExpand)
        service.startForeground(42, builder.build())
    }

    private fun hideNotification() {
        service.stopForeground(true)
    }

    override fun onSurfacesCreated(vlcVout: IVLCVout) {
        service.setVideoAspectRatio(null)
        service.setVideoScale(0f)
        service.setVideoTrackEnabled(true)
        if (service.hasMedia()) {
            service.flush()
            playPauseButton!!.setImageResource(if (service.isPlaying) R.drawable.ic_popup_pause else R.drawable.ic_popup_play)
        } else
            service.playIndex(service.currentMediaPosition)
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
