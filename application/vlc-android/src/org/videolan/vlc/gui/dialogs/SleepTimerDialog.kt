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

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import androidx.core.content.edit
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.videolan.tools.SLEEP_TIMER_DEFAULT_INTERVAL
import org.videolan.tools.SLEEP_TIMER_DEFAULT_RESET_INTERACTION
import org.videolan.tools.SLEEP_TIMER_DEFAULT_WAIT
import org.videolan.tools.SLEEP_TIMER_RESET_INTERACTION
import org.videolan.tools.SLEEP_TIMER_WAIT
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.gui.video.VideoPlayerActivity.Companion.videoRemoteFlow
import org.videolan.vlc.viewmodels.PlaylistModel
import java.util.Calendar


private const val FOR_DEFAULT = "for_default"
class SleepTimerDialog : PickTimeFragment() {

    private var defaultSleepTimer: Boolean = false
    private lateinit var settings: SharedPreferences
    private val playlistModel by lazy { PlaylistModel.get(this) }

    override fun showTimeOnly() = false


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        maxTimeSize = 4
        settings = Settings.getInstance(requireActivity())
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultSleepTimer = arguments?.getBoolean(FOR_DEFAULT, false) ?: false
        binding.timPicWaitCheckbox.isChecked = settings.getBoolean(if (defaultSleepTimer) SLEEP_TIMER_DEFAULT_WAIT else SLEEP_TIMER_WAIT, false)
        binding.timPicResetCheckbox.isChecked = settings.getBoolean(if (defaultSleepTimer) SLEEP_TIMER_DEFAULT_RESET_INTERACTION else SLEEP_TIMER_RESET_INTERACTION, false)
        if (defaultSleepTimer) {
            val interval = settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL, -1L)
            if (interval > 0) {
                val hours = (interval / (HOURS_IN_MICROS / 1000)).toString()
                val minutes = (interval % (HOURS_IN_MICROS / 1000) / (MINUTES_IN_MICROS / 1000)).toString()
                updateValue("$hours$minutes")
            }
        }
        binding.timPicWaitCheckbox.isChecked = playlistModel.service?.waitForMediaEnd ?: false
        binding.timPicResetCheckbox.isChecked = playlistModel.service?.resetOnInteraction ?: false

    }

    override fun executeAction() {
        val hours = if (hours != "") java.lang.Long.parseLong(hours) * HOURS_IN_MICROS else 0L
        val minutes = if (minutes != "") java.lang.Long.parseLong(minutes) * MINUTES_IN_MICROS else 0L
        val interval = (hours + minutes) / MILLIS_IN_MICROS //Interval in ms
        val settings = Settings.getInstance(requireActivity())

        if (defaultSleepTimer) {
              settings.edit {
                  putLong(SLEEP_TIMER_DEFAULT_INTERVAL, interval)
                  putBoolean(SLEEP_TIMER_DEFAULT_WAIT, binding.timPicWaitCheckbox.isChecked)
                  putBoolean(SLEEP_TIMER_DEFAULT_RESET_INTERACTION, binding.timPicResetCheckbox.isChecked)
              }
            dismiss()
            return

        }

        playlistModel.service?.waitForMediaEnd = binding.timPicWaitCheckbox.isChecked
        playlistModel.service?.resetOnInteraction = binding.timPicResetCheckbox.isChecked

        settings.putSingle(SLEEP_TIMER_RESET_INTERACTION, binding.timPicResetCheckbox.isChecked)
        settings.putSingle(SLEEP_TIMER_WAIT, binding.timPicWaitCheckbox.isChecked)


        playlistModel.service?.sleepTimerInterval = interval

        val sleepTime = Calendar.getInstance()
        sleepTime.timeInMillis = sleepTime.timeInMillis + interval
        sleepTime.set(Calendar.SECOND, 0)
        playlistModel.service?.setSleepTimer(sleepTime)

        dismiss()
    }

    override fun showDeleteCurrent() = true

    override fun onClick(v: View) {
        if (v.id == R.id.tim_pic_delete_current) {
            if (defaultSleepTimer) {
                settings.putSingle(SLEEP_TIMER_DEFAULT_INTERVAL, -1L)
                dismiss()
                return
            }
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

        fun newInstance(forDefault:Boolean = false): SleepTimerDialog {
            val fragment = SleepTimerDialog()
            val bundle = Bundle(1)
            bundle.putBoolean(FOR_DEFAULT, forDefault)
            fragment.arguments = bundle

            return fragment
        }
    }
}