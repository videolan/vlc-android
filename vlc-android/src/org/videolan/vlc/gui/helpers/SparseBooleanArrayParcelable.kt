package org.videolan.vlc.gui.helpers

import android.os.Parcel
import android.os.Parcelable
import android.util.SparseBooleanArray

class SparseBooleanArrayParcelable : SparseBooleanArray, Parcelable {

    constructor()

    constructor(sparseBooleanArray: SparseBooleanArray) {
        for (i in 0 until sparseBooleanArray.size()) {
            this.put(sparseBooleanArray.keyAt(i), sparseBooleanArray.valueAt(i))
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        val keys = IntArray(size())
        val values = BooleanArray(size())

        for (i in 0 until size()) {
            keys[i] = keyAt(i)
            values[i] = valueAt(i)
        }

        dest.writeInt(size())
        dest.writeIntArray(keys)
        dest.writeBooleanArray(values)
    }

    companion object {
        @JvmField
        var CREATOR: Parcelable.Creator<SparseBooleanArrayParcelable> = object : Parcelable.Creator<SparseBooleanArrayParcelable> {
            override fun createFromParcel(source: Parcel): SparseBooleanArrayParcelable {
                val read = SparseBooleanArrayParcelable()
                val size = source.readInt()

                val keys = IntArray(size)
                val values = BooleanArray(size)

                source.readIntArray(keys)
                source.readBooleanArray(values)

                for (i in 0 until size) {
                    read.put(keys[i], values[i])
                }

                return read
            }

            override fun newArray(size: Int): Array<SparseBooleanArrayParcelable?> {
                return arrayOfNulls(size)
            }
        }
    }
}