package com.virtualapt.rover.cheburashka;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.TrafficStats;
import android.os.Message;
import android.os.Bundle;
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

import java.util.Timer;
import java.util.TimerTask;

import static com.virtualapt.rover.cheburashka.Constants.*;

public class RemoteControllerActivity extends Activity{

    public static final String TAG = "RemoteControllerActivity";

    MasterApplication mApp = null;
    ImageView roverFeed;
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

    public int frontPingCM;
    public int rightPingCM;
    public int leftPingCM;
    public int backPingCM;

    private TextView jsData;

    private SeekBar lWheel_seekBar;
    private SeekBar rWheel_seekBar;
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

        roverFeed = (ImageView)findViewById(R.id.roverFeed);
        roverFeed.setScaleType(ImageView.ScaleType.FIT_XY);
        mApp = (MasterApplication)getApplication();

        //degreesLeft = (Button) findViewById(R.id.degrees_left_90);
        //degreesRight = (Button) findViewById(R.id.degrees_right_90);
        leftTurnButton = (ImageButton) findViewById(R.id.left_turn_button);
        rightTurnButton = (ImageButton) findViewById(R.id.right_turn_button);

        stopButton = (ImageButton)findViewById(R.id.stop_button);

        lWheel_seekBar = (SeekBar)findViewById(R.id.lWheelSeekBar);
        rWheel_seekBar = (SeekBar)findViewById(R.id.rWheelSeekBar);
        t_seekBar = (SeekBar)findViewById(R.id.t_SeekBar);

        lWheel_label = (TextView)findViewById(R.id.textView);
        rWheel_label = (TextView)findViewById(R.id.textView2);
        t_desired_label = (TextView)findViewById(R.id.textView3);

        sendCommandButton = (Button)findViewById(R.id.commandButton);

        //degreesLeft.setText("90");
        //degreesRight.setText("90");
        stopTimer = new Timer();





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
                        pushOutSpeedCommand(0,0);
                        curve1();
                    }
                };
                //pushOutSpeedCommand(lWheelSpeed,rWheelSpeed);

                //stopTimer.schedule(stopTimerTask,(int)(time_desired*1000.0));
                pushOutSpeedCommand((int)travelDistInTime(36,2000),(int)travelDistInTime(36,2000));
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
                    pushOutSpeedCommand(0,0);
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
                    pushOutSpeedCommand(0,0);
                }
                return false;
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                pushOutSpeedCommand(0,0);
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
                        pushOutSpeedCommand(0,0);
                } else if(arg1.getAction() == MotionEvent.ACTION_CANCEL){
                        pushOutSpeedCommand(0,0);
                    }
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerActivityToService(true);
    }

    protected void registerActivityToService(boolean register){
        if( ConnectionService.getInstance() != null ){
            Message msg = ConnectionService.getInstance().getHandler().obtainMessage();
            msg.what = MSG_REGISTER_RC_ACTIVITY;
            msg.obj = this;
            msg.arg1 = register ? 1 : 0;
            ConnectionService.getInstance().getHandler().sendMessage(msg);
        }
    }

    private void rotateLeftButton2Command(int init, int last){
        if(init - last > 0){
            //reduce senistivity a bit
            pushOutSpeedCommand((int)(-0.2*(init - last)),(int)(0.2*(init - last)));
        }
    }
    private void rotateRightButton2Command(int init, int last){
        if(init - last > 0){
            //reduce senistivity a bit
            pushOutSpeedCommand((int)(0.2*(init - last)),(int)(-0.2*(init - last)));
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
        pushOutSpeedCommand(lWheelSpeed,rWheelSpeed);
    }

    //the command should be composed of 8 bytes: 4 for X direction, 4 for Y direction integers
    public void pushOutSpeedCommand(int lWheel, int rWheel) {
        byte[] command = new byte[8];
        System.arraycopy(toBytes(lWheel),0,command,0,4);
        System.arraycopy(toBytes(rWheel),0,command,4,4);
        Message msg = ConnectionService.getInstance().getHandler().obtainMessage();
        msg.what = MSG_PUSHOUT_DATA;
        msg.obj = command;
        msg.arg1 = 1;
        ConnectionService.getInstance().getHandler().sendMessage(msg);
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

    public void showImage(final byte[] data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                roverFeed.setImageBitmap(bitmap);
            }
        });
    }

    void curve1(){
        stopTimerTask = new TimerTask(){
            @Override
            public void run() {
                pushOutSpeedCommand(0,0);
                curve2();
            }
        };
        //pushOutSpeedCommand(lWheelSpeed,rWheelSpeed);

        //stopTimer.schedule(stopTimerTask,(int)(time_desired*1000.0));
        pushOutSpeedCommand((int)travelDistInTime((36+7.75)*2*Math.PI*(.25),2469),(int)travelDistInTime((36-7.75)*2*Math.PI*(.25),2469));
        stopTimer.schedule(stopTimerTask,(int)(2469));
        stopTimerTask = null;
    }

    void curve2(){
        stopTimerTask = new TimerTask(){
            @Override
            public void run() {
                pushOutSpeedCommand(0,0);
            }
        };
        //pushOutSpeedCommand(lWheelSpeed,rWheelSpeed);

        //stopTimer.schedule(stopTimerTask,(int)(time_desired*1000.0));
        pushOutSpeedCommand((int)travelDistInTime((36-7.75)*2*Math.PI*(.25),2469),(int)travelDistInTime((36+7.75)*2*Math.PI*(.25),2469));
        stopTimer.schedule(stopTimerTask,(int)(2469));
        stopTimerTask = null;
    }

    private double travelDistInTime(double inch_dist, int milliseconds){
        return (inch_dist/19.635)*144*(1000.0/milliseconds);
    }
}
