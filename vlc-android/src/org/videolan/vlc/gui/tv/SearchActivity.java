/*****************************************************************************
 * SearchActivity.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
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
 *****************************************************************************/
package org.videolan.vlc.gui.tv;

import android.annotation.TargetApi;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v17.leanback.widget.SpeechRecognitionCallback;
import android.support.v4.app.FragmentActivity;

import org.videolan.vlc.R;
import org.videolan.vlc.util.Util;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class SearchActivity extends FragmentActivity {

    SearchFragment mFragment;
    private static final int REQUEST_SPEECH = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_search);

        mFragment = (SearchFragment) getSupportFragmentManager()
                .findFragmentById(R.id.search_fragment);
        final Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction()) || "com.google.android.gms.actions.SEARCH_ACTION".equals(intent.getAction())) {
            mFragment.onQueryTextSubmit(intent.getStringExtra(SearchManager.QUERY));
        } else {
            final Intent recognitionIntent = mFragment.getRecognizerIntent();
            if (Util.isCallable(recognitionIntent)) {
                final SpeechRecognitionCallback speechRecognitionCallback = new SpeechRecognitionCallback() {

                    @Override
                    public void recognizeSpeech() {
                        startActivityForResult(recognitionIntent, REQUEST_SPEECH);
                    }
                };
                mFragment.setSpeechRecognitionCallback(speechRecognitionCallback);
            }
        }
    }

    @Override
    public boolean onSearchRequested() {
        mFragment.startRecognition();
        return true;
    }
}
