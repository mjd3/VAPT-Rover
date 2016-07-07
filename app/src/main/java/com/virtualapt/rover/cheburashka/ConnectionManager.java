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

import android.content.Context;
import android.util.Log;

import com.virtualapt.rover.cheburashka.MasterApplication.PTPLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * this class encapsulate the NIO buffer and NIO channel on top of socket. It is all abt NIO style.
 * SSLServerSocketChannel, ServerSocketChannel, SocketChannel, Selector, ByteBuffer, etc. 
 * NIO buffer (ByteBuffer) either in writing mode or in reading mode. Need to flip the mode before reading or writing.
 *
 * You know when a socket channel disconnected when you read -1 or write exception. You need app level ACK.
 */
public class ConnectionManager {
	
	private final String TAG = "PTP_ConnMan";

	ConnectionService mService;
	MasterApplication mApp;
	
	// Server knows all clients. key is ip addr, value is socket channel. 
	// when remote client screen on, a new connection with the same ip addr is established.
	private Map<String, SocketChannel> mClientChannels = new HashMap<String, SocketChannel>();
	
	// global selector and channels
	private Selector mClientSelector = null;
	private Selector mServerSelector = null;
	private ServerSocketChannel mServerSocketChannel = null;
	private SocketChannel mClientSocketChannel = null;
	String mClientAddr = null;
	String mServerAddr = null;

	static byte[] RCSpeedCommandHeader = new byte[]{24,25,26,27,28};
	static byte[] RCPingDataHeader = new byte[]{27,26,25,24,23};
	
	/**
	 * constructor
	 */
	public ConnectionManager(ConnectionService service) {
		mService = service;
		mApp = (MasterApplication)mService.getApplication();
	}
	
	public void configIPV4() {
		 // by default Selector attempts to work on IPv6 stack.
		java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
		java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
	}
	
	/**
	 * create a server socket channel to listen to the port for incoming connections.
	 */
	public static ServerSocketChannel createServerSocketChannel(int port) throws IOException {
	    // Create a non-blocking socket channel
	    ServerSocketChannel ssChannel = ServerSocketChannel.open();
	    ssChannel.configureBlocking(false);
	    ServerSocket serverSocket = ssChannel.socket();
	    serverSocket.bind(new InetSocketAddress(port));  // bind to the port to listen.
	    return ssChannel;
	}
	
	/**
	 * Creates a non-blocking socket channel to connect to specified host name and port.
	 * connect() is called on the new channel before it is returned.
	 */
	public static SocketChannel createSocketChannel(String hostName, int port) throws IOException {
	    // Create a non-blocking socket channel
	    SocketChannel sChannel = SocketChannel.open();
	    sChannel.configureBlocking(false);
	    // Send a connection request to the server; this method is non-blocking
	    sChannel.connect(new InetSocketAddress(hostName, port));
	    return sChannel;
	}

	/**
	 * create a socket channel and connect to the host.
	 * after return, the socket channel guarantee to be connected.
	 */
	public SocketChannel connectTo(String hostname, int port) throws Exception {
		SocketChannel sChannel = null;
		
		sChannel = createSocketChannel(hostname, port);  // connect to the remote host, port

		// Before the socket is usable, the connection must be completed. finishConnect().
		while (!sChannel.finishConnect()) {
				// blocking spin lock
		}
		
		// Socket channel is now ready to use
		return sChannel;
	}
	
	/**
	 * client, after p2p connection available, connect to group owner and select monitoring the sockets.
	 * start blocking selector monitoring in an async task, infinite loop
	 */
	public int startClientSelector(String host) {
		closeServer();   // close linger server.
		
		if( mClientSocketChannel != null){
			return -1;
		}
		try {
			// connected to the server upon start client.
			SocketChannel sChannel = connectTo(host, 8000);
			mClientSelector = Selector.open();
		    mClientSocketChannel = sChannel;
		    mClientAddr = mClientSocketChannel.socket().getLocalAddress().getHostName();
		    sChannel.register(mClientSelector, SelectionKey.OP_READ );
		    mApp.setMyAddr(mClientAddr);
			// start selector monitoring, set to blocking mode
			new SelectorAsyncTask(mService, mClientSelector).execute();
			return 0;

		} catch(Exception e) {
			mClientSelector = null;
			mClientSocketChannel = null;
			mApp.setMyAddr(null);
			return -1;
		}		
	}
	
