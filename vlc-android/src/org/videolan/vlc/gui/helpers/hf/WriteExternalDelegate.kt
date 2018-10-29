package org.videolan.vlc.gui.helpers.hf

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.fragment.app.FragmentActivity
import androidx.documentfile.provider.DocumentFile
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import android.text.TextUtils
import kotlinx.coroutines.channels.Channel
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Settings


class WriteExternalDelegate : BaseHeadlessFragment() {

    @TargetApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showDialog()
    }

    private fun showDialog() {
        if (!isAdded) return
        val builder = AlertDialog.Builder(activity!!)
        builder.setMessage(R.string.sdcard_permission_dialog_message)
                .setTitle(R.string.sdcard_permission_dialog_title)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    arguments?.getString(KEY_STORAGE_PATH)?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(it)) }
                    startActivityForResult(intent, REQUEST_CODE_STORAGE_ACCES)
                }
                .setNeutralButton(getString(R.string.dialog_sd_wizard)) { _, _ -> showHelpDialog() }.create().show()
    }

    private fun showHelpDialog() {
        if (!isAdded) return
        activity?.let {
            val inflater = it.layoutInflater
            AlertDialog.Builder(it).setView(inflater.inflate(R.layout.dialog_sd_write, null))
                    .setOnDismissListener { showDialog() }
                    .create().show()
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data !== null && requestCode == REQUEST_CODE_STORAGE_ACCES) {
            if (resultCode == Activity.RESULT_OK) {
                val context = context ?: return
                val treeUri = data.data
                Settings.getInstance(context).edit()
                        .putString("tree_uri_$storage", treeUri.toString())
                        .apply()
                val treeFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                val contentResolver = context.contentResolver

                // revoke access if a permission already exists
                val persistedUriPermissions = contentResolver.persistedUriPermissions
                for (uriPermission in persistedUriPermissions) {
                    val file = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uriPermission.uri)
                    if (treeFile?.name == file?.name) {
                        contentResolver.releasePersistableUriPermission(uriPermission.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        return
                    }
                }

                // else set permission
                contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                permissions = contentResolver.persistedUriPermissions
                executePendingAction()
                return
            }
        }
    }

    companion object {
        internal const val TAG = "VLC/WriteExternal"
        internal const val KEY_STORAGE_PATH = "VLC/storage_path"
        private const val REQUEST_CODE_STORAGE_ACCES = 42
        private var permissions = VLCApplication.getAppContext().contentResolver.persistedUriPermissions
        private lateinit var storage: String

        fun askForExtWrite(activity: androidx.fragment.app.FragmentActivity?, uri: Uri, cb: Runnable? = null) {
            if (activity === null) return
            val fragment = WriteExternalDelegate()
            val channel = if (cb != null) Channel<Unit>(1) else null
            storage = FileUtils.getMediaStorage(uri) ?: return
            fragment.arguments = Bundle(1).apply { putString(KEY_STORAGE_PATH, storage) }
            channel?.let { fragment.channel = it }
            activity.supportFragmentManager.beginTransaction().add(fragment, TAG).commitAllowingStateLoss()
            channel?.let { waitForIt(it, cb!!) }
        }

        fun needsWritePermission(uri: Uri) : Boolean {
            val path = uri.path
            return AndroidUtil.isLolliPopOrLater && "file" == uri.scheme
                    && !TextUtils.isEmpty(path) && path.startsWith('/')
                    && !path.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
                    && !(FileUtils.findFile(uri)?.canWrite() ?: false)
        }
    }
}