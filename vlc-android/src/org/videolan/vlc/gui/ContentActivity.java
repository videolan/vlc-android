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
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.videolan.libvlc.RendererItem;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.RendererDelegate;
import org.videolan.vlc.gui.audio.AudioBrowserFragment;
import org.videolan.vlc.gui.audio.EqualizerFragment;
import org.videolan.vlc.gui.browser.ExtensionBrowser;
import org.videolan.vlc.gui.browser.SortableFragment;
import org.videolan.vlc.gui.dialogs.RenderersDialog;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.interfaces.Filterable;
import org.videolan.vlc.media.MediaUtils;

public class ContentActivity extends AudioPlayerContainerActivity implements SearchView.OnQueryTextListener, MenuItemCompat.OnActionExpandListener, RendererDelegate.RendererListener, RendererDelegate.RendererPlayer {
    public static final String TAG = "VLC/ContentActivity";

    protected Menu mMenu;
    private SearchView mSearchView;
    private boolean showRenderers = !RendererDelegate.INSTANCE.getRenderers().isEmpty();

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        final View view = AndroidUtil.isNougatOrLater ? getWindow().peekDecorView() : null;
        if (view != null) view.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return true;
                    case DragEvent.ACTION_DROP:
                        final ClipData clipData = event.getClipData();
                        if (clipData == null) return false;
                        final int itemsCount = clipData.getItemCount();
                        for (int i = 0; i < itemsCount; i++) {
                            final DragAndDropPermissions permissions = requestDragAndDropPermissions(event);
                            if (permissions != null)  {
                                final ClipData.Item item = clipData.getItemAt(i);
                                if (item.getUri() != null) MediaUtils.openUri(ContentActivity.this, item.getUri());
                                else if (item.getText() != null) {
                                    final Uri uri = Uri.parse(item.getText().toString());
                                    final MediaWrapper media = new MediaWrapper(uri);
                                    if (!"file".equals(uri.getScheme())) media.setType(MediaWrapper.TYPE_STREAM);
                                    MediaUtils.openMedia(ContentActivity.this, media);
                                }
                                return true;
                            }
                        }
                        return false;
                    default:
                        return false;
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mMenu = menu;
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder) instanceof AboutFragment)
            return true;
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
        menu.findItem(R.id.ml_menu_renderers).setVisible(showRenderers);
        menu.findItem(R.id.ml_menu_renderers).setIcon(RendererDelegate.INSTANCE.getSelectedRenderer() == null ? R.drawable.ic_am_renderer_normal_w : R.drawable.ic_am_renderer_on_w);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onStart() {
        super.onStart();
        RendererDelegate.INSTANCE.addListener(this);
        RendererDelegate.INSTANCE.addPlayerListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        RendererDelegate.INSTANCE.removeListener(this);
        RendererDelegate.INSTANCE.removePlayerListener(this);
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
                if (mService != null && !mService.hasRenderer()
                        && RendererDelegate.INSTANCE.getRenderers().size() == 1) {
                    final RendererItem renderer = RendererDelegate.INSTANCE.getRenderers().get(0);
                    RendererDelegate.INSTANCE.selectRenderer(renderer);
                    mService.setRenderer(renderer);
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
            final Filterable filterable = (Filterable) current;
            if (filterQueryString.length() < 3)
                filterable.restoreList();
            else
                filterable.getFilter().filter(filterQueryString);
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
        menu.findItem(R.id.ml_menu_renderers).setVisible(!hide && showRenderers);
        if (current instanceof SortableFragment) {
            menu.findItem(R.id.ml_menu_sortby).setVisible(!hide && ((SortableFragment)current).isSortEnabled());
        }
        if (current instanceof VideoGridFragment || current instanceof AudioBrowserFragment) {
            menu.findItem(R.id.ml_menu_last_playlist).setVisible(!hide);
        }
    }

    public void onClick(View v) {
        if (v.getId() == R.id.searchButton)
            openSearchActivity();
    }

    public void closeSearchView() {
        if (mMenu != null) MenuItemCompat.collapseActionView(mMenu.findItem(R.id.ml_menu_filter));
    }

    public void restoreCurrentList() {
        Fragment current = getCurrentFragment();
        if (current instanceof Filterable) {
            ((Filterable) current).restoreList();
        }
    }

    @Override
    public void onRenderersChanged(boolean empty) {
        showRenderers = !empty;
        supportInvalidateOptionsMenu();
    }

    @Override
    public void onRendererChanged(@Nullable RendererItem renderer) {
        supportInvalidateOptionsMenu();
    }
}
