/*
 * *************************************************************************
 *  SleepTimerDialog.java
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

package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.PlayerOptionsDelegate
import org.videolan.vlc.gui.helpers.setSleep
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class SleepTimerDialog : PickTimeFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        maxTimeSize = 4
        return view
    }

    override fun executeAction() {
        val hours = if (hours != "") java.lang.Long.parseLong(hours) * HOURS_IN_MICROS else 0L
        val minutes = if (minutes != "") java.lang.Long.parseLong(minutes) * MINUTES_IN_MICROS else 0L
        val interval = (hours + minutes) / MILLIS_IN_MICROS //Interval in ms

        if (interval < ONE_DAY_IN_MILLIS) {
            val sleepTime = Calendar.getInstance()
            sleepTime.timeInMillis = sleepTime.timeInMillis + interval
            sleepTime.set(Calendar.SECOND, 0)
            requireContext().setSleep(sleepTime)
        }

        dismiss()
    }

    override fun showDeleteCurrent(): Boolean {
        return PlayerOptionsDelegate.playerSleepTime != null
    }

    override fun onClick(v: View) {
        if (v.id == R.id.tim_pic_delete_current) {
            requireActivity().setSleep(null)
            dismiss()
        } else super.onClick(v)
    }

    override fun getTitle(): Int {
        return R.string.sleep_in
    }

    companion object {

        private var ONE_DAY_IN_MILLIS = (24 * 60 * 60 * 1000).toLong()

        fun newInstance(): SleepTimerDialog {
            return SleepTimerDialog()
        }
    }
}