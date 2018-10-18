package org.videolan.vlc.gui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.textfield.TextInputLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.appcompat.app.AppCompatDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.database.MediaDatabase;
import org.videolan.vlc.gui.DialogActivity;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.repository.BrowserFavRepository;
import org.videolan.vlc.util.WorkersKt;

public class NetworkServerDialog extends DialogFragment implements AdapterView.OnItemSelectedListener, TextWatcher, View.OnClickListener {

    private static final String TAG = "VLC/NetworkServerDialog";

    public static final String FTP_DEFAULT_PORT = "21";
    public static final String FTPS_DEFAULT_PORT = "990";
    public static final String SFTP_DEFAULT_PORT = "22";
    public static final String HTTP_DEFAULT_PORT = "80";
    public static final String HTTPS_DEFAULT_PORT = "443";

    private BrowserFavRepository mBrowserFavRepository;

    private Activity mActivity;

    String[] mProtocols;
    TextInputLayout mEditAddressLayout;
    EditText mEditAddress, mEditPort, mEditFolder, mEditUsername, mEditServername;
    Spinner mSpinnerProtocol;
    TextView mUrl, mPortTitle;
    Button mCancel, mSave;
    Uri mUri;
    String mName;

    //Dummy hack because spinner callback is called right on registration
    boolean mIgnoreFirstSpinnerCb = false;

    public NetworkServerDialog() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final FragmentActivity activity = getActivity();
        final AppCompatDialog dialog = new AppCompatDialog(activity, getTheme());
        dialog.setTitle(R.string.server_add_title);

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = activity;
        if (mBrowserFavRepository == null) mBrowserFavRepository = BrowserFavRepository.Companion.getInstance(activity);
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

        mEditAddressLayout = v.findViewById(R.id.server_domain);
        mEditAddress = mEditAddressLayout.getEditText();
        mEditFolder = ((TextInputLayout)v.findViewById(R.id.server_folder)).getEditText();
        mEditUsername = ((TextInputLayout)v.findViewById(R.id.server_username)).getEditText();
        mEditServername = ((TextInputLayout)v.findViewById(R.id.server_name)).getEditText();
        mSpinnerProtocol = v.findViewById(R.id.server_protocol);
        mEditPort = v.findViewById(R.id.server_port);
        mUrl = v.findViewById(R.id.server_url);
        mSave = v.findViewById(R.id.server_save);
        mCancel = v.findViewById(R.id.server_cancel);
        mPortTitle = v.findViewById(R.id.server_port_text);

        mProtocols = getResources().getStringArray(R.array.server_protocols);
        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(view.getContext(), R.layout.dropdown_item, getResources().getStringArray(R.array.server_protocols));
        mSpinnerProtocol.setAdapter(spinnerArrayAdapter);

        if (mUri != null) {
            mIgnoreFirstSpinnerCb = true;
            mEditAddress.setText(mUri.getHost());
            if (!TextUtils.isEmpty(mUri.getUserInfo()))
                mEditUsername.setText(mUri.getUserInfo());
            if (!TextUtils.isEmpty(mUri.getPath()))
                mEditFolder.setText(mUri.getPath());
            if (!TextUtils.isEmpty(mName))
                mEditServername.setText(mName);

            int position = getProtocolSpinnerPosition(mUri.getScheme().toUpperCase());
            mSpinnerProtocol.setSelection(position);
            int port = mUri.getPort();
            mEditPort.setText(port != -1 ? String.valueOf(port) : getPortForProtocol(position));
        }
        mSpinnerProtocol.setOnItemSelectedListener(this);
        mSave.setOnClickListener(this);
        mCancel.setOnClickListener(this);

        mEditPort.addTextChangedListener(this);
        mEditAddress.addTextChangedListener(this);
        mEditFolder.addTextChangedListener(this);
        mEditUsername.addTextChangedListener(this);

        updateUrl();
    }

    private void saveServer() {
        final String name = (TextUtils.isEmpty(mEditServername.getText().toString())) ?
                mEditAddress.getText().toString() : mEditServername.getText().toString();
        final Uri uri = Uri.parse(mUrl.getText().toString());
        if (mUri != null) {
            WorkersKt.runIO(new Runnable() {
                @Override
                public void run() {
                    mBrowserFavRepository.deleteBrowserFav(mUri);
                }
            });
        }
        mBrowserFavRepository.addNetworkFavItem(uri, name, null);
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
            if (!mEditFolder.getText().toString().startsWith("/"))
                sb.append('/');
            sb.append(mEditFolder.getText());
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

    private int getProtocolSpinnerPosition(String protocol) {
        for (int i = 0; i < mProtocols.length; ++i) {
            if (TextUtils.equals(mProtocols[i], protocol))
                return i;
        }
        return 0;
    }


    private String getPortForProtocol(int position) {
        switch (mProtocols[position]) {
            case "FTP":
                return FTP_DEFAULT_PORT;
            case "FTPS":
                return FTPS_DEFAULT_PORT;
            case "SFTP":
                return SFTP_DEFAULT_PORT;
            case "HTTP":
                return HTTP_DEFAULT_PORT;
            case "HTTPS":
                return HTTPS_DEFAULT_PORT;
            default:
                return "";
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (mIgnoreFirstSpinnerCb) {
            mIgnoreFirstSpinnerCb = false;
            return;
        }
        boolean portEnabled = true, userEnabled = true;
        String port = getPortForProtocol(position);
        int addressHint = R.string.server_domain_hint;
        switch (mProtocols[position]) {
            case "SMB":
                addressHint = R.string.server_share_hint;
                userEnabled = false;
                break;
            case "NFS":
                addressHint = R.string.server_share_hint;
                userEnabled = false;
                portEnabled = false;
                break;
        }
        mEditAddressLayout.setHint(getString(addressHint));
        mPortTitle.setVisibility(portEnabled ? View.VISIBLE : View.INVISIBLE);
        mEditPort.setVisibility(portEnabled ? View.VISIBLE : View.INVISIBLE);
        mEditPort.setText(port);
        mEditPort.setEnabled(portEnabled);
        mEditUsername.setVisibility(userEnabled ? View.VISIBLE : View.GONE);
        mEditUsername.setEnabled(userEnabled);
        updateUrl();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mEditUsername.hasFocus() &&
                TextUtils.equals(mSpinnerProtocol.getSelectedItem().toString(),"SFTP")) {
            mEditFolder.removeTextChangedListener(this);
            mEditFolder.setText("/home/" + mEditUsername.getText().toString());
            mEditFolder.addTextChangedListener(this);
        }
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

    public void setServer(MediaWrapper mw) {
        mUri = mw.getUri();
        mName = mw.getTitle();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        final Activity activity = getActivity();
        if (activity instanceof DialogActivity) activity.finish();
    }
}
