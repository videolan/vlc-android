/*****************************************************************************
 * CardPresenter.java
 *
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
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
package org.videolan.vlc.gui.tv

import android.annotation.TargetApi
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.gui.helpers.loadImage
import org.videolan.vlc.gui.helpers.loadPlaylistImageWithWidth
import org.videolan.vlc.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class CardPresenter(private val context: Activity) : Presenter() {

    private var mIsSeenMediaMarkerVisible = true
    private var sDefaultCardImage: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_default_cone)

    private val imageDefaultWidth: Float by lazy { context.resources.getDimension(R.dimen.tv_grid_card_thumb_width) }

    init {
        mIsSeenMediaMarkerVisible = Settings.getInstance(context).getBoolean("media_seen", true)

    }

    inner class ViewHolder(view: View) : Presenter.ViewHolder(view) {
        val cardView: ImageCardView = view as ImageCardView

        init {
            cardView.mainImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        fun updateCardViewImage(item: MediaLibraryItem) {
            val noArt = TextUtils.isEmpty(item.artworkMrl)
            if (item is AbstractMediaWrapper) {
                val group = item.type == AbstractMediaWrapper.TYPE_GROUP
                val folder = item.type == AbstractMediaWrapper.TYPE_DIR
                val video = item.type == AbstractMediaWrapper.TYPE_VIDEO
                if (!folder && (group || video && !item.isThumbnailGenerated)) {
                    if (noArt) {
                        cardView.mainImageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        cardView.mainImage = BitmapDrawable(cardView.resources, getDefaultImage(item))
                    }
                    loadImage(cardView, item)
                    return
                }
            }
            when {
                item.itemType == MediaLibraryItem.TYPE_PLAYLIST -> {
                    cardView.mainImageView.scaleType = ImageView.ScaleType.FIT_CENTER
                    loadPlaylistImageWithWidth(cardView.mainImageView, item, imageDefaultWidth.toInt())
                }
                noArt -> {
                    cardView.mainImageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    cardView.mainImage = BitmapDrawable(cardView.resources, getDefaultImage(item))
                }
                else -> loadImage(cardView, item)
            }
        }

        private fun getDefaultImage(mediaLibraryItem: MediaLibraryItem): Bitmap? {
            var picture: Bitmap?
            val res = cardView.resources
            picture = if (mediaLibraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && (mediaLibraryItem as AbstractMediaWrapper).type == AbstractMediaWrapper.TYPE_DIR) {
                if (TextUtils.equals(mediaLibraryItem.uri.scheme, "file"))
                    BitmapFactory.decodeResource(res, R.drawable.ic_menu_folder_big)
                else
                    BitmapFactory.decodeResource(res, R.drawable.ic_menu_network_big)
            } else
                AudioUtil.readCoverBitmap(Uri.decode(mediaLibraryItem.artworkMrl), res.getDimensionPixelSize(R.dimen.tv_grid_card_thumb_width))
            if (picture == null) picture = getBitmapFromDrawable(context, TvUtil.getIconRes(mediaLibraryItem))
            return picture
        }

        fun updateCardViewImage(image: Drawable?) {
            cardView.mainImage = image
            cardView.mainImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(context)
        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        cardView.setBackgroundColor(ContextCompat.getColor(context, R.color.lb_details_overview_bg_color))
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        val holder = viewHolder as ViewHolder
        when (item) {
            is AbstractMediaWrapper -> {
                holder.cardView.titleText = item.title
                holder.cardView.contentText = item.description
                holder.updateCardViewImage(item)
                if (mIsSeenMediaMarkerVisible
                        && item.type == AbstractMediaWrapper.TYPE_VIDEO
                        && item.seen > 0L)
                    holder.cardView.badgeImage = ContextCompat.getDrawable(context, R.drawable.ic_seen_tv_normal)
                holder.view.setOnLongClickListener { v ->
                    TvUtil.showMediaDetail(v.context, item)
                    true
                }
            }
            is MediaLibraryItem -> {
                holder.cardView.titleText = item.title
                holder.cardView.contentText = item.description
                holder.updateCardViewImage(item)
            }
            is String -> {
                holder.cardView.titleText = item
                holder.cardView.contentText = ""
                holder.updateCardViewImage(sDefaultCardImage)
            }
        }
        if (item is DummyItem && item.id == CATEGORY_NOW_PLAYING) {
            val badge = AnimatedVectorDrawableCompat.create(context, R.drawable.anim_now_playing)!!
            holder.cardView.badgeImage = badge
            badge.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable?) {
                    badge.start()
                    super.onAnimationEnd(drawable)
                }
            })
            badge.start()
        }
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any, payloads: List<Any>?) {
        if (payloads!!.isEmpty())
            onBindViewHolder(viewHolder, item)
        else {
            val holder = viewHolder as ViewHolder
            val media = item as MediaLibraryItem
            for (data in payloads) {
                when (data as Int) {
                    UPDATE_DESCRIPTION -> holder.cardView.contentText = media.description
                    UPDATE_THUMB -> loadImage(holder.cardView, media)
                    UPDATE_TIME -> {
                        val mediaWrapper = item as AbstractMediaWrapper
                        Tools.setMediaDescription(mediaWrapper)
                        holder.cardView.contentText = mediaWrapper.description
                        if (mediaWrapper.time <= 0) {
                            if (mIsSeenMediaMarkerVisible && item.type == AbstractMediaWrapper.TYPE_VIDEO
                                    && item.seen > 0L)
                                holder.cardView.badgeImage = ContextCompat.getDrawable(context, R.drawable.ic_seen_tv_normal)
                        }
                    }
                    UPDATE_SEEN -> {
                        val mw = item as AbstractMediaWrapper
                        if (mIsSeenMediaMarkerVisible && mw.type == AbstractMediaWrapper.TYPE_VIDEO && mw.seen > 0L)
                            holder.cardView.badgeImage = ContextCompat.getDrawable(context, R.drawable.ic_seen_tv_normal)
                    }
                }
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {}

    override fun onViewAttachedToWindow(viewHolder: Presenter.ViewHolder?) {
        // TODO?
    }

    companion object {

        private const val TAG = "CardPresenter"
        private val CARD_WIDTH = VLCApplication.appResources.getDimensionPixelSize(R.dimen.tv_grid_card_thumb_width)
        private val CARD_HEIGHT = VLCApplication.appResources.getDimensionPixelSize(R.dimen.tv_grid_card_thumb_height)

    }
}
