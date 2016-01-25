/*
 * *************************************************************************
 *  pluginListing.java
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

package org.videolan.vlc.extensions;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;

public class ExtensionListing implements Parcelable {

    public static final int PARCELABLE_VERSION = 1;

    private ComponentName mComponentName;
    private int mProtocolVersion;
    private boolean mCompatible;
    private int menuIcon = 0;
    private String mTitle;
    private String mDescription;
    private ComponentName mSettingsActivity;

    private ExtensionManagerService.Connection connection;

    public ExtensionListing(){}

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Returns the full qualified component name of the extension.
     */
    public ComponentName componentName() {
        return mComponentName;
    }

    /**
     * Sets the full qualified component name of the extension.
     */
    public ExtensionListing componentName(ComponentName componentName) {
        mComponentName = componentName;
        return this;
    }

    /**
     * Returns the version of the {@link org.videolan.vlc.extensions.api.VLCExtensionService}
     * protocol used by the extension.
     */
    public int protocolVersion() {
        return mProtocolVersion;
    }

    /**
     * Sets the resId of the icon displayed in VLC menu for this extension.
     */
    public ExtensionListing menuIcon(int menuIcon) {
        this.menuIcon = menuIcon;
        return this;
    }

    /**
     * Returns the resId of the icon displayed in VLC menu for this extension.
     */
    public int menuIcon() {
        return menuIcon;
    }

    /**
     * Sets the version of the {@link org.videolan.vlc.extensions.api.VLCExtensionService}
     * protocol used by the extension.
     */
    public ExtensionListing protocolVersion(int protocolVersion) {
        mProtocolVersion = protocolVersion;
        return this;
    }

    /**
     * Returns whether this extension is compatible to the host application; that is whether
     * the version of the {@link org.videolan.vlc.extensions.api.VLCExtensionService}
     * protocol used by the extension matches what is used by the host application.
     */
    public boolean compatible() {
        return mCompatible;
    }

    /**
     * Sets whether this extension is considered compatible to the host application.
     */
    public ExtensionListing compatible(boolean compatible) {
        mCompatible = compatible;
        return this;
    }

    /**
     * Returns the label of the extension.
     */
    public String title() {
        return mTitle;
    }

    /**
     * Sets the label of the extension.
     */
    public ExtensionListing title(String title) {
        mTitle = title;
        return this;
    }

    /**
     * Returns a description of the extension.
     */
    public String description() {
        return mDescription;
    }

    /**
     * Sets a description of the extension.
     */
    public ExtensionListing description(String description) {
        mDescription = description;
        return this;
    }

    /**
     * Returns the full qualified component name of the settings class to configure
     * the extension.
     */
    public ComponentName settingsActivity() {
        return mSettingsActivity;
    }

    /**
     * Sets the full qualified component name of the settings class to configure
     * the extension.
     */
    public ExtensionListing settingsActivity(ComponentName settingsActivity) {
        this.mSettingsActivity = settingsActivity;
        return this;
    }

    public ExtensionManagerService.Connection getConnection() {
        return connection;
    }

    public void setConnection(ExtensionManagerService.Connection connection) {
        this.connection = connection;
    }

    /**
     * @see android.os.Parcelable
     */
    public static final Creator<ExtensionListing> CREATOR
            = new Creator<ExtensionListing>() {
        public ExtensionListing createFromParcel(Parcel in) {
            return new ExtensionListing(in);
        }

        public ExtensionListing[] newArray(int size) {
            return new ExtensionListing[size];
        }
    };

    private ExtensionListing(Parcel in) {
        int parcelableVersion = in.readInt();

        // Version 1 below
        if (parcelableVersion >= 1) {
            mComponentName = ComponentName.readFromParcel(in);
            mProtocolVersion = in.readInt();
            mCompatible = in.readInt() == 1;
            mTitle = in.readString();
            mDescription = in.readString();
            boolean hasSettings = in.readInt() == 1;
            menuIcon = in.readInt();
            if (hasSettings) {
                mSettingsActivity = ComponentName.readFromParcel(in);
            }
        }
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        /**
         * NOTE: When adding fields in the process of updating this API, make sure to bump
         * {@link #PARCELABLE_VERSION}.
         */
        parcel.writeInt(PARCELABLE_VERSION);

        // Version 1 below
        mComponentName.writeToParcel(parcel, 0);
        parcel.writeInt(mProtocolVersion);
        parcel.writeInt(mCompatible ? 1 : 0);
        parcel.writeString(mTitle);
        parcel.writeString(mDescription);
        parcel.writeInt(mSettingsActivity != null ? 1 : 0);
        parcel.writeInt(menuIcon);
        if (mSettingsActivity != null) {
            mSettingsActivity.writeToParcel(parcel, 0);
        }
    }
}