	/**
	 * create a selector to manage a server socket channel
	 * The registration process yields an object called a selection key which identifies the selector/socket channel pair
	 */
	public int startServerSelector() {
		closeClient();   // close linger client, if exists.
		try {
			// create server socket and register to selector to listen OP_ACCEPT event
		    ServerSocketChannel sServerChannel = createServerSocketChannel(8000); // BindException if already bind.
		    mServerSocketChannel = sServerChannel;
			if(mServerSocketChannel != null) {
				mServerAddr = mServerSocketChannel.socket().getInetAddress().getHostAddress();
				if ("0.0.0.0".equals(mServerAddr)) {
					mServerAddr = "Master";
				}
				((MasterApplication) mService.getApplication()).setMyAddr(mServerAddr);

				mServerSelector = Selector.open();
				SelectionKey acceptKey = sServerChannel.register(mServerSelector, SelectionKey.OP_ACCEPT);
				acceptKey.attach("accept_channel");
				mApp.mIsServer = true;

				//SocketChannel sChannel = createSocketChannel("hostname.com", 80);
				//sChannel.register(selector, SelectionKey.OP_CONNECT);  // listen to connect event.
				new SelectorAsyncTask(mService, mServerSelector).execute();
			}
			return 0;
			
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}
	
	/**
	 * handle selector error, re-start
	 */
	public void onSelectorError() {
		Log.e(TAG, " onSelectorError : do nothing for now");
		// new SelectorAsyncTask(mService, mSelector).execute();
	}
	
	/**
	 * a device can only be either group owner, or group client, not both.
	 * when we start as client, close server, if existing due to linger connection.
	 */
	public void closeServer() {
		if( mServerSocketChannel != null ){
			try{
				mServerSocketChannel.close();
				mServerSelector.close();
			}catch(Exception e){
				
			}finally{
				mApp.mIsServer = false;
				mServerSocketChannel = null;
				mServerSelector = null;
				mServerAddr = null;
				mClientChannels.clear();
			}
		}
	}
	
	public void closeClient() {
		if( mClientSocketChannel != null ){
			try{
				mClientSocketChannel.close();
				mClientSelector.close();
			}catch(Exception e){
				
			}finally{
				mClientSocketChannel = null;
				mClientSelector = null;
				mClientAddr = null;
			}
		}
	}
	
	/**
	 * read out -1, connection broken, remove it from clients collection
	 */
	public void onBrokenConn(SocketChannel schannel){
		try{
			String peeraddr = schannel.socket().getInetAddress().getHostAddress();
			if( mApp.mIsServer ){
				mClientChannels.remove(peeraddr);
			}else{
				closeClient();
			}
			schannel.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * Server handle new client coming in.
	 */
	public void onNewClient(SocketChannel schannel){
		String ipaddr = schannel.socket().getInetAddress().getHostAddress();
		mClientChannels.put(ipaddr, schannel);
	}
	
	/**
	 * Client's connect to server success, 
	 */
	public void onFinishConnect(SocketChannel schannel){
		String clientaddr = schannel.socket().getLocalAddress().getHostAddress();
		String serveraddr = schannel.socket().getInetAddress().getHostAddress();
		mClientSocketChannel = schannel;
		mClientAddr = clientaddr;
		((MasterApplication)mService.getApplication()).setMyAddr(mClientAddr);
	}

    byte[] toBytes(int i)
    {
        byte[] result = new byte[4];
        result[0] = (byte) (i >> 24);
        result[1] = (byte) (i >> 16);
        result[2] = (byte) (i >> 8);
        result[3] = (byte) (i);
        return result;
    }

    private int writeData(byte[] data,SocketChannel sChannel,int request) {
		int nwritten = 0;

		switch (request) {
			case 0:
				try {
					byte[] somebytes = data;
					byte[] headerKey = new byte[]{1, 2, 3, 4, 5};
					int fileLength = somebytes.length;
					byte[] headerValue = toBytes(fileLength);
					ByteBuffer bytebuf = ByteBuffer.allocate(fileLength + 9);
					bytebuf.put(headerKey);
					bytebuf.put(headerValue);
					bytebuf.put(somebytes);
					bytebuf.clear();
					nwritten = sChannel.write(bytebuf);
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 1:
				try {
					byte[] somebytes = data;
					int fileLength = somebytes.length;
					// data(two integers) + 5(header)
					ByteBuffer bytebuf = ByteBuffer.allocate(fileLength + 13);
					bytebuf.put(RCSpeedCommandHeader);
					bytebuf.put(somebytes);
					bytebuf.clear();
					nwritten = sChannel.write(bytebuf);
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 3:
				try{
					byte[] somebytes = data;
					int fileLength = somebytes.length;
					// data(four integers) + 5(header)
					ByteBuffer bytebuf = ByteBuffer.allocate(fileLength + 5);
					bytebuf.put(RCPingDataHeader);
					bytebuf.put(somebytes);
					bytebuf.clear();
					nwritten = sChannel.write(bytebuf);
				}catch(IOException e){
					e.printStackTrace();
				}

				break;
		}
		return nwritten;
	}
	/**
	 * the device want to push out data.
	 * If the device is client, the only channel is to the server.
	 * If the device is server, it just pub the data to all clients for now.
	 */
	public int pushOutData(byte[] data,int request){
		if( !mApp.mIsServer ){   // device is client, can only send to server
			sendDataToServer(data,request);
		}
		//keep this, it's used by group owner
		else {
			pubDataToAllClients(data, null, request);
		}
		return 0;
	}
	/**
	 * server publish data to all the connected clients
	 */
	private void pubDataToAllClients(byte[] data, SocketChannel incomingChannel, int request){
	if( !mApp.mIsServer ){
			return;
		}
		for( SocketChannel s: mClientChannels.values()) {
			if ( s != incomingChannel){
				String peeraddr = s.socket().getInetAddress().getHostAddress();
				writeData(data,s,request);
			}
		}
	}
	/**
	 * whenever client write to server, carry the format of "client_addr : msg "
	 */
	private int sendDataToServer(byte[] data, int request) {
		if(mClientSocketChannel == null) {
			return 0;
		}
		return writeData(data,mClientSocketChannel,request);
	}
}
