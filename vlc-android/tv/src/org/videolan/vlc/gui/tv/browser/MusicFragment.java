/*
 * *************************************************************************
 *  MusicFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;

import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.tv.MainTvActivity;
import org.videolan.vlc.gui.audio.MediaComparators;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MusicFragment extends MediaLibBrowserFragment {

    public static final String MEDIA_SECTION = "section";
    public static final String AUDIO_CATEGORY = "category";
    public static final String AUDIO_FILTER = "filter";

    public static final long FILTER_ARTIST = 3;
    public static final long FILTER_GENRE = 4;

    public static final int CATEGORY_ARTISTS = 1;
    public static final int CATEGORY_ALBUMS = 2;
    public static final int CATEGORY_GENRES = 3;
    public static final int CATEGORY_SONGS = 4;

    protected Map<String, ListItem> mMediaItemMap;
    protected ArrayList<ListItem> mMediaItemList;

    String mFilter;
    int mCategory;
    long mType;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null){
            mType = savedInstanceState.getLong(MEDIA_SECTION);
            mCategory = savedInstanceState.getInt(AUDIO_CATEGORY);
            mFilter = savedInstanceState.getString(AUDIO_FILTER);
        } else {
            mType = getActivity().getIntent().getLongExtra(MEDIA_SECTION, -1);
            mCategory = getActivity().getIntent().getIntExtra(AUDIO_CATEGORY, 0);
            mFilter = getActivity().getIntent().getStringExtra(AUDIO_FILTER);
        }
    }

    public void onResume() {
        super.onResume();
        if (mAdapter.size() == 0) {
            new AsyncAudioUpdate().execute();
        }
    }

    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putInt(AUDIO_CATEGORY, mCategory);
        outState.putLong(MEDIA_SECTION, mType);
    }

    public class AsyncAudioUpdate extends AsyncTask<Void, ListItem, String> {

        public AsyncAudioUpdate() {}

        @Override
        protected void onPreExecute() {
            setTitle(getString(R.string.app_name_full));
            mAdapter.clear();
            mMediaItemMap = new HashMap<String, ListItem>();
            mMediaItemList = new ArrayList<ListItem>();
        }

        @Override
        protected String doInBackground(Void... params) {
            String title;
            ListItem item;

            List<MediaWrapper> audioList = MediaLibrary.getInstance().getAudioItems();
            if (CATEGORY_ARTISTS == mCategory){
                Collections.sort(audioList, MediaComparators.byArtist);
                title = getString(R.string.artists);
                for (MediaWrapper MediaWrapper : audioList){
                    item = add(MediaWrapper.getArtist(), null, MediaWrapper);
                    if (item != null)
                        publishProgress(item);
                }
            } else if (CATEGORY_ALBUMS == mCategory){
                title = getString(R.string.albums);
                Collections.sort(audioList, MediaComparators.byAlbum);
                for (MediaWrapper MediaWrapper : audioList){
                    if (mFilter == null
                            || (mType == FILTER_ARTIST && mFilter.equals(MediaWrapper.getArtist()))
                            || (mType == FILTER_GENRE && mFilter.equals(MediaWrapper.getGenre()))) {
                        item = add(MediaWrapper.getAlbum(), MediaWrapper.getArtist(), MediaWrapper);
                        if (item != null)
                            publishProgress(item);
                    }
                }
                //Customize title for artist/genre browsing
                if (mType == FILTER_ARTIST){
                    title = title + " " + mMediaItemList.get(0).mediaList.get(0).getArtist();
                } else if (mType == FILTER_GENRE){
                    title = title + " " + mMediaItemList.get(0).mediaList.get(0).getGenre();
                }
            } else if (CATEGORY_GENRES == mCategory){
                title = getString(R.string.genres);
                Collections.sort(audioList, MediaComparators.byGenre);
                for (MediaWrapper MediaWrapper : audioList){
                    item = add(MediaWrapper.getGenre(), null, MediaWrapper);
                    if (item != null)
                        publishProgress(item);
                }
            } else if (CATEGORY_SONGS == mCategory){
                title = getString(R.string.songs);
                Collections.sort(audioList, MediaComparators.byName);
                ListItem mediaItem;
                for (MediaWrapper MediaWrapper : audioList){
                    mediaItem = new ListItem(MediaWrapper.getTitle(), MediaWrapper.getArtist(), MediaWrapper);
                    mMediaItemMap.put(title, mediaItem);
                    mMediaItemList.add(mediaItem);
                    publishProgress(mediaItem);
                }
            } else {
                title = getString(R.string.app_name_full);
            }
            return title;
        }

        protected void onProgressUpdate(ListItem... items){
            mAdapter.add(items[0]);
        }

        @Override
        protected void onPostExecute(String title) {
            setTitle(title);
            setOnItemViewClickedListener(new OnItemViewClickedListener() {
                @Override
                public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                          RowPresenter.ViewHolder rowViewHolder, Row row) {
                    ListItem listItem = (ListItem) item;
                    Intent intent;
                    if (CATEGORY_ARTISTS == mCategory) {
                        intent = new Intent(mContext, VerticalGridActivity.class);
                        intent.putExtra(MainTvActivity.BROWSER_TYPE, MainTvActivity.HEADER_CATEGORIES);
                        intent.putExtra(AUDIO_CATEGORY, CATEGORY_ALBUMS);
                        intent.putExtra(MEDIA_SECTION, FILTER_ARTIST);
                        intent.putExtra(AUDIO_FILTER, listItem.mediaList.get(0).getArtist());
                    } else if (CATEGORY_GENRES == mCategory) {
                        intent = new Intent(mContext, VerticalGridActivity.class);
                        intent.putExtra(MainTvActivity.BROWSER_TYPE, MainTvActivity.HEADER_CATEGORIES);
                        intent.putExtra(AUDIO_CATEGORY, CATEGORY_ALBUMS);
                        intent.putExtra(MEDIA_SECTION, FILTER_GENRE);
                        intent.putExtra(AUDIO_FILTER, listItem.mediaList.get(0).getGenre());
                    } else {
                        ArrayList<String> locations = new ArrayList<String>();
                        if (CATEGORY_ALBUMS == mCategory)
                            Collections.sort(listItem.mediaList, MediaComparators.byTrackNumber);
                        for (MediaWrapper MediaWrapper : listItem.mediaList) {
                            locations.add(MediaWrapper.getLocation());
                        }
                        intent = new Intent(mContext, AudioPlayerActivity.class);
                        intent.putExtra("locations", locations);
                    }
                    startActivity(intent);
                }
            });
        }
    }

    public static class ListItem {
        public String mTitle;
        public String mSubTitle;
        public ArrayList<MediaWrapper> mediaList;

        public ListItem(String title, String subTitle, MediaWrapper MediaWrapper) {
            mediaList = new ArrayList<MediaWrapper>();
            if (MediaWrapper != null)
                mediaList.add(MediaWrapper);
            mTitle = title;
            mSubTitle = subTitle;
        }
    }

    public ListItem add(String title, String subTitle, MediaWrapper MediaWrapper) {
        if(title == null) return null;
        title = title.trim();
        if(subTitle != null) subTitle = subTitle.trim();
        if (mMediaItemMap.containsKey(title))
            mMediaItemMap.get(title).mediaList.add(MediaWrapper);
        else {
            ListItem item = new ListItem(title, subTitle, MediaWrapper);
            mMediaItemMap.put(title, item);
            mMediaItemList.add(item);
            return item;
        }
        return null;
    }
}