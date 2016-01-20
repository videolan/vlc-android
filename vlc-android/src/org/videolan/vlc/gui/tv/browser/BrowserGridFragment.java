/*
 * *************************************************************************
 *  BrowserGridFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.tv.browser;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.gui.browser.BaseBrowserFragment;
import org.videolan.vlc.gui.tv.DetailsActivity;
import org.videolan.vlc.gui.tv.MediaItemDetails;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;

import java.util.ArrayList;
import java.util.Collections;

public class BrowserGridFragment extends GridFragment implements MediaBrowser.EventListener, OnItemViewSelectedListener {

    private MediaBrowser mMediaBrowser;
    private Uri mUri;
    ArrayList<MediaWrapper> mMediaList = null;
    private MediaWrapper mItemSelected;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null){
            mUri = Uri.parse(savedInstanceState.getString(BaseBrowserFragment.KEY_MRL));
        } else {
            Intent intent = getActivity().getIntent();
            if (intent != null && intent.hasExtra(BaseBrowserFragment.KEY_MRL))
                mUri = Uri.parse(intent.getStringExtra(BaseBrowserFragment.KEY_MRL));
        }
        setOnItemViewSelectedListener(this);
    }

    public void onResume() {
        super.onResume();
        if (mAdapter.size() == 0) {
            mMediaBrowser = new MediaBrowser(VLCInstance.get(), this);
            if (mMediaBrowser != null) {
                mMediaList = new ArrayList<MediaWrapper>();
                if (mUri != null)
                    mMediaBrowser.browse(mUri);
                else
                    mMediaBrowser.discoverNetworkShares(Util.NETWORK_DISCOVER_LIST);
                ((BrowserActivityInterface)getActivity()).showProgress(true);
            }
        }
        setOnItemViewClickedListener(mClickListener);
    }

    public void onPause(){
        super.onPause();
        if (mMediaBrowser != null) {
            mMediaBrowser.release();
            mMediaBrowser = null;
        }
        ((BrowserActivityInterface)getActivity()).updateEmptyView(false);
    }
    @Override
    public void onMediaAdded(int index, Media media) {
        MediaWrapper mw = new MediaWrapper(media);
        int type = mw.getType();
        if (type == MediaWrapper.TYPE_AUDIO || type == MediaWrapper.TYPE_VIDEO || type == MediaWrapper.TYPE_DIR)
            mMediaList.add(mw);

        if (mUri == null) { // we are at root level
            mAdapter.clear();
            mAdapter.addAll(0, mMediaList); //FIXME adding 1 by 1 doesn't work
        }
        ((BrowserActivityInterface)getActivity()).showProgress(false);
    }

    @Override
    public void onMediaRemoved(int index, Media media) {}

    @Override
    public void onBrowseEnd() {
        ((BrowserActivityInterface)getActivity()).showProgress(false);
        ((BrowserActivityInterface)getActivity()).updateEmptyView(mMediaList.isEmpty());
        sortList();
    }

    public void sortList(){
        ArrayList<MediaWrapper> files = new ArrayList<MediaWrapper>(), dirs = new ArrayList<MediaWrapper>();
        for (Object item : mMediaList){
            if (item instanceof MediaWrapper) {
                MediaWrapper media = (MediaWrapper) item;
                if (media.getType() == MediaWrapper.TYPE_DIR)
                    dirs.add(media);
                else
                    files.add(media);
            }
        }
        Collections.sort(dirs, MediaComparators.byName);
        Collections.sort(files, MediaComparators.byName);
        mMediaList.clear();
        mMediaList.addAll(dirs);
        mMediaList.addAll(files);
        mAdapter.clear();
        mAdapter.addAll(0, mMediaList);
        mAdapter.notifyArrayItemRangeChanged(0, mMediaList.size());
    }

    public void showDetails() {
        if (mItemSelected.getType() == MediaWrapper.TYPE_DIR) {
            Intent intent = new Intent(getActivity(),
                    DetailsActivity.class);
            // pass the item information
            intent.putExtra("media", mItemSelected);
            intent.putExtra("item", (Parcelable) new MediaItemDetails(mItemSelected.getTitle(), mItemSelected.getArtist(), mItemSelected.getAlbum(), mItemSelected.getLocation()));
            startActivity(intent);
        }
    }

    @Override
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        mItemSelected = (MediaWrapper)item;
    }
}
