package com.virtualapt.rover.cheburashka;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Timer;
import java.util.TimerTask;


public class AutonomousActivity extends Activity {

    public static final String TAG = "AutonomousActivity";

    private static final String DEVICE_NAME = "DEVICE_NAME";

    // Assign indices to the seek bars
    private enum progBar {KP_ANG, KP_DIST, KI_ANG, KI_DIST, KD_ANG, KD_DIST}

    // Intent request codes
    static final int REQUEST_CONNECT_DEVICE = 1;
    static final int REQUEST_ENABLE_BT = 2;
    static final int REQUEST_RC_MODE = 3;

    Timer updatePingTimer;
    TimerTask updatePingTask;

    boolean camLock = false;
    Timer camLockTimer;

    // Declare/define all of the textViews,  buttons, and seekBars, as well as the
    // changeable values (kp, ki, kd)
    private progBar bar;
    private char kp_angle = 0, ki_angle = 0, kd_angle = 0,
                 kp_dist = 0, ki_dist = 0, kd_dist = 0;

    private TextView kp_angle_textView;
    private TextView ki_angle_textView;
    private TextView kd_angle_textView;
    private TextView kp_dist_textView;
    private TextView ki_dist_textView;
    private TextView kd_dist_textView;
    private TextView BTPingData;
    private TextView commandData;

    private SeekBar kp_angle_seekBar;
    private SeekBar ki_angle_seekBar;
    private SeekBar kd_angle_seekBar;
    private SeekBar kp_dist_seekBar;
    private SeekBar ki_dist_seekBar;
    private SeekBar kd_dist_seekBar;

    private Button connectButton;
    private Button switchButton;
    private Button powerButton;
    private Button captureButton;
    private Button startLidarButton;
    private ImageButton stopButton;
    private ImageView bluetoothLogoView;

