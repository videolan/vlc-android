/**
 * **************************************************************************
 * Helpers.java
 * ****************************************************************************
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
package org.videolan.vlc.extensions.api.tools;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.videolan.vlc.extensions.api.WarningActivity;

public class Helpers {

    /**
     * Helper method to check if VLC is installed on device. If not, shows an AlertDialog and offers
     * the user to install it from the Play Store.
     * @param context A simple context reference
     * @return true is VLC is installed, false if not.
     */
    public static boolean checkVlc(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo("org.videolan.vlc", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            context.startActivity(new Intent(context, WarningActivity.class));
            return false;
        }
    }
}
