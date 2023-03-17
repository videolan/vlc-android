/*
 * ************************************************************************
 *  MiniPlayerConfigureActivity.kt
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

package org.videolan.vlc.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.WidgetMiniPlayerConfigureBinding
import org.videolan.vlc.gui.BaseActivity
import org.videolan.vlc.gui.dialogs.WidgetExplanationDialog
import org.videolan.vlc.gui.helpers.bitmapFromView
import org.videolan.vlc.gui.preferences.widgets.PreferencesWidgets
import org.videolan.vlc.gui.preferences.widgets.WIDGET_ID
import org.videolan.vlc.widget.utils.WidgetCache

class MiniPlayerConfigureActivity : BaseActivity() {

    internal lateinit var model: WidgetViewModel
    override val displayTitle = true
    private lateinit var binding: WidgetMiniPlayerConfigureBinding
    var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    override fun getSnackAnchorView(overAudioPlayer: Boolean) = binding.coordinator

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = WidgetMiniPlayerConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Launching MiniPlayerConfigureActivity")

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)
        val toolbar = findViewById<MaterialToolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        title = getString(R.string.configure_widget)


        // Find the widget id from the intent.
        appWidgetId = intent?.extras?.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID


        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        model = ViewModelProvider(this, WidgetViewModel.Factory(this, appWidgetId))[WidgetViewModel::class.java]

        model.widget.observe(this) { widget ->
            if (widget == null) {
                lifecycleScope.launch(Dispatchers.IO) { model.create(this@MiniPlayerConfigureActivity, appWidgetId) }
                return@observe
            }
            val settings = Settings.getInstance(this@MiniPlayerConfigureActivity)
            settings.putSingle("widget_theme", widget.theme.toString())
            settings.putSingle("opacity", widget.opacity)
            settings.putSingle("background_color", widget.backgroundColor)
            settings.putSingle("foreground_color", widget.foregroundColor)
            updatePreview()
        }

        if (savedInstanceState == null) {
            val preferencesWidgets = PreferencesWidgets().apply {
                arguments = bundleOf(WIDGET_ID to appWidgetId)
            }
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_placeholder, preferencesWidgets)
                    .commit()
        }

        binding.previewPlaying.isChecked = Settings.getInstance(this).getBoolean(WIDGETS_PREVIEW_PLAYING, true)
        binding.previewPlaying.setOnCheckedChangeListener { _, checked ->
            Settings.getInstance(this).putSingle(WIDGETS_PREVIEW_PLAYING, checked)
            updatePreview()
        }

        if (!settings.getBoolean(PREF_WIDGETS_TIPS_SHOWN, false)) {
            val widgetExplanationDialog = WidgetExplanationDialog()
            widgetExplanationDialog.show(supportFragmentManager, "fragment_widget_explanation")
            settings.putSingle(PREF_WIDGETS_TIPS_SHOWN, true)
        }
    }

    private fun updatePreview() {
        model.widget.value?.let {widget ->
            lifecycleScope.launch {
                withContext((Dispatchers.IO)) {
                        val coverBitmap = BitmapFactory.decodeResource(resources, R.drawable.vlc_fake_cover)
                        val palette = Palette.from(coverBitmap).generate()
                    val provider = MiniPlayerAppWidgetProvider()
                    model.widget.value?.widgetId?.let { id ->

                        val width = if (widget.width <= 0 || widget.height <= 0) 276 else widget.width
                        val height = if (widget.width <= 0 || widget.height <= 0) 94 else widget.height
                        val views = provider.layoutWidget(this@MiniPlayerConfigureActivity, id, Intent(MiniPlayerAppWidgetProvider.ACTION_WIDGET_INIT), true, coverBitmap, palette, previewPlaying = binding.previewPlaying.isChecked)
                        val container = FrameLayout(this@MiniPlayerConfigureActivity).apply {
                            layoutParams = LayoutParams(width, height)
                        }
                        val preview = views?.apply(applicationContext, container)
                        preview?.let {
                            (preview.layoutParams as LayoutParams).gravity = Gravity.CENTER
                            container.addView(preview)
                            val bm: Bitmap = bitmapFromView(container, width.dp, height.dp)
                            withContext(Dispatchers.Main) { binding.widgetPreview.setImageBitmap(bm) }
                        }
                    }
                }
            }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.widget_configure_option, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.widget_configure_done -> {
                onWidgetContainerClicked()
                finish()
                return true
            }
            R.id.widget_info -> {
                val widgetExplanationDialog = WidgetExplanationDialog()
                widgetExplanationDialog.show(supportFragmentManager, "fragment_widget_explanation")
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun finish() {
        onWidgetContainerClicked()
        super.finish()
    }

    private fun onWidgetContainerClicked() {
        model.widget.value?.let { WidgetCache.clear(it) }

        //refresh widget
        sendBroadcast(Intent(MiniPlayerAppWidgetProvider.ACTION_WIDGET_INIT).apply {
            component = ComponentName(applicationContext, MiniPlayerAppWidgetProvider::class.java)
        })

        // Make sure we pass back the original appWidgetId
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
    }
}