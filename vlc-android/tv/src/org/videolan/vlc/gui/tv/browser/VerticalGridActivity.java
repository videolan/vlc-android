/*****************************************************************************
 * VerticalGridActivity.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
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
package org.videolan.vlc.gui.tv.browser;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.network.NetworkFragment;
import org.videolan.vlc.gui.tv.MainTvActivity;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.Bundle;

public class VerticalGridActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_vertical_grid);
        getWindow().setBackgroundDrawableResource(R.drawable.background);
        long type = getIntent().getLongExtra(MainTvActivity.BROWSER_TYPE, -1);
        GridFragment fragment;
        if (type == MainTvActivity.HEADER_VIDEO)
                fragment = new VideoGridFragment();
        else if (type == MainTvActivity.HEADER_CATEGORIES)
                fragment = new MusicFragment();
        else if (type == MainTvActivity.HEADER_NETWORK)
                fragment = new BrowserGridFragment();
        else {
            finish();
            return;
        }
        getFragmentManager().beginTransaction()
                .add(R.id.tv_fragment_placeholder, fragment)
                .commit();
    }
}
