/*****************************************************************************
 * MediaItemDetailsFragment.java
 *****************************************************************************
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
 *****************************************************************************/
package org.videolan.vlc.gui.tv;

import java.util.ArrayList;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.widget.Toast;

public class MediaItemDetailsFragment extends DetailsFragment implements PlaybackService.Client.Callback {
    private static final String TAG = "MediaItemDetailsFragment";
    private static final int ID_PLAY = 1;
    private static final int ID_LISTEN = 2;
    private static final int ID_FAVORITE_ADD = 3;
    private static final int ID_FAVORITE_DELETE = 4;
    private static final int ID_BROWSE = 5;
    private ArrayObjectAdapter mRowsAdapter;
    private MediaItemDetails mMedia;
    private MediaWrapper mMediaWrapper;
    private MediaDatabase mDb;
    private PlaybackService mService;

    @Override
    public void onStop() {
        super.onStop();
        PlaybackServiceFragment.unregisterPlaybackService(this, this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildDetails();
    }

    public void onPause(){
        super.onPause();
        if (mService != null && mService.isPlaying()) {
            mService.stop();
        }
    }

    private void buildDetails() {
        Bundle extras = getActivity().getIntent().getExtras();
        mMedia = extras.getParcelable("item");
        boolean hasMedia = extras.containsKey("media");
        ClassPresenterSelector selector = new ClassPresenterSelector();
        final MediaWrapper media = hasMedia ? (MediaWrapper) extras.getParcelable("media") : new MediaWrapper(AndroidUtil.LocationToUri(mMedia.getLocation()));
        if (!hasMedia){
            media.setDisplayTitle(mMedia.getTitle());
        }
        mMediaWrapper = media;
        // Attach your media item details presenter to the row presenter:
        DetailsOverviewRowPresenter rowPresenter =
                new DetailsOverviewRowPresenter(new DetailsDescriptionPresenter());

        Resources res = getActivity().getResources();
        final DetailsOverviewRow detailsOverview = new DetailsOverviewRow(mMedia);
        final Action actionAdd = new Action(ID_FAVORITE_ADD, getString(R.string.favorites_add));
        final Action actionDelete = new Action(ID_FAVORITE_DELETE, getString(R.string.favorites_remove));

        rowPresenter.setBackgroundColor(getResources().getColor(R.color.orange500));
        rowPresenter.setOnActionClickedListener(new OnActionClickedListener() {

            @Override
            public void onActionClicked(Action action) {
                switch ((int)action.getId()){
                    case ID_LISTEN:
                        PlaybackServiceFragment.registerPlaybackService(MediaItemDetailsFragment.this, MediaItemDetailsFragment.this);
                        break;
                    case ID_PLAY:
                        ArrayList<MediaWrapper> tracks = new ArrayList<MediaWrapper>();
                        tracks.add(media);
                        Intent intent = new Intent(getActivity(), AudioPlayerActivity.class);
                        intent.putExtra(AudioPlayerActivity.MEDIA_LIST, tracks);
                        startActivity(intent);
                        break;
                    case ID_FAVORITE_ADD:
                        mDb.addNetworkFavItem(Uri.parse(mMedia.getLocation()), mMedia.getTitle());
                        detailsOverview.removeAction(actionAdd);
                        detailsOverview.addAction(actionDelete);
                        mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());
                        Toast.makeText(getActivity(), R.string.favorite_added, Toast.LENGTH_SHORT).show();
                        break;
                    case ID_FAVORITE_DELETE:
                        mDb.deleteNetworkFav(Uri.parse(mMedia.getLocation()));
                        detailsOverview.removeAction(actionDelete);
                        detailsOverview.addAction(actionAdd);
                        mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());
                        Toast.makeText(getActivity(), R.string.favorite_removed, Toast.LENGTH_SHORT).show();
                        break;
                    case ID_BROWSE:
                        TvUtil.openMedia(getActivity(), media, null);
                }
            }
        });
        selector.addClassPresenter(DetailsOverviewRow.class, rowPresenter);
        selector.addClassPresenter(ListRow.class,
                new ListRowPresenter());
        mRowsAdapter = new ArrayObjectAdapter(selector);

        if (media.getType() == MediaWrapper.TYPE_DIR) {
            mDb = MediaDatabase.getInstance();
            detailsOverview.setImageDrawable(getResources().getDrawable(R.drawable.ic_menu_network_big));
            detailsOverview.setImageScaleUpAllowed(true);
            detailsOverview.addAction(new Action(ID_BROWSE, "Browse folder"));
            if (mDb.networkFavExists(Uri.parse(mMedia.getLocation())))
                detailsOverview.addAction(actionDelete);
            else
                detailsOverview.addAction(actionAdd);

        } else {
            // Add images and action buttons to the details view
            Bitmap cover = AudioUtil.getCover(getActivity(), MediaLibrary.getInstance().getMediaItem(mMedia.getLocation()), 480);
            if (cover == null)
                detailsOverview.setImageDrawable(res.getDrawable(R.drawable.cone));
            else
                detailsOverview.setImageBitmap(getActivity(), cover);

            detailsOverview.addAction(new Action(ID_PLAY, "Play"));
            detailsOverview.addAction(new Action(ID_LISTEN, "Listen"));
        }
        mRowsAdapter.add(detailsOverview);

        setAdapter(mRowsAdapter);
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
        mService.load(mMediaWrapper);
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }
}
