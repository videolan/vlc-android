/*
 * ***************************************************************************
 * DialogActivity.java
 * ***************************************************************************
 * Copyright © 2016 VLC authors and VideoLAN
 * Author: Geoffrey Métais
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
 * ***************************************************************************
 */

package org.videolan.vlc.gui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.dialogs.DeviceDialog;
import org.videolan.vlc.gui.dialogs.NetworkServerDialog;
import org.videolan.vlc.gui.dialogs.VlcDialog;
import org.videolan.vlc.gui.dialogs.VlcLoginDialog;
import org.videolan.vlc.gui.dialogs.VlcProgressDialog;
import org.videolan.vlc.gui.dialogs.VlcQuestionDialog;
import org.videolan.vlc.gui.network.MRLPanelFragment;
import org.videolan.vlc.media.MediaUtils;

import java.util.List;

import androidx.fragment.app.FragmentManager;

public class DialogActivity extends BaseActivity {

    public static final String KEY_LOGIN = "LoginDialog";
    public static final String KEY_QUESTION = "QuestionDialog";
    public static final String KEY_PROGRESS = "ProgressDialog";
    public static final String KEY_STREAM = "streamDialog";
    public static final String KEY_SERVER = "serverDialog";
    public static final String KEY_SUBS_DL = "subsdlDialog";
    public static final String KEY_DEVICE = "deviceDialog";

    public static final String EXTRA_MEDIALIST = "extra_media";
    public static final String EXTRA_PATH = "extra_path";
    public static final String EXTRA_UUID = "extra_uuid";
    public static final String EXTRA_SCAN = "extra_scan";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.transparent);
        String key = getIntent().getAction();
        if (TextUtils.isEmpty(key)) {
            finish();
            return;
        }
        if (key.startsWith(KEY_LOGIN)) setupLoginDialog(key);
        else if (key.startsWith(KEY_QUESTION)) setupQuestionDialog(key);
        else if (key.startsWith(KEY_PROGRESS)) setupProgressDialog(key);
        else if (KEY_STREAM.equals(key)) setupStreamDialog();
        else if (KEY_SERVER.equals(key)) setupServerDialog();
        else if (KEY_SUBS_DL.equals(key)) setupSubsDialog();
        else if (KEY_DEVICE.equals(key)) setupDeviceDialog();
    }

    private void setupDeviceDialog() {
        getWindow().getDecorView().setAlpha(0.f);
        final DeviceDialog dialog = new DeviceDialog();
        final Intent intent = getIntent();
        dialog.setDevice(intent.getStringExtra(EXTRA_PATH), intent.getStringExtra(EXTRA_UUID), intent.getBooleanExtra(EXTRA_SCAN, false));
        dialog.show(getSupportFragmentManager(), "device_dialog");
    }

    private void setupStreamDialog() {
        new MRLPanelFragment().show(getSupportFragmentManager(), "fragment_mrl");
    }

    private void setupServerDialog() {
        new NetworkServerDialog().show(getSupportFragmentManager(), "fragment_mrl");
    }

    private void setupSubsDialog() {
        final List<MediaWrapper> medialist = getIntent().getParcelableArrayListExtra(EXTRA_MEDIALIST);
        if (medialist != null) MediaUtils.INSTANCE.getSubs(this, medialist);
        else finish();
    }

    private void setupLoginDialog(String key) {
        VlcLoginDialog dialog = new VlcLoginDialog();
        startVlcDialog(key, dialog);
    }

    private void setupQuestionDialog(String key) {
        VlcQuestionDialog dialog = new VlcQuestionDialog();
        startVlcDialog(key, dialog);
    }

    private void setupProgressDialog(String key) {
        VlcProgressDialog dialog = new VlcProgressDialog();
        startVlcDialog(key, dialog);
    }

    private void startVlcDialog(String key, VlcDialog dialog) {
        dialog.init(key);
        FragmentManager fm = getSupportFragmentManager();
        dialog.show(fm, key);
    }
}
