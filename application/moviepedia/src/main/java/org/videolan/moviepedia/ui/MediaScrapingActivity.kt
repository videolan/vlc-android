/*
 * ************************************************************************
 *  NextActivity.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.moviepedia.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.moviepedia.R
import org.videolan.moviepedia.databinding.MoviepediaActivityBinding
import org.videolan.moviepedia.models.resolver.ResolverMedia
import org.videolan.moviepedia.viewmodel.MediaScrapingModel
import org.videolan.resources.MOVIEPEDIA_MEDIA
import org.videolan.resources.util.parcelable
import org.videolan.vlc.gui.BaseActivity
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.applyTheme

open class MediaScrapingActivity : BaseActivity(), TextWatcher, TextView.OnEditorActionListener {

    private lateinit var mediaScrapingResultAdapter: MediaScrapingResultAdapter

    private lateinit var viewModel: MediaScrapingModel
    private lateinit var media: MediaWrapper
    private lateinit var binding: MoviepediaActivityBinding
    private val clickHandler = ClickHandler()
    override fun getSnackAnchorView(overAudioPlayer:Boolean): View? = findViewById(android.R.id.content)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        val intent = intent
        binding = DataBindingUtil.setContentView(this, R.layout.moviepedia_activity)
        binding.handler = clickHandler

        mediaScrapingResultAdapter = MediaScrapingResultAdapter(layoutInflater)
        mediaScrapingResultAdapter.clickHandler = clickHandler
        binding.nextResults.adapter = mediaScrapingResultAdapter
        binding.nextResults.layoutManager = GridLayoutManager(this, 2)

        intent.parcelable<MediaWrapper>(MOVIEPEDIA_MEDIA)?.let {
            media = it
        }
        if (!::media.isInitialized) {
            finish()
            return
        }

        binding.searchEditText.addTextChangedListener(this)
        binding.searchEditText.setOnEditorActionListener(this)
        viewModel = ViewModelProvider(this)[media.uri.path ?: "", MediaScrapingModel::class.java]
        viewModel.apiResult.observe(this) {
            mediaScrapingResultAdapter.setItems(it.getAllResults())
        }
        viewModel.search(media.uri)
        binding.searchEditText.setText(media.title)
    }

    private fun performSearh(query: String) {
        viewModel.search(query)
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {}

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            UiTools.setKeyboardVisibility(binding.root, false)
            performSearh(v.text.toString())
            return true
        }
        return false
    }

    inner class ClickHandler {

        fun onBack(@Suppress("UNUSED_PARAMETER") v: View) {
            finish()
        }

        fun onItemClick(@Suppress("UNUSED_PARAMETER") item: ResolverMedia) {
            //todo
            finish()
        }
    }
}
