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

    private ImageView bluetoothLogoView;
    private ImageButton stopButton;
    private ImageButton leftArrow;
    private ImageButton rightArrow;
    private ImageButton upArrow;
    private ImageButton downArrow;
    private Button switchButton;

    private SeekBar speed_seekBar;
    private TextView speedSensitivtyText;
    private double speedSensitivity = 1;

    private TextView BTPingData;
    private TextView jsData;
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
        speedSensitivtyText = (TextView) findViewById(R.id.speedSensitivityText);
        BTPingData = (TextView)findViewById(R.id.BTPingData);
        speed_seekBar = (SeekBar)findViewById(R.id.speed_seekBar);
        bluetoothLogoView = (ImageView)findViewById(R.id.bluetoothLogoView);
        switchButton = (Button)findViewById(R.id.switchButton);
        stopButton = (ImageButton)findViewById(R.id.stop_button);
        upArrow = (ImageButton)findViewById(R.id.up_arrow);
        downArrow = (ImageButton)findViewById(R.id.down_arrow);
        leftArrow = (ImageButton)findViewById(R.id.left_arrow);
        rightArrow = (ImageButton)findViewById(R.id.right_arrow);

        // Set up joystick layout
        jsData = (TextView)findViewById(R.id.jsData);
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
                speedSensitivtyText.setText("Sensitivity: " + speedSensitivity);
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
                Intent intent = new Intent();
                setResult(Activity.RESULT_OK, intent);
                finish();
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

        jsData.setText("lWheelSpeed: " + lWheelSpeed + "\nrWheelSpeed: " + rWheelSpeed);
        sendMotionCommand(lWheelSpeed,rWheelSpeed);
    }

    @Override
    public void onStart() {
        super.onStart();
        final String mode = Utils.getPrefence(this, getString(R.string.pref_commands_mode));
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
            //mesage consists of a header: VAPT0, followed immediately by the dhb10_com input string and termination character
            byte[] command = String.format("VAPT0GOSPD %d %d\r",lWheel,rWheel).getBytes();
            connector.write(command);
        }
    }

    private boolean isConnected() {
        return ((RCApplication) getApplication()).isConnected();
    }

}