package org.videolan.vlc.extensions;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import com.google.android.material.navigation.NavigationView;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.view.MenuItem;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.util.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ExtensionsManager {

    private static final String KEY_PROTOCOL_VERSION = "protocolVersion";
    private static final String KEY_LISTING_TITLE = "title";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_MENU_ICON = "menuicon";
    private static final String KEY_SETTINGS_ACTIVITY = "settingsActivity";
    private static final String KEY_ANDROID_AUTO_ENABLED = "androidAutoEnabled";
    private static final String ACTION_EXTENSION = "org.videolan.vlc.Extension";
    private static final int PROTOCOLE_VERSION = 1;
    public final static String EXTENSION_PREFIX = "extension";
    public final static String ANDROID_AUTO_SUFFIX = "androidAuto";
    public static boolean androidAutoInstalled = false;

    private static ExtensionsManager sExtensionsManager;
    private final List<ExtensionListing> mExtensions = new ArrayList<>();

    public static ExtensionsManager getInstance() {
        if (sExtensionsManager == null)
            sExtensionsManager = new ExtensionsManager();
        return sExtensionsManager;
    }

    private List<ExtensionListing> updateAvailableExtensions(Context context) {
        androidAutoInstalled = isAndroidAutoInstalled(context);
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(
                new Intent(ACTION_EXTENSION), PackageManager.GET_META_DATA);

        List<ExtensionListing> extensions = new ArrayList<>();

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
                extension.androidAutoEnabled(metaData.getBoolean(KEY_ANDROID_AUTO_ENABLED, false));
                extension.menuIcon(metaData.getInt(KEY_MENU_ICON, 0));
                extensions.add(extension);
            }
        }

        SharedPreferences settings = Settings.INSTANCE.getInstance(context);
        deleteUnusedExtensionPreferences(extensions, settings);

        if (context instanceof MainActivity && ((MainActivity)context).getNavigator().currentIdIsExtension()) {
            if (previousExtensionIsEnabled(context)) {
                String lastExtensionTitle = settings.getString("current_extension_name", null);
                for (int i = 0; i < extensions.size(); ++i) {
                    if (TextUtils.equals(extensions.get(i).title(), lastExtensionTitle)) {
                        ((MainActivity)context).getNavigator().setCurrentFragmentId(i);
                        settings.edit().putInt("fragment_id", i).apply();
                        break;
                    }
                }
            } else {
                ((MainActivity)context).getNavigator().setCurrentFragmentId(-1);
                settings.edit().putInt("fragment_id", -1).apply();
            }
        }

        synchronized (mExtensions) {
            mExtensions.clear();
            mExtensions.addAll(extensions);
        }
        return extensions;
    }

    public List<ExtensionListing> getExtensions(Context context, boolean update) {
        if (mExtensions.size() == 0 || update)
            return updateAvailableExtensions(context);
        else
            return mExtensions;
    }

    public boolean previousExtensionIsEnabled(Context context) {
        SharedPreferences settings = Settings.INSTANCE.getInstance(context);
        String key = EXTENSION_PREFIX + "_" + settings.getString("current_extension_name", null);
        return settings.contains(key) && settings.getBoolean(key, false);
    }

    private boolean deleteUnusedExtensionPreferences(List<ExtensionListing> list, SharedPreferences settings) {
        boolean extensionMissing = false;
        List<String> extensionNames = new ArrayList<>();
        for (ExtensionListing extension : list)
            extensionNames.add(extension.componentName().getPackageName());
        for (Map.Entry<String, ?> entry : settings.getAll().entrySet())
            if (entry.getKey().startsWith(EXTENSION_PREFIX + "_")
                    && !entry.getKey().endsWith("_" + ANDROID_AUTO_SUFFIX)
                    && !extensionNames.contains(entry.getKey().replace(EXTENSION_PREFIX + "_", ""))) {
                settings.edit().remove(entry.getKey()).apply();
                settings.edit().remove(entry.getKey() + "_" + ANDROID_AUTO_SUFFIX).apply();
                extensionMissing = true;
            }
        return extensionMissing;
    }

    public void displayPlugin(Activity activity, int id, ExtensionListing extension, boolean visible) {
        if (visible) {
            MenuItem extensionGroup = ((NavigationView)activity.findViewById(R.id.navigation)).getMenu().findItem(R.id.extensions_group);
            extensionGroup.setVisible(true);
            MenuItem item = extensionGroup.getSubMenu().add(R.id.extensions_group, id, 0, extension.title());
            item.setCheckable(false);
            int iconRes = extension.menuIcon();
            Drawable extensionIcon = null;
            if (iconRes != 0) {
                try {
                    Resources res = activity.getPackageManager().getResourcesForApplication(extension.componentName().getPackageName());
                    extensionIcon = res.getDrawable(extension.menuIcon());
                } catch (PackageManager.NameNotFoundException ignored) {}
            }
            if (extensionIcon != null)
                item.setIcon(extensionIcon);
            else
                try {
                    item.setIcon(activity.getPackageManager().getApplicationIcon(extension.componentName().getPackageName()));
                } catch (PackageManager.NameNotFoundException e) {
                    item.setIcon(R.drawable.icon);
                }
        }
    }

    public void showExtensionPermissionDialog(final Activity activity, final int id, final ExtensionListing extension, final String key) {
        final List<CharSequence> extraTitles = new ArrayList<>();
        final List<String> extraKeys = new ArrayList<>();

        //Add necessary checkboxes
        if (androidAutoInstalled && extension.androidAutoEnabled()) {
            extraTitles.add(activity.getString(R.string.extension_permission_checkbox_title, activity.getString(R.string.android_auto)));
            extraKeys.add(key + "_" + ANDROID_AUTO_SUFFIX);
        }

        final boolean[] extraCheckedStates = new boolean[extraTitles.size()];
        Arrays.fill(extraCheckedStates, Boolean.TRUE);
        new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.extension_permission_title, extension.title()))
                .setMultiChoiceItems(extraTitles.toArray(new CharSequence[extraTitles.size()]),
                        extraCheckedStates,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int pos, boolean b) {
                                extraCheckedStates[pos] = b;
                            }
                        })
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        Settings.INSTANCE.getInstance(activity.getApplication()).edit().putBoolean(key, true).apply();
                        for (int i=0; i<extraTitles.size(); i++)
                            Settings.INSTANCE.getInstance(activity.getApplication()).edit().putBoolean(extraKeys.get(i), extraCheckedStates[i]).apply();
                        displayPlugin(activity, id, extension, true);
                        activity.findViewById(R.id.navigation).postInvalidate();
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        Settings.INSTANCE.getInstance(activity.getApplication()).edit().putBoolean(key, false).apply();
                        for (int i=0; i<extraTitles.size(); i++)
                            Settings.INSTANCE.getInstance(activity.getApplication()).edit().putBoolean(extraKeys.get(i), false).apply();
                    }
                })
                .show();
    }

    public int getExtensionId(String packageName) {
        if (mExtensions == null || mExtensions.isEmpty())
            return 0;
        for (int i=0; i<mExtensions.size(); i++)
            if (mExtensions.get(i).componentName().getPackageName().equals(packageName))
                return i;
        return 0;
    }

    private boolean isAndroidAutoInstalled(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        try {
            packageManager.getPackageInfo("com.google.android.projection.gearhead", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
