/*
 * *************************************************************************
 *  HackyDrawerLayout.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.drawerlayout.widget.DrawerLayout

/*
 * Workaround for support lib bug, see https://code.google.com/p/android/issues/detail?id=60464
 */
class HackyDrawerLayout : DrawerLayout {

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {}

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onInterceptTouchEvent(ev)
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }

    }
}
