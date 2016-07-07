package com.virtualapt.rover.cheburashka;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
public class WifiDirectBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Intent serviceIntent = new Intent(context, ConnectionService.class);  // start ConnectionService
        serviceIntent.setAction(action);   // put in action and extras
        serviceIntent.putExtras(intent);
        context.startService(serviceIntent);  // start the connection service
    }
}

