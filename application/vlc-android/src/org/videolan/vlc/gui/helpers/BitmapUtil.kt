/*****************************************************************************
 * BitmapUtil.java
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

package org.videolan.vlc.gui.helpers

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.VectorDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.View.MeasureSpec
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AppContextProvider
import org.videolan.tools.BitmapCache
import org.videolan.tools.dp
import org.videolan.tools.removeFileScheme
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat


object BitmapUtil {
    const val TAG = "VLC/UiTools/BitmapUtil"


    fun getPictureFromCache(media: MediaWrapper): Bitmap? {
        // mPicture is not null only if passed through
        // the ctor which is deprecated by now.
        val b = media.picture
        return b ?: BitmapCache.getBitmapFromMemCache(media.location)
    }

    private fun fetchPicture(media: MediaWrapper): Bitmap? {

        val picture = readCoverBitmap(media.artworkURL)
        if (picture != null) BitmapCache.addBitmapToMemCache(media.location, picture)
        return picture
    }

    fun getPicture(media: MediaWrapper): Bitmap? {
        val picture = getPictureFromCache(media)
        return picture ?: fetchPicture(media)
    }

    private fun readCoverBitmap(path: String?): Bitmap? {
        if (path == null) return null
        val ctx = AppContextProvider.appContext
        val res = ctx.resources
        val uri = Uri.decode(path).removeFileScheme()
        var cover: Bitmap? = null
        val options = BitmapFactory.Options()
        val height = res.getDimensionPixelSize(R.dimen.grid_card_thumb_height)
        val width = res.getDimensionPixelSize(R.dimen.grid_card_thumb_width)

        /* Get the resolution of the bitmap without allocating the memory */
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(uri, options)

        if (options.outWidth > 0 && options.outHeight > 0) {
            if (options.outWidth > width) {
                options.outWidth = width
                options.outHeight = height
            }
            options.inJustDecodeBounds = false

            // Decode the file (with memory allocation this time)
            try {
                cover = BitmapFactory.decodeFile(uri, options)
            } catch (e: OutOfMemoryError) {
                cover = null
            }

        }

        return cover
    }

    fun convertBitmapToByteArray(bitmap: Bitmap): ByteArray? {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
    }


    /**
     * Encode bitmap in WEBP format.
     */
    @Suppress("DEPRECATION")
    fun encodeImage(bmp: Bitmap?, enableTracing:Boolean = false, timestampProvider: (() -> String?)? = null): ByteArray? {
        if (bmp == null) return null
        val bos = ByteArrayOutputStream()
        val startTime = if (enableTracing) System.currentTimeMillis() else 0L
        bmp.compress(Bitmap.CompressFormat.WEBP, 100, bos)
        if (enableTracing) {
            val endTime = System.currentTimeMillis()
            val ratio = DecimalFormat("###.#%").format((1 - (bos.size().toDouble() / bmp.byteCount.toDouble())))
            Log.d("VLC/ArtworkProvider", "encImage() Time: ${timestampProvider?.let { it() } ?: ""} Duration: " + (endTime - startTime) + "ms Comp. Ratio: $ratio Thread: ${Thread.currentThread().name}")
        }
        return bos.toByteArray()
    }


    fun centerCrop(srcBmp: Bitmap, width: Int, height: Int): Bitmap {
        val widthDiff = srcBmp.width - width
        val heightDiff = srcBmp.height - height
        if (widthDiff <= 0 && heightDiff <= 0) return srcBmp
        return try {
            Bitmap.createBitmap(
                    srcBmp,
                    widthDiff / 2,
                    heightDiff / 2,
                    width,
                    height
            )
        } catch (ignored: Exception) {
            srcBmp
        }

    }

    fun getBitmapFromVectorDrawable(context: Context, @DrawableRes drawableId: Int, width: Int = -1, height: Int = -1): Bitmap? {
        var drawable: Drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = DrawableCompat.wrap(drawable).mutate()
        }
        return when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            is VectorDrawableCompat, is VectorDrawable -> {
                val bitmap = if (width > 0 && height > 0)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                else
                    Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
            else -> BitmapFactory.decodeResource(context.resources, drawableId)
        }
    }

    fun vectorToBitmap(context: Context, @DrawableRes resVector: Int, width: Int? = null, height: Int? = null): Bitmap {
        val drawable = AppCompatResources.getDrawable(context, resVector) ?: throw IllegalStateException("Invalid drawable")
        val b = Bitmap.createBitmap(width ?: drawable.intrinsicWidth, height
                ?: drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        drawable.setBounds(0, 0, c.width, c.height)
        drawable.draw(c)
        return b
    }

    /**
     * Tints a [Bitmap] with a color
     * @param bitmap the bitmap to tint
     * @param color the color used yt the tint
     * @return a tinted [Bitmap]
     */
    fun tintImage(bitmap: Bitmap, color: Int): Bitmap {
        val paint = Paint()
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        val bitmapResult = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmapResult)
        canvas.drawBitmap(bitmap, 0F, 0F, paint)
        return bitmapResult
    }

    fun makeTransparentBackground(context: Context, width: Int = 48.dp): Bitmap {
        val colorLight = ContextCompat.getColor(context, R.color.grey500)
        val colorDark = ContextCompat.getColor(context, R.color.grey700)
        val paint = Paint()
        val bitmapResult = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmapResult)
        var iter = 0

        val squareSize = width / 6F
        for (i in 0..5) {
            for (j in 0..5) {
                paint.color = if (iter % 2 == 0) colorDark else colorLight
                canvas.drawRect(i * squareSize, j * squareSize, (i + 1) * squareSize, (j + 1) * squareSize, paint)
                iter++
            }
            iter++
        }
        return bitmapResult
    }

    /**
     * Cut a [Bitmap] into a round one
     * @param the [Bitmap] to use
     * @return a rounded [Bitmap]
     */
    fun roundBitmap(bm: Bitmap): Bitmap {

        var w: Int = bm.width
        var h: Int = bm.height

        val radius = if (w < h) w else h
        w = radius
        h = radius

        val bmOut = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmOut)

        val paint = Paint()
        paint.isAntiAlias = true
        paint.color = -0xFFFFFF

        val rect = Rect(0, 0, w, h)
        val rectF = RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(rectF.left + rectF.width() / 2, rectF.top + rectF.height() / 2, (radius / 2).toFloat(), paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        //depending on the bm ratio, modify the bounds to keep a square bitmap
        val bounds = bm.getMaximalSquareBounds()
        canvas.drawBitmap(bm, bounds, rect, paint)

        return bmOut
    }

    /**
     * Cut a [Bitmap] into a rounded rectangle
     *
     * @param bm the [Bitmap] to cut
     * @param width the size of the returned bitmap
     * @param radius the corner radius to use
     * @param topLeft cut the top left corner?
     * @param topRight cut the top right corner?
     * @param bottomLeft cut the bottom left corner?
     * @param bottomRight cut the bottom right corner?
     * @return a rounded rectangle bitmap
     */
    fun roundedRectangleBitmap(bm: Bitmap, width: Int, height: Int = -1, radius: Float = 12.dp.toFloat(), topLeft: Boolean = true, topRight: Boolean = true, bottomLeft: Boolean = true, bottomRight: Boolean = true): Bitmap {

        val w: Int = width
        val h: Int = if (height == -1) width else height


        val bmOut = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmOut)

        val paint = Paint()
        paint.isAntiAlias = true
        paint.color = -0xFFFFFF

        val rect = Rect(0, 0, w, h)
        val rectF = RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawRoundRect(rectF, radius, radius, paint)

        if (!topLeft) canvas.drawRect(RectF(0F, 0F, radius, radius), paint)
        if (!topRight) canvas.drawRect(RectF(w.toFloat() - radius, 0F, w.toFloat(), radius), paint)
        if (!bottomLeft) canvas.drawRect(RectF(0F, h.toFloat() - radius, radius, h.toFloat()), paint)
        if (!bottomRight) canvas.drawRect(RectF(w.toFloat() - radius, h.toFloat() - radius, w.toFloat(), h.toFloat()), paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        //depending on the bm ratio, modify the bounds to keep a square bitmap
        val bounds = if (height == -1 || width == height) bm.getMaximalSquareBounds() else null

        canvas.drawBitmap(bm, bounds, rect, paint)

        return bmOut
    }

    /**
     * Get the bound of the maximal size to cut a bitmap into a square
     *
     * @return the bounds
     */
    private fun Bitmap.getMaximalSquareBounds() = when {
        width > height -> Rect((width - height) / 2, 0, height + ((width - height) / 2), height)
        width < height -> Rect(0, (height - width) / 2, width, width + ((height - width) / 2))
        else -> Rect(0, 0, width, height)
    }

    fun saveOnDisk(bitmap: Bitmap, destPath: String):Boolean {
        val destFile = File(destPath)
        return when {
            destFile.parentFile?.canWrite() == true -> {
                try {
                    ByteArrayOutputStream().use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                        FileOutputStream(destFile).use { it.write(stream.toByteArray()) }
                    }
                    true
                } catch (e: IOException) {
                    Log.e(TAG, "Could not save image to disk", e)
                    false
                }
            }
            else -> {
                Log.e(TAG, "File path not writable: $destFile")
                false
            }
        }
    }

}

