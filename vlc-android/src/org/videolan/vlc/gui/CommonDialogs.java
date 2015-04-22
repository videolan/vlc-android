/*****************************************************************************
 * CommonDialogs.java
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
 * Copyright © 2012 Edward Wang
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
package org.videolan.vlc.gui;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.support.v7.app.AlertDialog;

import org.videolan.vlc.R;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCRunnable;

public class CommonDialogs {
    public final static String TAG = "VLC/CommonDialogs";

    public static enum MenuType {
        Video, Audio
    };
    public static final int INTENT_SPECIFIC = 10; // PICK_FILE intent
    public static final int INTENT_GENERIC = 20; // generic CATEGORY_OPENABLE

    public static AlertDialog deleteMedia(final Context context,
                                          final String addressMedia,
                                          final VLCRunnable runnable) {
        final String name = Uri.decode(addressMedia.substring(addressMedia.lastIndexOf('/')+1));
        return  deleteMedia(context, addressMedia, name, runnable);
    }

    public static AlertDialog deleteMedia(final Context context,
                                          final String addressMedia,
                                          final String name,
                                          final VLCRunnable runnable) {

        return confirmDialog(
                context,
                context.getResources().getString(R.string.confirm_delete,
                        name),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Util.deleteFile(context, addressMedia);
                        if (runnable != null)
                            runnable.run();
                    }
                });
    }

    public static AlertDialog confirmDialog(final Context context,
            final String confirmationString,
            final DialogInterface.OnClickListener callback) {
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.validation).setMessage(confirmationString)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, callback)
                .setNegativeButton(android.R.string.cancel, null).create();

        return alertDialog;
    }
}
