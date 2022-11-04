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

import org.videolan.vlc.R

class JumpToTimeDialog : PickTimeFragment() {

    override fun executeAction() {
        val newTime = getTimeInMillis() //Time in ms
        playbackService.setTime(newTime)
        playbackService.playlistManager.player.updateProgress(newTime)
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
