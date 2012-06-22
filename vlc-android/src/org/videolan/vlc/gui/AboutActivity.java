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

import org.videolan.vlc.R;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.widget.TextView;

public class AboutActivity extends Activity {

    public final static String TAG = "VLC/AboutActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.about);
        displayVersionName();
        super.onCreate(savedInstanceState);
    }
    
    private void displayVersionName() {
        String versionName = "";
        PackageInfo packageInfo;
        try {
            packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = "v " + packageInfo.versionName;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        TextView tv = (TextView) findViewById(R.id.textViewVersion);
        tv.setText(versionName);
    }

}
