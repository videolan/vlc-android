/*
 * *************************************************************************
 *  JumpToTimeDIalog.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.dialogs

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.R

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class JumpToTimeDialog : PickTimeFragment() {

    override fun executeAction() {
        if (playbackService == null)
            return
        val hours = if (hours != "") java.lang.Long.parseLong(hours) * HOURS_IN_MICROS else 0L
        val minutes = if (minutes != "") java.lang.Long.parseLong(minutes) * MINUTES_IN_MICROS else 0L
        val seconds = if (seconds != "") java.lang.Long.parseLong(seconds) * SECONDS_IN_MICROS else 0L
        playbackService.time = (hours + minutes + seconds) / 1000L //Time in ms
        dismiss()
    }

    override fun getTitle(): Int {
        return R.string.jump_to_time
    }

    companion object {

        fun newInstance(): JumpToTimeDialog {
            return JumpToTimeDialog()
        }
    }


}
