/*
 * ************************************************************************
 *  FeedbackActivity.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.vlc.gui

import android.os.Bundle
import android.view.MenuItem
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import org.videolan.resources.AndroidDevices
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.tools.isVisible
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.AboutFeedbackActivityBinding
import org.videolan.vlc.gui.helpers.FeedbackUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.openLinkIfPossible

/**
 * Activity showing the different ways to report some feedback
 */
class FeedbackActivity : BaseActivity() {

    internal lateinit var binding: AboutFeedbackActivityBinding
    override fun getSnackAnchorView(overAudioPlayer: Boolean) = binding.root
    override val displayTitle = true
    private lateinit var feedbackTypeEntries: Array<CharSequence>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.about_feedback_activity)
        val toolbar = findViewById<MaterialToolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_up)
        title = getString(R.string.send_feedback)

        if (AndroidDevices.isTv) {
            applyOverscanMargin(this)
        }

        binding.feedbackForumSummary.text = TextUtils.separatedString(arrayOf(getString(R.string.send_feedback), getString(R.string.get_help)))
        binding.emailSupportSummary.text = TextUtils.separatedString(arrayOf(getString(R.string.send_feedback), getString(R.string.get_help), getString(R.string.report_a_bug)))
        binding.readDocSummary.text = TextUtils.separatedString(arrayOf(getString(R.string.get_help)))
        feedbackTypeEntries = resources.getTextArray(R.array.feedback_entries)
        binding.showIncludes = false
        binding.feedbackTypeEntry.addTextChangedListener {
            updateFormIncludesVisibility()
        }
        binding.feedbackTypeEntry.setText(feedbackTypeEntries[0], false)
        binding.emailSupportCard.setOnClickListener {
            if (binding.emailSupportForm.isVisible()) {
                binding.emailSupportForm.setGone()
                UiTools.setKeyboardVisibility(binding.messageTextInputLayout, false)
                binding.emailSupportCard.nextFocusDownId = R.id.read_doc_card
                binding.emailSupportCard.nextFocusRightId = R.id.read_doc_card
            } else {
                binding.emailSupportForm.setVisible()
                binding.emailSupportCard.nextFocusDownId = R.id.feedback_type_entry
                binding.emailSupportCard.nextFocusRightId = R.id.feedback_type_entry
            }

            updateFormIncludesVisibility()
        }
        binding.feedbackForumCard.setOnClickListener {
            openLinkIfPossible("https://forum.videolan.org/viewforum.php?f=35")
        }
        binding.readDocCard.setOnClickListener {
            openLinkIfPossible("https://docs.videolan.me/vlc-user/android/")
        }
        binding.emailSupportSend.setOnClickListener {
            val feedbackTypePosition = feedbackTypeEntries.indexOf(binding.feedbackTypeEntry.text.toString())
            val subjectPrepend = when (feedbackTypePosition) {
                0 -> "[Help] "
                1 -> "[Feedback/Request] "
                2 -> "[Bug] "
                else -> "[Crash] "
            }
            val mail = if (BuildConfig.BETA && feedbackTypePosition > 2) FeedbackUtil.SupportType.CRASH_REPORT_EMAIL else FeedbackUtil.SupportType.SUPPORT_EMAIL
            lifecycleScope.launch {
                FeedbackUtil.sendEmail(
                    this@FeedbackActivity,
                    mail,
                    binding.showIncludes && binding.includeMedialibrary.isChecked,
                    binding.messageTextInputLayout.editText?.text.toString(),
                    subjectPrepend + binding.subjectTextInputLayout.editText?.text.toString()
                )
            }
        }
        val installSource = FeedbackUtil.getInstallSource(this)
        if (installSource == null)
            binding.rateCard.setGone()
        else
            binding.rateSummary.text = installSource.second

        binding.rateCard.setOnClickListener {
            installSource?.first?.let {
                openLinkIfPossible(it)
            }
        }

    }

    /**
     * Update the visibility for the form includes section.
     *
     */
    private fun updateFormIncludesVisibility() {
        if (!binding.emailSupportForm.isVisible()) {
            binding.showIncludes = false
            return
        }
        val position = feedbackTypeEntries.indexOf(binding.feedbackTypeEntry.text.toString())
        when (position) {
            0, 1 -> binding.showIncludes = false
            else -> binding.showIncludes = true
        }
        when (position) {
            3 -> {
                binding.includeMedialibrary.isChecked = true
                binding.includeLogs.isChecked = true
            }

            else -> {
                binding.includeMedialibrary.isChecked = false
                binding.includeLogs.isChecked = false
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
        }
        return super.onOptionsItemSelected(item)
    }

}


