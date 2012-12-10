package org.videolan.vlc;

import java.util.Calendar;

import org.videolan.vlc.gui.SpeedSelectorDialog;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

public class AdvFuncDialog extends Dialog{

    public final static String TAG = "VLC/AdvFuncPopupWindow";

    private static View mAdvFuncView ;

    private ImageButton mSleep;
    private TimeSleepDialog mTimeSleepDialog;
    private ImageButton mSpeed;
    private TextView mSpeedInfo;
    private SpeedSelectorDialog mSpeedSelectorDialog;

    /**
     * Put all advance functionality here
     */
    public AdvFuncDialog(Activity activity) {
        super (activity);
        setOwnerActivity(activity);

        try {
            LayoutInflater inflater = LayoutInflater.from(getOwnerActivity());
            mAdvFuncView = inflater.inflate(R.layout.advance_function,
                    (ViewGroup) findViewById(R.id.adv_func));
        } catch (Exception e) {
            e.printStackTrace();
        }
        setContentView(mAdvFuncView);
        setTitle(getOwnerActivity().getString(R.string.advfunc_title));
        setCanceledOnTouchOutside(true);

        // Init Sleep function
        mSleep = (ImageButton) mAdvFuncView.findViewById(R.id.adv_func_sleep);
        mSleep.setOnClickListener(mSleepListener);
        IntentFilter filter = new IntentFilter();
        filter.addAction(SleepAlarmReceiver.SLEEP_INTENT);
        getOwnerActivity().registerReceiver(mSleepReceiver, filter);

        // Init Speed function
        mSpeed = (ImageButton) mAdvFuncView.findViewById(R.id.adv_func_speed);
        mSpeed.setOnClickListener(mSpeedLabelListener);
        mSpeedInfo = (TextView) mAdvFuncView.findViewById(R.id.adv_func_speed_info);
    }

    private final BroadcastReceiver mSleepReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (action.equalsIgnoreCase(SleepAlarmReceiver.SLEEP_INTENT))
                getOwnerActivity().finish();
        }
    };

    private final View.OnClickListener mSleepListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            final Calendar c = Calendar.getInstance();
            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);
            mTimeSleepDialog = new TimeSleepDialog(getOwnerActivity(), hour, minute);
        };
    };

    private final View.OnClickListener mSpeedLabelListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mSpeedSelectorDialog = new SpeedSelectorDialog(getOwnerActivity());
            mSpeedSelectorDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {

                @Override
                public void onDismiss(DialogInterface dialog) {
                    mSpeedInfo.setText(mSpeedSelectorDialog.getSpeedInfo());
                }
            });
            mSpeedSelectorDialog.show();
        }
    };

    public void destroyAdvFuncDialog() {

        // Dismiss secondary window
        if (mTimeSleepDialog!=null)
            if (mTimeSleepDialog.isShowing()) {
                mTimeSleepDialog.dismiss();
            }
        if (mSpeedSelectorDialog!=null)
            if (mSpeedSelectorDialog.isShowing()) {
                mSpeedSelectorDialog.dismiss();
            }

        // Unregister Receiver
        getOwnerActivity().unregisterReceiver(mSleepReceiver);

        // Dismiss main window
        if (isShowing()) dismiss();
    }
}
