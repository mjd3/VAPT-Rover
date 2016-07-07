package com.virtualapt.rover.cheburashka;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import static com.virtualapt.rover.cheburashka.Constants.*;
import static com.virtualapt.rover.cheburashka.Constants.MSG_BROKEN_CONN;
import static com.virtualapt.rover.cheburashka.Constants.MSG_FINISH_CONNECT;
import static com.virtualapt.rover.cheburashka.Constants.MSG_NEW_CLIENT;
import static com.virtualapt.rover.cheburashka.Constants.MSG_PULLIN_DATA;
import static com.virtualapt.rover.cheburashka.Constants.MSG_SELECT_ERROR;
import com.virtualapt.rover.cheburashka.Debug;

/**
 * AsyncTask is only for UI thread to submit work(impl inside doInbackground) to executor.
 * The threading rule of AsyncTask: create/load/execute by UI thread only _ONCE_. can be callabled.
 */

/**
 * the selector only monitors OP_CONNECT and OP_READ. Do not monitor OP_WRITE as a channel is always writable.
 * Upon event out, either accept a connection, or read the data from the channel.
 * The writing to the channel is done inside the connection service main thread. 
 */

public class SelectorAsyncTask extends AsyncTask<Void, Void, Void> {
	private static final String TAG = "PTP_SEL";
	
	private ConnectionService mConnService;
	private Selector mSelector;
	
	public SelectorAsyncTask(ConnectionService connservice, Selector selector) {
		mConnService = connservice;
		mSelector = selector;
	}
	
	@Override
	protected Void doInBackground(Void... arg0) {
		select();
		return null;
	}
	
	private void select() {
		// Wait for events looper
		while (true) {
			try {
		    	//if(Debug.DEBUG)
					//Log.d(TAG, "select : selector monitoring: ");
		    	mSelector.select();   // blocked on waiting for event
				//if(Debug.DEBUG)
		    		//Log.d(TAG, "select : selector evented out: ");
			    // Get list of selection keys with pending events, and process it.
			    Iterator<SelectionKey> keys = mSelector.selectedKeys().iterator();
			    while (keys.hasNext()) {
			        // Get the selection key, and remove it from the list to indicate that it's being processed
			        SelectionKey selKey = keys.next();
			        keys.remove();
					//if(Debug.DEBUG)
			        //	Log.d(TAG, "select : selectionkey: " + selKey.attachment());
			        
			        try {
			            processSelectionKey(mSelector, selKey);  // process the selection key.
			        } catch (IOException e) {
			            selKey.cancel();
						if(Debug.DEBUG)
			            	Log.e(TAG, "select : io exception in processing selector event: " + e.toString());
			        }
			    }
		    } catch (Exception e) {  // catch all exception in select() and the following ops in mSelector.
				if(Debug.DEBUG)
					//Log.e(TAG, "Exception in selector: " + e.toString());
		    	notifyConnectionService(MSG_SELECT_ERROR, null, null);
		        break;
		    }
		}
	}

