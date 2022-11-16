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

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import org.videolan.tools.SLEEP_TIMER_WAIT
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.viewmodels.PlaylistModel
import java.util.*

class SleepTimerDialog : PickTimeFragment() {

    private lateinit var settings: SharedPreferences
    private lateinit var waitCheckBox: CheckBox
    private val playlistModel by lazy { PlaylistModel.get(this) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        maxTimeSize = 4
        settings = Settings.getInstance(requireActivity())
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        waitCheckBox = view.findViewById(R.id.tim_pic_wait_checkbox)
        waitCheckBox.isChecked = settings.getBoolean(SLEEP_TIMER_WAIT, false)
    }

    override fun executeAction() {
        playlistModel.service?.waitForMediaEnd = waitCheckBox.isChecked
        settings.putSingle(SLEEP_TIMER_WAIT, waitCheckBox.isChecked)

        val hours = if (hours != "") java.lang.Long.parseLong(hours) * HOURS_IN_MICROS else 0L
        val minutes = if (minutes != "") java.lang.Long.parseLong(minutes) * MINUTES_IN_MICROS else 0L
        val interval = (hours + minutes) / MILLIS_IN_MICROS //Interval in ms

        val sleepTime = Calendar.getInstance()
        sleepTime.timeInMillis = sleepTime.timeInMillis + interval
        sleepTime.set(Calendar.SECOND, 0)
        playlistModel.service?.setSleepTimer(sleepTime)

        dismiss()
    }

    override fun showDeleteCurrent() = true

    override fun onClick(v: View) {
        if (v.id == R.id.tim_pic_delete_current) {
            playlistModel.service?.waitForMediaEnd = false
            playlistModel.service?.setSleepTimer(null)
            settings.putSingle(SLEEP_TIMER_WAIT, false)
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