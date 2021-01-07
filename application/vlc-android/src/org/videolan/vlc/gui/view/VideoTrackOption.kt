/*
 * ************************************************************************
 *  TitleListView.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable

class VideoTrackOption : ConstraintLayout {

    private val titleView: TextView by lazy {
        findViewById(R.id.option_title)
    }
    private val iconView: ImageView by lazy {
        findViewById(R.id.option_icon)
    }



    fun setIcon(@DrawableRes icon:Int) {
        iconView.setImageBitmap(context.getBitmapFromDrawable(icon))
    }

    fun setTitle(title:String) {
        titleView.text = title
    }

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
    }


    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.player_overlay_track_option_item, this, true)
    }
}
