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

import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;

public class FilePickerFragment extends FileBrowserFragment {

    public static final String EXTRA_MRL = "sub_mrl";

    private static String[] rootDirectories = AndroidDevices.getMediaDirectories();

    @Override
    protected Fragment createFragment() {
        return new FilePickerFragment();
    }

    public boolean isSortEnabled() {
        return false;
    }

    @Override
    public void onCreate(Bundle bundle) {
        if (getActivity().getIntent() != null) {
            Uri uri = getActivity().getIntent().getData();
            if (uri == null || TextUtils.equals(uri.getScheme(), "http")) {
                getActivity().setIntent(null);
            }
        }
        super.onCreate(bundle);
        mAdapter = new FilePickerAdapter(this);
        mRoot = defineIsRoot();
        runOnBrowserThread(new Runnable() {
            @Override
            public void run() {
                initMediaBrowser(FilePickerFragment.this);
                mMediaBrowser.setIgnoreFileTypes("db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv");
            }
        });
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEmptyView.setText(R.string.no_subs_found);
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().setTitle(getTitle());
        mSwipeRefreshLayout.setEnabled(false);
    }

    public void onClick(View v, int position, MediaLibraryItem item) {
        final MediaWrapper media = (MediaWrapper) item;
        if (media.getType() == MediaWrapper.TYPE_DIR)
            browse(media, position, true);
        else
            pickFile(media);

    }
    void pickFile(MediaWrapper mw){
        Intent i = new Intent(Intent.ACTION_PICK);
        i.putExtra(EXTRA_MRL, mw.getLocation());
        getActivity().setResult(Activity.RESULT_OK, i);
        getActivity().finish();
    }

    public void browseUp() {
        if (mRoot)
            getActivity().finish();
        else if (TextUtils.equals(Strings.removeFileProtocole(mMrl), AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) {
            mMrl = null;
            mRoot = true;
            mAdapter.clear();
            browseRoot();
        } else if (mMrl != null) {
            MediaWrapper mw = new MediaWrapper(Uri.parse(FileUtils.getParent(mMrl)));
            browse(mw, 0, false);
        }
    }

    protected boolean defineIsRoot() {
        if (mMrl == null)
            return true;
        if (mMrl.startsWith("file")) {
            String path = Strings.removeFileProtocole(mMrl);
            for (String directory : rootDirectories) {
                if (path.startsWith(directory))
                    return false;
            }
            return true;
        } else
            return mMrl.length() < 7;
    }

    @Override
    protected int getLayoutId(){
        return R.layout.file_picker_fragment;
    }

    @Override
    protected int getBrowserFlags() {
        return MediaBrowser.Flag.Interact|MediaBrowser.Flag.NoSlavesAutodetect;
    }
}
