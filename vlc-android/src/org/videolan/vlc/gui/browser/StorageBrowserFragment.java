/*
 * *************************************************************************
 *  StorageBrowserFragment.java
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

import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.DialogInterface;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.interfaces.EntryPointsEventsCb;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Storage;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.BrowserItemBinding;
import org.videolan.vlc.gui.AudioPlayerContainerActivity;
import org.videolan.vlc.gui.dialogs.ContextSheetKt;
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.CustomDirectories;
import org.videolan.vlc.viewmodels.browser.BrowserModel;
import org.videolan.vlc.viewmodels.browser.BrowserModelKt;

import java.io.File;
import java.io.IOException;

public class StorageBrowserFragment extends FileBrowserFragment implements EntryPointsEventsCb {

    public static final String KEY_IN_MEDIALIB = "key_in_medialib";

    boolean mScannedDirectory = false;
    private final SimpleArrayMap<String, CheckBox> mProcessingFolders = new SimpleArrayMap<>();
    private Snackbar mSnack;
    private AlertDialog mAlertDialog;

    @Override
    protected Fragment createFragment() {
        return new StorageBrowserFragment();
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setAdapter(new StorageBrowserAdapter(this));
        if (bundle == null) bundle = getArguments();
        if (bundle != null) mScannedDirectory = bundle.getBoolean(KEY_IN_MEDIALIB);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (isRootDirectory() && VLCApplication.showTvUi()) {
            mSnack = Snackbar.make(view, R.string.tv_settings_hint, Snackbar.LENGTH_INDEFINITE);
            if (AndroidUtil.isLolliPopOrLater) mSnack.getView().setElevation(view.getResources().getDimensionPixelSize(R.dimen.audio_player_elevation));
        }
    }

    protected void setupBrowser() {
        viewModel = ViewModelProviders.of(this, new BrowserModel.Factory(getMrl(), BrowserModelKt.TYPE_STORAGE, getShowHiddenFiles())).get(BrowserModel.class);
    }

    @Override
    public void onStart() {
        super.onStart();
        VLCApplication.getMLInstance().addEntryPointsEventsCb(this);
        if (mSnack != null) mSnack.show();
    }

    @Override
    public void onStop() {
        super.onStop();
        VLCApplication.getMLInstance().removeEntryPointsEventsCb(this);
        if (mSnack != null) mSnack.dismiss();
        if (mAlertDialog != null && mAlertDialog.isShowing()) mAlertDialog.dismiss();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IN_MEDIALIB, mScannedDirectory);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.ml_menu_custom_dir).setVisible(true);
        menu.findItem(R.id.ml_menu_equalizer).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.ml_menu_custom_dir) {
            showAddDirectoryDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void browse (MediaWrapper media, int position, boolean scanned){
        final FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
        final Fragment next = createFragment();
        final Bundle args = new Bundle();
        args.putParcelable(BaseBrowserFragmentKt.KEY_MEDIA, media);
        args.putBoolean(KEY_IN_MEDIALIB, mScannedDirectory || scanned);
        next.setArguments(args);
        ft.replace(R.id.fragment_placeholder, next, media.getLocation());
        ft.addToBackStack(getMrl());
        ft.commit();
    }

    public void onCtxClick(View v, int position, MediaLibraryItem item) {
        if (isRootDirectory()) {
            final Storage storage = (Storage) getAdapter().getItem(position);
            boolean isCustom = CustomDirectories.contains(storage.getUri().getPath());
            if (isCustom) ContextSheetKt.showContext(requireActivity(), this, position, item.getTitle(), Constants.CTX_CUSTOM_REMOVE);
        }
    }

    @Override
    public void onCtxAction(int position, int option) {
        final Storage storage = (Storage) getAdapter().getItem(position);
        MediaDatabase.getInstance().recursiveRemoveDir(storage.getUri().getPath());
        CustomDirectories.removeCustomDirectory(storage.getUri().getPath());
        viewModel.remove(storage);
        ((AudioPlayerContainerActivity)getActivity()).updateLib();
    }

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        final MediaWrapper mw = new MediaWrapper(((Storage) item).getUri());
        mw.setType(MediaWrapper.TYPE_DIR);
        browse(mw, position, ((BrowserItemBinding)DataBindingUtil.findBinding(v)).browserCheckbox.getState() == ThreeStatesCheckbox.STATE_CHECKED);
    }

    void processEvent(CheckBox cbp, String mrl) {
        cbp.setEnabled(false);
        mProcessingFolders.put(mrl, cbp);
    }

    @Override
    protected String getCategoryTitle() {
        return getString(R.string.directories_summary);
    }

    @Override
    public void onEntryPointBanned(String entryPoint, boolean success) {}

    @Override
    public void onEntryPointUnbanned(String entryPoint, boolean success) {}

    @Override
    public void onEntryPointRemoved(String entryPoint, final boolean success) {
        if (entryPoint.endsWith("/"))
            entryPoint = entryPoint.substring(0, entryPoint.length()-1);
        if (mProcessingFolders.containsKey(entryPoint)) {
            final CheckBox cb = mProcessingFolders.remove(entryPoint);
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    cb.setEnabled(true);
                    if (success) {
                        ((StorageBrowserAdapter) getAdapter()).updateMediaDirs();
                        getAdapter().notifyDataSetChanged();
                    } else cb.setChecked(true);
                }
            });
        }
    }

    @Override
    public void onDiscoveryStarted(String entryPoint) {}

    @Override
    public void onDiscoveryProgress(String entryPoint) {}

    @Override
    public void onDiscoveryCompleted(String entryPoint) {
        String path = entryPoint;
        if (path.endsWith("/")) path = path.substring(0, path.length()-1);
        if (mProcessingFolders.containsKey(path)) {
            final String finalPath = path;
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    mProcessingFolders.get(finalPath).setEnabled(true);
                }
            });
            ((StorageBrowserAdapter) getAdapter()).updateMediaDirs();
        }
    }

    private void showAddDirectoryDialog() {
        final Context context = getActivity();
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AppCompatEditText input = new AppCompatEditText(context);
        input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        builder.setTitle(R.string.add_custom_path);
        builder.setMessage(R.string.add_custom_path_description);
        builder.setView(input);
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {}
        });
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String path = input.getText().toString().trim();
                File f = new File(path);
                if (!f.exists() || !f.isDirectory()) {
                    UiTools.snacker(getView(), getString(R.string.directorynotfound, path));
                    return;
                }

                try {
                    CustomDirectories.addCustomDirectory(f.getCanonicalPath());
                    ((AudioPlayerContainerActivity)getActivity()).updateLib();
                } catch (IOException ignored) {}
            }
        });
        mAlertDialog = builder.show();
    }
}
