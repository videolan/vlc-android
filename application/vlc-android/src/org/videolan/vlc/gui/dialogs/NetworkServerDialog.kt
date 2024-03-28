package org.videolan.vlc.gui.dialogs

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.AppScope
import org.videolan.vlc.R
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.helpers.UiTools.showPinIfNeeded
import org.videolan.vlc.repository.BrowserFavRepository
import java.util.Locale

class NetworkServerDialog : VLCBottomSheetDialogFragment(), AdapterView.OnItemSelectedListener, TextWatcher, View.OnClickListener {

    private lateinit var browserFavRepository: BrowserFavRepository

    private lateinit var protocols: Array<String>
    private lateinit var editAddressLayout: TextInputLayout
    private lateinit var editAddress: EditText
    private lateinit var editPort: EditText
    private lateinit var editFolder: EditText
    private lateinit var editUsername: TextInputLayout
    private lateinit var editServername: EditText
    private lateinit var spinnerProtocol: Spinner
    private lateinit var url: TextView
    private lateinit var portTitle: TextView
    private lateinit var cancel: Button
    private lateinit var save: Button
    private lateinit var networkUri: Uri
    private lateinit var networkName: String

    //Dummy hack because spinner callback is called right on registration
    var ignoreFirstSpinnerCb = false

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? MainActivity)?.forceRefresh()
    }

    override fun getDefaultState(): Int = BottomSheetBehavior.STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = false

    override fun initialFocusedView(): View = spinnerProtocol

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { if (requireActivity().showPinIfNeeded()) dismiss() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        if (!::browserFavRepository.isInitialized) browserFavRepository = BrowserFavRepository.getInstance(requireActivity())
        val v = inflater.inflate(R.layout.network_server_dialog, container, false)

        editAddressLayout = v.findViewById(R.id.server_domain)
        editAddress = editAddressLayout.editText!!
        editFolder = (v.findViewById<View>(R.id.server_folder) as TextInputLayout).editText!!
        editUsername = (v.findViewById<View>(R.id.server_username) as TextInputLayout)
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
            ignoreFirstSpinnerCb = true
            editAddress.setText(networkUri.host)
            if (!networkUri.userInfo.isNullOrEmpty())
                editUsername.editText!!.setText(networkUri.userInfo)
            if (!networkUri.path.isNullOrEmpty())
                editFolder.setText(networkUri.path)
            if (!networkName.isEmpty())
                editServername.setText(networkName)

            networkUri.scheme?.uppercase(Locale.getDefault())?.let {
                val position = getProtocolSpinnerPosition(it)
                spinnerProtocol.setSelection(position)
                val port = networkUri.port
                editPort.setText(if (port != -1) port.toString() else getPortForProtocol(position))
            }
        }
        spinnerProtocol.onItemSelectedListener = this
        save.setOnClickListener(this)
        cancel.setOnClickListener(this)

        editPort.addTextChangedListener(this)
        editAddress.addTextChangedListener(this)
        editFolder.addTextChangedListener(this)
        editUsername.editText!!.addTextChangedListener(this)

        updateUrl()
    }

    private fun saveServer() {
        val name = if (editServername.text.toString().isEmpty())
            editAddress.text.toString()
        else
            editServername.text.toString()
        val uri = url.text.toString().toUri()
        AppScope.launch {
            if (::networkUri.isInitialized) browserFavRepository.deleteBrowserFav(networkUri)
            browserFavRepository.addNetworkFavItem(uri, name, null)
            dismiss()
        }
    }

    private fun updateUrl() {
        val sb = StringBuilder()
        sb.append(spinnerProtocol.selectedItem.toString().lowercase(Locale.getDefault()))
                .append("://")
        if (editUsername.isEnabled && !editUsername.editText!!.text.isNullOrEmpty()) {
            sb.append(editUsername.editText!!.text).append('@')
        }
        sb.append(editAddress.text)
        if (needPort()) {
            sb.append(':').append(editPort.text)
        }
        if (editFolder.isEnabled && !editFolder.text.isNullOrEmpty()) {
            if (!editFolder.text.toString().startsWith("/"))
                sb.append('/')
            sb.append(editFolder.text)
        }
        url.text = sb.toString()
        save.isEnabled = editAddress.text.toString().isNotEmpty()
    }

    private fun needPort(): Boolean {
        if (!editPort.isEnabled || editPort.text.isNullOrEmpty())
            return false
        return when (editPort.text.toString()) {
            FTP_DEFAULT_PORT, SFTP_DEFAULT_PORT, HTTP_DEFAULT_PORT, HTTPS_DEFAULT_PORT -> false
            else -> true
        }
    }

    private fun getProtocolSpinnerPosition(protocol: String) = protocols.indexOfFirst { it == protocol }

    private fun getPortForProtocol(position: Int): String {
        return when (protocols[position]) {
            "FTP" -> FTP_DEFAULT_PORT
            "FTPS" -> FTPS_DEFAULT_PORT
            "FTPES" -> FTPES_DEFAULT_PORT
            "SFTP" -> SFTP_DEFAULT_PORT
            "HTTP" -> HTTP_DEFAULT_PORT
            "HTTPS" -> HTTPS_DEFAULT_PORT
            else -> ""
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (ignoreFirstSpinnerCb) {
            ignoreFirstSpinnerCb = false
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
        if (editUsername.hasFocus() && spinnerProtocol.selectedItem.toString() == "SFTP") {
            editFolder.removeTextChangedListener(this)
            editFolder.setText("/home/" + editUsername.editText!!.text.toString())
            editFolder.addTextChangedListener(this)
        }
        updateUrl()
    }

    override fun afterTextChanged(s: Editable) {}

    override fun onClick(v: View) {
        when (v.id) {
            R.id.server_save -> {
                saveServer()
            }
            R.id.server_cancel -> dismiss()
        }
    }

    fun setServer(mw: MediaWrapper) {
        networkUri = mw.uri
        networkName = mw.title
    }

    override fun onDestroy() {
        super.onDestroy()
        val activity = activity
        if (activity is DialogActivity) activity.finish()
    }

    companion object {

        private const val TAG = "VLC/NetworkServerDialog"

        const val FTP_DEFAULT_PORT = "21"
        const val FTPS_DEFAULT_PORT = "990"
        const val FTPES_DEFAULT_PORT = "21"
        const val SFTP_DEFAULT_PORT = "22"
        const val HTTP_DEFAULT_PORT = "80"
        const val HTTPS_DEFAULT_PORT = "443"
    }
}
