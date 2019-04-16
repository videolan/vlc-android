/*
 * *************************************************************************
 *  FilePickerActivity.java
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

package org.videolan.vlc.gui.browser

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.videolan.vlc.R

class FilePickerActivity : AppCompatActivity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.file_picker_activity)
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.fragment_placeholder, FilePickerFragment(), "picker")
        ft.commit()
    }

    override fun onBackPressed() {
        val fpf = supportFragmentManager.findFragmentById(R.id.fragment_placeholder) as FilePickerFragment
        when {
            fpf.isRootDirectory -> finish()
            supportFragmentManager.backStackEntryCount > 0 -> super.onBackPressed()
            else -> fpf.browseUp()
        }
    }

    fun onHomeClick(v: View) {
        (supportFragmentManager.findFragmentById(R.id.fragment_placeholder) as FilePickerFragment).browseRoot()
    }

    companion object {
        private val TAG = "VLC/BaseBrowserFragment"
    }
}
