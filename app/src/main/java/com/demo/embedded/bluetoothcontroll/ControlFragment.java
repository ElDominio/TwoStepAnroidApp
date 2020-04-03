package com.demo.embedded.bluetoothcontroll;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import okio.Buffer;


public class ControlFragment extends Fragment {


    // Debugging
    private static final String TAG = ControlFragment.class.getSimpleName();

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_ENABLE_BT = 3;

    private TextView txtRpmGlobal;
    //
    private EditText txtPotPosition;
    private EditText txtTwoStepRpm;
    private EditText txtRpmHysterisis;
    private EditText txtCylMode;
    private EditText txtCoilPower;
    private EditText txtRpmFilter;
    private EditText txtToyotaMode;
    private EditText txtSystemArmed;
    private EditText txtTrigType;
    //
    private EditText setTxtDesiredRpm;
    private EditText setTxtRpmHysterisis;
    private EditText setTxtPosition;
    //
    private Spinner coilTypeSpinner;
    private Spinner triggerTypeSpinner;
    //
    private SwitchCompat clutchSwitch;
    private SwitchCompat rpmFilterSwitch;
    //
    private CheckBox toyotaChẹcbox;
    private RadioButton rb4Cylinder;
    private RadioButton rb6Cylinder;
    private RadioButton rb8Cylinder;
    //
    private Button btnStart;
    private Button btnStop;
    private Button btnSave;
    //
    private View toyotaLayout;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;


    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothSPPService mChatService = null;
    //
    private Buffer buffer = new Buffer();
    private Buffer extantBuffer = new Buffer();


    ScheduledFuture scheduledSyncTimeFuture;
    ScheduledFuture scheduledUartFuture;
    ScheduledExecutorService scheduledExecutorService;
    StringBuilder readMessagePoll = new StringBuilder();

    private class TimeTask implements Runnable {
        @Override
        public void run() {
            sendMessage("r");
        }
    }

    private class UartTask implements Runnable {
        @Override
        public void run() {
            updateReceive(readMessagePoll.toString());

        }
    }

    private void initTimerCycle() {
        if (scheduledExecutorService == null)
            scheduledExecutorService = Executors.newScheduledThreadPool(2);
    }

    private void scheduleSyncTimeTask() {
        scheduledSyncTimeFuture = scheduledExecutorService.scheduleAtFixedRate(new TimeTask(), 0, 1, TimeUnit.SECONDS);//Chay di chay lai
        Log.i(TAG, "scheduleSyncTimeTask");
    }


    private void cancelSyncTimeTask() {
        if (scheduledSyncTimeFuture != null) {
            scheduledSyncTimeFuture.cancel(false);
            scheduledSyncTimeFuture = null;
            Log.i(TAG, "cancelSyncTimeTask");
        }
    }

    private void scheduleUartTask() {
        scheduledUartFuture = scheduledExecutorService.scheduleAtFixedRate(new UartTask(), 0, 50, TimeUnit.MILLISECONDS);//Chay di chay lai

    }

    private void cancelUartTask() {
        if (scheduledUartFuture != null) {
            scheduledUartFuture.cancel(false);
            scheduledUartFuture = null;
            ;
        }
    }

    private void clearTimerCycle() {
        if (scheduledExecutorService != null) scheduledExecutorService.shutdown();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        FragmentActivity activity = getActivity();
        if (mBluetoothAdapter == null && activity != null) {
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mBluetoothAdapter == null) {
            return;
        }
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupInit();
        }
        initTimerCycle();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
        clearTimerCycle();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothSPPService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_alpha_x, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        txtRpmGlobal = rootView.findViewById(R.id.txt_rpm_global);
        //
        txtPotPosition = rootView.findViewById(R.id.txt_pot_position);
        txtTwoStepRpm = rootView.findViewById(R.id.txt_two_step_rpm);
        txtRpmHysterisis = rootView.findViewById(R.id.txt_rpm_hysterisis);
        txtCylMode = rootView.findViewById(R.id.txt_cyl_mode);
        txtCoilPower = rootView.findViewById(R.id.txt_coil_power);
        txtRpmFilter = rootView.findViewById(R.id.txt_rpm_filter);
        txtToyotaMode = rootView.findViewById(R.id.txt_toyota_mode);
        txtSystemArmed = rootView.findViewById(R.id.txt_system_armed);
        txtTrigType = rootView.findViewById(R.id.txt_trig_type);
        //
        setTxtDesiredRpm = rootView.findViewById(R.id.set_txt_desired_rpm);
        setTxtRpmHysterisis = rootView.findViewById(R.id.set_txt_rpm_hysterisis);
        setTxtPosition = rootView.findViewById(R.id.set_txt_position);
        //

