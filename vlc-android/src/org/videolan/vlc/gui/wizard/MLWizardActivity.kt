/**
 * **************************************************************************
 * MLWizardActivity.kt
 * ****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 * Author: Geoffrey Métais
 *
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.wizard

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.tools.coroutineScope
import org.videolan.vlc.R
import org.videolan.vlc.databinding.MlWizardActivityBinding
import org.videolan.vlc.startMedialibrary
import org.videolan.vlc.util.*

class MLWizardActivity : AppCompatActivity() {

    lateinit var binding: MlWizardActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.ml_wizard_activity)
        setFinishOnTouchOutside(false)
    }

    @SuppressLint("ApplySharedPref")
    @Suppress("UNUSED_PARAMETER")
    fun apply(v: View) = coroutineScope.launch(Dispatchers.IO) {
        val parse = binding.wizardCheckScan.isChecked
        val prefs = Settings.getInstance(this@MLWizardActivity)
        prefs.edit().putInt(KEY_MEDIALIBRARY_SCAN, if (parse) ML_SCAN_ON else ML_SCAN_OFF).commit()
        startMedialibrary(true, true, parse)
        if (!isFinishing) finish()
    }
}

fun Context.startMLWizard() = AppScope.launch { startActivity(Intent(applicationContext, MLWizardActivity::class.java)) }