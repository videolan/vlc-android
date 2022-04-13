/*
 * ************************************************************************
 *  MediaImageCardPresenter.kt
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
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import org.videolan.moviepedia.database.models.MediaImage
import org.videolan.moviepedia.database.models.MediaImageType
import org.videolan.moviepedia.database.models.Person
import org.videolan.television.R
import org.videolan.tools.dp
import org.videolan.vlc.gui.helpers.downloadIcon

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class MediaImageCardPresenter(private val context: Activity, private val imageType: MediaImageType) : Presenter() {

    private var defaultCardImage: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_people_big)

    inner class ViewHolder(view: View) : Presenter.ViewHolder(view) {
        private val cardView: ImageCardView = view as ImageCardView

        init {
            cardView.mainImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        }

        fun updateCardViewImage(item: Person) {
            downloadIcon(cardView.mainImageView, item.image?.toUri())
        }

        fun updateCardViewImage(image: Drawable?) {
            cardView.mainImage = image
            cardView.mainImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        }

        fun updateCardViewImage(image: Uri?) {
            cardView.mainImage = defaultCardImage
            cardView.mainImageView.scaleType = ImageView.ScaleType.CENTER_CROP
            downloadIcon(cardView.mainImageView, image)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(ContextThemeWrapper(context, R.style.VLCImageCardViewNoTitle))
        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        cardView.setBackgroundColor(ContextCompat.getColor(context, R.color.lb_details_overview_bg_color))
        cardView.setMainImageDimensions(if (imageType == MediaImageType.POSTER) CARD_WIDTH_POSTER else CARD_WIDTH_BACKDROP, if (imageType == MediaImageType.POSTER) CARD_HEIGHT_POSTER else CARD_HEIGHT_BACKDROP)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        val holder = viewHolder as ViewHolder
        val mediaImage = item as MediaImage
        holder.updateCardViewImage(mediaImage.url.toUri())
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {}

    override fun onViewAttachedToWindow(viewHolder: Presenter.ViewHolder?) {
        // TODO?
    }

    companion object {

        private const val TAG = "CardPresenter"

        private val CARD_WIDTH_POSTER = 100.dp
        private val CARD_HEIGHT_POSTER = 150.dp

        private val CARD_WIDTH_BACKDROP = 213.dp
        private val CARD_HEIGHT_BACKDROP = 120.dp
    }
}
