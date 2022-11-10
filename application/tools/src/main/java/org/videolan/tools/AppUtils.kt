/*
 * ************************************************************************
 *  AppUtils.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */
package org.videolan.tools

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

@Suppress("DEPRECATION")
object AppUtils {

    fun totalMemory(): Long {
        val statFs = StatFs(Environment.getRootDirectory().absolutePath)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            statFs.blockCountLong * statFs.blockSizeLong
        else (statFs.blockCount * statFs.blockSize).toLong()
    }

    fun freeMemory(): Long {
        val statFs = StatFs(Environment.getRootDirectory().absolutePath)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            statFs.availableBlocksLong * statFs.blockSizeLong
        else (statFs.availableBlocks * statFs.blockSize).toLong()
    }
}