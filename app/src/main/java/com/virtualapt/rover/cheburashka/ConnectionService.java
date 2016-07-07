/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.virtualapt.rover.cheburashka;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.virtualapt.rover.cheburashka.MasterApplication.PTPLog;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import static com.virtualapt.rover.cheburashka.Constants.MSG_BROKEN_CONN;
import static com.virtualapt.rover.cheburashka.Constants.MSG_FINISH_CONNECT;
import static com.virtualapt.rover.cheburashka.Constants.MSG_NEW_CLIENT;
import static com.virtualapt.rover.cheburashka.Constants.MSG_NULL;
import static com.virtualapt.rover.cheburashka.Constants.MSG_PULLIN_DATA;
import static com.virtualapt.rover.cheburashka.Constants.MSG_PUSHOUT_DATA;
import static com.virtualapt.rover.cheburashka.Constants.MSG_REGISTER_ROVER_ACTIVITY;
import static com.virtualapt.rover.cheburashka.Constants.MSG_REGISTER_RC_ACTIVITY;
import static com.virtualapt.rover.cheburashka.Constants.MSG_SELECT_ERROR;
import static com.virtualapt.rover.cheburashka.Constants.MSG_STARTCLIENT;
import static com.virtualapt.rover.cheburashka.Constants.MSG_STARTSERVER;

public class ConnectionService extends Service implements ChannelListener, PeerListListener, ConnectionInfoListener {  // callback of requestPeers{
	
	private static final String TAG = "PTP_Serv";
	
	private static ConnectionService _sinstance = null;

	private  WorkHandler mWorkHandler;
    private  MessageHandler mHandler;
	boolean retryChannel = false;
    MasterApplication mApp;
	public RemoteControllerActivity mRCActivity; //one or the other (next) will be defined, the other null based on the device and user selection
	public RoverActivity mRoverActivity;
	ConnectionManager mConnMan;

	//TODO probably should put this stuff in a different class...
    static int imgLength = 0;
    static int currPos = 0;
    static ByteBuffer byteBuffer = null;
    static byte[] imgBytes = null;
    static boolean receivedHeader = false;
	static byte[] imageHeaderKey = new byte[]{1,2,3,4,5};
	static byte[] RCSpeedCommandHeader = new byte[]{24,25,26,27,28};
	static byte[] RCPingDataHeader = new byte[]{27,26,25,24,23};

	static boolean skipThisImage = false;
	static int nImageFrames = 0;

	/**
     * @see android.app.Service#onCreate()
     */
    private void _initialize() {
    	if (_sinstance != null) {
			return;
        }
    	_sinstance = this;
    	mWorkHandler = new WorkHandler(TAG);
        mHandler = new MessageHandler(mWorkHandler.getLooper());
        mApp = (MasterApplication)getApplication();
        mApp.mP2pMan = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mApp.mP2pChannel = mApp.mP2pMan.initialize(this, mWorkHandler.getLooper(), null);
		mConnMan = new ConnectionManager(this);
    }

