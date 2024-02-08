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
package org.videolan.television.ui

import android.annotation.TargetApi
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.moviepedia.models.resolver.ResolverMedia
import org.videolan.resources.*
import org.videolan.tools.Settings
import org.videolan.tools.dp
import org.videolan.tools.getLocaleLanguages
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.*


public const val FAVORITE_FLAG = 1000
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class CardPresenter(private val context: Activity, private val isPoster: Boolean = false, private val fromHistory:Boolean = false) : Presenter() {

    private var seenMediaMarkerVisible = true
    private var sDefaultCardImage: Drawable? = VectorDrawableCompat.create(context.resources, R.drawable.ic_default_cone, context.theme)

    private val imageDefaultWidth: Float by lazy { context.resources.getDimension(R.dimen.tv_grid_card_thumb_width) }
    private val seenDrawable: Drawable? by lazy { VectorDrawableCompat.create(context.resources, R.drawable.ic_seen_tv_normal, context.theme) }

    init {
        seenMediaMarkerVisible = Settings.getInstance(context).getBoolean("media_seen", true)

    }

    inner class ViewHolder(view: View) : Presenter.ViewHolder(view) {
        val cardView: ImageCardView = view as ImageCardView

        init {
            cardView.mainImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        fun updateCardViewImage(item: MediaLibraryItem) {
            val noArt = item.artworkMrl.isNullOrEmpty()
            if (item is MediaWrapper) {
                if (BuildConfig.DEBUG) Log.d("CardPresenter", "ITEM: ${item.title} // meta = ${item.hasFlag(FAVORITE_FLAG)}")
                if (item.hasFlag(FAVORITE_FLAG)) cardView.badgeImage = ContextCompat.getDrawable(cardView.context, R.drawable.ic_favorite_tv_badge)
                val folder = item.type == MediaWrapper.TYPE_DIR
                val video = item.type == MediaWrapper.TYPE_VIDEO
                if (!folder && (video && !item.isThumbnailGenerated)) {
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
                    loadPlaylistImageWithWidth(cardView.mainImageView, item, imageDefaultWidth.toInt(), true)
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
            picture = if (mediaLibraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && (mediaLibraryItem as MediaWrapper).type == MediaWrapper.TYPE_DIR) {
                if (mediaLibraryItem.uri.scheme == "file")
                    context.getBitmapFromDrawable(R.drawable.ic_folder_big)
                else
                    context.getBitmapFromDrawable(R.drawable.ic_network_big)
            } else
                AudioUtil.readCoverBitmap(Uri.decode(mediaLibraryItem.artworkMrl), res.getDimensionPixelSize(R.dimen.tv_grid_card_thumb_width))
            if (picture == null) picture = getBitmapFromDrawable(context, getTvIconRes(mediaLibraryItem))
            return picture
        }

        fun updateCardViewImage(image: Drawable?) {
            cardView.mainImage = image
            cardView.mainImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        fun updateCardViewImage(image: Uri?) {
            cardView.mainImage = BitmapDrawable(cardView.resources, getBitmapFromDrawable(context, R.drawable.ic_video_big))
            cardView.mainImageView.scaleType = ImageView.ScaleType.FIT_CENTER
            downloadIcon(cardView.mainImageView, image)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(context)
        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        cardView.setBackgroundColor(ContextCompat.getColor(context, R.color.lb_details_overview_bg_color))
        if (isPoster) cardView.setMainImageDimensions(CARD_WIDTH_POSTER, CARD_HEIGHT_POSTER)
        else cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        val holder = viewHolder as ViewHolder
        when (item) {
            is MediaWrapper -> {
                Tools.setMediaDescription(item)
                holder.cardView.titleText = item.title
                holder.cardView.contentText = item.description
                holder.updateCardViewImage(item)
                if (seenMediaMarkerVisible
                        && item.type == MediaWrapper.TYPE_VIDEO
                        && item.seen > 0L)
                    holder.cardView.badgeImage = seenDrawable
                holder.view.setOnLongClickListener { v ->
                    TvUtil.showMediaDetail(v.context, item, fromHistory)
                    true
                }
            }
            is ResolverMedia -> {
                holder.cardView.titleText = item.title()
                holder.cardView.contentText = item.getCardSubtitle()

                holder.updateCardViewImage(item.imageUri(context.getLocaleLanguages()))
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
        if (item is DummyItem && (item.id == CATEGORY_NOW_PLAYING || item.id == CATEGORY_NOW_PLAYING_PIP)) {
            val badge = AnimatedVectorDrawableCompat.create(context, R.drawable.anim_now_playing)!!
            holder.cardView.badgeImage = badge
            badge.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable?) {
                    holder.cardView.post { badge.start() }
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
                    UPDATE_DESCRIPTION -> {
                        Tools.setMediaDescription(item)
                        holder.cardView.contentText = media.description
                    }
                    UPDATE_THUMB -> loadImage(holder.cardView, media)
                    UPDATE_TIME -> {
                        val mediaWrapper = item as MediaWrapper
                        Tools.setMediaDescription(mediaWrapper)
                        holder.cardView.contentText = mediaWrapper.description
                        if (mediaWrapper.time <= 0) {
                            if (seenMediaMarkerVisible && item.type == MediaWrapper.TYPE_VIDEO
                                    && item.seen > 0L)
                                holder.cardView.badgeImage = seenDrawable
                        }
                    }
                    UPDATE_SEEN -> {
                        val mw = item as MediaWrapper
                        if (seenMediaMarkerVisible && mw.type == MediaWrapper.TYPE_VIDEO && mw.seen > 0L)
                            holder.cardView.badgeImage = seenDrawable
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
        private val CARD_WIDTH = AppContextProvider.appResources.getDimensionPixelSize(R.dimen.tv_grid_card_thumb_width)
        private val CARD_HEIGHT = AppContextProvider.appResources.getDimensionPixelSize(R.dimen.tv_grid_card_thumb_height)

        private val CARD_WIDTH_POSTER = 190.dp
        private val CARD_HEIGHT_POSTER = 285.dp

    }
}
