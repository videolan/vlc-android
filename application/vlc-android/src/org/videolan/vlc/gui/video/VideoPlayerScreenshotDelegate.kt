/*
 * ************************************************************************
 *  VideoPlayerScreenshotDelegate.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.video

import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.ViewStubCompat
import org.videolan.tools.dp
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.util.getScreenHeight
import org.videolan.vlc.util.share
import java.io.File

class VideoPlayerScreenshotDelegate(private val player: VideoPlayerActivity) {


    private lateinit var screenshotImageBackground: View
    private lateinit var screenshotActions: FrameLayout
    private lateinit var flash: View
    private lateinit var screenshotShare: View
    private lateinit var screenshotImageView: ImageView
    private lateinit var container: FrameLayout

    /**
     * Inflates the screenshot layout and retrieve the views
     *
     */
    private fun initScreenshot() {
        val vsc = player.findViewById<ViewStubCompat>(R.id.player_screenshot_stub)
        if (vsc != null) {
            vsc.setVisible()
            screenshotImageView = player.findViewById(R.id.screenshot_bitmap)
            screenshotImageBackground = player.findViewById(R.id.screenshot_bitmap_background)
            screenshotShare = player.findViewById(R.id.screenshot_share)
            flash = player.findViewById(R.id.screenshot_flash)
            container = player.findViewById(R.id.screenshotContainer)
            screenshotActions = player.findViewById(R.id.screenshot_actions)
        }
    }

    /**
     * Display the screenshot that has just been taken
     *
     * @param dst the screenshot file
     * @param bitmap the screenshot bitmap
     * @param surfaceBounds the surface view bounds
     * @param width the screenshot width
     * @param height the screenshot height
     */
    fun takeScreenshot(dst: File, bitmap: Bitmap, surfaceBounds: IntArray, width: Int, height: Int) {
        initScreenshot()
        screenshotShare.setOnClickListener {
            player.share(dst)
        }

        //this animation seems complicated but there was not way to make it work with ConstraintSet
        screenshotImageView.setImageBitmap(bitmap)

        screenshotImageView.alpha = 1F

        screenshotImageView.translationX = surfaceBounds[0].toFloat()
        screenshotImageView.translationY = surfaceBounds[1].toFloat()
        screenshotImageView.layoutParams.width = width
        screenshotImageView.layoutParams.height = height
        screenshotImageView.scaleX = 1F
        screenshotImageView.scaleY = 1F
        screenshotImageBackground.setVisible()
        screenshotImageBackground.pivotX = 0F
        screenshotImageBackground.pivotY = 0F
        screenshotImageView.setVisible()
        screenshotImageView.pivotX = 0F
        screenshotImageView.pivotY = 0F
        val ratio = 150.dp.toFloat() / width.toFloat()
        val newHeight = height.toFloat() * ratio
        val newY = player.getScreenHeight().toFloat() - newHeight - 48.dp

        screenshotActions.alpha = 0F
        screenshotImageView.animate().translationY(newY).translationX(16.dp.toFloat()).scaleX(ratio).scaleY(ratio).withEndAction {
            screenshotActions.translationY = player.getScreenHeight().toFloat() - 48.dp - 48.dp
            screenshotActions.layoutParams.width = 250.dp
            screenshotActions.setVisible()
            screenshotActions.animate().alpha(1F)
            screenshotImageBackground.animate().alpha(1F)
            val roundedRectangleBitmap = BitmapUtil.roundedRectangleBitmap(bitmap, (width.toDouble() * ratio).toInt(), (height.toDouble() * ratio).toInt(), 4.dp.toFloat())
            screenshotImageView.setImageBitmap(roundedRectangleBitmap)
            screenshotImageView.layoutParams.width = (width * ratio).toInt()
            screenshotImageView.layoutParams.height = (height * ratio).toInt()
            screenshotImageView.scaleX = 1F
            screenshotImageView.scaleY = 1F

        }

        val padding = 6.dp
        screenshotImageBackground.translationY = newY - padding
        screenshotImageBackground.translationX = (16.dp - padding).toFloat()
        screenshotImageBackground.layoutParams.width = ((width.toFloat() * ratio) + padding * 2).toInt()
        screenshotImageBackground.layoutParams.height = ((height.toFloat() * ratio) + padding * 2).toInt()

        flash.setVisible()
        flash.animate().alpha(1F).withEndAction {
            flash.animate().alpha(0F).withEndAction {
                flash.setGone()
            }
        }

        player.handler.removeMessages(VideoPlayerActivity.FADE_OUT_SCREENSHOT)
        player.handler.sendEmptyMessageDelayed(VideoPlayerActivity.FADE_OUT_SCREENSHOT, 5000)

    }

    /**
     * Hides the screenshot UI
     *
     */
    fun hide() {
        if (::screenshotImageView.isInitialized) screenshotImageView.animate().translationYBy(200.dp.toFloat()).alpha(0F).withEndAction {
            screenshotImageView.setGone()
        }
        if (::screenshotActions.isInitialized) screenshotActions.animate().translationYBy(200.dp.toFloat()).alpha(0F).withEndAction {
            screenshotActions.setGone()
        }
        if (::screenshotImageBackground.isInitialized) screenshotImageBackground.animate().translationYBy(200.dp.toFloat()).alpha(0F).withEndAction {
            screenshotImageBackground.setGone()
        }
    }

}