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

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.virtualapt.rover.cheburashka.DeviceListFragment.DeviceActionListener;
import com.virtualapt.rover.cheburashka.MasterApplication.PTPLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment {

	private static final String TAG = "PTP_Detail";
	
    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    ProgressDialog progressDialog = null;
    
    MasterApplication mApp = null;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mApp = (MasterApplication)getActivity().getApplication();
    }

    /*
    @Override
    public void onAttach(Activity activity){
    	super.onAttach(activity);
    	// onAttach -> onCreate -> onCreateView -> onActivityCreated -> onStart -> onResume
    }
    */

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.device_detail, null);
        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                config.groupOwnerIntent = 0;  // least inclination to be group owner.

                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true,  // cancellable
                        new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                ((DeviceActionListener) getActivity()).cancelDisconnect();
                            }
                        });
                // perform p2p connect upon user click the connect button, connect available handle when connection done.
                ((DeviceActionListener) getActivity()).connect(config);
            }
        });

        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((DeviceActionListener) getActivity()).disconnect();
                    }
                });

        // p2p connected, manager request connection info done, group owner elected. 
        mContentView.findViewById(R.id.btn_start_rover).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(mApp.deviceRole == null) {
                            mApp.deviceRole = MasterApplication.DeviceRole.ROVER;
                        }
                        ((WiFiDirectActivity)getActivity()).startRoverActivity();  // no init msg if started from button click.
                    }
                });
        mContentView.findViewById(R.id.btn_start_RC).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(mApp.deviceRole == null) {
                            mApp.deviceRole = MasterApplication.DeviceRole.REMOTE;
                        }
                       ((WiFiDirectActivity)getActivity()).startRCActivity();  // no init msg if started from button click.
                    }
                });
        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        /* From the original source code to send files, may be useful for opening ports etc. later
        Uri uri = data.getData();
        Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS, info.groupOwnerAddress.getHostAddress());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
        getActivity().startService(serviceIntent);
        */
    }

    /**
     * p2p connection setup, proceed to setup socket connection.
     */
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);

        // The owner IP is now known.
       if (info.groupFormed && info.isGroupOwner) {
        	Log.d(TAG, "onConnectionInfoAvailable: device is groupOwner: startSocketServer done ");
        } else if (info.groupFormed) {
        	Log.d(TAG, "onConnectionInfoAvailable: device is client, connect to group owner: startSocketClient done ");
        }
        if( ! mApp.mIsServer && mApp.mMyAddr == null ){
        	Toast.makeText(mApp, "Connect to Server Failed, Please try again...", Toast.LENGTH_LONG).show();
        	PTPLog.d(TAG,  "onConnectionInfoAvailable : connect to serve failed...try again ! ");
        }else{
        	// hide the connect button and enable start chat button
        	mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
        	mContentView.findViewById(R.id.btn_start_rover).setVisibility(View.VISIBLE);
            mContentView.findViewById(R.id.btn_start_RC).setVisibility(View.VISIBLE);
        	PTPLog.d(TAG, "onConnectionInfoAvailable: socket connection established, show start chat button ! ");
        }
    }

    /**
     * Updates the UI with device data
     * 
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        //TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        //view.setText(device.deviceAddress);
        //view = (TextView) mContentView.findViewById(R.id.device_info);
        //view.setText(device.toString());
    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
    	PTPLog.d(TAG, "resetViews: detail frag dismiss progress dialog and clear views");
    	if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    	
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_RC).setVisibility(View.GONE);
        mContentView.findViewById(R.id.btn_start_rover).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;

        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                ServerSocket serverSocket = new ServerSocket(8988);
                Socket client = serverSocket.accept();

                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();
                InputStream inputstream = client.getInputStream();
                copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
                return f.getAbsolutePath();
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                //statusText.setText("File copied - " + result);
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + result), "image/*");
                context.startActivity(intent);
            }

        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            //statusText.setText("Opening a server socket");
        }
    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(WiFiDirectActivity.TAG, e.toString());
            return false;
        }
        return true;
    }
}
