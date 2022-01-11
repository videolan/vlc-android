/*
 * *************************************************************************
 *  ThreeStatesCheckbox.java
 * **************************************************************************
 *  Copyright © 2017 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.helpers

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatCheckBox
import org.videolan.vlc.R

class ThreeStatesCheckbox : AppCompatCheckBox {
    private var currentState = 0
    var state: Int
        get() = currentState
        set(state) {
            currentState = state
            updateBtn()
        }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    internal fun init() {
        updateBtn()
        setOnCheckedChangeListener { _, _ ->
            when (currentState) {
                STATE_PARTIAL, STATE_UNCHECKED -> currentState = STATE_CHECKED
                STATE_CHECKED -> currentState = STATE_UNCHECKED
            }
            updateBtn()
        }
    }

    private fun updateBtn() {
        val btnDrawable: Int = when (currentState) {
            STATE_PARTIAL -> R.drawable.ic_checkbox_partialy
            STATE_CHECKED -> R.drawable.ic_checkbox_true
            else -> R.drawable.ic_checkbox_not_checked
        }
        setButtonDrawable(btnDrawable)

    }

    companion object {

        const val STATE_UNCHECKED = 0
        const val STATE_CHECKED = 1
        const val STATE_PARTIAL = 2
    }
}
