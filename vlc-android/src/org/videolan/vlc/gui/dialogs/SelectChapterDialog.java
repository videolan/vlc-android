/**
 * **************************************************************************
 * PickTimeFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
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
package org.videolan.vlc.gui.dialogs;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import org.videolan.libvlc.MediaPlayer;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.util.Strings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SelectChapterDialog extends DialogFragment implements PlaybackService.Client.Callback {

    public final static String TAG = "VLC/SelectChapterDialog";

    private ListView mChapterList;

    protected PlaybackService mService;

    public SelectChapterDialog() {
    }

    public static SelectChapterDialog newInstance(int theme) {
        SelectChapterDialog myFragment = new SelectChapterDialog();

        Bundle args = new Bundle();
        args.putInt("theme", theme);
        myFragment.setArguments(args);

        return myFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, getArguments().getInt("theme"));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_select_chapter, container);
        mChapterList = (ListView) view.findViewById(R.id.chapter_list);

        getDialog().setCancelable(true);
        getDialog().setCanceledOnTouchOutside(true);
        Window window = getDialog().getWindow();
        window.setBackgroundDrawableResource(UiTools.getResourceFromAttribute(getActivity(), R.attr.rounded_bg));
        window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        return view;
    }

    private void initChapterList() {
        final MediaPlayer.Chapter[] chapters = mService.getChapters(-1);
        int chaptersCount = chapters != null ? chapters.length : 0;
        if (chaptersCount <= 1) {
            return;
        }

        ArrayList<Map<String, String>> chapterList = new ArrayList<Map<String, String>>();

        for (int i = 0; i < chaptersCount; i++) {
            String name;
            if (chapters[i].name == null || chapters[i].name.equals(""))
                name = getResources().getString(R.string.chapter) + " " + i;
            else
                name = chapters[i].name;
            chapterList.add(putData(name, Strings.millisToString(chapters[i].timeOffset)));
        }

        String[] from = { "name", "time" };
        int[] to = { R.id.chapter_name, R.id.chapter_time };
        SimpleAdapter adapter = new SimpleAdapter(getActivity(), chapterList,
                R.layout.dialog_select_chapter_item, from, to);

        mChapterList.setAdapter(adapter);
        mChapterList.setSelection(mService.getChapterIdx());
        mChapterList.setItemChecked(mService.getChapterIdx(), true);
        mChapterList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mService.setChapterIdx(position);
                dismiss();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        PlaybackServiceFragment.registerPlaybackService(this, this);
    }

    @Override
    public void onStop() {
        super.onStop();
        PlaybackServiceFragment.unregisterPlaybackService(this, this);
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
        initChapterList();
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }

    private HashMap<String, String> putData(String name, String time) {
        HashMap<String, String> item = new HashMap<String, String>();
        item.put("name", name);
        item.put("time", time);
        return item;
    }
}
