/*
 * ************************************************************************
 *  MediaMetadataCardPresenter.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

/*****************************************************************************
 * PersonCardPresenter.java
 *
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
 * Author: Nicolas POMEPUY
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
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.moviepedia.database.models.MediaMetadataWithImages
import org.videolan.moviepedia.database.models.Person
import org.videolan.moviepedia.database.models.tvEpisodeSubtitle
import org.videolan.television.R
import org.videolan.tools.dp
import org.videolan.vlc.gui.helpers.downloadIcon
import org.videolan.vlc.util.generateResolutionClass

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class MetadataCardPresenter(private val context: Activity) : Presenter() {

    private var defaultCardImage: Drawable? = VectorDrawableCompat.create(context.resources, R.drawable.ic_people_big, context.theme)

    inner class ViewHolder(view: View) : Presenter.ViewHolder(view) {

        val cover: ImageView = view.findViewById(R.id.media_cover)
        val title: TextView = view.findViewById(R.id.title)
        val seenBadge: ImageView = view.findViewById(R.id.ml_item_seen)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
        val resolution: TextView = view.findViewById(R.id.badgeTV)
        val progress: ProgressBar = view.findViewById(R.id.progressBar)

        init {
            cover.scaleType = ImageView.ScaleType.FIT_CENTER
            view.findViewById<View>(R.id.container).background = ContextCompat.getDrawable(context, R.drawable.tv_card_background)
        }

        fun updateCardViewImage(item: Person) {
            downloadIcon(cover, Uri.parse(item.image))
        }

        fun updateCardViewImage(image: Drawable?) {
            cover.setImageDrawable(image)
            cover.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        fun updateCardViewImage(image: Uri?) {
            cover.setImageDrawable(defaultCardImage)
            cover.scaleType = ImageView.ScaleType.FIT_CENTER
            downloadIcon(cover, image)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = context.layoutInflater.inflate(R.layout.movie_browser_tv_item, parent, false)
        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        cardView.setBackgroundColor(ContextCompat.getColor(context, R.color.lb_details_overview_bg_color))
        val lp = cardView.findViewById<ImageView>(R.id.media_cover).layoutParams
        lp.width = CARD_WIDTH_POSTER
        lp.height = CARD_HEIGHT_POSTER
        cardView.findViewById<View>(R.id.container).layoutParams.height = CARD_HEIGHT_POSTER + 64.dp
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        val holder = viewHolder as ViewHolder
        val mediaMetadataWithImages = item as MediaMetadataWithImages
        holder.title.text = mediaMetadataWithImages.metadata.title
        holder.subtitle.text = mediaMetadataWithImages.tvEpisodeSubtitle()
        mediaMetadataWithImages.metadata.currentPoster.let { holder.updateCardViewImage(Uri.parse(it)) }
        mediaMetadataWithImages.media?.let { media ->
            holder.resolution.text = generateResolutionClass(media.width, media.height)
        }
        holder.resolution.visibility = if (mediaMetadataWithImages.media != null) View.VISIBLE else View.GONE
        holder.seenBadge.visibility = if (mediaMetadataWithImages.media?.seen != null && mediaMetadataWithImages.media?.seen!! > 0L) View.VISIBLE else View.GONE
        var progress = 0
        var max = 0
        mediaMetadataWithImages.media?.let { media ->
            if (media.length > 0) {
                val lastTime = media.displayTime
                if (lastTime > 0) {
                    max = (media.length / 1000).toInt()
                    progress = (lastTime / 1000).toInt()
                }
            }
        }
        holder.progress.max = max
        holder.progress.progress = progress
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {}

    override fun onViewAttachedToWindow(viewHolder: Presenter.ViewHolder?) {
        // TODO?
    }

    companion object {

        private const val TAG = "CardPresenter"

        private val CARD_WIDTH_POSTER = 100.dp
        private val CARD_HEIGHT_POSTER = 150.dp
    }
}
