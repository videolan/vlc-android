/*
 * ************************************************************************
 *  TvAdapterUtils.kt
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

package org.videolan.television.ui.browser

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.television.ui.FocusableConstraintLayout
import org.videolan.vlc.R

object TvAdapterUtils {

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun itemFocusChange(hasFocus: Boolean, itemSize: Int, container: FocusableConstraintLayout, isList: Boolean, listener: () -> Unit) {
        if (hasFocus) {
            val growFactor = if (isList) 1.05 else 1.1
            var newWidth = (itemSize * growFactor).toInt()
            if (newWidth % 2 == 1) {
                newWidth--
            }
            val scale = newWidth.toFloat() / itemSize
            if (AndroidUtil.isLolliPopOrLater)
                container.animate().scaleX(scale).scaleY(scale).translationZ(scale)
            else
                container.animate().scaleX(scale).scaleY(scale)

            listener()
        } else {
            if (AndroidUtil.isLolliPopOrLater)
                container.animate().scaleX(1f).scaleY(1f).translationZ(1f)
            else
                container.animate().scaleX(1f).scaleY(1f)
        }

        (container.background as? TransitionDrawable)?.let {
            if (hasFocus) it.startTransition(250) else it.reverseTransition(250)
        }
    }
}