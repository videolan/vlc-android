/*
 * *************************************************************************
 *  ContentActivity.java
 * **************************************************************************
 *  Copyright © 2017 VLC authors and VideoLAN
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

package org.videolan.vlc.gui

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import org.videolan.resources.AndroidDevices
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.RendererDelegate
import org.videolan.vlc.gui.browser.MLStorageBrowserFragment
import org.videolan.vlc.gui.dialogs.RenderersDialog
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.interfaces.Filterable

open class ContentActivity : AudioPlayerContainerActivity(), SearchView.OnQueryTextListener, MenuItem.OnActionExpandListener {

    private lateinit var searchView: SearchView
    private lateinit var searchItem: MenuItem
    private var showRenderers = !AndroidDevices.isChromeBook && !RendererDelegate.renderers.value.isNullOrEmpty()
    private val searchHiddenMenuItem = ArrayList<MenuItem>()
    open fun hideRenderers() = false


    override fun initAudioPlayerContainerActivity() {
        super.initAudioPlayerContainerActivity()
        if (!AndroidDevices.isChromeBook && !AndroidDevices.isAndroidTv
                && Settings.getInstance(this).getBoolean("enable_casting", true)) {
            PlaybackService.renderer.observe(this) {
                val item = toolbar.menu.findItem(R.id.ml_menu_renderers) ?: return@observe
                item.isVisible = !hideRenderers() && showRenderers
                item.setIcon(if (!PlaybackService.hasRenderer()) R.drawable.ic_renderer else R.drawable.ic_renderer_on)
            }
            RendererDelegate.renderers.observe(this) { rendererItems ->
                showRenderers = !rendererItems.isNullOrEmpty()
                val item = toolbar.menu.findItem(R.id.ml_menu_renderers)
                if (item != null) item.isVisible = !hideRenderers() && showRenderers
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        UiTools.setOnDragListener(this)
    }

    override fun onPause() {
        super.onPause()
        if (::searchItem.isInitialized) searchItem.collapseActionView()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val current = currentFragment
        super.onCreateOptionsMenu(menu)
        if (current is AboutFragment) return true
        menuInflater.inflate(R.menu.activity_option, menu)
        if (current is Filterable) {
            val filterable = current as Filterable?
            searchItem = menu.findItem(R.id.ml_menu_filter)
            searchView = searchItem.actionView as SearchView
            searchView.queryHint = getString(R.string.search_in_list_hint)
            searchView.setOnQueryTextListener(this)
            val query = filterable?.getFilterQuery()
            if (!query.isNullOrEmpty()) {
                searchView.post {
                    searchItem.expandActionView()
                    searchView.clearFocus()
                    UiTools.setKeyboardVisibility(searchView, false)
                    searchView.setQuery(query, false)
                }
            }
            searchItem.setOnActionExpandListener(this)
        } else
            menu.findItem(R.id.ml_menu_filter).isVisible = false
        menu.findItem(R.id.ml_menu_renderers).isVisible = current !is MLStorageBrowserFragment && !hideRenderers() && showRenderers && Settings.getInstance(this).getBoolean("enable_casting", true)
        menu.findItem(R.id.ml_menu_renderers).setIcon(if (!PlaybackService.hasRenderer()) R.drawable.ic_renderer else R.drawable.ic_renderer_on)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.ml_menu_search -> {
                startActivity(Intent(Intent.ACTION_SEARCH, null, this, SearchActivity::class.java))
                return true
            }
            R.id.ml_menu_renderers -> {
                if (!PlaybackService.hasRenderer() && RendererDelegate.renderers.size == 1) {
                    val renderer = RendererDelegate.renderers.value[0]
                    PlaybackService.renderer.value = renderer
                    UiTools.snacker(this, getString(R.string.casting_connected_renderer, renderer.displayName))
                } else if (supportFragmentManager.findFragmentByTag("renderers") == null)
                    RenderersDialog().show(supportFragmentManager, "renderers")
                return true
            }
            R.id.ml_menu_filter -> {
                if (!item.isActionViewExpanded) setSearchVisibility(true)
                return super.onOptionsItemSelected(item)
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onQueryTextChange(filterQueryString: String): Boolean {
        val current = currentFragment
        if (current is Filterable) {
            if (filterQueryString.isEmpty())
                (current as Filterable).restoreList()
            else
                (current as Filterable).filter(filterQueryString)
            return true
        }
        return false
    }

    override fun onMenuItemActionExpand(item: MenuItem): Boolean {
        setSearchVisibility(true)
        return true
    }

    override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
        setSearchVisibility(false)
        restoreCurrentList()
        return true
    }

    override fun onQueryTextSubmit(query: String) = false

    private fun openSearchActivity() {
        setSearchVisibility(false)
        startActivity(Intent(Intent.ACTION_SEARCH, null, this, SearchActivity::class.java)
                .putExtra(SearchManager.QUERY, searchView.query.toString()))
    }

    private fun setSearchVisibility(visible: Boolean) {
        val current = currentFragment
        if (current is Filterable) {
            (current as Filterable).setSearchVisibility(visible)
            makeRoomForSearch(visible)
        }
    }

    // Hide options menu items to make room for filter EditText
    private fun makeRoomForSearch(hide: Boolean) {
        val menu = toolbar.menu
        if (!hide) {
            searchHiddenMenuItem.forEach {
                it.isVisible = true
            }
            searchHiddenMenuItem.clear()
            invalidateOptionsMenu()
        } else {
            for (i in 0 until menu.size()) {
                val menuItem = menu.getItem(i)
                if (menuItem.isVisible) {
                    menuItem.isVisible = false
                    searchHiddenMenuItem.add(menuItem)
                }
            }
        }
    }

    fun onClick(v: View) {
        if (v.id == R.id.searchButton) openSearchActivity()
    }

    fun closeSearchView() {
        toolbar.menu?.findItem(R.id.ml_menu_filter)?.collapseActionView()
    }

    fun openSearchView() {
        toolbar.menu?.findItem(R.id.ml_menu_filter)?.expandActionView()
    }

    fun isSearchViewVisible() =
        toolbar.menu?.findItem(R.id.ml_menu_filter)?.isActionViewExpanded ?: false

    fun getCurrentQuery() = searchView.query.toString()

    fun setCurrentQuery(query:String) {
        searchView.setQuery(query, false)
    }


    private fun restoreCurrentList() {
        (currentFragment as? Filterable)?.restoreList()
    }

    companion object {
        const val TAG = "VLC/ContentActivity"
    }
}
