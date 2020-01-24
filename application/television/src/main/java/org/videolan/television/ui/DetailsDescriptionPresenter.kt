/*****************************************************************************
 * DetailsDescriptionPresenter.java
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

import android.net.Uri
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import org.videolan.moviepedia.database.models.MediaMetadataWithImages
import org.videolan.moviepedia.database.models.subtitle

class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    var metadata: MediaMetadataWithImages? = null

    override fun onBindDescription(viewHolder: ViewHolder, itemData: Any) {
        val details = itemData as MediaItemDetails
        // In a production app, the itemData object contains the information
        // needed to display details for the media item:
        // viewHolder.getTitle().setText(details.getShortTitle());

        // Here we provide static data for testing purposes:
        val body = when {
            metadata != null -> metadata!!.metadata.summary
            details.body == null -> Uri.decode(details.location)
            else -> details.body + "\n" + Uri.decode(details.location)
        }
        viewHolder.title.text = metadata?.metadata?.title ?: details.title
        viewHolder.subtitle.text = metadata?.subtitle() ?: details.subTitle
        viewHolder.body.text = body
    }

    companion object {
        const val TAG = "DetailsDescriptionPresenter"
    }
}
