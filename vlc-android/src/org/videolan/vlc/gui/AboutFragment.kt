/*****************************************************************************
 * AboutFragment.java
 *
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

package org.videolan.vlc.gui

import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ScrollView
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext

import org.videolan.libvlc.util.AndroidUtil
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.audio.AudioPagerAdapter
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.Util

const val TAG = "VLC/AboutFragment"
const val MODE_TOTAL = 2 // Number of audio browser modes

class AboutFragment : Fragment() {

    private lateinit var viewPager: ViewPager
    private lateinit var tabLayout: TabLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = "VLC ${BuildConfig.VERSION_NAME}"
        //Fix android 7 Locale problem with webView
        //https://stackoverflow.com/questions/40398528/android-webview-locale-changes-abruptly-on-android-n
        if (AndroidUtil.isNougatOrLater)
            UiTools.setLocale(activity)

        val aboutMain = view.findViewById<ScrollView>(R.id.about_main)
        val webView = view.findViewById<WebView>(R.id.webview)
        val revision = getString(R.string.build_revision)

        val lists = arrayOf(aboutMain, webView)
        val titles = arrayOf(getString(R.string.about), getString(R.string.licence))
        viewPager = view.findViewById(R.id.pager)
        viewPager.offscreenPageLimit = MODE_TOTAL - 1
        viewPager.adapter = AudioPagerAdapter(lists, titles)

        tabLayout = view.findViewById(R.id.sliding_tabs)
        tabLayout.setupWithViewPager(viewPager)
        launch(UI.immediate) {
            val asset = withContext(CommonPool) {
                Util.readAsset("licence.htm", "").replace("!COMMITID!", revision)
            }
            UiTools.fillAboutView(view)
            webView.loadData(asset, "text/html", "UTF8")
        }
    }

}
