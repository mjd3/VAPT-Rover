package com.virtualapt.rover.cheburashka;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.media.Image;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

public class RemoteControllerActivity extends Activity{

    public static final String TAG = "RemoteControllerActivity";

    private static DeviceConnector connector;

    TimerTask updatePingTask;
    Timer updatePingTimer;

    boolean camLock = false;
    Timer camLockTimer;

    private ImageView bluetoothLogoView;
    private ImageButton stopButton;
    private ImageButton leftArrow;
    private ImageButton rightArrow;
    private ImageButton upArrow;
    private ImageButton downArrow;
    private Button switchButton;
    private Button optionsButton;
    private Button startLidarButton;
    private Button dataButton;
    private Button resetButton;
    private Button saveButton;
    private Button powerButton;
    private Button captureButton;
    private boolean lidarStartFlag = false;

    private SeekBar speed_seekBar;
    private TextView speedSensitivityText;
    private double speedSensitivity = 1;

    private TextView BTPingData;
    String jsString;
    JoyStickClass js;
    RelativeLayout layout_joystick;

    static int prevbytes = 0;
    static double rxRate = 0;

    static long prevTime = System.currentTimeMillis();

    private boolean showJSStats = false;
    private boolean showRxStats = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set default result to CANCELED, in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        // Set layout for the RC mode
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_remote_controller);

        Intent i = getIntent();
        if (i == null) {
            Intent intent = new Intent();
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
        connector = ((RCApplication) getApplication()).getBTConnector(null);

        // Find all views in the layout and set their variables
        speedSensitivityText = (TextView) findViewById(R.id.speedSensitivityText);
        BTPingData = (TextView)findViewById(R.id.BTPingData);
        speed_seekBar = (SeekBar)findViewById(R.id.speed_seekBar);
        bluetoothLogoView = (ImageView)findViewById(R.id.bluetoothLogoView);
        switchButton = (Button)findViewById(R.id.switchButton);
        optionsButton = (Button)findViewById(R.id.optionsButton);
        startLidarButton = (Button)findViewById(R.id.startLidarButton);
        dataButton = (Button)findViewById(R.id.dataButton);
        resetButton = (Button)findViewById(R.id.resetButton);
        saveButton = (Button)findViewById(R.id.saveButton);
        captureButton = (Button)findViewById(R.id.camCaptureButton);
        powerButton = (Button)findViewById(R.id.camPowerButton);
        stopButton = (ImageButton)findViewById(R.id.stopButton);
        upArrow = (ImageButton)findViewById(R.id.up_arrow);
        downArrow = (ImageButton)findViewById(R.id.down_arrow);
        leftArrow = (ImageButton)findViewById(R.id.left_arrow);
        rightArrow = (ImageButton)findViewById(R.id.right_arrow);

        // Setup camera locking timer
        camLockTimer = new Timer();
        class CamLockTask extends TimerTask {
            public void run() {
                camLock = false;
            }
        }

        // Set up joystick layout
        layout_joystick = (RelativeLayout)findViewById(R.id.layout_joystick);
        js = new JoyStickClass(getApplicationContext(),
                layout_joystick, R.drawable.image_button);
        js.setStickSize(150, 150);
        js.setLayoutSize(600, 600);
        js.setLayoutAlpha(150);
        js.setStickAlpha(100);
        js.setOffset(90);
        js.setMinimumDistance(50);

        // Speed sensitivity bar listener; changing progress updates the sensitivity
        speed_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){

                speedSensitivity = (double)(progress)/50.0;
                speedSensitivityText.setText("Sensitivity: " + speedSensitivity);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Set up listener for stop button
        stopButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                String str = new String("VAPT3\r");

                byte[] command = str.getBytes();

                connector.write(command);
            }
        });

        // If the switch mode button is clicked, end the RC activity and return to the Autonomous
        // Mode activity
        switchButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                connector.write("VAPT5".getBytes());
                Intent intent = new Intent();
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        });

        // If the options button is clicked, cycle through different options menus
        optionsButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (getString(R.string.lidar_options).equals(optionsButton.getText().toString())) {
                    optionsButton.setText(R.string.data_options);
                    startLidarButton.setVisibility(View.GONE);
                    dataButton.setVisibility(View.VISIBLE);
                    resetButton.setVisibility(View.VISIBLE);
                    saveButton.setVisibility(View.VISIBLE);
                }
                else if (getString(R.string.data_options).equals(optionsButton.getText().toString())) {
                    optionsButton.setText(R.string.cam_options);
                    dataButton.setVisibility(View.GONE);
                    resetButton.setVisibility(View.GONE);
                    saveButton.setVisibility(View.GONE);
                    powerButton.setVisibility(View.VISIBLE);
                    captureButton.setVisibility(View.VISIBLE);
                }
                else if (lidarStartFlag){
                    optionsButton.setText(R.string.data_options);
                    powerButton.setVisibility(View.GONE);
                    captureButton.setVisibility(View.GONE);
                    dataButton.setVisibility(View.VISIBLE);
                    resetButton.setVisibility(View.VISIBLE);
                    saveButton.setVisibility(View.VISIBLE);
                }
                else {
                    optionsButton.setText(R.string.lidar_options);
                    powerButton.setVisibility(View.GONE);
                    captureButton.setVisibility(View.GONE);
                    startLidarButton.setVisibility(View.VISIBLE);
                }
            }
        });

        startLidarButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (isConnected()) {
                    connector.write("VAPT6".getBytes());
                    lidarStartFlag = true;
                }
            }
        });

        dataButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (isConnected()) {
                    if (lidarStartFlag) {
                        connector.write("VAPT7".getBytes());
                        if (getString(R.string.start_data).equals(dataButton.getText().toString()))
                            dataButton.setText(R.string.stop_data);
                        else
                            dataButton.setText(R.string.start_data);
                    }
                    else
                        Toast.makeText(RemoteControllerActivity.this,
                                "Must start Lidar to start data collection", Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(RemoteControllerActivity.this,
                            "Must connect to Bluetooth before collecting data", Toast.LENGTH_SHORT).show();
                }
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (isConnected()) {
                    if (lidarStartFlag && getString(R.string.start_data).equals(dataButton.getText().toString())) {
                        connector.write("VAPT8".getBytes());
                    }
                    else
                        Toast.makeText(RemoteControllerActivity.this,
                                "Must start and stop collecting data before resetting", Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(RemoteControllerActivity.this,
                            "Must connect to Bluetooth before collecting data", Toast.LENGTH_SHORT).show();
                }
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (isConnected()) {
                    if (lidarStartFlag) {
                        if (getString(R.string.start_data).equals(dataButton.getText().toString()))
                            connector.write("VAPT9".getBytes());
                        else
                            Toast.makeText(RemoteControllerActivity.this,
                                    "Must stop collecting data to save", Toast.LENGTH_SHORT).show();
                    }
                    else
                        Toast.makeText(RemoteControllerActivity.this,
                                "Must start Lidar to save data", Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(RemoteControllerActivity.this,
                            "Must connect to Bluetooth before saving data", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // If power button is clicked, send message to Propeller board to press the power button
        // on the Andoers, if the phone is connected over BT
        powerButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (isConnected()) {
                    if (camLock) {
                        Toast toast = Toast.makeText(RemoteControllerActivity.this,
                                "Wait until camera has finished processing previous command",
                                Toast.LENGTH_SHORT);
                        TextView toastView = (TextView) toast.getView().findViewById(android.R.id.message);
                        if (toastView != null) toastView.setGravity(Gravity.CENTER);
                        toast.show();
                    }
                    else {
                        connector.write("VAPTA".getBytes());
                        camLock = true;
                        camLockTimer.schedule(new CamLockTask(), 6500);
                        if (getString(R.string.cam_on).equals(powerButton.getText().toString()))
                            powerButton.setText(R.string.cam_off);
                        else
                            powerButton.setText(R.string.cam_on);
                    }
                }

                else {
                    Toast toast = Toast.makeText(RemoteControllerActivity.this,
                            "Phone must first be connected over Bluetooth",
                            Toast.LENGTH_SHORT);
                    TextView toastView = (TextView) toast.getView().findViewById(android.R.id.message);
                    if (toastView != null) toastView.setGravity(Gravity.CENTER);
                    toast.show();
                }
            }
        });

        // If capture button is clicked, send message to Propeller board to press the capture button
        // on the Andoers, if the phone is connected over BT
        captureButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (isConnected()) {
                    if (camLock) {
                        Toast toast = Toast.makeText(RemoteControllerActivity.this,
                                "Wait until camera has finished processing previous command",
                                Toast.LENGTH_SHORT);
                        TextView toastView = (TextView) toast.getView().findViewById(android.R.id.message);
                        if (toastView != null) toastView.setGravity(Gravity.CENTER);
                        toast.show();
                    }
                    else {
                        connector.write("VAPTB".getBytes());
                        camLock = true;
                        camLockTimer.schedule(new CamLockTask(), 250);
                        if (getString(R.string.cam_capture_start).equals(captureButton.getText().toString()))
                            captureButton.setText(R.string.cam_capture_stop);
                        else
                            captureButton.setText(R.string.cam_capture_start);
                    }
                }

                else {
                    Toast toast = Toast.makeText(RemoteControllerActivity.this,
                            "Phone must first be connected over Bluetooth",
                            Toast.LENGTH_SHORT);
                    TextView toastView = (TextView) toast.getView().findViewById(android.R.id.message);
                    if (toastView != null) toastView.setGravity(Gravity.CENTER);
                    toast.show();
                }
            }
        });

        upArrow.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent motion) {
                if(motion.getAction() == MotionEvent.ACTION_MOVE)
                    sendMotionCommand(150, 150);
                else
                    sendMotionCommand(0, 0);
                return true;
            }
        });

        downArrow.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent motion) {
                if(motion.getAction() == MotionEvent.ACTION_MOVE)
                    sendMotionCommand(-150, -150);
                else
                    sendMotionCommand(0, 0);
                return true;
            }
        });

        rightArrow.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent motion) {
                if(motion.getAction() == MotionEvent.ACTION_MOVE)
                    sendMotionCommand(150, -150);
                else
                    sendMotionCommand(0, 0);
                return true;
            }
        });

        leftArrow.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent motion) {
                if(motion.getAction() == MotionEvent.ACTION_MOVE)
                    sendMotionCommand(-150, 150);
                else
                    sendMotionCommand(0, 0);
                return true;
            }
        });

        layout_joystick.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View arg0, MotionEvent arg1) {

                js.drawStick(arg1);
                if(arg1.getAction() == MotionEvent.ACTION_MOVE)
                    jsCoords2MotionCommand(js);
                else if(arg1.getAction() == MotionEvent.ACTION_UP)
                    sendMotionCommand(0,0);
                else if(arg1.getAction() == MotionEvent.ACTION_CANCEL)
                    sendMotionCommand(0,0);
                return true;
            }
        });

        // Begin updating ping numbers
        updatePingTimer = new Timer();
        updatePingTask = new TimerTask(){
            @Override
            public void run() {
                updatePingText();
            }
        };
        updatePingTimer.scheduleAtFixedRate(updatePingTask, 0, 250);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void updatePingText() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int pings = ((RCApplication) getApplication()).getPings();
                BTPingData.setText("Pings: " + pings);
                if (isConnected()) {
                    bluetoothLogoView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    public void jsCoords2MotionCommand(JoyStickClass js) {

       //JoyStick reverses y axis due to landscape layout, so re-invert it back here
        double x = js.getX()*0.5;
        double y = js.getY()*-0.5;

        int lWheelSpeed, rWheelSpeed;

        //if we're close enough to the vertical axis just go straight
        if(x < 10 && x > -10) {
            lWheelSpeed = (int) y;
            rWheelSpeed = (int) y;
        } //otherwise apply x axis position to turn
        else if (y > 0) {
            lWheelSpeed = (int) (y + x);
            rWheelSpeed = (int) (y - x);
        } else {
            lWheelSpeed = (int) (y - x);
            rWheelSpeed = (int) (y + x);
        }
        sendMotionCommand(lWheelSpeed,rWheelSpeed);
    }

    public void sendMotionCommand(int lWheel, int rWheel) {

        //sensitivity multiplier from 0 to 2 from seekbar
        lWheel *= speedSensitivity;
        rWheel *= speedSensitivity;

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

        if (isConnected()) {
            //mesage consists of a header: VAPT0, followed immediately by the dhb10_com input string and termination character
            byte[] command = String.format("VAPT0GOSPD %d %d\r",lWheel,rWheel).getBytes();
            connector.write(command);
        }
    }

    private boolean isConnected() {
        return ((RCApplication) getApplication()).isConnected();
    }

}