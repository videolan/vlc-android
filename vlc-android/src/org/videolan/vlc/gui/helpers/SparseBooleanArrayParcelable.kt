package org.videolan.vlc.gui.helpers

import android.os.Parcelable
import android.util.SparseBooleanArray
import kotlinx.android.parcel.Parcelize

@Parcelize
class SparseBooleanArrayParcelable(val data: SparseBooleanArray) : Parcelable