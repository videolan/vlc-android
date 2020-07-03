/*
 * ************************************************************************
 *  DonationSkuView.kt
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
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.donations.util.SkuDetails

class DonationSkuView : ConstraintLayout {
    private val skuTitle: TextView by lazy {
        findViewById<TextView>(R.id.skuTitle)
    }
    private val skuCheck: ImageView by lazy {
        findViewById<ImageView>(R.id.skuCheck)
    }
    private val container: View by lazy {
        findViewById<View>(R.id.container)
    }

    var isChecked = false
        set(value) {
            field = value
            if (value) skuCheck.setVisible() else skuCheck.setGone()
            container.background = ContextCompat.getDrawable(context, if (value) R.drawable.donate_purchased_background_color else R.drawable.donate_background_color)
        }

    var skuPurchased: Boolean = false
    lateinit var skuDetail: SkuDetails

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
    }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.donation_sku, this, true)
    }

    fun setText(text: String) {
        skuTitle.text = text
    }
}
