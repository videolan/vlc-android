/*****************************************************************************
 * AboutLicenceFragment.java
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
 * Copyright © 2012 Edward Wang
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

import org.videolan.vlc.R;
import org.videolan.vlc.Util;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

public class AboutLicenceFragment extends Fragment {
    public final static String TAG = "VLC/AboutLicenceFragment";

    /* All subclasses of Fragment must include a public empty constructor. */
    public AboutLicenceFragment() { }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.about_licence, container, false);
        String revision = Util.readAsset("revision.txt", "Unknown revision");
        WebView t = (WebView)v.findViewById(R.id.webview);
        t.loadData(Util.readAsset("licence.htm", "").replace("!COMMITID!",revision), "text/html", "UTF8");
        return v;
    }
}