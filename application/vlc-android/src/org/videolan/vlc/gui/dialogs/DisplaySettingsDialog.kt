/*
 * ************************************************************************
 *  DisplaySettingsDialog.kt
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

package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AppContextProvider
import org.videolan.resources.GROUP_VIDEOS_FOLDER
import org.videolan.resources.GROUP_VIDEOS_NAME
import org.videolan.resources.GROUP_VIDEOS_NONE
import org.videolan.tools.setGone
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogDisplaySettingsBinding
import org.videolan.vlc.gui.helpers.UiTools.showPinIfNeeded
import org.videolan.vlc.viewmodels.DisplaySettingsViewModel
import org.videolan.vlc.viewmodels.mobile.VideoGroupingType
import org.videolan.vlc.viewmodels.mobile.VideosViewModel


const val DISPLAY_IN_CARDS = "display_in_cards"
const val SHOW_ALL_ARTISTS = "show_all_artists"
const val VIDEO_GROUPING = "show_video_groups"
const val ONLY_FAVS = "only_favs"
const val SORTS = "sorts"
const val CURRENT_SORT = "current_sort"
const val CURRENT_SORT_DESC = "current_sort_desc"

/**
 * Dialog showing the display settings for a media list (audio video)
 */
class DisplaySettingsDialog : VLCBottomSheetDialogFragment() {

    //current values
    private var displayInCards: Boolean = false
    private var onlyFavs: Boolean = false
    private lateinit var sorts: ArrayList<Int>
    private var currentSort: Int = -1
    private var currentSortDesc = false
    private var showAllArtists: Boolean? = null
    private var showVideoGroups: String? = null

    private lateinit var binding: DialogDisplaySettingsBinding

    private val displaySettingsViewModel: DisplaySettingsViewModel by activityViewModels()

    companion object {

        fun newInstance(displayInCards: Boolean, showAllArtists: Boolean? = null, onlyFavs: Boolean, sorts: List<Int>, currentSort: Int, currentSortDesc:Boolean, videoGroup:String? = null): DisplaySettingsDialog {
            return DisplaySettingsDialog().apply {
                arguments = bundleOf(DISPLAY_IN_CARDS to displayInCards, ONLY_FAVS to onlyFavs, SORTS to sorts, CURRENT_SORT to currentSort, CURRENT_SORT_DESC to currentSortDesc, VIDEO_GROUPING to videoGroup)
                if (showAllArtists != null) arguments!!.putBoolean(SHOW_ALL_ARTISTS, showAllArtists)
            }
        }
    }

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun needToManageOrientation(): Boolean {
        return false
    }

    override fun initialFocusedView(): View = binding.title