	/**
	 * process the event popped to the selector
	 */
	public void processSelectionKey(Selector selector, SelectionKey selKey) throws IOException {
        if (selKey.isValid() && selKey.isAcceptable()) {  // there is a connection to the server socket channel
            ServerSocketChannel ssChannel = (ServerSocketChannel)selKey.channel();
            SocketChannel sChannel = ssChannel.accept();  // accept the connect and get a new socket channel.
            sChannel.configureBlocking(false);
            
            // let the selector monitor read/write the accepted connections.
            SelectionKey socketKey = sChannel.register(selector, SelectionKey.OP_READ );
            socketKey.attach("accepted_client " + sChannel.socket().getInetAddress().getHostAddress());
			if(Debug.DEBUG)
				Log.d(TAG, "processSelectionKey : accepted a client connection: " + sChannel.socket().getInetAddress().getHostAddress());
            notifyConnectionService(MSG_NEW_CLIENT, sChannel, null);
        } else if (selKey.isValid() && selKey.isConnectable()) {   // client connect to server got the response.
	        SocketChannel sChannel = (SocketChannel)selKey.channel();

	        boolean success = sChannel.finishConnect();
	        if (!success) {
	            // An error occurred; unregister the channel.
	            selKey.cancel();
				if(Debug.DEBUG)
					Log.e(TAG, " processSelectionKey : finish connection not success !");
	        }
			if(Debug.DEBUG)
				Log.d(TAG, "processSelectionKey : this client connect to remote success: ");
	        notifyConnectionService(MSG_FINISH_CONNECT, sChannel, null);
	        //mOutChannels.put(Integer.toString(sChannel.socket().getLocalPort()), sChannel);
	    } else if (selKey.isValid() && selKey.isReadable()) {
	        // Get channel with bytes to read
	        SocketChannel sChannel = (SocketChannel)selKey.channel();
			if(Debug.DEBUG)
				Log.d(TAG, "processSelectionKey : remote client is readable, read data: " + selKey.attachment());
	        // we can retrieve the key we attached earlier, so we now what to do / where the data is coming from
	        // MyIdentifierType myIdentifier = (MyIdentifierType)key.attachment();
	        // myIdentifier.readTheData();
	        doReadable(sChannel);
	    } else if (selKey.isValid() && selKey.isWritable()) {
	    	// Not select on writable...endless loop.
	        SocketChannel sChannel = (SocketChannel)selKey.channel();
			if(Debug.DEBUG)
				Log.d(TAG, "processSelectionKey : remote client is writable, write data: ");
	    }
	}
	/**
	 * handle the readable event from selector
	 */
	public void doReadable(SocketChannel schannel){
		byte[] data = readData(schannel);
		if(Debug.DEBUG)
			Log.d(TAG, "read in channel data, sending PULLIN_DATA request");
		if( data != null ){
			Bundle b = new Bundle();
			b.putByteArray("DATA", data);
			notifyConnectionService(MSG_PULLIN_DATA, schannel, b);
		}
	}
	/**
	 * read data when OP_READ event 
	 */
	public byte[] readData(SocketChannel sChannel) {
		ByteBuffer buf = ByteBuffer.allocate(1024*20);   // let's cap json string to 4k for now.
		byte[] bytes = null;
		
		try {
		    buf.clear();  // Clear the buffer and read bytes from socket
		    int numBytesRead = sChannel.read(buf);
		    if (numBytesRead == -1) {
		        // read -1 means socket channel is broken. remove it from the selector
				if(Debug.DEBUG)
					Log.e(TAG, "readData : channel closed due to read -1: ");
		    	sChannel.close();  // close the channel.
		    	notifyConnectionService(MSG_BROKEN_CONN, sChannel, null);
		    	// sChannel.close();
		    } else {
				if(Debug.DEBUG)
					Log.d(TAG, "readData: bufpos: limit : " + buf.position() + ":" + buf.limit() + " : " + buf.capacity());
		        buf.flip();  // make buffer ready for read by flipping it into read mode.
				if(Debug.DEBUG)
					Log.d(TAG, "readData: bufpos: limit : " + buf.position() + ":" + buf.limit() + " : " + buf.capacity());
		        bytes = new byte[buf.limit()];  // use bytes.length will cause underflow exception.
		        buf.get(bytes);
		        // while ( buf.hasRemaining() ) buf.get();

		    }
		}catch(Exception e){
			if(Debug.DEBUG)
				Log.e(TAG, "readData : exception: " + e.toString());
			notifyConnectionService(MSG_BROKEN_CONN, sChannel, null);
		}
        return bytes;
	}

	/**
	 * notify connection manager event
	 */
	private void notifyConnectionService(int what, Object obj, Bundle data){
		Handler hdl = mConnService.getHandler();
		Message msg = hdl.obtainMessage();
		msg.what = what;
		
		if( obj != null ){
			msg.obj = obj;	
		}
		if( data != null ){
			msg.setData(data);
		}
		hdl.sendMessage(msg);		
	}

}