    // Initialize Bluetooth objects and variables
    private BluetoothAdapter btAdapter;
    private boolean pendingRequestEnableBt = false;
    private DeviceConnector connector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up according to the XML layout specified for this activity
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_autonomous);

        // Match all of the objects on the screen with their variable names
        bluetoothLogoView = (ImageView)findViewById(R.id.bluetoothLogoView);

        kp_angle_textView = (TextView) findViewById(R.id.kp_angle_textView);
        kp_angle_seekBar = (SeekBar)findViewById(R.id.kp_angle_seekBar);

        ki_angle_textView = (TextView) findViewById(R.id.ki_angle_textView);
        ki_angle_seekBar = (SeekBar)findViewById(R.id.ki_angle_seekBar);

        kd_angle_textView = (TextView) findViewById(R.id.kd_angle_textView);
        kd_angle_seekBar = (SeekBar)findViewById(R.id.kd_angle_seekBar);

        kp_dist_textView = (TextView) findViewById(R.id.kp_dist_textView);
        kp_dist_seekBar = (SeekBar)findViewById(R.id.kp_dist_seekBar);

        ki_dist_textView = (TextView) findViewById(R.id.ki_dist_textView);
        ki_dist_seekBar = (SeekBar)findViewById(R.id.ki_dist_seekBar);

        kd_dist_textView = (TextView) findViewById(R.id.kd_dist_textView);
        kd_dist_seekBar = (SeekBar)findViewById(R.id.kd_dist_seekBar);

        connectButton = (Button)findViewById(R.id.connectButton);
        switchButton = (Button)findViewById(R.id.switchButton);
        captureButton = (Button)findViewById(R.id.camCaptureButton);
        powerButton = (Button)findViewById(R.id.camPowerButton);
        startLidarButton = (Button)findViewById(R.id.startLidarButton);
        stopButton = (ImageButton)findViewById(R.id.stopButton);
        BTPingData = (TextView)findViewById(R.id.BTPingData);
        commandData = (TextView)findViewById(R.id.commandData);

        btAdapter = ((RCApplication) getApplication()).getBTAdapter();

        // If bluetooth is turned off, request to turn it on
        if (!btAdapter.isEnabled() && !pendingRequestEnableBt) {
            pendingRequestEnableBt = true;
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            Log.d("BT", "Setting up BT adapter");
        }

        // Begin updating ping numbers
        updatePingTimer = new Timer();
        updatePingTask = new TimerTask(){
            @Override
            public void run() {
                updatePingText();
            }
        };
        updatePingTimer.scheduleAtFixedRate(updatePingTask, 0, 250);

        // Setup camera locking timer
        camLockTimer = new Timer();
        class CamLockTask extends TimerTask {
            public void run() {
                camLock = false;
            }
        }

        // Set the startDeviceListActivity function as the result of clicking the connect button
        // and disconnect from bluetooth if disconnect button is clicked
        connectButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (getString(R.string.connect).equals(connectButton.getText().toString())) {
                    startDeviceListActivity();
                }
                else {
                    connectButton.setText(R.string.connect);
                    bluetoothLogoView.setVisibility(View.GONE);
                    ((RCApplication) getApplication()).stopConnection();
                }
            }
        });

        // If the switch mode button is clicked, start the RC activity unless
        // the BT is not connected yet
        switchButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (isConnected()) {
                    connector.write("VAPT4".getBytes());
                    startRemoteControllerActivity();
                }
                else {
                    Toast.makeText(AutonomousActivity.this,
                            "Must connect to BT before changing modes", Toast.LENGTH_SHORT).show();
                }
            }
        });

        startLidarButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (isConnected()) {
                    connector.write("VAPT7".getBytes());
                }
                else {
                    Toast.makeText(AutonomousActivity.this,
                            "Must connect to Bluetooth before starting Lidar", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // If power button is clicked, send message to Propeller board to press the power button
        // on the Andoers, if the phone is connected over BT
        powerButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (isConnected()) {
                    if (camLock)
                        Toast.makeText(AutonomousActivity.this,
                                "Wait until camera has finished processing previous command",
                                Toast.LENGTH_SHORT).show();
                    else {
                        connector.write("VAPT5".getBytes());
                        camLock = true;
                        camLockTimer.schedule(new CamLockTask(), 6500);
                        if (getString(R.string.on).equals(powerButton.getText().toString()))
                            powerButton.setText(R.string.off);
                        else
                            powerButton.setText(R.string.on);
                    }
                }

                else {
                    Toast.makeText(AutonomousActivity.this,
                            "Must connect to BT first", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // If capture button is clicked, send message to Propeller board to press the capture button
        // on the Andoers, if the phone is connected over BT
        captureButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (isConnected()) {
                    if (camLock)
                        Toast.makeText(AutonomousActivity.this,
                                "Wait until camera has finished processing previous command",
                                Toast.LENGTH_SHORT).show();
                    else {
                        connector.write("VAPT6".getBytes());
                        camLock = true;
                        camLockTimer.schedule(new CamLockTask(), 250);
                        if (getString(R.string.start).equals(captureButton.getText().toString()))
                            captureButton.setText(R.string.stop);
                        else
                            captureButton.setText(R.string.start);
                    }
                }

                else {
                    Toast.makeText(AutonomousActivity.this,
                            "Must connect to BT first", Toast.LENGTH_SHORT).show();
                }
            }
        });


        // Create listeners for each seek bar that is triggered when their progress is changed.
        // Send packet with new values to the propeller board each time one is changed.
        kp_angle_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){

                // Defines which bar has been changed and sets the text to its new value, then
                // sends the update to the Propeller board
                bar = progBar.KP_ANG;
                kp_angle = (char)(progress);
                kp_angle_textView.setText("kp_angle: " + (int)kp_angle);
                sendValUpdate(bar, kp_angle);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ki_angle_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){

                bar = progBar.KI_ANG;
                ki_angle = (char)(progress);
                ki_angle_textView.setText("ki_angle: " + (int)ki_angle);
                sendValUpdate(bar, ki_angle);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        kd_angle_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){

                bar = progBar.KD_ANG;
                kd_angle = (char)(progress);
                kd_angle_textView.setText("kd_angle: " + (int)kd_angle);
                sendValUpdate(bar, kd_angle);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        kp_dist_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){

                bar = progBar.KP_DIST;
                kp_dist = (char)(progress);
                kp_dist_textView.setText("kp_dist: " + (int)kp_dist);
                sendValUpdate(bar, kp_dist);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ki_dist_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){

                bar = progBar.KI_DIST;
                ki_dist = (char)(progress);
                ki_dist_textView.setText("ki_dist: " + (int)ki_dist);
                sendValUpdate(bar, ki_dist);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        kd_dist_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){

                bar = progBar.KD_DIST;
                kd_dist = (char)(progress);
                kd_dist_textView.setText("kd_dist: " + (int)kd_dist);
                sendValUpdate(bar, kd_dist);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();

        // Initializes listener for the stop button, which sends the stop command to the Propeller
        // board when touched
        stopButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                String str = new String("VAPT0004\r");

                byte[] command = str.getBytes();
                if (isConnected())
                    connector.write(command);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    void updatePingText() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int pings = ((RCApplication) getApplication()).getPings();
                BTPingData.setText("Pings: " + pings);
                if (isConnected() && bluetoothLogoView.getVisibility() == View.GONE) {
                    bluetoothLogoView.setVisibility(View.VISIBLE);
                    connectButton.setText(R.string.disconnect);
                }
            }
        });
    }

    // Assembles the message and sends it to the Propeller board. Also prints it for debugging
    void sendValUpdate(progBar bar, char val) {

        String str = new String("VAPT2");

        byte[] command = str.getBytes();

        byte[] c = new byte[command.length + 3];
        for (int i = 0; i < command.length; i++) {
            c[i] = command[i];
        }
        c[command.length] = (byte) bar.ordinal();
        c[command.length + 1] = (byte) val;
        c[command.length + 2] = (byte) '\r';

        String strs = new String(c);
        commandData.setText("Command: " + strs);

        if (isConnected())
            connector.write(c);
    }

    // Starts the RC activity for controlling the robot via virtual joysticks on the device
    private void startRemoteControllerActivity() {
        if(Debug.DEBUG)
            Log.d(TAG, "startRemoteContollerActivity: entered");
        Intent i = new Intent();
        i.setClass(this, RemoteControllerActivity.class);
        startActivityForResult(i, REQUEST_RC_MODE);
    }

    // Used when DeviceListActivity or RemoteControllerActivity return
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect, set up the connection
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    connector = ((RCApplication) getApplication()).getBTConnector(address);
                }
                break;

            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                pendingRequestEnableBt = false;
                if (resultCode != Activity.RESULT_OK) {
                    Utils.log("BT not enabled");
                }
                break;

            case REQUEST_RC_MODE:
                // When the RC mode activity returns
                break;

        }
    }

    private void startDeviceListActivity() {
        if(Debug.DEBUG)
            Log.d("BT", "startDeviceListActivity: entered");
        ((RCApplication) getApplication()).stopConnection();
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }

    private boolean isConnected() {
        return ((RCApplication) getApplication()).isConnected();
    }

    @Override
    public void onStart() {
        super.onStart();
        final String mode = Utils.getPrefence(this, getString(R.string.pref_commands_mode));
    }
}