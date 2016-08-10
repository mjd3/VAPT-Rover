package com.virtualapt.rover.cheburashka;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import android.os.Looper;
import java.util.Timer;
import java.util.TimerTask;

public class RCApplication extends Application {

    DeviceConnector connector;
    BluetoothAdapter btAdapter;
    private static final Handler mHandler = new Handler();
    private static BluetoothResponseHandler bHandler;
    private String deviceName;
    TimerTask pingTimerTask;
    Timer pingTimer;
    int nPing = 0;

    // Message types sent from the DeviceConnector Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;



    @Override
    public void onCreate() {
        super.onCreate();

        // Begin pinging task
        pingTimer = new Timer();
        pingTimerTask = new TimerTask(){
            @Override
            public void run() {
                startPingProcess();
            }
        };

        // Make sure device is bluetooth-enabled
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            final String no_bluetooth ="BT unsupported on this device";
            Utils.log(no_bluetooth);
        }
        if (btAdapter == null) return;

        // Set up Bluetooth handler
        if (bHandler == null) bHandler = new BluetoothResponseHandler();
    }

    void startPingProcess(){
        //send the rover a ping every N milliseconds, if the rover doesn't receive this
        // ping within a set time, it disables the motors
        if (isConnected()) {
            //mesage consists of a header: VAPT0002, that's all is needed for this message header
            byte[] command = String.format("VAPT1\r").getBytes();
            connector.write(command);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    nPing++;
                }
            });

        }
        else{
            Toast.makeText(this,"Error, ping attempted but BT not connected",Toast.LENGTH_SHORT);
        }
    }

    // Returns true when the BT adapter exists and is enabled
    boolean isAdapterReady() {
        return (btAdapter != null) && (btAdapter.isEnabled());
    }

    // Setup the connection between the phone and other device
    private void setupConnector(BluetoothDevice connectedDevice) {
        stopConnection();
        try {
            String emptyName = getString(R.string.empty_device_name);
            DeviceData data = new DeviceData(connectedDevice, emptyName);
            connector = new DeviceConnector(data, bHandler);
            connector.connect();
        } catch (IllegalArgumentException e) {
            Utils.log("setupConnector failed: " + e.getMessage());
        }
    }

    void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
        //getSupportActionBar().setSubtitle(deviceName);
    }

    private class BluetoothResponseHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                // If the state of the bluetooth connection has changed
                case MESSAGE_STATE_CHANGE:
                    Utils.log("MESSAGE_STATE_CHANGE: " + msg.arg1);
                    //final ActionBar bar = activity.getSupportActionBar();
                    switch (msg.arg1) {

                        // If just connected, display bluetooth logo, switch connect button
                        // to disconnect button, and start pinging
                        case DeviceConnector.STATE_CONNECTED:
                            pingTimer.scheduleAtFixedRate(pingTimerTask, 0, 250);
                            //bar.setSubtitle(MSG_CONNECTED);
                            break;
                        case DeviceConnector.STATE_CONNECTING:
                            //bar.setSubtitle(MSG_CONNECTING);
                            break;
                        case DeviceConnector.STATE_NONE:
                            //bar.setSubtitle(MSG_NOT_CONNECTED);
                            break;

                        // If disconnected, remove bluetooth logo and change button back to
                        // connect button
                        case DeviceConnector.STATE_DISCONNECTED:
                            break;
                    }
                    break;

                case MESSAGE_READ:
                    break;

                case MESSAGE_DEVICE_NAME:
                    setDeviceName((String) msg.obj);
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

    public static final void runOnUiThread(Runnable runnable) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            runnable.run();
        } else {
            mHandler.post(runnable);
        }
    }

    // Stops the bluetooth connection
    public synchronized void stopConnection() {
        if (connector != null) {
            connector.stop();
            connector = null;
            deviceName = null;
        }
    }

    public synchronized int getPings() {
        return nPing;
    }

    // Returns true when the bluetooth connection is live between the phone and propeller board
    public synchronized boolean isConnected() {
        return (connector != null) && (connector.getState() == DeviceConnector.STATE_CONNECTED);
    }

    public synchronized BluetoothAdapter getBTAdapter() {
        return btAdapter;
    }

    public synchronized DeviceConnector getBTConnector(String address) {
        if (isAdapterReady() && (connector == null)) {
            BluetoothDevice device = btAdapter.getRemoteDevice(address);
            setupConnector(device);
        }
        return connector;
    }
}