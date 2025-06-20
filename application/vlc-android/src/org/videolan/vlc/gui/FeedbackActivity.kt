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

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.CRASH_HAPPENED
import org.videolan.resources.CRASH_ML_CTX
import org.videolan.resources.CRASH_ML_MSG
import org.videolan.resources.TV_PREFERENCE_ACTIVITY
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.tools.Settings
import org.videolan.tools.isVisible
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.DebugLogService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.AboutFeedbackActivityBinding
import org.videolan.vlc.gui.helpers.FeedbackUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.preferences.EXTRA_PREF_END_POINT
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.openLinkIfPossible
import java.io.File
import java.io.IOException

/**
 * Activity showing the different ways to report some feedback
 */
class FeedbackActivity : BaseActivity(), DebugLogService.Client.Callback {

    internal lateinit var binding: AboutFeedbackActivityBinding
    override fun getSnackAnchorView(overAudioPlayer: Boolean) = binding.root
    override val displayTitle = true
    private lateinit var feedbackTypeEntries: Array<CharSequence>
    private var mlErrorMessage: String? = null
    private var mlErrorContext: String? = null

    //logs
    private var logMessage = ""
    private lateinit var client: DebugLogService.Client
    private lateinit var logcatZipPath: String
    private var snackbarLogs: Snackbar? = null

    override fun onStarted(logList: List<String>) {
        binding.emailSupportSend.isEnabled = false
        snackbarLogs = UiTools.snackerMessageInfinite(this, getString(R.string.generating_logs))
        snackbarLogs?.show()
        logMessage = "Starting collecting logs at ${System.currentTimeMillis()}"
        //initiate a log to wait for
        Log.d("FeedbackActivity", logMessage)
    }

    override fun onStopped() {
    }

    override fun onLog(msg: String) {
        //Wait for the log to initiate a save to avoid ANR
        if (msg.contains(logMessage)) {
            if (AndroidUtil.isOOrLater && !Permissions.canWriteStorage())
                Permissions.askWriteStoragePermission(this, false) { client.save() }
            else
                client.save()
        }
    }

