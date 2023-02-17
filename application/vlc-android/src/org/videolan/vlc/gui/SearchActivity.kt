package org.videolan.vlc.gui

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.SearchAggregate
import org.videolan.resources.util.getFromMl
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.databinding.SearchActivityBinding
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.applyTheme
import org.videolan.vlc.media.MediaUtils

open class SearchActivity : BaseActivity(), TextWatcher, TextView.OnEditorActionListener {

    private lateinit var medialibrary: Medialibrary
    private lateinit var binding: SearchActivityBinding
    private val clickHandler = ClickHandler()
    override fun getSnackAnchorView(overAudioPlayer:Boolean): View? = findViewById(android.R.id.content)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        val intent = intent
        binding = DataBindingUtil.setContentView(this, R.layout.search_activity)
        binding.handler = clickHandler
        binding.searchAggregate = SearchAggregate()
        medialibrary = Medialibrary.getInstance()
        if (Intent.ACTION_SEARCH == intent.action || "com.google.android.gms.actions.SEARCH_ACTION" == intent.action) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            initializeLists()
            if (!query.isNullOrEmpty()) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
                binding.searchEditText.setText(query)
                binding.searchEditText.setSelection(query.length)
                performSearh(query)
            }
        }
        binding.searchEditText.addTextChangedListener(this)
        binding.searchEditText.setOnEditorActionListener(this)
    }

    private fun performSearh(query: String?) {
        if (query != null && query.isNotEmpty()) lifecycleScope.launchWhenStarted {
            val searchAggregate = getFromMl { search(query, Settings.includeMissing, false) }
            binding.searchAggregate = searchAggregate
            searchAggregate?.let { result ->
                result.albums?.filterNotNull()?.let { (binding.albumsResults.adapter as SearchResultAdapter).add(it.toTypedArray()) }
                result.artists?.filterNotNull()?.let { (binding.artistsResults.adapter as SearchResultAdapter).add(it.toTypedArray()) }
                result.genres?.filterNotNull()?.let { (binding.genresResults.adapter as SearchResultAdapter).add(it.toTypedArray()) }
                result.playlists?.filterNotNull()?.let { (binding.playlistsResults.adapter as SearchResultAdapter).add(it.toTypedArray()) }
                result.videos?.filterNotNull()?.let { (binding.othersResults.adapter as SearchResultAdapter).add(it.toTypedArray()) }
                result.tracks?.filterNotNull()?.let { (binding.songsResults.adapter as SearchResultAdapter).add(it.toTypedArray()) }
            }
        }
    }

    private fun initializeLists() {
        val count = binding.resultsContainer.childCount
        for (i in 0 until count) {
            val v = binding.resultsContainer.getChildAt(i)
            if (v is RecyclerView) {
                v.adapter = SearchResultAdapter(layoutInflater)
                v.layoutManager = LinearLayoutManager(this)
                (v.adapter as SearchResultAdapter).setClickHandler(clickHandler)
            }
        }
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (s == null || s.isEmpty())
            binding.searchAggregate = SearchAggregate()
        else
            performSearh(s.toString())
    }

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            UiTools.setKeyboardVisibility(binding.root, false)
            return true
        }
        return false
    }

    inner class ClickHandler {

        fun onBack(@Suppress("UNUSED_PARAMETER") v: View) {
            finish()
        }

        fun onItemClick(item: MediaLibraryItem) {
            MediaUtils.playTracks(this@SearchActivity, item, 0)
            finish()
        }
    }

    companion object {

        const val TAG = "VLC/SearchActivity"
    }
}
