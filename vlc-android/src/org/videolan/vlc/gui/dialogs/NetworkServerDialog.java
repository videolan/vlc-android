package org.videolan.vlc.gui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.media.MediaDatabase;

public class NetworkServerDialog extends DialogFragment implements AdapterView.OnItemSelectedListener, TextWatcher, View.OnClickListener {

    private static final String TAG = "VLC/NetworkServerDialog";

    public static final String FTP_DEFAULT_PORT = "21";
    public static final String FTPS_DEFAULT_PORT = "990";
    public static final String SFTP_DEFAULT_PORT = "22";
    public static final String HTTP_DEFAULT_PORT = "80";
    public static final String HTTPS_DEFAULT_PORT = "443";

    private Activity mActivity;

    String[] mProtocols;
    TextInputLayout mEditAddressLayout;
    EditText mEditAddress, mEditPort, mEditFolder, mEditUsername, mEditServername;
    Spinner mSpinnerProtocol;
    TextView mUrl, mPortTitle;
    Button mCancel, mSave;


    public NetworkServerDialog() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AppCompatDialog dialog = new AppCompatDialog(getActivity(), getTheme());
        dialog.setTitle(R.string.server_add_title);

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mActivity = null;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mActivity instanceof MainActivity)
            ((MainActivity)mActivity).forceRefresh();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.network_server_dialog, container, false);

        mEditAddress = ((TextInputLayout)v.findViewById(R.id.server_domain)).getEditText();
        mEditAddressLayout = (TextInputLayout) mEditAddress.getParent();
        mEditFolder = ((TextInputLayout)v.findViewById(R.id.server_folder)).getEditText();
        mEditUsername = ((TextInputLayout)v.findViewById(R.id.server_username)).getEditText();
        mEditServername = ((TextInputLayout)v.findViewById(R.id.server_name)).getEditText();
        mSpinnerProtocol = (Spinner) v.findViewById(R.id.server_protocol);
        mEditPort = (EditText) v.findViewById(R.id.server_port);
        mUrl = (TextView) v.findViewById(R.id.server_url);
        mSave = (Button) v.findViewById(R.id.server_save);
        mCancel = (Button) v.findViewById(R.id.server_cancel);
        mPortTitle = (TextView) v.findViewById(R.id.server_port_text);

        mProtocols = getResources().getStringArray(R.array.server_protocols);
        mSpinnerProtocol.setOnItemSelectedListener(this);
        mSave.setOnClickListener(this);
        mCancel.setOnClickListener(this);

        mEditPort.addTextChangedListener(this);
        mEditAddress.addTextChangedListener(this);
        mEditFolder.addTextChangedListener(this);
        mEditUsername.addTextChangedListener(this);

        updateUrl();
        return v;
    }

    private void saveServer() {
        String name = (TextUtils.isEmpty(mEditServername.getText().toString())) ?
                mEditAddress.getText().toString() : mEditServername.getText().toString();
        Uri uri = Uri.parse(mUrl.getText().toString());
         MediaDatabase db = MediaDatabase.getInstance();
        if (!db.networkFavExists(uri))
            db.addNetworkFavItem(uri, name);
    }

    private void updateUrl() {
        StringBuilder sb = new StringBuilder();
        sb.append(mSpinnerProtocol.getSelectedItem().toString().toLowerCase())
                .append("://");
        if (mEditUsername.isEnabled() && !TextUtils.isEmpty(mEditUsername.getText())) {
            sb.append(mEditUsername.getText()).append('@');
        }
        sb.append(mEditAddress.getText());
        if (needPort()) {
            sb.append(':').append(mEditPort.getText());
        }
        if (mEditFolder.isEnabled() && !TextUtils.isEmpty(mEditFolder.getText())) {
            sb.append('/').append(mEditFolder.getText());
        }
        mUrl.setText(sb.toString());
        mSave.setEnabled(!TextUtils.isEmpty(mEditAddress.getText().toString()));
    }

    private boolean needPort() {
        if (!mEditPort.isEnabled() || TextUtils.isEmpty(mEditPort.getText()))
            return false;
        switch (mEditPort.getText().toString()) {
            case FTP_DEFAULT_PORT:
            case SFTP_DEFAULT_PORT:
            case HTTP_DEFAULT_PORT:
            case HTTPS_DEFAULT_PORT:
                return false;
            default:
                return true;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        boolean portEnabled = true, userEnabled = true;
        String port = null;
        int addressHint = R.string.server_domain_hint;
        switch (mProtocols[position]) {
            case "FTP":
                port = FTP_DEFAULT_PORT;
                break;
            case "FTPS":
                port = FTPS_DEFAULT_PORT;
                break;
            case "SFTP":
                port = SFTP_DEFAULT_PORT;
                break;
            case "HTTP":
                port = HTTP_DEFAULT_PORT;
                break;
            case "HTTPS":
                port = HTTPS_DEFAULT_PORT;
                break;
            case "SMB":
                addressHint = R.string.server_share_hint;
                portEnabled = false;
                port = "";
                break;
            case "NFS":
                addressHint = R.string.server_share_hint;
                userEnabled = false;
                portEnabled = false;
                port = "";
                break;
        }
        mEditAddressLayout.setHint(getString(addressHint));
        mPortTitle.setVisibility(portEnabled ? View.VISIBLE : View.INVISIBLE);
        mEditPort.setVisibility(portEnabled ? View.VISIBLE : View.INVISIBLE);
        mEditPort.setText(port);
        mEditUsername.setVisibility(userEnabled ? View.VISIBLE : View.GONE);
        updateUrl();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        updateUrl();
    }

    @Override
    public void afterTextChanged(Editable s) {}

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.server_save:
                saveServer();
            case R.id.server_cancel:
                dismiss();
                break;

        }
    }
}
