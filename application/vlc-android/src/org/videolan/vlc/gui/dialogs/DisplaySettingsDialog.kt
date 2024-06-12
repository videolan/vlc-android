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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.databinding.DataBindingUtil
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
import org.videolan.vlc.databinding.SortDisplaySettingBinding
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
const val SHOW_ONLY_MULTIMEDIA_FILES = "show_only_multimedia_files"
const val SHOW_HIDDEN_FILES = "show_hidden_files"

/**
 * Dialog showing the display settings for a media list (audio video)
 */
class DisplaySettingsDialog : VLCBottomSheetDialogFragment() {

    //current values
    private var displayInCards: Boolean = false
    private var onlyFavs: Boolean? = null
    private lateinit var sorts: ArrayList<Int>
    private var currentSort: Int = -1
    private var currentSortDesc = false
    private var showAllArtists: Boolean? = null
    private var showOnlyMultimediaFiles: Boolean? = null
    private var showHiddenFiles: Boolean? = null
    private var showVideoGroups: String? = null

    private lateinit var binding: DialogDisplaySettingsBinding

    private val displaySettingsViewModel: DisplaySettingsViewModel by activityViewModels()

    companion object {

        fun newInstance(displayInCards: Boolean, showAllArtists: Boolean? = null, onlyFavs: Boolean?, sorts: List<Int>, currentSort: Int, currentSortDesc: Boolean, videoGroup: String? = null, showOnlyMultimediaFiles:Boolean? = null, showHiddenFiles:Boolean? = null): DisplaySettingsDialog {
            return DisplaySettingsDialog().apply {
                arguments = bundleOf(DISPLAY_IN_CARDS to displayInCards, SORTS to sorts, CURRENT_SORT to currentSort, CURRENT_SORT_DESC to currentSortDesc, VIDEO_GROUPING to videoGroup)
                if (onlyFavs != null) arguments!!.putBoolean(ONLY_FAVS, onlyFavs)
                if (showAllArtists != null) arguments!!.putBoolean(SHOW_ALL_ARTISTS, showAllArtists)
                if (showOnlyMultimediaFiles != null) arguments!!.putBoolean(SHOW_ONLY_MULTIMEDIA_FILES, showOnlyMultimediaFiles)
                if (showHiddenFiles != null) arguments!!.putBoolean(SHOW_HIDDEN_FILES, showHiddenFiles)
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
        onlyFavs = if (arguments?.containsKey(ONLY_FAVS) == true) arguments?.getBoolean(ONLY_FAVS) else null
        sorts = arguments?.getIntegerArrayList(SORTS)
                ?: throw IllegalStateException("Sorts should be provided")
        currentSort = arguments?.getInt(CURRENT_SORT)
                ?: throw IllegalStateException("Current sort should be provided")
        currentSortDesc = arguments?.getBoolean(CURRENT_SORT_DESC)
                ?: throw IllegalStateException("Current sort desc should be provided")
        showAllArtists = if (arguments?.containsKey(SHOW_ALL_ARTISTS) == true) arguments?.getBoolean(SHOW_ALL_ARTISTS) else null
        showOnlyMultimediaFiles = if (arguments?.containsKey(SHOW_ONLY_MULTIMEDIA_FILES) == true) arguments?.getBoolean(SHOW_ONLY_MULTIMEDIA_FILES) else null
        showHiddenFiles = if (arguments?.containsKey(SHOW_HIDDEN_FILES) == true) arguments?.getBoolean(SHOW_HIDDEN_FILES) else null
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
        updateShowAllFiles()
        updateShowHiddenFiles()
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

        binding.showHiddenFilesGroup.setOnClickListener {
            binding.showHiddenFilesCheckbox.isChecked = !binding.showHiddenFilesCheckbox.isChecked
        }

        binding.showHiddenFilesCheckbox.setOnCheckedChangeListener { _, isChecked ->
            showHiddenFiles = isChecked
            updateShowHiddenFiles()
            lifecycleScope.launch { displaySettingsViewModel.send(SHOW_HIDDEN_FILES, showHiddenFiles!!) }
        }

        binding.showAllFilesGroup.setOnClickListener {
            binding.showAllFilesCheckbox.isChecked = !binding.showAllFilesCheckbox.isChecked
        }

        binding.showAllFilesCheckbox.setOnCheckedChangeListener { _, isChecked ->
            showOnlyMultimediaFiles = isChecked
            updateShowAllFiles()
            lifecycleScope.launch { displaySettingsViewModel.send(SHOW_ONLY_MULTIMEDIA_FILES, showOnlyMultimediaFiles!!) }
        }

        binding.onlyFavsGroup.setOnClickListener {
            binding.onlyFavsCheckbox.isChecked = !binding.onlyFavsCheckbox.isChecked
        }

        if (onlyFavs == null) binding.onlyFavsCheckbox.setGone()
        binding.onlyFavsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            onlyFavs = isChecked
            updateShowAllArtists()
            lifecycleScope.launch { displaySettingsViewModel.send(ONLY_FAVS, onlyFavs!!) }
        }
        if (showVideoGroups == null) {
            binding.videoGroupsGroup.setGone()
            binding.videoGroupSpinner.setGone()
            binding.videoGroupText.setGone()
            binding.videoGroupImage.setGone()
        }
        val spinnerArrayAdapter = ArrayAdapter(requireActivity(), android.R.layout.simple_spinner_item, VideoGroup.values())

        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.videoGroupSpinner.adapter = spinnerArrayAdapter
        binding.videoGroupsGroup.setOnClickListener {
            binding.videoGroupSpinner.performClick()
        }
        binding.videoGroupSpinner.setSelection(VideoGroup.values().indexOf(VideoGroup.findByValue(showVideoGroups)))
        binding.videoGroupSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val groupType = spinnerArrayAdapter.getItem(position) as VideoGroup
                if (groupType.value != showVideoGroups) {
                    lifecycleScope.launch { displaySettingsViewModel.send(VIDEO_GROUPING, groupType) }
                    //dismissing as changing grouping will also change the available sorts
                    dismiss()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
            binding.allArtistsImage.setGone()
            return
        }
        binding.showAllArtistCheckbox.isChecked = showAllArtists!!
    }

    /**
     * Update the view for the "show all files" item
     *
     */
    private fun updateShowAllFiles() {
        if (showOnlyMultimediaFiles == null) {
            binding.showAllFilesGroup.setGone()
            binding.allFilesImage.setGone()
            return
        }
        binding.showAllFilesCheckbox.isChecked = showOnlyMultimediaFiles!!
    }

    /**
     * Update the view for the "show hidden files" item
     *
     */
    private fun updateShowHiddenFiles() {
        if (showHiddenFiles == null) {
            binding.showHiddenFilesGroup.setGone()
            binding.hiddenFilesImage.setGone()
            return
        }
        binding.showHiddenFilesCheckbox.isChecked = showHiddenFiles!!
    }

    /**
     * Update the view for the "show only favorites" item
     *
     */
    private fun updateShowOnlyFavs() {
        if (onlyFavs == null) {
            binding.onlyFavsGroup.setGone()
            binding.onlyFavsImage.setGone()
        } else binding.onlyFavsCheckbox.isChecked = onlyFavs!!
    }

    /**
     * Update the views for the sort items
     *
     */
    private fun updateSorts() {
        //first time: create all the views
        if (binding.sortsContainer.childCount == 0)
            sorts.forEach { sort ->

                val binding: SortDisplaySettingBinding = DataBindingUtil.inflate(LayoutInflater.from(requireActivity()), R.layout.sort_display_setting, binding.sortsContainer, true)
                binding.sortAsc.setTag(R.id.sort, getSortTag(sort, false))
                binding.sortDesc.setTag(R.id.sort, getSortTag(sort, true))

                binding.sortAsc.setOnClickListener {
                    currentSort = sort
                    currentSortDesc = false

                    updateSorts()
                    lifecycleScope.launch { displaySettingsViewModel.send(CURRENT_SORT, Pair(sort, false)) }
                }
                binding.sortDesc.setOnClickListener {
                    currentSort = sort
                    currentSortDesc = true

                    updateSorts()
                    lifecycleScope.launch { displaySettingsViewModel.send(CURRENT_SORT, Pair(sort, true)) }
                }
                val isCurrentSort = (sort == currentSort || currentSort == Medialibrary.SORT_DEFAULT && sort == Medialibrary.SORT_ALPHA)
                when (sort) {
                    Medialibrary.SORT_ALPHA -> setupSortViews(binding, isCurrentSort, R.string.sortby_name, R.string.sort_alpha_asc, R.string.sort_alpha_desc, R.drawable.ic_sort_alpha)
                    Medialibrary.SORT_FILENAME -> setupSortViews(binding, isCurrentSort, R.string.sortby_filename, R.string.sort_alpha_asc, R.string.sort_alpha_desc, R.drawable.ic_sort_filename)
                    Medialibrary.SORT_ARTIST -> setupSortViews(binding, isCurrentSort, R.string.sortby_artist_name, R.string.sort_alpha_asc, R.string.sort_alpha_desc, R.drawable.ic_sort_artist)
                    Medialibrary.SORT_DURATION -> setupSortViews(binding, isCurrentSort, R.string.sortby_length, R.string.sortby_length_asc, R.string.sortby_length_desc, R.drawable.ic_sort_length)
                    Medialibrary.SORT_INSERTIONDATE -> setupSortViews(binding, isCurrentSort, R.string.sortby_date_insertion, R.string.sort_date_asc, R.string.sort_date_desc, R.drawable.ic_medialibrary_date)
                    Medialibrary.SORT_LASTMODIFICATIONDATE -> setupSortViews(binding, isCurrentSort, R.string.sortby_date_last_modified, R.string.sort_date_asc, R.string.sort_date_desc, R.drawable.ic_medialibrary_scan)
                    Medialibrary.SORT_ALBUM -> setupSortViews(binding, isCurrentSort, R.string.sortby_album_name, R.string.sort_alpha_asc, R.string.sort_alpha_desc, R.drawable.ic_sort_album)
                    Medialibrary.SORT_RELEASEDATE -> setupSortViews(binding, isCurrentSort, R.string.sortby_date_release, R.string.sort_date_asc, R.string.sort_date_desc, R.drawable.ic_sort_date)
                    Medialibrary.NbMedia -> setupSortViews(binding, isCurrentSort, R.string.sortby_number, R.string.sortby_number_asc, R.string.sortby_number_desc, R.drawable.ic_sort_number)
                    else -> throw IllegalStateException("Unsupported sort: $sort")
                }

            }
        //views are added. Update their states
        binding.sortsContainer.children.forEach { container ->
            (container as ViewGroup).children.forEach childrenForEach@{
                if (it.getTag(R.id.sort) == null) return@childrenForEach
                val selected = it.getTag(R.id.sort) == getSortTag(currentSort, currentSortDesc) || (currentSort == Medialibrary.SORT_DEFAULT && it.getTag(R.id.sort) == getSortTag(Medialibrary.SORT_ALPHA, currentSortDesc))
                it.isSelected = selected
                (it as TextView).setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, if (selected) ContextCompat.getDrawable(requireActivity(), R.drawable.ic_check_large) else null, null)
            }
        }
    }

