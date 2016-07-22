package com.virtualapt.rover.cheburashka;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
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

    private ImageView bluetoothLogoView;

    private char kp_angle = 0, ki_angle = 0, kp_dist = 0, ki_dist = 0;

    private TextView ki_angle_textView;
    private TextView kp_angle_textView;
    private TextView ki_dist_textView;
    private TextView kp_dist_textView;

    private SeekBar kp_angle_seekBar;
    private SeekBar ki_angle_seekBar;
    private SeekBar kp_dist_seekBar;
    private SeekBar ki_dist_seekBar;

    private SeekBar speedSeekBar;
    private double speedSensitivity = 1;
    private SeekBar turnSeekBar;

    private Button connectButton;

    TimerTask pingTimerTask;
    Timer pingTimer;
    int nPing = 0;

    String jsString;
    JoyStickClass js;
    RelativeLayout layout_joystick;

    static int prevbytes = 0;
    static double rxRate = 0;

    static long prevTime = System.currentTimeMillis();

    private ImageButton stopButton;

    private ImageButton leftTurnButton;
    private ImageButton rightTurnButton;

    private int initLeftY;
    private int lastLeftY;
    private boolean initLeftON = false;
    private int initRightY;
    private int lastRightY;
    private boolean initRightON = false;

    private boolean showJSStats = false;
    private boolean showRxStats = false;

    private TextView jsData;

    private SeekBar t_seekBar;

    private int lWheelSpeed;
    private int rWheelSpeed;
    private double time_desired;

    private TextView lWheel_label;
    private TextView rWheel_label;
    private TextView t_desired_label;

    private Button sendCommandButton;

    TimerTask stopTimerTask;
    Timer stopTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_remote_controller);

        bluetoothLogoView = (ImageView)findViewById(R.id.bluetoothLogoView);

        kp_angle_textView = (TextView) findViewById(R.id.kp_angle_textView);
        kp_angle_seekBar = (SeekBar)findViewById(R.id.kp_angle_seekBar);

        ki_angle_textView = (TextView) findViewById(R.id.ki_angle_textView);
        ki_angle_seekBar = (SeekBar)findViewById(R.id.ki_angle_seekBar);

        kp_dist_textView = (TextView) findViewById(R.id.kp_dist_textView);
        kp_dist_seekBar = (SeekBar)findViewById(R.id.kp_dist_seekBar);

        ki_dist_textView = (TextView) findViewById(R.id.ki_dist_textView);
        ki_dist_seekBar = (SeekBar)findViewById(R.id.ki_dist_seekBar);

        connectButton = (Button)findViewById(R.id.connectButton);

        pingTimer = new Timer();

        pingTimerTask = new TimerTask(){
            @Override
            public void run() {
                startPingProcess();
            }
        };

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

        jsData = (TextView)findViewById(R.id.jsData);

        kp_angle_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){

                kp_angle = (char)(progress);
                kp_angle_textView.setText("kp_angle: " + (int)kp_angle);

                String str = new String("VAPT0003");// + kp_angle + ki_angle + kp_dist + ki_dist + '\r');

                byte[] command = str.getBytes();

                byte [] c= new byte[command.length+5];
                for(int i=0;i<command.length;i++){
                    c[i]=command[i];
                }
                c[command.length] = (byte)kp_angle;
                c[command.length+1] = (byte)ki_angle;
                c[command.length+2] = (byte)kp_dist;
                c[command.length+3] = (byte)ki_dist;
                c[command.length+4] = (byte)'\r';

                String strs = new String(c);

                ki_angle_textView.setText(strs);

                connector.write(c);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ki_angle_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){

                ki_angle = (char)(progress);
                ki_angle_textView.setText("ki_angle: " + (int)ki_angle);

                String str = new String("VAPT0003");

                byte[] command = str.getBytes();

                byte [] c= new byte[command.length+5];
                for(int i=0;i<command.length;i++){
                    c[i]=command[i];
                }
                c[command.length] = (byte)kp_angle;
                c[command.length+1] = (byte)ki_angle;
                c[command.length+2] = (byte)kp_dist;
                c[command.length+3] = (byte)ki_dist;
                c[command.length+4] = (byte)'\r';

                connector.write(c);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        kp_dist_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){

                kp_dist = (char)(progress);
                kp_dist_textView.setText("kp_dist: " + kp_dist);

                String str = new String("VAPT0003");

                byte[] command = str.getBytes();

                byte [] c= new byte[command.length+5];
                for(int i=0;i<command.length;i++){
                    c[i]=command[i];
                }
                c[command.length] = (byte)kp_angle;
                c[command.length+1] = (byte)ki_angle;
                c[command.length+2] = (byte)kp_dist;
                c[command.length+3] = (byte)ki_dist;
                c[command.length+4] = (byte)'\r';

                connector.write(c);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ki_dist_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){


                ki_dist = (char)(progress);
                ki_dist_textView.setText("ki_dist: " + ki_dist);

                String str = new String("VAPT0003");

                byte[] command = str.getBytes();

                byte [] c= new byte[command.length+5];
                for(int i=0;i<command.length;i++){
                    c[i]=command[i];
                }
                c[command.length] = (byte)kp_angle;
                c[command.length+1] = (byte)ki_angle;
                c[command.length+2] = (byte)kp_dist;
                c[command.length+3] = (byte)ki_dist;
                c[command.length+4] = (byte)'\r';

                connector.write(c);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

/*
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
        */
/*
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
*/

        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();


        //degreesLeft = (Button) findViewById(R.id.degrees_left_90);
        //degreesRight = (Button) findViewById(R.id.degrees_right_90);
        leftTurnButton = (ImageButton) findViewById(R.id.left_turn_button);
        rightTurnButton = (ImageButton) findViewById(R.id.right_turn_button);

        stopButton = (ImageButton)findViewById(R.id.stop_button);

        //lWheel_seekBar = (SeekBar)findViewById(R.id.kp_dist_seekBar);
        //rWheel_seekBar = (SeekBar)findViewById(R.id.ki_dist_seekBar);
        t_seekBar = (SeekBar)findViewById(R.id.t_SeekBar);

        //lWheel_label = (TextView)findViewById(R.id.textView);
        //rWheel_label = (TextView)findViewById(R.id.textView2);
        t_desired_label = (TextView)findViewById(R.id.textView3);

        sendCommandButton = (Button)findViewById(R.id.commandButton);

        //degreesLeft.setText("90");
        //degreesRight.setText("90");
        stopTimer = new Timer();
/*
        lWheel_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                lWheelSpeed = (int)((progress - 50)*3);
                lWheel_label.setText("Left Wheel: " +lWheelSpeed);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        rWheel_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                rWheelSpeed = (int)((progress - 50)*3);
                rWheel_label.setText("Right Wheel: " +rWheelSpeed);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
*/
        t_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                time_desired = (((double)progress)/(double)25.0) + 2.0;
                t_desired_label.setText("Action time: " + time_desired);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        sendCommandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTimerTask = new TimerTask(){
                    @Override
                    public void run() {
                        sendMotionCommand(0,0);
                        curve1();
                    }
                };
                //sendMotionCommand(lWheelSpeed,rWheelSpeed);

                //stopTimer.schedule(stopTimerTask,(int)(time_desired*1000.0));
                sendMotionCommand((int)travelDistInTime(36,2000),(int)travelDistInTime(36,2000));
                stopTimer.schedule(stopTimerTask,(int)(2*1000.0));
                stopTimerTask = null;
            }
        });

        leftTurnButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    //TODO call left rotate commands
                    //degreesLeft.setVisibility(View.VISIBLE);
                    //leftTurnButton.setVisibility(View.VISIBLE);
                    //v.setVisibility(View.VISIBLE);
                    //degreesLeft.setPressed(false);
                }
                if(event.getAction() == MotionEvent.ACTION_MOVE) {
                    if(!initLeftON) {
                        initLeftON = true;
                        initLeftY = (int) event.getY();
                    }
                    else{
                        lastLeftY = (int) event.getY();
                        rotateLeftButton2Command(initLeftY,lastLeftY);
                    }

                   /*
                    randData.setText("" + event.getX() + "   " + event.getY());
                    if(event.getX() > 0 && event.getX() < 280 && event.getY() < 0 && event.getY() > -280){
                        degreesLeft.setPressed(true);
                    }else {
                        degreesLeft.setPressed(false);
                    }
                    */
                }
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    sendMotionCommand(0,0);
                    //if(event.getX() > 0 && event.getX() < 280 && event.getY() < 0 && event.getY() > -280){
                    //    degreesLeft.setPressed(false);
                    //}
                    //degreesLeft.setVisibility(View.GONE);
                    //leftTurnButton.setVisibility(View.VISIBLE);
                    //v.setVisibility(View.VISIBLE);
                }
                return false;
            }
        });
        rightTurnButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){

                }
                if(event.getAction() == MotionEvent.ACTION_MOVE) {
                    if(!initRightON) {
                        initRightON = true;
                        initRightY = (int) event.getY();
                    }
                    else{
                        lastRightY = (int) event.getY();
                        rotateRightButton2Command(initRightY,lastRightY);
                    }
                }
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    sendMotionCommand(0,0);
                }
                return false;
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                String str = new String("VAPT0004\r");

                byte[] command = str.getBytes();

                connector.write(command);

                //sendMotionCommand(0,0);
            }
        });

        jsData = (TextView)findViewById(R.id.jsData);

        layout_joystick = (RelativeLayout)findViewById(R.id.layout_joystick);

        js = new JoyStickClass(getApplicationContext()
                , layout_joystick, R.drawable.image_button);
        js.setStickSize(150, 150);
        js.setLayoutSize(500, 500);
        js.setLayoutAlpha(150);
        js.setStickAlpha(100);
        js.setOffset(90);
        js.setMinimumDistance(50);

        layout_joystick.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View arg0, MotionEvent arg1) {
                // computes and displays how many KB/s are being received from the rover
                if(showRxStats) {
                    int nowbytes = (int) TrafficStats.getTotalRxBytes();
                    if (nowbytes - prevbytes > 500000) {
                        rxRate = (nowbytes - prevbytes) / (System.currentTimeMillis() - prevTime);
                        prevTime = System.currentTimeMillis();
                        prevbytes = nowbytes;
                        jsData.setText(String.valueOf(rxRate));
                    }
                }
                js.drawStick(arg1);
                //  if(arg1.getAction() == MotionEvent.ACTION_DOWN && arg1.getAction() == MotionEvent.ACTION_MOVE) {
                    if(arg1.getAction() == MotionEvent.ACTION_MOVE) {
                        if(showJSStats) {
                            jsString = "X : ";
                            jsString = jsString.concat(String.valueOf(js.getX()) + "\n");
                            jsString = jsString.concat("Y : " + String.valueOf(js.getY()) + "\n");
                            jsString = jsString.concat("Angle : " + String.valueOf(js.getAngle()) + "\n");
                            jsString = jsString.concat("Distance : " + String.valueOf(js.getDistance()) + "\n");
                            jsData.setText(jsString);
                        }

                    int direction = js.get8Direction();
                    if(direction == JoyStickClass.STICK_UP) {
                        //Log.d(TAG,"Direction : Up");
                    } else if(direction == JoyStickClass.STICK_UPRIGHT) {
                        //Log.d(TAG,"Direction : Up Right");
                    } else if(direction == JoyStickClass.STICK_RIGHT) {
                        //Log.d(TAG,"Direction : Right");
                    } else if(direction == JoyStickClass.STICK_DOWNRIGHT) {
                        //Log.d(TAG,"Direction : Down Right");
                    } else if(direction == JoyStickClass.STICK_DOWN) {
                        //Log.d(TAG,"Direction : Down");
                    } else if(direction == JoyStickClass.STICK_DOWNLEFT) {
                       // Log.d(TAG,"Direction : Down Left");
                    } else if(direction == JoyStickClass.STICK_LEFT) {
                       // Log.d(TAG,"Direction : Left");
                    } else if(direction == JoyStickClass.STICK_UPLEFT) {
                      //  Log.d(TAG,"Direction : Up Left");
                    } else if(direction == JoyStickClass.STICK_NONE) {
                      //  Log.d(TAG,"Direction : Center");
                    }
                        jsCoords2MotionCommand(js);
                } else if(arg1.getAction() == MotionEvent.ACTION_UP) {
                        if(showJSStats) {
                            jsString = "X : ";
                            jsString = jsString.concat(String.valueOf(js.getX()) + "\n");
                            jsString = jsString.concat("Y : " + String.valueOf(js.getY()) + "\n");
                            jsString = jsString.concat("Angle : " + String.valueOf(js.getAngle()) + "\n");
                            jsString = jsString.concat("Distance : " + String.valueOf(js.getDistance()) + "\n");
                            jsData.setText(jsString);
                        }
                        sendMotionCommand(0,0);
                } else if(arg1.getAction() == MotionEvent.ACTION_CANCEL){
                        sendMotionCommand(0,0);
                    }
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    boolean isAdapterReady() {
        return (btAdapter != null) && (btAdapter.isEnabled());
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

    private void rotateLeftButton2Command(int init, int last){
        if(init - last > 0){
            //reduce senistivity a bit
            sendMotionCommand((int)(-0.2*(init - last)),(int)(0.2*(init - last)));
        }
    }
    private void rotateRightButton2Command(int init, int last){
        if(init - last > 0){
            //reduce senistivity a bit
            sendMotionCommand((int)(0.2*(init - last)),(int)(-0.2*(init - last)));
        }
    }
    public void jsCoords2MotionCommand(JoyStickClass js) {

        //double x = js.getX() * .9;
        //double x = js.getX() * .5;
        //double y = -1 * js.getY() * .4; //JoyStick reverses y axis due to landscape layout, so re-invert it back here

        double x = js.getX()*.5;
        double y = js.getY()*-0.5;

        int lWheelSpeed, rWheelSpeed;

        //double x_cubed = 0.000001*x*x*x;
        //double x_cubed = 0.00001*x*x*x;

        //if we're close enough to the vertical axis just go straight
        if(x < 20 && x > -20) {
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
        /*
        if (y > 0) {
            lWheelSpeed = (int) (y + x_cubed);
            rWheelSpeed = (int) (y - x_cubed);
        } else {
            lWheelSpeed = (int) (y - x_cubed);
            rWheelSpeed = (int) (y + x_cubed);
        }
        */

        jsData.setText("lWheelSpeed: " + lWheelSpeed + " rWheelSpeed: " + rWheelSpeed);
        //jsData.setText("Front: " + frontPingCM + "\nLeft: " + leftPingCM + "\nRight: " + rightPingCM + "\nBack: " + backPingCM);
        sendMotionCommand(lWheelSpeed,rWheelSpeed);
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

    @Override
    public void onStart() {
        super.onStart();
        final String mode = Utils.getPrefence(this, getString(R.string.pref_commands_mode));
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

    private class BluetoothResponseHandler extends Handler {
        private WeakReference<RemoteControllerActivity> mActivity;

        public BluetoothResponseHandler(RemoteControllerActivity activity) {
            mActivity = new WeakReference<RemoteControllerActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            RemoteControllerActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:
                        Utils.log("MESSAGE_STATE_CHANGE: " + msg.arg1);
                        //final ActionBar bar = activity.getSupportActionBar();
                        switch (msg.arg1) {
                            case DeviceConnector.STATE_CONNECTED:
                                bluetoothLogoView.setVisibility(View.VISIBLE);
                                connectButton.setText(R.string.disconnect_button);
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
                                connectButton.setText(R.string.connect_button);
                                break;
                        }
                        break;

                    case MESSAGE_READ:
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

    byte[] toBytes(int i)
    {
        byte[] result = new byte[4];

        result[0] = (byte) (i >> 24);
        result[1] = (byte) (i >> 16);
        result[2] = (byte) (i >> 8);
        result[3] = (byte) (i /*>> 0*/);

        return result;
    }

    void curve1(){
        stopTimerTask = new TimerTask(){
            @Override
            public void run() {
                sendMotionCommand(0,0);
                curve2();
            }
        };
        //sendMotionCommand(lWheelSpeed,rWheelSpeed);

        //stopTimer.schedule(stopTimerTask,(int)(time_desired*1000.0));
        sendMotionCommand((int)travelDistInTime((36+7.75)*2*Math.PI*(.25),2469),(int)travelDistInTime((36-7.75)*2*Math.PI*(.25),2469));
        stopTimer.schedule(stopTimerTask, 2469);
        stopTimerTask = null;
    }

    void curve2(){
        stopTimerTask = new TimerTask(){
            @Override
            public void run() {
                sendMotionCommand(0,0);
            }
        };
        //sendMotionCommand(lWheelSpeed,rWheelSpeed);

        //stopTimer.schedule(stopTimerTask,(int)(time_desired*1000.0));
        sendMotionCommand((int)travelDistInTime((36-7.75)*2*Math.PI*(.25),2469),(int)travelDistInTime((36+7.75)*2*Math.PI*(.25),2469));
        stopTimer.schedule(stopTimerTask, 2469);
        stopTimerTask = null;
    }

    private double travelDistInTime(double inch_dist, int milliseconds){
        return (inch_dist/19.635)*144*(1000.0/milliseconds);
    }
}