/**
 * Constructs a [StateListDrawable] from a drawable and colors
 * @param drawable the drawable to use
 * @param color the color for the normal state
 * @param pressedColor the color for the pressed state
 * @return a [StateListDrawable]
 */
fun Context.getColoredStateListDawable(@DrawableRes drawable: Int, color: Int, pressedColor: Int) = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), getColoredBitmapFromColor(drawable, pressedColor).toDrawable(resources))
    addState(intArrayOf(android.R.attr.state_pressed), getColoredBitmapFromColor(drawable, pressedColor).toDrawable(resources))
    addState(intArrayOf(android.R.attr.state_enabled), getColoredBitmapFromColor(drawable, color).toDrawable(resources))
}

/**
 * Get a colored [Bitmap] from a drawable
 * @param drawableRes the drawable resource to use
 * @param color the color to use to tint the [Bitmap]
 * @param width the [Bitmap] width
 * @param height the [Bitmap] height
 *
 * @return a colored [Bitmap]
 */
fun Context.getColoredBitmapFromColor(@DrawableRes drawableRes: Int, color: Int, width: Int? = null, height: Int? = null) =
        BitmapUtil.tintImage(BitmapUtil.vectorToBitmap(this, drawableRes, width, height)!!, color).also {
            if (BuildConfig.DEBUG) Log.d("AppWidget", "Refresh - getColoredBitmapFromColor - $drawableRes - ${resources.getResourceName(drawableRes)}")
        }

