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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;

public class AboutActivity extends FragmentActivity implements OnTabChangeListener {
    public final static String TAG = "VLC/AboutActivity";

    private final static String CURRENT_TAB_TAG = "tabtag";
    private final static String CURRENT_TAB_ID = "tabid";

    private class DummyContentFactory implements TabHost.TabContentFactory {
        private final Context mContext;
        public DummyContentFactory(Context ctx) {
            mContext = ctx;
        }
        @Override
        public View createTabContent(String tag) {
            View dummy = new View(mContext);
            dummy.setMinimumHeight(0);
            dummy.setMinimumWidth(0);
            return dummy;
        }
    }

    private TabHost mTabHost;
    private String mCurrentTabTag;
    private AboutMainFragment mMainFragment;
    private AboutLicenceFragment mLicenceFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);
        displayVersionName();

        mMainFragment = new AboutMainFragment();
        mLicenceFragment = new AboutLicenceFragment();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(android.R.id.tabcontent, mMainFragment);
        ft.add(android.R.id.tabcontent, mLicenceFragment);
        ft.commit();
        DummyContentFactory dcf = new DummyContentFactory(this);

        mTabHost = (TabHost)findViewById(R.id.about_tabhost);
        mTabHost.setup();
        TabHost.TabSpec tab_main = mTabHost.newTabSpec("main");
        tab_main.setContent(dcf);
        tab_main.setIndicator(getNewTabIndicator(mTabHost.getContext(),
                getResources().getText(R.string.about).toString()));
        mTabHost.addTab(tab_main);
        TabHost.TabSpec tab_licence = mTabHost.newTabSpec("licence");
        tab_licence.setContent(dcf);
        tab_licence.setIndicator(getNewTabIndicator(mTabHost.getContext(),
                getResources().getText(R.string.licence).toString()));
        mTabHost.addTab(tab_licence);

        mTabHost.setOnTabChangedListener(this);
        this.onTabChanged("main");
        if (savedInstanceState != null) {
            mTabHost.setCurrentTab(savedInstanceState.getInt(CURRENT_TAB_ID));
            this.onTabChanged(savedInstanceState.getString(CURRENT_TAB_TAG));
        }
    }

    private View getNewTabIndicator(Context context, String title) {
        View v = LayoutInflater.from(context).inflate(R.layout.tab_layout, null);
        TextView tv = (TextView) v.findViewById(R.id.textView);
        tv.setText(title);
        return v;
    }


    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(CURRENT_TAB_TAG, mCurrentTabTag);
        outState.putInt(CURRENT_TAB_ID, mTabHost.getCurrentTab());
    }

    @Override
    public void onTabChanged(String newTag) {
        String oldTag = mCurrentTabTag; /* cosmetics */
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if(oldTag == "main" && newTag == "licence") {
            ft.setCustomAnimations(R.anim.anim_enter_right, R.anim.anim_leave_left);
        } else if(newTag == "main" && oldTag == "licence") {
            ft.setCustomAnimations(R.anim.anim_enter_left, R.anim.anim_leave_right);
        }
        ft.detach(getFragmentFromTag(oldTag));
        ft.attach(getFragmentFromTag(newTag));
        ft.commit();
        mCurrentTabTag = newTag;
    }

    private Fragment getFragmentFromTag(String tag) {
        if(tag == "main")
            return mMainFragment;
        else
            return mLicenceFragment;
    }

    private void displayVersionName() {
        String versionName = getVersion(this);
        TextView tv = (TextView) findViewById(R.id.textViewVersion);
        tv.setText(versionName);
    }

    public static String getVersion(Context ctx) {
        String versionName = "";
        PackageInfo packageInfo;
        try {
            packageInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            versionName = "v" + packageInfo.versionName;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionName;
    }

}
