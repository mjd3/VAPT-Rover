package com.virtualapt.rover.cheburashka;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.graphics.Point;
import android.media.Image;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import static com.virtualapt.rover.cheburashka.Constants.MSG_PUSHOUT_DATA;
import static com.virtualapt.rover.cheburashka.Constants.MSG_REGISTER_ROVER_ACTIVITY;

public class RoverActivity extends Activity implements ImageCommunicator{
    private static final String TAG = "RoverActivity";

    MasterApplication mApp = null;
    CameraFragment mCamFrag = null;

    private static final String DEVICE_NAME = "DEVICE_NAME";

    // Intent request codes
    static final int REQUEST_CONNECT_DEVICE = 1;
    static final int REQUEST_ENABLE_BT = 2;

    // Message types sent from the DeviceConnector Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    BluetoothAdapter btAdapter;

    private static final String SAVED_PENDING_REQUEST_ENABLE_BT = "PENDING_REQUEST_ENABLE_BT";
    boolean pendingRequestEnableBt = false;

    private static DeviceConnector connector;
    private static BluetoothResponseHandler mHandler;

    private String deviceName;

    private ImageView wifiLogoView;
    private ImageView bluetoothLogoView;

    private TextView jsData;

    private TextView speedSensitivityText;
    private TextView turnSensitivityText;
    private SeekBar speedSeekBar;
    private double speedSensitivity = 1;
    private SeekBar turnSeekBar;

    private Button connectButton;

    TimerTask pingTimerTask;
    Timer pingTimer;

