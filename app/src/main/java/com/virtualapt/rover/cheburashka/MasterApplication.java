package com.virtualapt.rover.cheburashka;

import android.app.Application;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static com.virtualapt.rover.cheburashka.Constants.MSG_STARTCLIENT;
import static com.virtualapt.rover.cheburashka.Constants.MSG_STARTSERVER;

public class MasterApplication extends Application {

	private static final String TAG = "PTP_APP";
	
	WifiP2pManager mP2pMan = null;;
	Channel mP2pChannel = null;
	boolean mP2pConnected = false;
	String mMyAddr = null;
	String mDeviceName = null;   // the p2p name that is configurated from UI.
	
	WifiP2pDevice mThisDevice = null;
	WifiP2pInfo mP2pInfo = null;  // set when connection info available, reset when WIFI_P2P_CONNECTION_CHANGED_ACTION

	DeviceRole deviceRole = null;

	boolean mIsServer = false;
	
	public enum DeviceRole {
		ROVER,
		REMOTE
	}

	@Override
    public void onCreate() {
        super.onCreate();
    }
	
    /**
     * get the intent to launch any activity (specifically the chat activity)
     */
    public Intent getLaunchActivityIntent(Class<?> cls, String initmsg){
    	Intent i = new Intent(this, cls);
    	i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    	i.putExtra("FIRST_MSG", initmsg);
    	return i;
    }
    
    public void setMyAddr(String addr){
    	mMyAddr = addr;
    }
    
	public static class PTPLog {
		public static void i(String tag, String msg) {
            Log.i(tag, msg);
        }
		public static void d(String tag, String msg) {
            Log.d(tag, msg);
        }
		public static void e(String tag, String msg) {
            Log.e(tag, msg);
        }
	}

}
