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

package org.videolan.vlc.gui;


import android.app.SearchManager;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.audio.EqualizerFragment;
import org.videolan.vlc.gui.browser.ExtensionBrowser;
import org.videolan.vlc.interfaces.Filterable;

public class ContentActivity extends AudioPlayerContainerActivity implements SearchView.OnQueryTextListener, MenuItemCompat.OnActionExpandListener {
    public static final String TAG = "VLC/ContentActivity";

    protected Menu mMenu;
    private SearchView mSearchView;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mMenu = menu;
        getMenuInflater().inflate(R.menu.activity_option, menu);
        if (getCurrentFragment() instanceof ExtensionBrowser){
            menu.findItem(R.id.ml_menu_last_playlist).setVisible(false);
            menu.findItem(R.id.ml_menu_sortby).setVisible(false);
        }
        if (getCurrentFragment() instanceof Filterable) {
            MenuItem searchItem = menu.findItem(R.id.ml_menu_filter);
            mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
            mSearchView.setQueryHint(getString(R.string.search_list_hint));
            mSearchView.setOnQueryTextListener(this);
            MenuItemCompat.setOnActionExpandListener(searchItem, this);
        }
        else
            menu.findItem(R.id.ml_menu_filter).setVisible(false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Handle item selection
        switch (item.getItemId()) {
            case R.id.ml_menu_equalizer:
                new EqualizerFragment().show(getSupportFragmentManager(), "equalizer");
                return true;
            case R.id.ml_menu_search:
                startActivity(new Intent(Intent.ACTION_SEARCH, null, this, SearchActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onQueryTextChange(String filterQueryString) {
        if (filterQueryString.length() < 3)
            return false;
        Fragment current = getCurrentFragment();
        if (current instanceof Filterable) {
            ((Filterable) current).getFilter().filter(filterQueryString);
            return true;
        }
        return false;
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        setSearchVisibility(true);
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        setSearchVisibility(false);
        restoreCurrentList();
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    public void openSearchActivity() {
        startActivity(new Intent(Intent.ACTION_SEARCH, null, this, SearchActivity.class)
                .putExtra(SearchManager.QUERY, mSearchView.getQuery().toString()));
    }

    private void setSearchVisibility(boolean visible) {
        Fragment current = getCurrentFragment();
        if (current instanceof Filterable)
            ((Filterable) current).setSearchVisibility(visible);
    }

    public void onClick(View v) {
        if (v.getId() == R.id.searchButton)
            openSearchActivity();
    }

    public void closeSearchView() {
        if (mMenu != null)
            MenuItemCompat.collapseActionView(mMenu.findItem(R.id.ml_menu_filter));
    }

    public void restoreCurrentList() {
        Fragment current = getCurrentFragment();
        if (current instanceof Filterable) {
            ((Filterable) current).restoreList();
        }
    }
}