fun Bitmap?.centerCrop(dstWidth: Int, dstHeight: Int): Bitmap? {
    if (this == null) return null
    return BitmapUtil.centerCrop(this, dstWidth, dstHeight)
}


fun Context.getBitmapFromDrawable(@DrawableRes drawableId: Int, width: Int = -1, height: Int = -1): Bitmap? {
    var drawable: Drawable = try {
        ContextCompat.getDrawable(this, drawableId) ?: return null
    } catch (e: Resources.NotFoundException) {
        VectorDrawableCompat.create(this.resources, drawableId, this.theme)!!
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        drawable = DrawableCompat.wrap(drawable).mutate()
    }
    return when {
        drawable is BitmapDrawable -> drawable.bitmap
        drawable is VectorDrawableCompat || (AndroidUtil.isLolliPopOrLater && drawable is VectorDrawable) -> {
            val bitmap = if (width > 0 && height > 0)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            else
                Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
        else -> BitmapFactory.decodeResource(this.resources, drawableId)
    }
}

fun bitmapFromView(view: View, width: Int, height: Int): Bitmap {
    var bmp: Bitmap
    try {
        bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.measure(MeasureSpec.makeMeasureSpec(bmp.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(bmp.height, MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        view.draw(Canvas(bmp))
    } catch (e: OutOfMemoryError) {
        Log.e("BitmapUtil", e.message, e)
        bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, Color.TRANSPARENT)
    }
    return bmp
}