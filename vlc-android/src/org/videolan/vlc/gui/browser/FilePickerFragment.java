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
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.View;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.viewmodels.browser.BrowserModel;
import org.videolan.vlc.viewmodels.browser.BrowserModelKt;

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
        final Activity activity = getActivity();
        if (activity != null && activity.getIntent() != null) {
            final Uri uri = getActivity().getIntent().getData();
            if (uri == null || TextUtils.equals(uri.getScheme(), "http")) {
                activity.setIntent(null);
            }
        }
        super.onCreate(bundle);
        setAdapter(new FilePickerAdapter(this));
        setRootDirectory(defineIsRoot());
    }

    @Override
    protected void setupBrowser() {
        viewModel = ViewModelProviders.of(this, new BrowserModel.Factory(getMrl(), BrowserModelKt.TYPE_PICKER, false)).get(BrowserModel.class);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getBinding().empty.setText(R.string.no_subs_found);
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().setTitle(getTitle());
        mSwipeRefreshLayout.setEnabled(false);
    }

    public void onClick(View v, int position, MediaLibraryItem item) {
        final MediaWrapper media = (MediaWrapper) item;
        if (media.getType() == MediaWrapper.TYPE_DIR) browse(media, true);
        else pickFile(media);

    }
    void pickFile(MediaWrapper mw){
        Intent i = new Intent(Intent.ACTION_PICK);
        i.putExtra(EXTRA_MRL, mw.getLocation());
        requireActivity().setResult(Activity.RESULT_OK, i);
        requireActivity().finish();
    }

    public void browseUp() {
        if (isRootDirectory()) requireActivity().finish();
        else if (TextUtils.equals(Strings.removeFileProtocole(getMrl()), AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) {
            setMrl(null);
            setRootDirectory(true);
            viewModel.fetch();
        } else if (getMrl() != null) {
            final MediaWrapper mw = new MediaWrapper(Uri.parse(FileUtils.getParent(getMrl())));
            browse(mw, false);
        }
    }

    protected boolean defineIsRoot() {
        if (getMrl() == null) return true;
        if (getMrl().startsWith("file")) {
            final String path = Strings.removeFileProtocole(getMrl());
            for (String directory : rootDirectories) {
                if (path.startsWith(directory))
                    return false;
            }
            return true;
        } else return getMrl().length() < 7;
    }
}
