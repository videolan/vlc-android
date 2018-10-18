/**
 * **************************************************************************
 * SelectorViewHolder.java
 * ****************************************************************************
 * Copyright © 2017 VLC authors and VideoLAN
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
package org.videolan.vlc.gui.helpers;

import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

import org.videolan.vlc.BR;

public class SelectorViewHolder<T extends ViewDataBinding> extends RecyclerView.ViewHolder implements View.OnFocusChangeListener {

    public T binding = null;

    public SelectorViewHolder(T vdb) {
        super(vdb.getRoot());
        binding = vdb;
        itemView.setOnFocusChangeListener(this);
    }

    public void selectView(boolean selected) {
        setViewBackground(itemView.hasFocus(), selected);
    }

    private void setViewBackground(boolean focus, boolean selected) {
        final int color = focus ? UiTools.Resources.ITEM_FOCUS_ON : selected ? UiTools.Resources.ITEM_SELECTION_ON : UiTools.Resources.ITEM_FOCUS_OFF;
        binding.setVariable(BR.bgColor, color);
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (getLayoutPosition() >= 0) setViewBackground(hasFocus, isSelected());
    }

    protected boolean isSelected() {
        return false;
    }
}
