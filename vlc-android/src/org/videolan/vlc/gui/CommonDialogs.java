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
import org.videolan.vlc.Util;
import org.videolan.vlc.VlcRunnable;
import org.videolan.vlc.interfaces.OnExpandableListener;
import org.videolan.vlc.widget.ExpandableLayout;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class CommonDialogs {
    public final static String TAG = "VLC/CommonDialogs";

    public static enum MenuType {
        Video, Audio
    };
    public static final int INTENT_SPECIFIC = 10; // PICK_FILE intent
    public static final int INTENT_GENERIC = 20; // generic CATEGORY_OPENABLE

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

    public static void advancedOptions(final Context context, View v, MenuType t) {

        int style = Util.getResourceFromAttribute(context, R.attr.advanced_options_style);

        final Dialog dialog = new Dialog(context, style);
        dialog.setContentView(R.layout.advanced_options);
        dialog.setCanceledOnTouchOutside(true);

        // register listener on each ExpandableLayout in advanced_layout
        LinearLayout advanced_layout = (LinearLayout) dialog.findViewById(R.id.advanced_layout);
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

        TextView add_subtitle = (TextView)dialog.findViewById(R.id.add_subtitle);
        LinearLayout equalizer = (LinearLayout)dialog.findViewById(R.id.equalizer);
        if(t == MenuType.Video) {
            add_subtitle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent("org.openintents.action.PICK_FILE");

                    File file = new File(android.os.Environment.getExternalStorageDirectory().getPath());
                    intent.setData(Uri.fromFile(file));

                    // Set fancy title and button (optional)
                    intent.putExtra("org.openintents.extra.TITLE", context.getString(R.string.subtitle_select));
                    intent.putExtra("org.openintents.extra.BUTTON_TEXT", context.getString(R.string.open));

                    if (context
                            .getPackageManager()
                            .queryIntentActivities(intent,
                                    PackageManager.MATCH_DEFAULT_ONLY).size() > 0) {
                        ((Activity)context).startActivityForResult(intent, INTENT_SPECIFIC);
                    } else {
                        // OI intent not found, trying anything
                        Intent intent2 = new Intent(Intent.ACTION_GET_CONTENT);
                        intent2.setType("*/*");
                        intent2.addCategory(Intent.CATEGORY_OPENABLE);
                        try {
                            ((Activity)context).startActivityForResult(intent2, INTENT_GENERIC);
                        } catch(ActivityNotFoundException e) {
                            Log.i(TAG, "No file picker found on system");
                            Toast.makeText(context,
                                    R.string.no_file_picker_found,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    dialog.dismiss();
                }
            });
            equalizer.setVisibility(View.GONE);
        } else {
            final MainActivity activity = (MainActivity)context;
            equalizer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activity.showSecondaryFragment("equalizer");
                    dialog.dismiss();
                }
            });
            add_subtitle.setVisibility(View.GONE);
        }

        // show dialog
        dialog.show();

        // force location
        float density = context.getResources().getDisplayMetrics().density;
        LayoutParams lp = dialog.getWindow().getAttributes();
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