    override fun onSaved(success: Boolean, path: String) {
        if (!success) {
            Snackbar.make(window.decorView, R.string.dump_logcat_failure, Snackbar.LENGTH_LONG).show()
            client.stop()
            return
        }
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(Dispatchers.IO) {
                client.stop()
                snackbarLogs?.dismiss()
                if (!::logcatZipPath.isInitialized) {
                    val externalPath = AppContextProvider.appContext.getExternalFilesDir(null)?.absolutePath
                        ?: return@withContext
                    logcatZipPath = "$externalPath/logcat.zip"
                }
                val filesToAdd = mutableListOf(path)
                //add previous crash logs
                try {
                    AppContextProvider.appContext.getExternalFilesDir(null)?.absolutePath?.let { folder ->
                        File(folder).listFiles()?.forEach {
                            if (it.isFile && (it.name.contains("crash_") || it.name.contains("logcat_"))) filesToAdd.add(it.path)
                        }
                    }
                } catch (exception: IOException) {
                    Snackbar.make(window.decorView, R.string.dump_logcat_failure, Snackbar.LENGTH_LONG).show()
                    client.stop()
                    return@withContext
                }

                if (!FileUtils.zip(filesToAdd.toTypedArray(), logcatZipPath)) {
                    Snackbar.make(window.decorView, R.string.dump_logcat_failure, Snackbar.LENGTH_LONG).show()
                    client.stop()
                    return@withContext
                }
                try {
                    filesToAdd.forEach { FileUtils.deleteFile(it) }
                } catch (exception: IOException) {
                    Snackbar.make(window.decorView, R.string.dump_logcat_failure, Snackbar.LENGTH_LONG).show()
                    client.stop()
                    return@withContext
                }

                sendEmail(true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        client = DebugLogService.Client(this, this)
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
        binding.feedbackTypeEntry.setOnClickListener {
            binding.feedbackTypeEntry.showDropDown()
        }
        binding.feedbackTypeEntry.setText(feedbackTypeEntries[0], false)
        binding.emailWarningExplanation.text = getString(R.string.feedback_email_warning_explanation, getString(R.string.remote_access), getString(R.string.send_feedback))
        binding.tryAnyway.setOnClickListener {
            binding.emailWarning.setGone()
            switchFormVisibility()
            updateFormIncludesVisibility()
        }
        binding.openSettings.setOnClickListener {
            lifecycleScope.launch {
                if (Settings.tvUI) {
                    val intent = Intent(Intent.ACTION_VIEW).setClassName(this@FeedbackActivity, TV_PREFERENCE_ACTIVITY)
                    intent.putExtra(EXTRA_PREF_END_POINT, "remote_access_category")
                    startActivity(intent)
                }
                else
                    PreferencesActivity.launchWithPref(this@FeedbackActivity, "enable_remote_access")
            }
        }
        binding.emailSupportCard.setOnClickListener {
            if (!isMailClientPresent()) {
                switchNoEmailVisibility()
            } else {
                switchFormVisibility()
                updateFormIncludesVisibility()
            }

        }
        binding.feedbackForumCard.setOnClickListener {
            openLinkIfPossible(getString(R.string.forum_url))
        }
        binding.readDocCard.setOnClickListener {
            openLinkIfPossible(getString(R.string.doc_url))
        }
        binding.emailSupportSend.setOnClickListener {
            if (binding.includeLogs.isChecked) {
                client.start()
            } else
                sendEmail()
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

        // a ML crash happened
        mlErrorMessage = intent.extras?.getString(CRASH_ML_MSG)
        mlErrorContext = intent.extras?.getString(CRASH_ML_CTX)
        val isCrashFromML = !mlErrorContext.isNullOrEmpty() || !mlErrorMessage.isNullOrEmpty()

        // a crash happened
        if (intent.extras?.getBoolean(CRASH_HAPPENED) == true) {
            binding.feedbackForumCard.setGone()
            binding.readDocCard.setGone()
            binding.rateCard.setGone()
            binding.emailSupportCard.performClick()
            binding.feedbackTypeEntry.setText(feedbackTypeEntries[3])
            binding.messageTextInputLayout.setHint(R.string.describe_crash)
            if (isCrashFromML) UiTools.snackerMessageInfinite(this, getString(R.string.ml_crash_send))?.show()
        }
    }

    private fun switchFormVisibility(forceHide: Boolean = false) {
        if (forceHide || binding.emailSupportForm.isVisible()) {
            binding.emailSupportForm.setGone()
            UiTools.setKeyboardVisibility(binding.messageTextInputLayout, false)
            binding.emailSupportCard.nextFocusDownId = R.id.read_doc_card
            binding.emailSupportCard.nextFocusRightId = R.id.read_doc_card
        } else {
            binding.emailSupportForm.setVisible()
            binding.emailSupportCard.nextFocusDownId = R.id.feedback_type_entry
            binding.emailSupportCard.nextFocusRightId = R.id.feedback_type_entry
        }
    }

    private fun switchNoEmailVisibility() {
        switchFormVisibility(true)
        if (!binding.emailWarning.isVisible()) {
            binding.emailWarning.setVisible()
            binding.emailSupportCard.nextFocusDownId = R.id.open_settings
            binding.emailSupportCard.nextFocusRightId = R.id.open_settings
        } else {
            binding.emailWarning.setGone()
            binding.emailSupportCard.nextFocusDownId = R.id.read_doc_card
            binding.emailSupportCard.nextFocusRightId = R.id.read_doc_card
        }
    }

    fun isMailClientPresent(): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        val unsupportedActions = arrayOf("com.android.tv.frameworkpackagestubs", "com.google.android.tv.frameworkpackagestubs", "com.android.fallback")
        val resolved = try {
            intent.resolveActivity(packageManager)
        } catch (e: Exception) {
            return false
        }
        return resolved != null && resolved.packageName !in unsupportedActions
    }

    private fun sendEmail(includeLogs: Boolean = false) {
        val feedbackTypePosition = feedbackTypeEntries.indexOf(binding.feedbackTypeEntry.text.toString())
        val isCrashFromML = !mlErrorContext.isNullOrEmpty() || !mlErrorMessage.isNullOrEmpty()
        val mail = if (BuildConfig.BETA && feedbackTypePosition > 2) FeedbackUtil.SupportType.CRASH_REPORT_EMAIL else FeedbackUtil.SupportType.SUPPORT_EMAIL
        lifecycleScope.launch {
            val message = if (isCrashFromML)
                buildString {
                    append(binding.messageTextInputLayout.editText?.text.toString())
                    append("<br /><br />")
                    append("____________________________<br />")
                    append("ML Crash!<br />")
                    append("____________________________<br />")
                    append("ML Context: $mlErrorContext<br />ML error message: $mlErrorMessage")
                }
            else binding.messageTextInputLayout.editText?.text.toString()
            if (!FeedbackUtil.sendEmail(
                this@FeedbackActivity,
                mail,
                binding.showIncludes && binding.includeMedialibrary.isChecked,
                message,
                binding.subjectTextInputLayout.editText?.text.toString(),
                if (isCrashFromML) 100 else feedbackTypePosition,
                if (includeLogs) logcatZipPath else null
            )) {
                UiTools.snacker(this@FeedbackActivity, R.string.feedback_email_warning)
                switchNoEmailVisibility()
            } else
                finish()
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

    override fun onDestroy() {
        job?.complete()
        job = null
        if (::client.isInitialized) client.release()
        super.onDestroy()
    }

    companion object {
        var job: CompletableJob? = null
    }

}


