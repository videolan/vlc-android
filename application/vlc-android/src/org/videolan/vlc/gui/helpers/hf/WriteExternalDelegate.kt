package org.videolan.vlc.gui.helpers.hf

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.AndroidDevices
import org.videolan.tools.AppScope
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.util.FileUtils

class WriteExternalDelegate : BaseHeadlessFragment() {
    private var storage : String? = null

    @TargetApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showDialog()
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun showDialog() {
        if (!isAdded || isDetached) return
        val builder = AlertDialog.Builder(requireActivity())
        builder.setMessage(R.string.sdcard_permission_dialog_message)
                .setTitle(R.string.sdcard_permission_dialog_title)
                .setPositiveButton(R.string.ok) { _, _ ->
                    if (!isAdded || isDetached) return@setPositiveButton
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    storage = arguments?.getString(KEY_STORAGE_PATH)?.apply { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, toUri()) }
                    startActivityForResult(intent, REQUEST_CODE_STORAGE_ACCESS)
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
        if (data !== null && requestCode == REQUEST_CODE_STORAGE_ACCESS) {
            if (resultCode == Activity.RESULT_OK) {
                val context = context ?: return
                val treeUri = data.data ?: return
                Settings.getInstance(context).putSingle("tree_uri_$storage", treeUri.toString())
                val treeFile = DocumentFile.fromTreeUri(context, treeUri)
                val contentResolver = context.contentResolver

                // revoke access if a permission already exists
                val persistedUriPermissions = contentResolver.persistedUriPermissions
                for (uriPermission in persistedUriPermissions) {
                    val file = DocumentFile.fromTreeUri(context, uriPermission.uri)
                    if (treeFile?.name == file?.name) {
                        contentResolver.releasePersistableUriPermission(uriPermission.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        model.deferredGrant.complete(false)
                        exit()
                        return
                    }
                }

                // else set permission
                contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                model.deferredGrant.complete(true)
                exit()
                return
            }
        }
        model.deferredGrant.complete(false)
        exit()
    }

    companion object {
        internal const val TAG = "VLC/WriteExternal"
        internal const val KEY_STORAGE_PATH = "VLC/storage_path"
        private const val REQUEST_CODE_STORAGE_ACCESS = 42

        fun askForExtWrite(activity: FragmentActivity, uri: Uri, cb: Runnable? = null) {
            AppScope.launch {
                if (activity.getExtWritePermission(uri)) cb?.run()
            }
        }

        fun needsWritePermission(uri: Uri) : Boolean {
            val path = uri.path ?: return false
            return AndroidUtil.isLolliPopOrLater && ("file" == uri.scheme || uri.scheme == null)
                    && path.isNotEmpty() && path.startsWith('/')
                    && !path.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
                    && !(FileUtils.findFile(uri)?.canWrite() ?: false)
        }
    }
}

suspend fun FragmentActivity.getExtWritePermission(uri: Uri) : Boolean {
    if (!WriteExternalDelegate.needsWritePermission(uri)) return true
    val storage = FileUtils.getMediaStorage(uri) ?: return false
    val model : PermissionViewmodel by viewModels()
    val fragment = WriteExternalDelegate()
    model.setupDeferred()
    fragment.arguments = bundleOf(WriteExternalDelegate.KEY_STORAGE_PATH to storage)
    supportFragmentManager.beginTransaction().add(fragment, WriteExternalDelegate.TAG).commitAllowingStateLoss()
    return model.deferredGrant.await()
}

suspend fun Fragment.getExtWritePermission(uri: Uri) = activity?.getExtWritePermission(uri) ?: false
