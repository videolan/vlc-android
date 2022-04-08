package org.videolan.vlc.gui.helpers

import android.os.Parcelable
import android.util.SparseBooleanArray
import kotlinx.parcelize.Parcelize

@Parcelize
class SparseBooleanArrayParcelable(val data: SparseBooleanArray) : Parcelable