    override fun onCreate(savedInstanceState: Bundle?) {
        lifecycleScope.launch { if (requireActivity().showPinIfNeeded()) dismiss() }
        super.onCreate(savedInstanceState)
        displayInCards = arguments?.getBoolean(DISPLAY_IN_CARDS)
                ?: throw IllegalStateException("Display in list should be provided")
        onlyFavs = arguments?.getBoolean(ONLY_FAVS)
                ?: throw IllegalStateException("Only favs should be provided")
        sorts = arguments?.getIntegerArrayList(SORTS)
                ?: throw IllegalStateException("Sorts should be provided")
        currentSort = arguments?.getInt(CURRENT_SORT)
                ?: throw IllegalStateException("Current sort should be provided")
        currentSortDesc = arguments?.getBoolean(CURRENT_SORT_DESC)
                ?: throw IllegalStateException("Current sort desc should be provided")
        showAllArtists = if (arguments?.containsKey(SHOW_ALL_ARTISTS) == true) arguments?.getBoolean(SHOW_ALL_ARTISTS) else null
        showVideoGroups = arguments?.getString(VIDEO_GROUPING, null)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogDisplaySettingsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateDisplayMode()
        updateShowAllArtists()
        updateShowOnlyFavs()
        updateSorts()

        binding.displayModeGroup.setOnClickListener {
            displayInCards = !displayInCards
            updateDisplayMode()
            lifecycleScope.launch { displaySettingsViewModel.send(DISPLAY_IN_CARDS, displayInCards) }
        }
        binding.showAllArtistGroup.setOnClickListener {
            binding.showAllArtistCheckbox.isChecked = !binding.showAllArtistCheckbox.isChecked
        }
        binding.showAllArtistCheckbox.setOnCheckedChangeListener { _, isChecked ->
            showAllArtists = isChecked
            updateShowAllArtists()
            lifecycleScope.launch { displaySettingsViewModel.send(SHOW_ALL_ARTISTS, showAllArtists!!) }
        }

        binding.onlyFavsGroup.setOnClickListener {
            binding.onlyFavsCheckbox.isChecked = !binding.onlyFavsCheckbox.isChecked
        }

        binding.onlyFavsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            onlyFavs = isChecked
            updateShowAllArtists()
            lifecycleScope.launch { displaySettingsViewModel.send(ONLY_FAVS, onlyFavs) }
        }
        if (showVideoGroups == null) {
            binding.videoGroupsGroup.setGone()
            binding.videoGroupSpinner.setGone()
            binding.videoGroupText.setGone()
        }
        val spinnerArrayAdapter = ArrayAdapter(requireActivity(), android.R.layout.simple_spinner_item, VideoGroup.values())

        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.videoGroupSpinner.adapter = spinnerArrayAdapter
        binding.videoGroupsGroup.setOnClickListener {
            binding.videoGroupSpinner.performClick()
        }
        binding.videoGroupSpinner.setSelection(VideoGroup.values().indexOf(VideoGroup.findByValue(showVideoGroups)))
        binding.videoGroupSpinner.onItemSelectedListener = object:OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val groupType = spinnerArrayAdapter.getItem(position) as VideoGroup
                if (groupType.value != showVideoGroups) {
                    lifecycleScope.launch { displaySettingsViewModel.send(VIDEO_GROUPING, groupType) }
                    //dismissing as changing grouping will also change the available sorts
                    dismiss()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }

    }

    /**
     * Update the view for the "display in list / grid" item
     *
     */
    private fun updateDisplayMode() {
        binding.displayInListText.text = getString(if (!displayInCards) R.string.display_in_grid else R.string.display_in_list)
        binding.displayInListImage.setImageDrawable(ContextCompat.getDrawable(requireActivity(), if (!displayInCards) R.drawable.ic_view_grid else R.drawable.ic_view_list))
    }

    /**
     * Update the view for the "show all artists" item
     *
     */
    private fun updateShowAllArtists() {
        if (showAllArtists == null) {
            binding.showAllArtistGroup.setGone()
            return
        }
        binding.showAllArtistCheckbox.isChecked = showAllArtists!!
    }

    /**
     * Update the view for the "show only favorites" item
     *
     */
    private fun updateShowOnlyFavs() {
        binding.onlyFavsCheckbox.isChecked = onlyFavs
    }

    /**
     * Update the views for the sort items
     *
     */
    private fun updateSorts() {
        //first time: create all the views
        if (binding.sortsContainer.childCount == 0)
            sorts.forEach { sort ->

                //one view for asc and one for desc
                arrayOf(false, true).forEach {desc ->
                    val v = View.inflate(requireActivity(), R.layout.sort_display_setting, null)
                    val tv = v.findViewById<TextView>(R.id.sort_title)
                    val container = v.findViewById<View>(R.id.sort_display_setting)
                    v.setTag(R.id.sort, sort)
                    v.setTag(R.id.sort_desc, desc)

                    container.setOnClickListener {
                        currentSort = sort
                        currentSortDesc = desc

                        updateSorts()
                        lifecycleScope.launch { displaySettingsViewModel.send(CURRENT_SORT, Pair(sort, desc)) }
                    }
                    tv.text = when (sort) {
                        Medialibrary.SORT_ALPHA -> getString(if (desc) R.string.sortby_name_desc else R.string.sortby_name_asc)
                        Medialibrary.SORT_FILENAME -> getString(if (desc) R.string.sortby_filename_desc else R.string.sortby_filename_asc)
                        Medialibrary.SORT_ARTIST -> getString(if (desc) R.string.sortby_artist_name_desc else R.string.sortby_artist_name_asc)
                        Medialibrary.SORT_DURATION -> getString(if (desc) R.string.sortby_length_desc else R.string.sortby_length_asc)
                        Medialibrary.SORT_INSERTIONDATE -> getString(if (desc) R.string.sortby_date_insertion_desc else R.string.sortby_date_insertion_asc)
                        Medialibrary.SORT_LASTMODIFICATIONDATE -> getString(if (desc) R.string.sortby_date_last_modified_desc else R.string.sortby_date_last_modified_asc)
                        Medialibrary.SORT_ALBUM -> getString(if (desc) R.string.sortby_album_name_desc else R.string.sortby_album_name_asc)
                        Medialibrary.SORT_RELEASEDATE -> getString(if (desc) R.string.sortby_date_release_desc else R.string.sortby_date_release_asc)
                        Medialibrary.NbMedia -> getString(if (desc) R.string.sortby_number_asc else R.string.sortby_number_desc)
                        else -> throw IllegalStateException("Unsupported sort: $sort")
                    }
                    val isCurrentSort = (sort == currentSort || currentSort == Medialibrary.SORT_DEFAULT && sort == Medialibrary.SORT_ALPHA) && currentSortDesc == desc
                    v.isSelected = isCurrentSort

                    binding.sortsContainer.addView(v)
                }
            }
        else {
            //views are already added. Update their states
            binding.sortsContainer.children.forEach {
                it.isSelected = it.getTag(R.id.sort) == currentSort &&  it.getTag(R.id.sort_desc) ==  currentSortDesc
            }
        }
    }

    /**
     * Video grouping entry
     *
     * @property value the value to be saved in the shared preferences
     * @property title the title resources to be shown
     * @property type the [VideosViewModel] type for this grouping
     */
    enum class VideoGroup(val value: String, val title:Int, val type:VideoGroupingType) {
        GROUP_BY_NAME(GROUP_VIDEOS_NAME, R.string.video_min_group_length_name, VideoGroupingType.NAME),
        GROUP_BY_FOLDER(GROUP_VIDEOS_FOLDER, R.string.video_min_group_length_folder, VideoGroupingType.FOLDER),
        NO_GROUP(GROUP_VIDEOS_NONE, R.string.video_min_group_length_disable, VideoGroupingType.NONE);

        override fun toString(): String {
            return AppContextProvider.appContext.getString(title)
        }

        companion object {
            /**
             * Retrieve a [VideoGroup] by its value
             *
             * @param value of the video group to retrieve
             * @return a [VideoGroup]
             */
            fun findByValue(value: String?): VideoGroup {
                values().forEach { if (value == it.value) return it }
                return GROUP_BY_NAME
            }
        }
    }
}





