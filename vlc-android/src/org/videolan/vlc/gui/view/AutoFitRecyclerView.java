/*
 * *************************************************************************
 *  AutoFitRecyclerView.java
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

package org.videolan.vlc.gui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.GridLayoutManager;
import android.util.AttributeSet;
import android.view.Display;
import android.view.WindowManager;

import org.videolan.vlc.VLCApplication;

public class AutoFitRecyclerView extends ContextMenuRecyclerView {

    private NpaGridLayoutManager mGridLayoutManager;
    private int mColumnWidth = -1;
    private int mSpanCount = -1;

    public AutoFitRecyclerView(Context context) {
        super(context);
        init(context, null);
    }

    public AutoFitRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public AutoFitRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            int[] attrsArray = {
                    android.R.attr.columnWidth
            };
            TypedArray array = context.obtainStyledAttributes(attrs, attrsArray);
            mColumnWidth = array.getDimensionPixelSize(0, -1);
            array.recycle();
        }

        mGridLayoutManager = new NpaGridLayoutManager(getContext(), 1);
        setLayoutManager(mGridLayoutManager);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (mSpanCount == -1 && mColumnWidth > 0) {
            int ratio = getMeasuredWidth() / mColumnWidth;
            int spanCount = Math.max(1, ratio);
            mGridLayoutManager.setSpanCount(spanCount);
        } else
            mGridLayoutManager.setSpanCount(mSpanCount);

    }

    public void setColumnWidth(int width) {
        mColumnWidth = width;
    }

    public int getPerfectColumnWidth(int columnWidth, int margin) {

        WindowManager wm = (WindowManager) VLCApplication.getAppContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        int displayWidth = display.getWidth() - margin;

        int remainingSpace = displayWidth % columnWidth;
        int ratio = displayWidth / columnWidth;
        int spanCount = Math.max(1, ratio);

        return (columnWidth + (remainingSpace / spanCount));
    }

    public int getColumnWidth() {
        return mColumnWidth;
    }

    public void setNumColumns(int spanCount) {
        mSpanCount = spanCount;
    }

    public void setSpanSizeLookup(GridLayoutManager.SpanSizeLookup spanSizeLookup) {
        mGridLayoutManager.setSpanSizeLookup(spanSizeLookup);
    }

}
