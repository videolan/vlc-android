/*****************************************************************************
 * SearchActivity.java
 *
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
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
 */
package org.videolan.vlc.gui.tv

import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.videolan.vlc.R

@ExperimentalCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class SearchActivity : FragmentActivity() {

    private lateinit var fragment: SearchFragment
    private var emptyView: TextView? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv_search)
        fragment = supportFragmentManager.findFragmentById(R.id.search_fragment) as SearchFragment
        emptyView = findViewById(R.id.empty)
    }

    fun updateEmptyView(empty: Boolean) {
        emptyView!!.visibility = if (empty) View.VISIBLE else View.GONE
    }

    override fun onSearchRequested(): Boolean {
        fragment.startRecognition()
        return true
    }

    companion object {
        private val TAG = "VLC/SearchActivity"
    }
}
