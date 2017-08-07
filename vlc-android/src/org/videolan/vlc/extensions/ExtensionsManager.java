package org.videolan.vlc.extensions;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class ExtensionsManager {

    private static ExtensionsManager sExtensionsManager;
    private List<ExtensionListing> mExtensions = new ArrayList<>();
    private static final String KEY_PROTOCOL_VERSION = "protocolVersion";
    private static final String KEY_LISTING_TITLE = "title";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_MENU_ICON = "menuicon";
    private static final String KEY_SETTINGS_ACTIVITY = "settingsActivity";
    private static final String ACTION_EXTENSION = "org.videolan.vlc.Extension";
    private static final int PROTOCOLE_VERSION = 1;

    public ExtensionsManager() {
    }

    public static ExtensionsManager getInstance() {
        if (sExtensionsManager == null)
            sExtensionsManager = new ExtensionsManager();
        return sExtensionsManager;
    }

    public List<ExtensionListing> updateAvailableExtensions(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(
                new Intent(ACTION_EXTENSION), PackageManager.GET_META_DATA);

        ArrayList<ExtensionListing> extensions = new ArrayList<>();

        for (ResolveInfo resolveInfo : resolveInfos) {
            ExtensionListing extension = new ExtensionListing();
            extension.componentName(new ComponentName(resolveInfo.serviceInfo.packageName,
                    resolveInfo.serviceInfo.name));
            Bundle metaData = resolveInfo.serviceInfo.metaData;
            if (metaData != null) {
                extension.compatible(metaData.getInt(KEY_PROTOCOL_VERSION) == PROTOCOLE_VERSION);
                if (!extension.compatible())
                    continue;
                String title = metaData.getString(KEY_LISTING_TITLE);
                extension.title(title != null ? title : resolveInfo.loadLabel(pm).toString());
                extension.description(metaData.getString(KEY_DESCRIPTION));
                String settingsActivity = metaData.getString(KEY_SETTINGS_ACTIVITY);
                if (!TextUtils.isEmpty(settingsActivity)) {
                    extension.settingsActivity(ComponentName.unflattenFromString(
                            resolveInfo.serviceInfo.packageName + "/" + settingsActivity));
                }
                extension.menuIcon(metaData.getInt(KEY_MENU_ICON, 0));
                extensions.add(extension);
            }
        }
        synchronized (mExtensions) {
            mExtensions.clear();
            mExtensions.addAll(extensions);
        }
        return extensions;
    }

    public List<ExtensionListing> getExtensions(Context context) {
        if (mExtensions.size() == 0)
            return updateAvailableExtensions(context);
        else
            return mExtensions;
    }

    public boolean extensionIsEnabled(SharedPreferences settings, int id) {
        return settings.getBoolean("extension_" + mExtensions.get(id).componentName().getPackageName(), true);
    }
}
