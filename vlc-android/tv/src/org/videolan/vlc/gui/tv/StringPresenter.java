/*****************************************************************************
 * StringPresenter.java
 *****************************************************************************
 * Copyright © 2012-2014 VLC authors, VideoLAN and VideoLabs
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

import org.videolan.vlc.R;

import android.content.res.Resources;
import android.support.v17.leanback.widget.Presenter;
import android.view.ViewGroup;
import android.widget.TextView;

public class StringPresenter extends Presenter {
	private static final String TAG = "StringPresenter";

    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        TextView textView = new TextView(parent.getContext());
        textView.setFocusable(true);
        textView.setFocusableInTouchMode(true);
        textView.setBackground(
                parent.getContext().getResources().getDrawable(R.drawable.background_cone));
        return new ViewHolder(textView);
    }

    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        Resources res = viewHolder.view.getContext().getResources();
        TextView tv = (TextView) viewHolder.view;
        tv.setText(item.toString());
        if (res.getString(R.string.preferences).equals(item.toString())) {
            tv.setBackground(res.getDrawable(R.drawable.ic_menu_preferences_big));
        }
        tv.setHeight(res.getDimensionPixelSize(R.dimen.tv_card_height));
        tv.setWidth(res.getDimensionPixelSize(R.dimen.tv_card_width));
    }

    public void onUnbindViewHolder(ViewHolder viewHolder) {
        // no op
    }

}
