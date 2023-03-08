package org.videolan.vlc.gui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.vlc.R
import org.videolan.vlc.databinding.AboutAuthorsActivityBinding
import org.videolan.vlc.databinding.AboutAuthorsItemBinding
import org.videolan.vlc.gui.helpers.SelectorViewHolder

/**
 * Activity showing the different libraries used by VLC for Android and their licenses
 */
class AuthorsActivity : BaseActivity() {

    internal lateinit var binding: AboutAuthorsActivityBinding
    override fun getSnackAnchorView(overAudioPlayer:Boolean) = binding.root
    override val displayTitle = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.about_authors_activity)
        val toolbar = findViewById<MaterialToolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_up)
        title = getString(R.string.authors)

        binding.authorsList.layoutManager = LinearLayoutManager(this)
        loadAuthors()
        if (AndroidDevices.isTv) {
            applyOverscanMargin(this)
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home -> finish()
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Load the authors list from the json file in assets and then populate the list
     */
    private fun loadAuthors() {
        lifecycleScope.launchWhenStarted {
            val authors = withContext(Dispatchers.IO) {
                val jsonData = AppContextProvider.appResources.openRawResource(R.raw.authors).bufferedReader().use {
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

class AuthorsAdapter(val authors: List<String>) : DiffUtilAdapter<String, AuthorsAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(AboutAuthorsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.author = authors[position]
    }

    override fun getItemCount() = authors.size

    inner class ViewHolder(vdb: AboutAuthorsItemBinding) : SelectorViewHolder<AboutAuthorsItemBinding>(vdb)
}


