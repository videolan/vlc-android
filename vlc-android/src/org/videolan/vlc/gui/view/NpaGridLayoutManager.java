/*
 * ************************************************************************
 *  NpaGridLayout.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
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
 *
 *  *************************************************************************
 */

package org.videolan.vlc.gui.view;

import android.content.Context;
import android.support.v7.widget.GridLayoutManager;

public class NpaGridLayoutManager extends GridLayoutManager {

    public NpaGridLayoutManager(Context context, int spanCount) {
        super(context, spanCount);
    }

    /*
     * Disable predictive animations to prevent a bug on Recyclerview
     * AppCompat 23.1.1, may be fixed with AppCompat 24.
     */
    @Override
    public boolean supportsPredictiveItemAnimations() {
        return false;
    }
}