    /**
     * Setup the sort view
     *
     * @param binding the binding to use for the views
     * @param isCurrentSort true if this view is the sort type currently used
     * @param titleString the sort title string res
     * @param ascString the sort asc variant string res
     * @param descString the sort desc variant string res
     * @param iconDrawable the sort icon drawable
     */
    private fun setupSortViews(binding: SortDisplaySettingBinding, isCurrentSort: Boolean, @StringRes titleString: Int, @StringRes ascString: Int, @StringRes descString: Int, @DrawableRes iconDrawable: Int) {
        binding.sortTitle.text = getString(titleString)
        binding.sortAsc.text = getString(ascString)
        binding.sortDesc.text = getString(descString)
        binding.sortIcon.setImageDrawable(ContextCompat.getDrawable(requireActivity(), iconDrawable))
        binding.sortAsc.isSelected = isCurrentSort && !currentSortDesc
        binding.sortDesc.isSelected = isCurrentSort && currentSortDesc
    }

    /**
     * Generate the sort tag
     *
     * @param sort the sort type int
     * @param desc is the sort desc
     */
    private fun getSortTag(sort: Int, desc: Boolean) = if (desc) "${sort}_desc" else "${sort}_asc"

    /**
     * Video grouping entry
     *
     * @property value the value to be saved in the shared preferences
     * @property title the title resources to be shown
     * @property type the [VideosViewModel] type for this grouping
     */
    enum class VideoGroup(val value: String, val title: Int, val type: VideoGroupingType) {
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





