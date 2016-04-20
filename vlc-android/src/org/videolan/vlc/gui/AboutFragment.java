/*****************************************************************************
 * AboutActivity.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

package org.videolan.vlc.gui;

import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.audio.AudioPagerAdapter;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.util.Util;

import java.util.Arrays;
import java.util.List;

public class AboutFragment extends Fragment {
    public final static String TAG = "VLC/AboutActivity";

    public final static int MODE_ABOUT = 0;
    public final static int MODE_LICENCE = 1;
    public final static int MODE_TOTAL = 2; // Number of audio browser modes

    private ViewPager mViewPager;
    private TabLayout mTabLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (getActivity() instanceof AppCompatActivity)
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("VLC " + BuildConfig.VERSION_NAME);
        View v = inflater.inflate(R.layout.about, container, false);

        View aboutMain = v.findViewById(R.id.about_main);
        WebView webView = (WebView)v.findViewById(R.id.webview);
        String revision = getString(R.string.build_revision);
        webView.loadData(Util.readAsset("licence.htm", "").replace("!COMMITID!",revision), "text/html", "UTF8");


        UiTools.fillAboutView(v);

        List<View> lists = Arrays.asList(aboutMain, webView);
        String[] titles = new String[] {getString(R.string.about), getString(R.string.licence)};
        mViewPager = (ViewPager) v.findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(MODE_TOTAL-1);
        mViewPager.setAdapter(new AudioPagerAdapter(lists, titles));

        mTabLayout = (TabLayout) v.findViewById(R.id.sliding_tabs);
        mTabLayout.setupWithViewPager(mViewPager);

        return v;
    }
}
