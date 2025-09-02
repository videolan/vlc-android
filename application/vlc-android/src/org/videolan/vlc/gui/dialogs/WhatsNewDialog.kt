/*
 * ************************************************************************
 *  WidgetMigrationDialog.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.dialogs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import kotlinx.coroutines.launch
import org.videolan.tools.KEY_ENABLE_REMOTE_ACCESS
import org.videolan.tools.KEY_EQUALIZER_ENABLED
import org.videolan.tools.KEY_SHOW_WHATS_NEW
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogWhatsNewBinding
import org.videolan.vlc.gui.EqualizerSettingsActivity
import org.videolan.vlc.gui.preferences.PreferencesActivity

class WhatsNewDialog : VLCBottomSheetDialogFragment() {
    override fun getDefaultState(): Int = STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = false

    private lateinit var binding: DialogWhatsNewBinding


    override fun initialFocusedView(): View = binding.title



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogWhatsNewBinding.inflate(layoutInflater, container, false)
        binding.title.text = getString(R.string.whats_new_title, "3.7")
        binding.showInSettings.setOnClickListener {
            lifecycleScope.launch {
                startActivity(Intent(requireActivity().applicationContext, EqualizerSettingsActivity::class.java))
                dismiss()
            }
        }
        binding.showInSettings2.setOnClickListener {
            lifecycleScope.launch {
                PreferencesActivity.launchWithPref(requireActivity(), "export_settings")
                dismiss()
            }
        }
        binding.showInSettings3.setOnClickListener {
            lifecycleScope.launch {
                PreferencesActivity.launchWithPref(requireActivity(), "playback_speed_audio_global")
                dismiss()
            }
        }
        binding.neverAgain.setOnCheckedChangeListener { _, isChecked ->
            Settings.getInstance(requireActivity()).putSingle(KEY_SHOW_WHATS_NEW, !isChecked)
        }
        return binding.root
    }
}

