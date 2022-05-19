/*
 * ************************************************************************
 *  PreferenceSearchActivity.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.preferences.search

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.videolan.resources.AppContextProvider
import org.videolan.resources.buildPkgString
import org.videolan.tools.LocaleUtils
import org.videolan.tools.setGone
import org.videolan.vlc.databinding.PreferencesSearchActivityBinding
import org.videolan.vlc.gui.BaseActivity
import org.videolan.vlc.gui.preferences.EXTRA_PREF_END_POINT
import org.videolan.vlc.viewmodels.PreferenceSearchModel
import java.util.*

class PreferenceSearchActivity : BaseActivity(), TextWatcher, PreferenceItemAdapter.ClickHandler {
    private lateinit var binding: PreferencesSearchActivityBinding
    private lateinit var viewmodel: PreferenceSearchModel
    private lateinit var adapter: PreferenceItemAdapter

    override fun getSnackAnchorView(overAudioPlayer:Boolean): View? = findViewById(android.R.id.content)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = PreferencesSearchActivityBinding.inflate(layoutInflater)

        setContentView(binding.root)
        binding.closeButton.setOnClickListener {
            finish()
        }
        viewmodel = ViewModelProvider(this, PreferenceSearchModel.Factory(this)).get(PreferenceSearchModel::class.java)
        binding.searchText.addTextChangedListener(this)
        viewmodel.filtered.observe(this) {
            adapter.submitList(it)
        }
        viewmodel.showTranslations.observe(this) {
            adapter.showTranslation = it
            binding.translateButton.isSelected = it
        }
        adapter = PreferenceItemAdapter(this)
        binding.list.adapter = adapter
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.translateButton.isSelected = false
        binding.translateButton.setOnClickListener {
            viewmodel.switchTranslations(binding.searchText.text.toString())
        }
        val locale:Locale = AppContextProvider.locale?.takeIf { it.isNotBlank() }?.let {LocaleUtils.getLocaleFromString(it)} ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) resources.configuration.locales[0] else resources.configuration.locale
        if (locale.language == "en") binding.translateButton.setGone()
    }

    override fun onResume() {
        binding.searchText.requestFocus()
        super.onResume()
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        s?.toString()?.lowercase(Locale.getDefault())?.let {
            viewmodel.filter(it)
            adapter.query = it
        }
    }

    override fun afterTextChanged(s: Editable?) {
    }

    override fun onClick(item: PreferenceItem) {
        setResult(RESULT_OK, Intent(ACTION_RESULT).apply { putExtra(EXTRA_PREF_END_POINT, item) })
        finish()
    }

    companion object {
        val ACTION_RESULT = "search.result".buildPkgString()
    }
}