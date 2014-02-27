/*****************************************************************************
 * ExpandableLayout.java
 *****************************************************************************
 * Copyright © 2013 VLC authors and VideoLAN
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

package org.videolan.vlc.widget;

import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.interfaces.OnExpandableListener;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ExpandableLayout extends LinearLayout {

    private final View mHeaderLayout;
    private final ImageView mIcon;
    private final TextView mTitle;
    private final TextView mText;
    private final ImageView mMore;
    private final LinearLayout mContent;
    private Boolean mExpanded;
    private OnExpandableListener listener = null;

    public ExpandableLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.expandable_layout, this, true);

        mHeaderLayout = findViewById(R.id.header_layout);
        mIcon = (ImageView) findViewById(R.id.icon);
        mTitle = (TextView) findViewById(R.id.title);
        mText = (TextView) findViewById(R.id.text);
        mMore = (ImageView) findViewById(R.id.more);
        mContent = (LinearLayout) findViewById(R.id.content);

        mHeaderLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setState(!mExpanded);
            }
        });

        setState(isInEditMode());
    }

    private void setState(Boolean expanded) {
        mExpanded = expanded;
        mMore.setImageResource(expanded ?
                Util.getResourceFromAttribute(getContext(), R.attr.ic_up_style) :
                    Util.getResourceFromAttribute(getContext(), R.attr.ic_down_style));
        mContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    public void setTitle(int resid) {
        mTitle.setText(resid);
    }

    public void setText(String text) {
        mText.setText(text);
        mText.setVisibility(text != null ? View.VISIBLE : View.GONE);
    }

    public void setIcon(int resid) {
        mIcon.setImageResource(resid);
        mIcon.setVisibility(View.VISIBLE);
    }

    public void setContent(Context context, int resid) {
        View view = LayoutInflater.from(context).inflate(resid, null, true);
        mContent.addView(view);
    }

    public void expand() {
        setState(true);
    }

    public void collapse() {
        setState(false);
    }

    public void dismiss() {
        if (this.listener != null)
            this.listener.onDismiss();
    }

    public void setOnExpandableListener(OnExpandableListener listener) {
        this.listener = listener;
    }
}