        coilTypeSpinner = rootView.findViewById(R.id.coil_type_spinner);
        ArrayAdapter<CharSequence> coilArrayAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.coil_range, android.R.layout.simple_spinner_item);
        coilArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        coilTypeSpinner.setAdapter(coilArrayAdapter);

        triggerTypeSpinner = rootView.findViewById(R.id.trigger_type_spinner);
        ArrayAdapter<CharSequence> typeArrayAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.type_range, android.R.layout.simple_spinner_item);
        typeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        triggerTypeSpinner.setAdapter(typeArrayAdapter);
        //
        clutchSwitch = rootView.findViewById(R.id.clutch_switch);
        rpmFilterSwitch = rootView.findViewById(R.id.rpm_filter_switch);
        //
        toyotaChẹcbox = rootView.findViewById(R.id.toyota_cb);
        rb4Cylinder = rootView.findViewById(R.id.rb_4_cylinder);
        rb6Cylinder = rootView.findViewById(R.id.rb_6_cylinder);
        rb8Cylinder = rootView.findViewById(R.id.rb_8_cylinder);
        //
        btnStart = rootView.findViewById(R.id.btn_start);
        btnStop = rootView.findViewById(R.id.btn_stop);
        btnSave = rootView.findViewById(R.id.btn_save_setting);
        //
        toyotaLayout = rootView.findViewById(R.id.toyota_layout);


    }


    /**
     * Set up the UI and background operations
     */
    private void setupInit() {
        Log.d(TAG, "setupInit()");

        // Initialize the array adapter for the conversation thread
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothSPPService(activity, mHandler);
        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer();

        toyotaChẹcbox.setOnCheckedChangeListener((compoundButton, enable) -> {

            if (!enable) {
                sendMessage("to");
            }
            if (enable) toyotaLayout.setVisibility(View.VISIBLE);
            else
                toyotaLayout.setVisibility(View.GONE);

        });
        btnSave.setOnClickListener(view -> {
            if ((!TextUtils.isEmpty(setTxtDesiredRpm.getText().toString())) && (!TextUtils.isEmpty(setTxtRpmHysterisis.getText().toString())) && (!TextUtils.isEmpty(setTxtPosition.getText().toString()))) {
                sendSettingData();
            }
        });
        btnStart.setOnClickListener(view -> {
            if (mChatService.getState() != BluetoothSPPService.STATE_CONNECTED) {
                Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                return;
            }
            scheduleUartTask();
            scheduleSyncTimeTask();
            btnStart.setVisibility(View.GONE);
            btnStop.setVisibility(View.VISIBLE);
        });
        btnStop.setOnClickListener(view -> {
            cancelUartTask();
            cancelSyncTimeTask();
            btnStart.setVisibility(View.VISIBLE);
            btnStop.setVisibility(View.GONE);
        });

        rb4Cylinder.setOnClickListener(view -> {
            sendMessage("tf");
        });

        rb6Cylinder.setOnClickListener(view -> {
            sendMessage("ts");
        });
        rb8Cylinder.setOnClickListener(view -> {
            sendMessage("te");
        });
        clutchSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            sendMessage("i");
        });
        rpmFilterSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            sendMessage("f");
        });
        triggerTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                sendMessage("c");
                String type = triggerTypeSpinner.getSelectedItem().toString();
                sendMessage(type);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        coilTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                sendMessage("c");
                String coilNo = coilTypeSpinner.getSelectedItem().toString();
                sendMessage(coilNo);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }


    private void sendSettingData() {
        // Send the user's text straight out the port
        sendMessage("p");
        sendMessage(setTxtPosition.getText().toString());
        sendMessage(setTxtDesiredRpm.getText().toString());
        sendMessage(setTxtRpmHysterisis.getText().toString());
        if (setTxtRpmHysterisis.getText().toString().length() < 4) {
            sendMessage(";");
        }
        setTxtPosition.setText("");
        setTxtDesiredRpm.setText("");
        setTxtRpmHysterisis.setText("");
    }


    void updateReceive(String data) {

        char[] recievedData = data.toCharArray();
        if (recievedData.length > 0) {
            Log.d(TAG, "updateReceive:" + data);
            if (data.equalsIgnoreCase("ok")) {//ok
                cancelSyncTimeTask();
            } else if (recievedData[0] == 'r') {//r0
                data = data.replace("r", "");
                txtRpmGlobal.setText(data);
            } else if (recievedData[0] == 'v') {//v01500411115003
                txtPotPosition.setText(Character.toString(recievedData[1]));
                char[] rpmHystVal = Arrays.copyOfRange(recievedData, 2, 4);
                txtRpmHysterisis.setText(String.valueOf(rpmHystVal));
                if (recievedData[5] == '0') {
                    txtSystemArmed.setText("Off");
                } else if (recievedData[5] == '1') {
                    txtSystemArmed.setText("On");
                }
                txtCylMode.setText(Character.toString(recievedData[6]));

                if (recievedData[7] == '0') {
                    txtCoilPower.setText("Off");
                } else if (recievedData[7] == '1') {
                    txtCoilPower.setText("On");
                }
                if (recievedData[8] == '0') {
                    txtToyotaMode.setText("Off");
                } else if (recievedData[8] == '1') {
                    txtToyotaMode.setText("On");
                }
                if (recievedData[9] == '0') {
                    txtRpmFilter.setText("Off");
                } else if (recievedData[9] == '1') {
                    txtRpmFilter.setText("On");
                }
                char[] cutRPMval = Arrays.copyOfRange(recievedData, 10, 13);
                txtTwoStepRpm.setText(String.valueOf(cutRPMval));
                txtTrigType.setText(Character.toString(recievedData[14]));
            }
            readMessagePoll.delete(0, readMessagePoll.length());
            readMessagePoll.setLength(0);
        }

    }

    void sendStopMessage() {
        sendMessage("r");
    }

    void sendStartMessage() {
        sendMessage("r");
    }

    void sendData(String text, boolean crlf) {
        if (!TextUtils.isEmpty(text)) {
            if (crlf)
                sendMessage(text + "\r\n");
            else
                sendMessage(text);
        }

    }

    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothSPPService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);
            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        }
    }


    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        MainActivity activity = (MainActivity) getActivity();
        if (null == activity) {
            return;
        }
        activity.setActionBarSubTitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        MainActivity activity = (MainActivity) getActivity();
        if (null == activity) {
            return;
        }
        activity.setActionBarSubTitle(subTitle);
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothSPPService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            // mLogText.setText("");
                            break;
                        case BluetoothSPPService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothSPPService.STATE_LISTEN:
                        case BluetoothSPPService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    // mLogText.append(writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    int size = msg.arg1;
                    String readMessage = new String(readBuf, 0, size);
                    Log.d(TAG, "readMessage:" + readMessage);
                    byte[] dataBuf = Arrays.copyOf(readBuf, size);
                    // construct a string from the valid bytes in the buffer
