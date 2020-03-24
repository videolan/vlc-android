package org.videolan.vlc.gui.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.AppScope
import org.videolan.vlc.util.runIO
import org.videolan.vlc.util.runOnMainThread

class NetworkServerDialog : DialogFragment(), AdapterView.OnItemSelectedListener, TextWatcher, View.OnClickListener {

    private lateinit var mBrowserFavRepository: BrowserFavRepository

    private var mActivity: Activity? = null

    private lateinit var protocols: Array<String>
    private lateinit var editAddressLayout: TextInputLayout
    private lateinit var editAddress: EditText
    private lateinit var editPort: EditText
    private lateinit var editFolder: EditText
    private lateinit var editUsername: EditText
    private lateinit var editServername: EditText
    private lateinit var spinnerProtocol: Spinner
    private lateinit var url: TextView
    private lateinit var portTitle: TextView
    private lateinit var cancel: Button
    private lateinit var save: Button
    private lateinit var networkUri: Uri
    private lateinit var networkName: String

    //Dummy hack because spinner callback is called right on registration
    var mIgnoreFirstSpinnerCb = false


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = activity
        val dialog = AppCompatDialog(activity, theme)
        dialog.setTitle(R.string.server_add_title)

        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        return dialog

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mActivity = activity
        if (!::mBrowserFavRepository.isInitialized) mBrowserFavRepository = BrowserFavRepository.getInstance(requireActivity())
    }

    override fun onDetach() {
        super.onDetach()
        mActivity = null
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (mActivity is MainActivity)
            (mActivity as MainActivity).forceRefresh()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val v = inflater.inflate(R.layout.network_server_dialog, container, false)

        editAddressLayout = v.findViewById(R.id.server_domain)
        editAddress = editAddressLayout.editText!!
        editFolder = (v.findViewById<View>(R.id.server_folder) as TextInputLayout).editText!!
        editUsername = (v.findViewById<View>(R.id.server_username) as TextInputLayout).editText!!
        editServername = (v.findViewById<View>(R.id.server_name) as TextInputLayout).editText!!
        spinnerProtocol = v.findViewById(R.id.server_protocol)
        editPort = v.findViewById(R.id.server_port)
        url = v.findViewById(R.id.server_url)
        save = v.findViewById(R.id.server_save)
        cancel = v.findViewById(R.id.server_cancel)
        portTitle = v.findViewById(R.id.server_port_text)

        protocols = resources.getStringArray(R.array.server_protocols)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spinnerArrayAdapter = ArrayAdapter(view.context, R.layout.dropdown_item, resources.getStringArray(R.array.server_protocols))
        spinnerProtocol.adapter = spinnerArrayAdapter

        if (::networkUri.isInitialized) {
            mIgnoreFirstSpinnerCb = true
            editAddress.setText(networkUri.host)
            if (!TextUtils.isEmpty(networkUri.userInfo))
                editUsername.setText(networkUri.userInfo)
            if (!TextUtils.isEmpty(networkUri.path))
                editFolder.setText(networkUri.path)
            if (!TextUtils.isEmpty(networkName))
                editServername.setText(networkName)

            val position = getProtocolSpinnerPosition(networkUri.scheme.toUpperCase())
            spinnerProtocol.setSelection(position)
            val port = networkUri.port
            editPort.setText(if (port != -1) port.toString() else getPortForProtocol(position))
        }
        spinnerProtocol.onItemSelectedListener = this
        save.setOnClickListener(this)
        cancel.setOnClickListener(this)

        editPort.addTextChangedListener(this)
        editAddress.addTextChangedListener(this)
        editFolder.addTextChangedListener(this)
        editUsername.addTextChangedListener(this)

        updateUrl()
    }

    private fun saveServer() {
        val name = if (TextUtils.isEmpty(editServername.text.toString()))
            editAddress.text.toString()
        else
            editServername.text.toString()
        val uri = Uri.parse(url.text.toString())
        AppScope.launch {
            if (::networkUri.isInitialized) mBrowserFavRepository.deleteBrowserFav(networkUri).join()
            mBrowserFavRepository.addNetworkFavItem(uri, name, null).join()
            dismiss()
        }
    }

    private fun updateUrl() {
        val sb = StringBuilder()
        sb.append(spinnerProtocol.selectedItem.toString().toLowerCase())
                .append("://")
        if (editUsername.isEnabled && !TextUtils.isEmpty(editUsername.text)) {
            sb.append(editUsername.text).append('@')
        }
        sb.append(editAddress.text)
        if (needPort()) {
            sb.append(':').append(editPort.text)
        }
        if (editFolder.isEnabled && !TextUtils.isEmpty(editFolder.text)) {
            if (!editFolder.text.toString().startsWith("/"))
                sb.append('/')
            sb.append(editFolder.text)
        }
        url.text = sb.toString()
        save.isEnabled = !TextUtils.isEmpty(editAddress.text.toString())
    }

    private fun needPort(): Boolean {
        if (!editPort.isEnabled || TextUtils.isEmpty(editPort.text))
            return false
        return when (editPort.text.toString()) {
            FTP_DEFAULT_PORT, SFTP_DEFAULT_PORT, HTTP_DEFAULT_PORT, HTTPS_DEFAULT_PORT -> false
            else -> true
        }
    }

    private fun getProtocolSpinnerPosition(protocol: String): Int {
        for (i in protocols.indices) {
            if (TextUtils.equals(protocols[i], protocol))
                return i
        }
        return 0
    }


    private fun getPortForProtocol(position: Int): String {
        return when (protocols[position]) {
            "FTP" -> FTP_DEFAULT_PORT
            "FTPS" -> FTPS_DEFAULT_PORT
            "SFTP" -> SFTP_DEFAULT_PORT
            "HTTP" -> HTTP_DEFAULT_PORT
            "HTTPS" -> HTTPS_DEFAULT_PORT
            else -> ""
        }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        if (mIgnoreFirstSpinnerCb) {
            mIgnoreFirstSpinnerCb = false
            return
        }
        var portEnabled = true
        var userEnabled = true
        val port = getPortForProtocol(position)
        var addressHint = R.string.server_domain_hint
        when (protocols[position]) {
            "SMB" -> {
                addressHint = R.string.server_share_hint
                userEnabled = false
            }
            "NFS" -> {
                addressHint = R.string.server_share_hint
                userEnabled = false
                portEnabled = false
            }
        }
        editAddressLayout.hint = getString(addressHint)
        portTitle.visibility = if (portEnabled) View.VISIBLE else View.INVISIBLE
        editPort.visibility = if (portEnabled) View.VISIBLE else View.INVISIBLE
        editPort.setText(port)
        editPort.isEnabled = portEnabled
        editUsername.visibility = if (userEnabled) View.VISIBLE else View.GONE
        editUsername.isEnabled = userEnabled
        updateUrl()
    }

    override fun onNothingSelected(parent: AdapterView<*>) {}

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        if (editUsername.hasFocus() && TextUtils.equals(spinnerProtocol.selectedItem.toString(), "SFTP")) {
            editFolder.removeTextChangedListener(this)
            editFolder.setText("/home/" + editUsername.text.toString())
            editFolder.addTextChangedListener(this)
        }
        updateUrl()
    }

    override fun afterTextChanged(s: Editable) {}

    override fun onClick(v: View) {
        when (v.id) {
            R.id.server_save -> saveServer()
            R.id.server_cancel -> dismiss()
        }
    }

    fun setServer(mw: AbstractMediaWrapper) {
        networkUri = mw.uri
        networkName = mw.title
    }

    override fun onDestroy() {
        super.onDestroy()
        val activity = activity
        if (activity is DialogActivity) activity.finish()
    }

    companion object {

        private val TAG = "VLC/NetworkServerDialog"

        const val FTP_DEFAULT_PORT = "21"
        const val FTPS_DEFAULT_PORT = "990"
        const val SFTP_DEFAULT_PORT = "22"
        const val HTTP_DEFAULT_PORT = "80"
        const val HTTPS_DEFAULT_PORT = "443"
    }
}
