/**
 * **************************************************************************
 * PickTimeFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.withContext
import org.videolan.resources.AppContextProvider
import org.videolan.vlc.databinding.DialogAboutAuthorsBinding
import org.videolan.vlc.databinding.DialogAboutAuthorsItemBinding
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.helpers.SelectorViewHolder

/**
 * Dialog showing the authors list of VLC for Android
 */
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class AboutAuthorsDialog : VLCBottomSheetDialogFragment() {

    private lateinit var binding: DialogAboutAuthorsBinding

    companion object {

        fun newInstance(): AboutAuthorsDialog {
            return AboutAuthorsDialog()
        }
    }

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun needToManageOrientation(): Boolean {
        return false
    }

    override fun initialFocusedView(): View = binding.authorsList

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        binding = DialogAboutAuthorsBinding.inflate(layoutInflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.authorsList.layoutManager = LinearLayoutManager(requireActivity())
        loadAuthors()
    }

    /**
     * Load the authors list from the json file in assets and then populate the list
     */
    private fun loadAuthors() {
        lifecycleScope.launchWhenStarted {
            val authors = withContext(Dispatchers.IO) {
                val jsonData = AppContextProvider.appContext.assets.open("authors.json").bufferedReader().use {
                    it.readText()
                }

                val moshi = Moshi.Builder().build()
                val type = Types.newParameterizedType(MutableList::class.java, String::class.java)

                val jsonAdapter: JsonAdapter<List<String>> = moshi.adapter(type)

                jsonAdapter.fromJson(jsonData)!!

            }
            binding.authorsList.adapter = AuthorsAdapter(authors)
        }
    }


}

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class AuthorsAdapter(val authors: List<String>) : DiffUtilAdapter<String, AuthorsAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(DialogAboutAuthorsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.author = authors[position]
    }

    override fun getItemCount() = authors.size

    inner class ViewHolder(vdb: DialogAboutAuthorsItemBinding) : SelectorViewHolder<DialogAboutAuthorsItemBinding>(vdb)
}





