/*
 * *************************************************************************
 *  AudioPagerAdapter.java
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

package org.videolan.vlc.gui.audio;

import android.database.DataSetObserver;
import androidx.viewpager.widget.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;

public class AudioPagerAdapter extends PagerAdapter {

    private View[] mLists;
    private String[] mTitles;

    public AudioPagerAdapter(View[] lists, String[] titles){
        mLists = lists;
        mTitles = titles;
    }

    @Override
    public int getCount() {
        return mLists == null ? 0 : mLists.length;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        return mLists[position];
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (position < 0 || position >= mTitles.length)
            return "";
        else
            return mTitles[position];
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        if (observer != null)
            super.unregisterDataSetObserver(observer);
    }
}