    public static ConnectionService getInstance(){
    	return _sinstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        _initialize();
	}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	_initialize();
    	processIntent(intent);
    	return START_STICKY;
    }
    /**
     * process all wifi p2p intent caught by bcast recver.
     * P2P connection setup event sequence:
     * 1. after find, peers_changed to available, invited
     * 2. when connection established, this device changed to connected.
     * 3. for server, WIFI_P2P_CONNECTION_CHANGED_ACTION intent: p2p connected,
     *    for client, this device changed to connected first, then CONNECTION_CHANGED
     * 4. WIFI_P2P_PEERS_CHANGED_ACTION: peer changed to connected.
     * 5. now both this device and peer are connected !
     *
     * if select p2p server mode with create group, this device will be group owner automatically, with
     * 1. this device changed to connected
     * 2. WIFI_P2P_CONNECTION_CHANGED_ACTION
     */
    private void processIntent(Intent intent){
    	if( intent == null){
    		return;
    	}
    	String action = intent.getAction();
		if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {  // this devices's wifi direct enabled state.
              int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
              if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                  // Wifi Direct mode is enabled
            	  mApp.mP2pChannel = mApp.mP2pMan.initialize(this, mWorkHandler.getLooper(), null);
            	  AppPreferences.setStringToPref(mApp, AppPreferences.PREF_NAME, AppPreferences.P2P_ENABLED, "1");
			} else {
            	  mApp.mThisDevice = null;  	// reset this device status
            	  mApp.mP2pChannel = null;
				  if(mRoverActivity != null) {
				  	mRoverActivity.sendMotionCommand(0,0);
					mRoverActivity.pingTimerTask.cancel();
				  }
            	  mApp.mPeers.clear();
				  if( mApp.mHomeActivity != null ){
            		  mApp.mHomeActivity.updateThisDevice(null);
            		  mApp.mHomeActivity.resetData();
            	  }
            	  AppPreferences.setStringToPref(mApp, AppPreferences.PREF_NAME, AppPreferences.P2P_ENABLED, "0");
              }
          } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
        	  // a list of peers are available after discovery, use PeerListListener to collect
              // request available peers from the wifi p2p manager. This is an
              // asynchronous call and the calling activity is notified with a
              // callback on PeerListListener.onPeersAvailable()
			if (mApp.mP2pMan != null) {
            	  mApp.mP2pMan.requestPeers(mApp.mP2pChannel, (PeerListListener) this);
              }
          } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
              if (mApp.mP2pMan == null) {
                  return;
              }

              	NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
				if (networkInfo.isConnected()) {
					// Connected with the other device, request connection info for group owner IP. Callback inside details fragment.
                  mApp.mP2pMan.requestConnectionInfo(mApp.mP2pChannel, this);
              	} else {
				  mApp.mP2pConnected = false;
            	  mApp.mP2pInfo = null;   // reset connection info after connection done.
            	  mConnMan.closeClient();
            	  if( mApp.mHomeActivity != null ){
            		  mApp.mHomeActivity.resetData();
					  if(mRoverActivity != null) {
						  mRoverActivity.sendMotionCommand(0,0);
						  mRoverActivity.pingTimerTask.cancel();
					  }
            	  }
              }
          } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
        	  	// this device details has changed(name, connected, etc)
        	  	mApp.mThisDevice = (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
        	  	mApp.mDeviceName = mApp.mThisDevice.deviceName;
				if( mApp.mHomeActivity != null ){
        		  mApp.mHomeActivity.updateThisDevice(mApp.mThisDevice);
        	  }
          }
    }
    /**
     * The channel to the framework Wifi P2p has been disconnected. could try re-initializing
     */
    @Override
    public void onChannelDisconnected() {
    	if( !retryChannel ){
			mApp.mP2pChannel = mApp.mP2pMan.initialize(this, mWorkHandler.getLooper(), null);
    		if( mApp.mHomeActivity != null) {
    			mApp.mHomeActivity.resetData();
    		}
    		retryChannel = true;
    	}else{
			if( mApp.mHomeActivity != null) {
    			mApp.mHomeActivity.onChannelDisconnected();
    		}
    		stopSelf();
    	}
    }
    /**
     * the callback of requestPeers upon WIFI_P2P_PEERS_CHANGED_ACTION intent.
     */
    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
    	mApp.mPeers.clear();
    	mApp.mPeers.addAll(peerList.getDeviceList());
    	WifiP2pDevice connectedPeer = mApp.getConnectedPeer();
    	if(mApp.mP2pInfo != null && connectedPeer != null ){
    		if( mApp.mP2pInfo.groupFormed && mApp.mP2pInfo.isGroupOwner ){
				mApp.startSocketServer();
    		}else if( mApp.mP2pInfo.groupFormed && connectedPeer != null ){
    			// XXX client path goes to connection info available after connection established.
    			// PTPLog.d(TAG, "onConnectionInfoAvailable: device is client, connect to group owner: startSocketClient ");
    			// mApp.startSocketClient(mApp.mP2pInfo.groupOwnerAddress.getHostAddress());
    		}
    	}
    	if( mApp.mHomeActivity != null){
    		mApp.mHomeActivity.onPeersAvailable(peerList);
    	}
    }
    /**
     * the callback of when the _Requested_ connectino info is available.
     * WIFI_P2P_CONNECTION_CHANGED_ACTION intent, requestConnectionInfo()
     */
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
		if (info.groupFormed && info.isGroupOwner ) {
			// XXX server path goes to peer connected.
            //new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text)).execute();
        	//PTPLog.d(TAG, "onConnectionInfoAvailable: device is groupOwner: startSocketServer ");
			mApp.startSocketServer();
        } else if (info.groupFormed) {
			mApp.startSocketClient(info.groupOwnerAddress.getHostAddress());
        }
        mApp.mP2pConnected = true;
        mApp.mP2pInfo = info;   // connection info available
    }
    private void enableStartChatActivity() {
		if(mApp.mHomeActivity != null ){
			mApp.mHomeActivity.onConnectionInfoAvailable(mApp.mP2pInfo);
    	}
    }

	@Override
	public IBinder onBind(Intent arg0) { return null; }

	public Handler getHandler() {
        return mHandler;
    }

	/**
     * message handler looper to handle all the msg sent to location manager.
     */
    final class MessageHandler extends Handler {
        public MessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            processMessage(msg);
        }
    }

    /**
     * the main message process loop.
     */
    private void processMessage(android.os.Message msg) {
    	
        switch (msg.what) {
        case MSG_NULL:
        	break;
        case MSG_REGISTER_RC_ACTIVITY:
			if(Debug.DEBUG)
				PTPLog.d(TAG, "processMessage: onActivityRegister to chat fragment...");
			onActivityRegister((RemoteControllerActivity)msg.obj, msg.arg1);
        	break;
		case MSG_REGISTER_ROVER_ACTIVITY:
			if(Debug.DEBUG)
				PTPLog.d(TAG, "processMessage: onActivityRegister to chat fragment...");
			onActivityRegister((RoverActivity) msg.obj, msg.arg1);
			break;
        case MSG_STARTSERVER:
			if(Debug.DEBUG)
				PTPLog.d(TAG, "processMessage: startServerSelector...");
        	if( mConnMan.startServerSelector() >= 0){
        		enableStartChatActivity();
        	}
        	break;
        case MSG_STARTCLIENT:
			if(Debug.DEBUG)
				PTPLog.d(TAG, "processMessage: startClientSelector...");
        	if( mConnMan.startClientSelector((String)msg.obj) >= 0){
        		enableStartChatActivity();
        	}
        	break;
        case MSG_NEW_CLIENT:
			if(Debug.DEBUG)
				PTPLog.d(TAG, "processMessage:  onNewClient...");
        	mConnMan.onNewClient((SocketChannel)msg.obj);
        	break;
        case MSG_FINISH_CONNECT:
			if(Debug.DEBUG)
				PTPLog.d(TAG, "processMessage:  onFinishConnect...");
        	mConnMan.onFinishConnect((SocketChannel)msg.obj);
        	break;
        case MSG_PULLIN_DATA:
			//if(Debug.DEBUG)
				PTPLog.d(TAG, "processMessage:  onPullIndata ...");
        	onPullInData((SocketChannel)msg.obj, msg.getData());
        	break;
        case MSG_PUSHOUT_DATA:
			//if(Debug.DEBUG)
				PTPLog.d(TAG, "processMessage: onPushOutData...");
			onPushOutData((byte[]) msg.obj,msg.arg1);
        	break;
        case MSG_SELECT_ERROR:
			if(Debug.DEBUG)
				PTPLog.d(TAG, "processMessage: onSelectorError...");
        	mConnMan.onSelectorError();
        	break;
        case MSG_BROKEN_CONN:
			if(Debug.DEBUG)
				PTPLog.d(TAG, "processMessage: onBrokenConn...");
        	mConnMan.onBrokenConn((SocketChannel)msg.obj);
        	break;
        default:
        	break;
        }
    }
    
    /**
     * register the activity that uses this service.
     */

	private void onActivityRegister(RemoteControllerActivity activity, int register) {
		if (register == 1) {
			mRCActivity = (RemoteControllerActivity) activity;
		} else {
			mRCActivity = null;    // set to null explicitly to avoid mem leak.
		}
	}

	private void onActivityRegister(RoverActivity activity, int register) {
		if (register == 1) {
			mRoverActivity = (RoverActivity) activity;
		} else {
			mRoverActivity = null;    // set to null explicitly to avoid mem leak.
		}
	}

 	public static int byteArrayToInt(byte[] b) {
		return b[3] & 0xFF |
				(b[2] & 0xFF) << 8 |
				(b[1] & 0xFF) << 16 |
				(b[0] & 0xFF) << 24;
	}

	/**
	 * service handle data in come from socket channel
	 */
	private byte[] onPullInData(SocketChannel schannel, Bundle b) {
		byte[] data = b.getByteArray("DATA");
		int dataLength = data.length;
		byte[] headerReceivedKey = new byte[5];
		try {
			System.arraycopy(data, 0, headerReceivedKey, 0, 5);
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}
		if(mRoverActivity != null) {

			if(!receivedHeader && Arrays.equals(headerReceivedKey, RCSpeedCommandHeader)){
				byte[] lWheelSpeed = new byte[4];
				byte[] rWheelSpeed = new byte[4];
				System.arraycopy(data, 5, lWheelSpeed, 0, 4);
				System.arraycopy(data, 9, rWheelSpeed, 0, 4);
				int x = byteArrayToInt(lWheelSpeed);
				int y = byteArrayToInt(rWheelSpeed);
				mRoverActivity.sendMotionCommand(byteArrayToInt(lWheelSpeed), byteArrayToInt(rWheelSpeed));
			}

		} else if(mRCActivity != null) {
			Log.d(TAG, "mRC Activity, showing data if header correct");
			if(Arrays.equals(headerReceivedKey, RCPingDataHeader)) {
				byte[] header = new byte[4];
				System.arraycopy(data, 0, header, 0, 4);
				if (Arrays.equals(header, "PING".getBytes())) {
					byte[] anInt = new byte[4];
					int i;
					for (i = 0; i < 4; i++) {
						System.arraycopy(data, 0, anInt, i * 4, 4);
						switch (i) {
							case 0: //front
								mRCActivity.frontPingCM = byteArrayToInt(anInt);
								break;
							case 1: //left
								mRCActivity.leftPingCM = byteArrayToInt(anInt);
								break;
							case 2: //right
								mRCActivity.rightPingCM = byteArrayToInt(anInt);
								break;
							case 3: //back
								mRCActivity.backPingCM = byteArrayToInt(anInt);
								break;
						}
					}
				}
				} else {
					if (!receivedHeader && Arrays.equals(headerReceivedKey, imageHeaderKey)) {
						//this is the start of an image we've received
						try {
							nImageFrames = 1;
							byte[] headerValue = new byte[4];
							System.arraycopy(data, 5, headerValue, 0, 4);
							imgLength = byteArrayToInt(headerValue);
							imgBytes = new byte[imgLength];
							byteBuffer = ByteBuffer.wrap(imgBytes);

							if (byteBuffer.capacity() < dataLength) {
								byteBuffer.put(data, 9, byteBuffer.capacity());
							} else {
								byteBuffer.put(data, 9, dataLength - 9);
							}

							currPos = dataLength - 9;
							receivedHeader = !receivedHeader;
							if (currPos >= imgLength) {
								displayImage(imgBytes);
								currPos = 0;
								receivedHeader = false;
								byteBuffer.clear();
							}
						} catch (ArrayIndexOutOfBoundsException e) {
							e.printStackTrace();
						} catch (IndexOutOfBoundsException e) {
							e.printStackTrace();
						}
					} else if (receivedHeader) {
						try {
							if (byteBuffer.capacity() < dataLength + currPos) {
								byteBuffer.put(data, 0, byteBuffer.capacity() - currPos);
							} else {
								byteBuffer.put(data, 0, dataLength);
							}

							currPos += data.length;

							nImageFrames++;
							if (nImageFrames > 200) {
								skipThisImage = true;
								nImageFrames = 0;
							}

							// show image if buffer, skip of strange voodoo occurred
							if ((currPos >= imgLength)) {
								currPos = 0;
								receivedHeader = false;
								byteBuffer.clear();
								displayImage(imgBytes);
							} else if (skipThisImage) {
								skipThisImage = false;
								currPos = 0;
								receivedHeader = false;
								byteBuffer.clear();
							}
						} catch (ArrayIndexOutOfBoundsException e) {
							e.printStackTrace();
						} catch (IndexOutOfBoundsException e) {
							e.printStackTrace();
						}
					}
				}
			}
        return null;
    }
    /**
     * handle data push out request. 
     * If the sender is the server, pub to all client.
     * If the sender is client, only can send to the server.
     */
    private void onPushOutData(byte[] data, int request){
		if(Debug.DEBUG)
			Log.d(TAG, "onPushOutData : " + data);
		mConnMan.pushOutData(data,request);
    }

	private void displayJSData(final int x, final int y){
		if( mRoverActivity != null ){
			mRoverActivity.showJSData(x,y);
		} else {
			if (mApp.mHomeActivity != null && mApp.mHomeActivity.mHasFocus == true) {
				if(Debug.DEBUG)
					PTPLog.d(TAG, "showInActivity :  chat activity down, force start only when home activity has focus !");
				//mApp.mHomeActivity.startChatActivity(row.mMsg);
			} else {
				if(Debug.DEBUG)
					PTPLog.d(TAG, "showInActivity :  Home activity down, do nothing, notification will launch it...");
			}
		}
	}

	/**
	 * show the message in activity
	 */
	private void displayImage(final byte[] data){
		if(Debug.DEBUG)
			PTPLog.d(TAG, "Class name: " + mRCActivity.getLocalClassName());
		if(Debug.DEBUG)
			PTPLog.d(TAG, "Class string: " + mRCActivity.toString());

		if( mRCActivity != null ){
			mRCActivity.showImage(data);
		} else {
			if (mApp.mHomeActivity != null && mApp.mHomeActivity.mHasFocus == true) {
				PTPLog.d(TAG, "showInActivity :  chat activity down, force start only when home activity has focus !");
				//mApp.mHomeActivity.startChatActivity(row.mMsg);
			} else {
				PTPLog.d(TAG, "showInActivity :  Home activity down, do nothing, notification will launch it...");
			}
		}

	}

    public static String getDeviceStatus(int deviceStatus) {
        switch (deviceStatus) {
            case WifiP2pDevice.AVAILABLE:
            	//Log.d(TAG, "getDeviceStatus : AVAILABLE");
                return "Available";
            case WifiP2pDevice.INVITED:
            	//Log.d(TAG, "getDeviceStatus : INVITED");
                return "Invited";
            case WifiP2pDevice.CONNECTED:
            	//Log.d(TAG, "getDeviceStatus : CONNECTED");
                return "Connected";
            case WifiP2pDevice.FAILED:
            	//Log.d(TAG, "getDeviceStatus : FAILED");
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
            	//Log.d(TAG, "getDeviceStatus : UNAVAILABLE");
                return "Unavailable";
            default:
                return "Unknown = " + deviceStatus;
        }
    }
 }
