/*****************************************************************************
 * DetailsDescriptionPresenter.java
 *****************************************************************************
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
 *****************************************************************************/
package org.videolan.vlc.gui.tv;

import android.net.Uri;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;

public class DetailsDescriptionPresenter extends
        AbstractDetailsDescriptionPresenter {
    public static final String TAG ="DetailsDescriptionPresenter";

    protected void onBindDescription(ViewHolder viewHolder, Object itemData) {
        MediaItemDetails details = (MediaItemDetails) itemData;
        // In a production app, the itemData object contains the information
        // needed to display details for the media item:
        // viewHolder.getTitle().setText(details.getShortTitle());

        // Here we provide static data for testing purposes:
        String body = details.getBody() == null ? Uri.decode(details.getLocation()) :
                details.getBody()+"\n"+Uri.decode(details.getLocation());
        viewHolder.getTitle().setText(details.getTitle());
        viewHolder.getSubtitle().setText(details.getSubTitle());
        viewHolder.getBody().setText(body);
    }


}
