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

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.videolan.libvlc.RendererItem;
import org.videolan.vlc.R;
import org.videolan.vlc.RendererDelegate;
import org.videolan.vlc.gui.audio.AudioBrowserFragment;
import org.videolan.vlc.gui.audio.EqualizerFragment;
import org.videolan.vlc.gui.browser.ExtensionBrowser;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.dialogs.RenderersDialog;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.interfaces.Filterable;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Settings;
import org.videolan.vlc.util.Util;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

@SuppressLint("Registered")
public class ContentActivity extends AudioPlayerContainerActivity implements SearchView.OnQueryTextListener, MenuItem.OnActionExpandListener {
    public static final String TAG = "VLC/ContentActivity";

    private SearchView mSearchView;
    private boolean showRenderers = !AndroidDevices.isChromeBook && !Util.isListEmpty(RendererDelegate.INSTANCE.getRenderers().getValue());

    @Override
    protected void initAudioPlayerContainerActivity() {
        super.initAudioPlayerContainerActivity();
        if (!AndroidDevices.isChromeBook && !AndroidDevices.isAndroidTv
                && Settings.INSTANCE.getInstance(this).getBoolean("enable_casting", true)) {
            RendererDelegate.INSTANCE.getSelectedRenderer().observe(this, new Observer<RendererItem>() {
                @Override
                public void onChanged(@Nullable RendererItem rendererItem) {
                    final MenuItem item = mToolbar.getMenu().findItem(R.id.ml_menu_renderers);
                    if (item == null) return;
                    item.setVisible(showRenderers);
                    item.setIcon(!RendererDelegate.INSTANCE.hasRenderer() ? R.drawable.ic_am_renderer_normal_w : R.drawable.ic_am_renderer_on_w);
                }
            });
            RendererDelegate.INSTANCE.getRenderers().observe(this, new Observer<List<RendererItem>>() {
                @Override
                public void onChanged(@Nullable List<RendererItem> rendererItems) {
                    showRenderers = !Util.isListEmpty(rendererItems);
                    final MenuItem item = mToolbar.getMenu().findItem(R.id.ml_menu_renderers);
                    if (item != null) item.setVisible(showRenderers);
                }
            });
        }
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        UiTools.setOnDragListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (AndroidDevices.isAndroidTv) return false;
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder) instanceof AboutFragment)
            return true;
        getMenuInflater().inflate(R.menu.activity_option, menu);
        if (getCurrentFragment() instanceof ExtensionBrowser){
            menu.findItem(R.id.ml_menu_last_playlist).setVisible(false);
            menu.findItem(R.id.ml_menu_sortby).setVisible(false);
        }
        if (getCurrentFragment() instanceof Filterable) {
            final Filterable filterable = (Filterable) getCurrentFragment();
            final MenuItem searchItem = menu.findItem(R.id.ml_menu_filter);
            mSearchView = (SearchView) searchItem.getActionView();
            mSearchView.setQueryHint(getString(R.string.search_list_hint));
            mSearchView.setOnQueryTextListener(this);
            searchItem.setOnActionExpandListener(this);
            final String query = filterable.getFilterQuery();
            if (!TextUtils.isEmpty(query)) {
                mActivityHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        searchItem.expandActionView();
                        mSearchView.clearFocus();
                        UiTools.setKeyboardVisibility(mSearchView, false);
                        mSearchView.setQuery(query, false);
                    }
                });
            }
        } else menu.findItem(R.id.ml_menu_filter).setVisible(false);
        menu.findItem(R.id.ml_menu_renderers).setVisible(showRenderers && Settings.INSTANCE.getInstance(this).getBoolean("enable_casting", true));
        menu.findItem(R.id.ml_menu_renderers).setIcon(!RendererDelegate.INSTANCE.hasRenderer() ? R.drawable.ic_am_renderer_normal_w : R.drawable.ic_am_renderer_on_w);
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
            case R.id.ml_menu_renderers:
                if (!RendererDelegate.INSTANCE.hasRenderer()
                        && RendererDelegate.INSTANCE.getRenderers().getValue().size() == 1) {
                    final RendererItem renderer = RendererDelegate.INSTANCE.getRenderers().getValue().get(0);
                    RendererDelegate.INSTANCE.selectRenderer(renderer);
                    final View v = findViewById(R.id.audio_player_container);
                    if (v != null) UiTools.snacker(v, getString(R.string.casting_connected_renderer, renderer.displayName));
                } else if (getSupportFragmentManager().findFragmentByTag("renderers") == null)
                    new RenderersDialog().show(getSupportFragmentManager(), "renderers");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onQueryTextChange(String filterQueryString) {
        final Fragment current = getCurrentFragment();
        if (current instanceof Filterable) {
            if (filterQueryString.length() < 3) ((Filterable) current).restoreList();
            else ((Filterable) current).filter(filterQueryString);
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
        final Fragment current = getCurrentFragment();
        if (current instanceof Filterable) {
            ((Filterable) current).setSearchVisibility(visible);
            makeRoomForSearch(current, visible);
        }
    }

    // Hide options menu items to make room for filter EditText
    protected void makeRoomForSearch(Fragment current, boolean hide) {
        final Menu menu = mToolbar.getMenu();
        final MenuItem renderersItem = menu.findItem(R.id.ml_menu_renderers);
        if (renderersItem != null) renderersItem.setVisible(!hide && showRenderers);
        if (current instanceof MediaBrowserFragment) {
        final MenuItem sortItem = menu.findItem(R.id.ml_menu_sortby);
            if (sortItem != null) sortItem.setVisible(!hide && ((MediaBrowserFragment) current).getViewModel().canSortByName());
        }
        if (current instanceof VideoGridFragment || current instanceof AudioBrowserFragment) {
            final MenuItem lastItem = menu.findItem(R.id.ml_menu_last_playlist);
            if (lastItem != null) lastItem.setVisible(!hide);
        }
    }

    public void onClick(View v) {
        if (v.getId() == R.id.searchButton) openSearchActivity();
    }

    public void closeSearchView() {
        if (mToolbar.getMenu() != null) {
            final MenuItem item = mToolbar.getMenu().findItem(R.id.ml_menu_filter);
            if (item != null) item.collapseActionView();
        }
    }

    public void restoreCurrentList() {
        final Fragment current = getCurrentFragment();
        if (current instanceof Filterable) {
            ((Filterable) current).restoreList();
        }
    }
}