    int nPing = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rover);
        mApp = (MasterApplication)getApplication();

        wifiLogoView = (ImageView)findViewById(R.id.wifiLogoView);
        bluetoothLogoView = (ImageView)findViewById(R.id.bluetoothLogoView);
        turnSensitivityText = (TextView) findViewById(R.id.turnSensitivityText);
        speedSensitivityText = (TextView) findViewById(R.id.speedSensitivityText);
        speedSeekBar = (SeekBar)findViewById(R.id.speedSeekBar);
        speedSeekBar.setProgress(50); //set it to the mid-value (scalara = 1.0 = 50/50)
        turnSeekBar = (SeekBar)findViewById(R.id.turnSeekBar);
        connectButton = (Button)findViewById(R.id.connectButton);

        if(mApp.mP2pConnected) {
            wifiLogoView.setVisibility(View.VISIBLE);
        }
        else {
            wifiLogoView.setVisibility(View.GONE);
        }

        PreferenceManager.setDefaultValues(this, R.xml.settings_activity, false);

        if (savedInstanceState != null) {
            pendingRequestEnableBt = savedInstanceState.getBoolean(SAVED_PENDING_REQUEST_ENABLE_BT);
        }
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            final String no_bluetooth ="BT unsupported on this device";
            Utils.log(no_bluetooth);
        }

        if (btAdapter == null) return;
        if (!btAdapter.isEnabled() && !pendingRequestEnableBt) {
            pendingRequestEnableBt = true;
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            Log.d("BT", "Setting up BT adapter");
        }

        if (mHandler == null) mHandler = new BluetoothResponseHandler(this);
        //else mHandler.setTarget(this);

        if (isConnected() && (savedInstanceState != null)) {
            setDeviceName(savedInstanceState.getString(DEVICE_NAME));
        } else
            Log.d("BT", "MSG_NOT_CONNECTED");
            //getSupportActionBar().setSubtitle(MSG_NOT_CONNECTED);

        connectButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                startDeviceListActivity();
            }
        });

        pingTimer = new Timer();

        pingTimerTask = new TimerTask(){
            @Override
            public void run() {
                startPingProcess();
            }
        };

        jsData = (TextView)findViewById(R.id.jsData);

        turnSensitivityText.setText("CURRENTLY DISABLED");

        speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                speedSensitivityText.setText("Speed sensitivity: " + ((double)progress)/50.0);
                speedSensitivity = progress/50.0;
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        turnSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                //turnSensitivityText.setText("Turn sensitivity: " + progress);
                //turnSensitivityText.setText("CURRENTLY DISABLED");
                //turnSensitivity = progress/100.0;
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });


        if( mCamFrag == null ){
            mCamFrag = CameraFragment.newInstance(this);
        }
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(R.id.frag_cam, mCamFrag, "cam_frag");
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerActivityToService(true);
    }

    boolean isAdapterReady() {
        return (btAdapter != null) && (btAdapter.isEnabled());
    }

    public void showJSData(final int x, final int y){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String jsString;
                jsString = "X : ";
                jsString = jsString.concat(String.valueOf(x) + "\n");
                jsString = jsString.concat("Y : " + String.valueOf(y) + "\n");
                jsData.setText(jsString);
            }
        });
    }

    protected void registerActivityToService(boolean register){
        if( ConnectionService.getInstance() != null ){
            Message msg = ConnectionService.getInstance().getHandler().obtainMessage();
            msg.what = MSG_REGISTER_ROVER_ACTIVITY;
            msg.obj = this;
            msg.arg1 = register ? 1 : 0;
            ConnectionService.getInstance().getHandler().sendMessage(msg);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_PENDING_REQUEST_ENABLE_BT, pendingRequestEnableBt);
        outState.putString(DEVICE_NAME, deviceName);
    }

    private boolean isConnected() {
        return (connector != null) && (connector.getState() == DeviceConnector.STATE_CONNECTED);
    }

    private void stopConnection() {
        if (connector != null) {
            connector.stop();
            connector = null;
            deviceName = null;
        }
    }

    private void startDeviceListActivity() {
        if(Debug.DEBUG)
            Log.d("BT", "startDeviceListActivity: entered");
        stopConnection();
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }

    public void pushOutMessage(byte[] data) {
        Message msg = ConnectionService.getInstance().getHandler().obtainMessage();
        msg.what = MSG_PUSHOUT_DATA;
        msg.obj = data;
        ConnectionService.getInstance().getHandler().sendMessage(msg);
    }

    public void pushOutPingData(byte[] data) {
        Message msg = ConnectionService.getInstance().getHandler().obtainMessage();
        msg.what = MSG_PUSHOUT_DATA;
        msg.obj = data;
        msg.arg1 = 3;
        ConnectionService.getInstance().getHandler().sendMessage(msg);
    }

    @Override //not really getting image... sending it to the main activity interface through getImage
    public void getImage(byte[] data) {
       pushOutMessage(data);
    }

    @Override
    public void onStart() {
        super.onStart();
        final String mode = Utils.getPrefence(this, getString(R.string.pref_commands_mode));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = btAdapter.getRemoteDevice(address);
                    if (isAdapterReady() && (connector == null))
                        setupConnector(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                pendingRequestEnableBt = false;
                if (resultCode != Activity.RESULT_OK) {
                    Utils.log("BT not enabled");
                }
                break;
        }
    }

    private void setupConnector(BluetoothDevice connectedDevice) {
        stopConnection();
        try {
            String emptyName = getString(R.string.empty_device_name);
            DeviceData data = new DeviceData(connectedDevice, emptyName);
            connector = new DeviceConnector(data, mHandler);
            connector.connect();
        } catch (IllegalArgumentException e) {
            Utils.log("setupConnector failed: " + e.getMessage());
        }
    }

    public void sendCommand(View view) {
        byte[] command = null;
            if (isConnected()) {
                connector.write(command);
            }
    }

    public void sendMotionCommand(int lWheel, int rWheel) {

        //sensitivity multiplier from 0 to 2 from seekbar
        //lWheel *= speedSensitivity;
        //rWheel *= speedSensitivity;

        int MAX = 600, MIN = -600;
        //bad things will happen if these are not set immediately before sending the command
        if(lWheel > MAX)
            lWheel = MAX;
        if(lWheel < MIN)
            lWheel = MIN;
        if(rWheel > MAX)
            rWheel = MAX;
        if(rWheel < MIN)
            rWheel = MIN;
        final int drWheel = rWheel;
        final int dlWheel = lWheel;

        if(Debug.DEBUG) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String jsString = (String) jsData.getText();
                    jsString = jsString.concat("\nRWheel: " + drWheel + "LWheel: " + dlWheel);
                    jsData.setText(jsString);
                }
            });
        }

        if (isConnected()) {
            //mesage consists of a header: VAPT0001, followed immediately by the dhb10_com input string and termination character
            byte[] command = String.format("VAPT0001GOSPD %d %d\r",lWheel,rWheel).getBytes();
            connector.write(command);
        }
    }

    void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
        //getSupportActionBar().setSubtitle(deviceName);
    }

    void startPingProcess(){
        //send the rover a ping every N milliseconds, if the rover doesn't receive this ping within a set time, it disables the motors
        if (isConnected()) {
            //mesage consists of a header: VAPT0002, that's all is needed for this message header
            byte[] command = String.format("VAPT0002\r").getBytes();
            connector.write(command);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    jsData.setText("SENT #:" + nPing++);
                }
            });

        }else{
            Toast.makeText(this,"Error, ping attempted but BT not connected",Toast.LENGTH_SHORT);
        }
    }

    private class BluetoothResponseHandler extends Handler {
        private WeakReference<RoverActivity> mActivity;

        public BluetoothResponseHandler(RoverActivity activity) {
            mActivity = new WeakReference<RoverActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            RoverActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:
                        Utils.log("MESSAGE_STATE_CHANGE: " + msg.arg1);
                        //final ActionBar bar = activity.getSupportActionBar();
                        switch (msg.arg1) {
                            case DeviceConnector.STATE_CONNECTED:
                                bluetoothLogoView.setVisibility(View.VISIBLE);
                                pingTimer.scheduleAtFixedRate(pingTimerTask, 0, 250);
                                //bar.setSubtitle(MSG_CONNECTED);
                                break;
                            case DeviceConnector.STATE_CONNECTING:
                                //bar.setSubtitle(MSG_CONNECTING);
                                break;
                            case DeviceConnector.STATE_NONE:
                                //bar.setSubtitle(MSG_NOT_CONNECTED);
                                break;
                            case DeviceConnector.STATE_DISCONNECTED:
                                bluetoothLogoView.setVisibility(View.GONE);
                                break;
                        }
                        break;

                    case MESSAGE_READ:
                        final String readMessage = (String) msg.obj;
                        byte[] data = readMessage.getBytes();
                        pushOutPingData(data);
                        break;

                    case MESSAGE_DEVICE_NAME:
                        activity.setDeviceName((String) msg.obj);
                        break;

                    case MESSAGE_WRITE:
                        // stub
                        break;

                    case MESSAGE_TOAST:
                        // stub
                        break;
                }
            }
        }
    }

    public int byteArrayToInt(byte[] b) {
        return b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
    }
}