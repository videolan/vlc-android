/*
 * *************************************************************************
 *  ContextMenuRecyclerView.java
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
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.ContextMenu;

public class ContextMenuRecyclerView extends RecyclerView {

    private ContextMenu.ContextMenuInfo mContextMenuInfo = null;

    public ContextMenuRecyclerView(Context context) {
        super(context);
    }

    public ContextMenuRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ContextMenuRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }


    @Override
    protected ContextMenu.ContextMenuInfo getContextMenuInfo() {
        return mContextMenuInfo;
    }

    public void openContextMenu(int position) {
        if (position >= 0)
            createContextMenuInfo(position);
        showContextMenu();
    }

    protected void createContextMenuInfo(int position) {
        if (mContextMenuInfo == null)
            mContextMenuInfo = new RecyclerContextMenuInfo(position);
        else
            ((RecyclerContextMenuInfo)mContextMenuInfo).setValues(position);
    }

    public static class RecyclerContextMenuInfo implements ContextMenu.ContextMenuInfo {

        public int position;

        public RecyclerContextMenuInfo(int position) {
            setValues(position);
        }

        public void setValues(int position){
            this.position = position;
        }
    }
}
