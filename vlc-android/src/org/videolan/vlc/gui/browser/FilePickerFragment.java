/*
 * *************************************************************************
 *  FilePickerFragment.java
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

package org.videolan.vlc.gui.browser;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.View;

import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.util.Strings;

public class FilePickerFragment extends FileBrowserFragment {

    @Override
    protected Fragment createFragment() {
        return new FilePickerFragment();
    }

    public FilePickerFragment(){}

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mAdapter = new FilePickerAdapter(this);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEmptyView.setText(R.string.no_subs_found);
    }

    @Override
    public void onStart() {
        super.onStart();
        mMediaBrowser.setIgnoreFileTypes("db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv");
        getActivity().setTitle(getTitle());
    }

    void pickFile(MediaWrapper mw){
        getActivity().setResult(Activity.RESULT_OK, new Intent(Intent.ACTION_PICK, mw.getUri()));
        getActivity().finish();
    }

    public void browseUp(){
        if (mRoot)
            getActivity().finish();
        else if (TextUtils.equals(Strings.removeFileProtocole(mMrl), ROOT)) {
            mMrl = null;
            mRoot = true;
            mAdapter.clear();
            browseRoot();
        } else {
            MediaWrapper mw = new MediaWrapper(Uri.parse(Strings.getParent(mMrl)));
            browse(mw, 0, false);
        }
    }

    @Override
    protected int getLayoutId(){
        return R.layout.file_picker_fragment;
    }
}
