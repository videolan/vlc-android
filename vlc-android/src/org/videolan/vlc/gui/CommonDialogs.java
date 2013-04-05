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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.VlcRunnable;
import org.videolan.vlc.interfaces.OnExpandableListener;
import org.videolan.vlc.widget.ExpandableLayout;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.LinearLayout;

public class CommonDialogs {
    public final static String TAG = "VLC/CommonDialogs";

    public static AlertDialog deleteMedia(final Context context,
                                          final String addressMedia,
                                          final VlcRunnable runnable) {
        URI adressMediaUri = null;
        try {
            adressMediaUri = new URI (addressMedia);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        final File fileMedia = new File(adressMediaUri);

        AlertDialog alertDialog = new AlertDialog.Builder(context)
        .setTitle(R.string.validation)
        .setMessage(context.getResources().getString(R.string.confirm_delete, fileMedia.getName()))
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                fileMedia.delete();
                if(runnable != null)
                    runnable.run();
            }
        })
        .setNegativeButton(android.R.string.cancel, null).create();

        return alertDialog;
    }

    public static void advancedOptions(final Context context, View v) {
        LayoutInflater inflater = LayoutInflater.from(VLCApplication.getAppContext());
        View view = inflater.inflate(R.layout.advanced_options, null);

        // build dialog
        Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(context, R.style.Theme_VLC_AlertMenu))
                .setView(view);
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);

        // register listener on each ExpandableLayout in advanced_layout
        LinearLayout advanced_layout = (LinearLayout) view.findViewById(R.id.advanced_layout);
        OnExpandableListener mExpandableListener = new OnExpandableListener() {
            @Override
            public void onDismiss() {
                dialog.dismiss();
            }
        };
        for (int i = 0; i < advanced_layout.getChildCount(); ++i)
        {
            View child = advanced_layout.getChildAt(i);
            if (child instanceof ExpandableLayout) {
                ((ExpandableLayout) child).setOnExpandableListener(mExpandableListener);
            }
        }

        // show dialog
        dialog.show();

        // force size
        float density = context.getResources().getDisplayMetrics().density;
        LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int) (density * 300 + 0.5f); // 300dp

        // force location
        if (v != null) {
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            int[] location = new int[2];
            v.getLocationInWindow(location);
            lp.x = location[0] - lp.width;
            lp.y = location[1] - (int) (density * 50 + 0.5f); // -50dp to compensate alertdialog margins
        }

        dialog.getWindow().setAttributes(lp);
    }
}