//                    recursiveCheckCharacter(dataBuf, (byte) 13);//LF
                    readMessagePoll.append(readMessage);//now original string is changed
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupInit();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    FragmentActivity activity = getActivity();
                    if (activity != null) {
                        Toast.makeText(activity, R.string.bt_not_enabled_leaving,
                                Toast.LENGTH_SHORT).show();
                        activity.finish();
                    }
                }
        }
    }

    /**
     * Establish connection with other device
     *
     * @param data An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     */
    private void connectDevice(Intent data) {
        // Get the device MAC address
        Bundle extras = data.getExtras();
        if (extras == null) {
            return;
        }
        String address = extras.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
        }
        return false;
    }

    private void recursiveCheckCharacter(byte[] cmd, byte splitValue) {
        boolean isMatch = false;
        for (byte b : cmd) {
            if (isMatch) {
                extantBuffer.writeByte(b);
            } else {
                buffer.writeByte(b);
                if (b == splitValue) {
                    isMatch = true;
                }
            }
        }
        if (isMatch) {
            byte[] sendBytes = buffer.readByteArray();
            String readMessage = new String(sendBytes, StandardCharsets.UTF_8).trim();//Trim will remove all CRLF and space
            updateReceive(readMessage);
            isMatch = false;
            byte[] extantBytes = extantBuffer.readByteArray();
            if (extantBytes.length > 0) recursiveCheckCharacter(extantBytes, splitValue);
        }
    }
}