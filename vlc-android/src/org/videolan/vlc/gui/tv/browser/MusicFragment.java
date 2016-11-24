/*
 * *************************************************************************
 *  MusicFragment.java
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

import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.text.TextUtils;

import org.videolan.medialibrary.media.Artist;
import org.videolan.medialibrary.media.Genre;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.tv.TvUtil;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;

import java.util.Arrays;
import java.util.List;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class MusicFragment extends MediaLibBrowserFragment implements OnItemViewClickedListener {

    public static final String MEDIA_SECTION = "section";
    public static final String AUDIO_CATEGORY = "category";
    public static final String AUDIO_ITEM = "item";

    public static final long FILTER_ARTIST = 3;
    public static final long FILTER_GENRE = 4;

    public static final int CATEGORY_NOW_PLAYING = 0;
    public static final long CATEGORY_ARTISTS = 1;
    public static final long CATEGORY_ALBUMS = 2;
    public static final long CATEGORY_GENRES = 3;
    public static final long CATEGORY_SONGS = 4;

    private volatile AsyncAudioUpdate mUpdater = null;
    MediaLibraryItem[] mDataList;

    long mCategory;
    long mType;
    MediaLibraryItem mCurrentItem;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null){
            mType = savedInstanceState.getLong(MEDIA_SECTION);
            mCategory = savedInstanceState.getLong(AUDIO_CATEGORY);
            mCurrentItem = savedInstanceState.getParcelable(AUDIO_ITEM);
        } else {
            mType = getActivity().getIntent().getLongExtra(MEDIA_SECTION, -1);
            mCategory = getActivity().getIntent().getLongExtra(AUDIO_CATEGORY, 0);
            mCurrentItem = getActivity().getIntent().getParcelableExtra(AUDIO_ITEM);
        }
    }

    public void onResume() {
        super.onResume();
        if (mUpdater == null) {
            mUpdater = new AsyncAudioUpdate();
            mUpdater.execute();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mUpdater != null)
            mUpdater.cancel(true);
    }

    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putLong(AUDIO_CATEGORY, mCategory);
        outState.putLong(MEDIA_SECTION, mType);
    }

    @Override
    public void updateList() {
        if (mUpdater == null) {
            mUpdater = new AsyncAudioUpdate();
            mUpdater.execute();
        }
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                              RowPresenter.ViewHolder rowViewHolder, Row row) {
        MediaLibraryItem mediaLibraryItem = (MediaLibraryItem) item;
        if (mediaLibraryItem.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
            int position = 0;
                for (int i = 0; i < mDataList.length; ++i) {
                    if (mediaLibraryItem.equals(mDataList[i])) {
                        position = i;
                        break;
                    }
                }
                TvUtil.playAudioList(mContext, (MediaWrapper[]) mDataList, position);
        } else
            TvUtil.openAudioCategory(mContext, mediaLibraryItem);
    }

    public class AsyncAudioUpdate extends AsyncTask<Void, MediaLibraryItem[], String> {

        AsyncAudioUpdate() {}

        @Override
        protected void onPreExecute() {
            setTitle(getString(R.string.app_name_full));
            mAdapter.clear();
            ((BrowserActivityInterface)getActivity()).showProgress(true);
        }

        @Override
        protected String doInBackground(Void... params) {
            String title;

            if (CATEGORY_ARTISTS == mCategory){
                mDataList = mMediaLibrary.getArtists();
                title = getString(R.string.artists);
            } else if (CATEGORY_ALBUMS == mCategory){
                title = mCurrentItem == null ?getString(R.string.albums) :  mCurrentItem.getTitle();
                if (mCurrentItem == null)
                    mDataList = mMediaLibrary.getAlbums();
                else if (mCurrentItem.getItemType() == MediaLibraryItem.TYPE_ARTIST)
                    mDataList = ((Artist)mCurrentItem).getAlbums(mMediaLibrary);
                else if (mCurrentItem.getItemType() == MediaLibraryItem.TYPE_GENRE)
                    mDataList = ((Genre)mCurrentItem).getAlbums(mMediaLibrary);
                else
                    return null;
            } else if (CATEGORY_GENRES == mCategory){
                title = getString(R.string.genres);
                mDataList = mMediaLibrary.getGenres();
            } else if (CATEGORY_SONGS == mCategory){
                title = getString(R.string.songs);
                mDataList = mMediaLibrary.getAudio();
            } else {
                title = getString(R.string.app_name_full);
            }
            publishProgress(mDataList);
            return title;
        }

        protected void onProgressUpdate(MediaLibraryItem[]... datalist){
            List<Object> list = Arrays.asList(((Object[]) datalist[0]));
            if (TextUtils.isEmpty(((MediaLibraryItem)list.get(0)).getTitle()) && list.size() > 1)
                list = list.subList(1, list.size());
            mAdapter.addAll(0, list);
        }

        @Override
        protected void onPostExecute(String title) {
            ((BrowserActivityInterface)getActivity()).showProgress(false);
            setTitle(title);
            setOnItemViewClickedListener(MusicFragment.this);
        }
    }
}