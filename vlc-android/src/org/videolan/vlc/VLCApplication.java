/*****************************************************************************
 * VLCApplication.java
 *****************************************************************************
 * Copyright © 2010-2013 VLC authors and VideoLAN
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
package org.videolan.vlc;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import org.videolan.libvlc.Dialog;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.gui.DialogActivity;
import org.videolan.vlc.gui.dialogs.VlcProgressDialog;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.BitmapCache;
import org.videolan.vlc.gui.helpers.NotificationHelper;
import org.videolan.vlc.util.Settings;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WorkersKt;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.Calendar;

import androidx.collection.SimpleArrayMap;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;

import static org.videolan.vlc.gui.helpers.UiTools.setLocale;

public class VLCApplication extends Application {
    public final static String TAG = "VLC/VLCApplication";

    public final static String ACTION_MEDIALIBRARY_READY = "VLC/VLCApplication";
    private static volatile VLCApplication instance;

    public final static String SLEEP_INTENT = Strings.buildPkgString("SleepIntent");

    public static Calendar sPlayerSleepTime = null;

    private static SimpleArrayMap<String, WeakReference<Object>> sDataMap = new SimpleArrayMap<>();

    private static int sDialogCounter = 0;

    // Property to get the new locale only on restart to prevent change the locale partially on runtime
    private static String locale = "";

    public VLCApplication() {
        super();
        instance = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        WorkersKt.runIO(new Runnable() {
            @Override
            public void run() {
                locale = Settings.INSTANCE.getInstance(instance).getString("set_locale", "");

                // Set the locale for API < 24 and set application resources and direction for API >=24
                setLocale(getAppContext());
            }
        });

        WorkersKt.runIO(new Runnable() {
            @Override
            public void run() {

                if (AndroidUtil.isOOrLater) NotificationHelper.createNotificationChannels(VLCApplication.this);
                // Prepare cache folder constants
                AudioUtil.prepareCacheFolder(getAppContext());

                if (!VLCInstance.testCompatibleCPU(getAppContext())) return;
                Dialog.setCallbacks(VLCInstance.get(), mDialogCallbacks);
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setLocale(getAppContext());
    }

    /**
     * Called when the overall system is running low on memory
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "System is running low on memory");

        BitmapCache.getInstance().clear();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.w(TAG, "onTrimMemory, level: "+level);

        BitmapCache.getInstance().clear();
    }

    /**
     * @return the main context of the Application
     */
    @SuppressLint("PrivateApi")
    public static Context getAppContext() {
        if (instance != null) return instance;
        else {
            try {
                instance = (VLCApplication) Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null);
            } catch (IllegalAccessException ignored) {}
            catch (InvocationTargetException ignored) {}
            catch (NoSuchMethodException ignored) {}
            catch (ClassNotFoundException ignored) {}
            catch (ClassCastException ignored) {}
            return instance;
        }
    }

    /**
     * @return the main resources from the Application
     */
    public static Resources getAppResources()
    {
        return getAppContext().getResources();
    }

    public static String getLocale(){
        return locale;
    }

    public static void storeData(String key, Object data) {
        sDataMap.put(key, new WeakReference<>(data));
    }

    public static Object getData(String key) {
        final WeakReference wr = sDataMap.remove(key);
        return wr != null ? wr.get() : null;
    }

    public static boolean hasData(String key) {
        return sDataMap.containsKey(key);
    }

    public static void clearData() {
        sDataMap.clear();
    }

    Dialog.Callbacks mDialogCallbacks = new Dialog.Callbacks() {
        @Override
        public void onDisplay(Dialog.ErrorMessage dialog) {
            Log.w(TAG, "ErrorMessage "+dialog.getText());
        }

        @Override
        public void onDisplay(Dialog.LoginDialog dialog) {
            final String key = DialogActivity.KEY_LOGIN + sDialogCounter++;
            fireDialog(dialog, key);
        }

        @Override
        public void onDisplay(Dialog.QuestionDialog dialog) {
            if (!Util.byPassChromecastDialog(dialog)) {
                final String key = DialogActivity.KEY_QUESTION + sDialogCounter++;
                fireDialog(dialog, key);
            }
        }

        @Override
        public void onDisplay(Dialog.ProgressDialog dialog) {
            final String key = DialogActivity.KEY_PROGRESS + sDialogCounter++;
            fireDialog(dialog, key);
        }

        @Override
        public void onCanceled(Dialog dialog) {
            if (dialog != null && dialog.getContext() != null) ((DialogFragment)dialog.getContext()).dismiss();
        }

        @Override
        public void onProgressUpdate(Dialog.ProgressDialog dialog) {
            VlcProgressDialog vlcProgressDialog = (VlcProgressDialog) dialog.getContext();
            if (vlcProgressDialog != null && vlcProgressDialog.isVisible()) vlcProgressDialog.updateProgress();
        }
    };

    private void fireDialog(Dialog dialog, String key) {
        storeData(key, dialog);
        startActivity(new Intent(getAppContext(), DialogActivity.class).setAction(key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    public static Medialibrary getMLInstance() {
        return Medialibrary.getInstance();
    }

    /**
     * Check if application is currently displayed
     * @return true if an activity is displayed, false if app is in background.
     */
    public static boolean isForeground() {
        return ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }
}
