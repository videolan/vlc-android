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

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;

import org.videolan.vlc.gui.dialogs.VlcDialog;
import org.videolan.vlc.gui.dialogs.VlcLoginDialog;
import org.videolan.vlc.gui.dialogs.VlcProgressDialog;
import org.videolan.vlc.gui.dialogs.VlcQuestionDialog;

public class DialogActivity extends AppCompatActivity {

    public static final String KEY_LOGIN = "LoginDialog";
    public static final String KEY_QUESTION = "QuestionDialog";
    public static final String KEY_PROGRESS = "ProgressDialog";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String key = getIntent().getAction();
        if (key.startsWith(KEY_LOGIN))
            setupLoginDialog(key);
        else if (key.startsWith(KEY_QUESTION))
            setupQuestionDialog(key);
        else if (key.startsWith(KEY_PROGRESS))
            setupProgressDialog(key);
        else
            finish();
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
