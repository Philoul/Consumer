package com.samsung.android.sdk.accessory.example.helloaccessory.consumer;

import java.io.IOException;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;
import android.util.Log;

import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.accessory.*;

public class ConsumerService extends SAAgent {
    private static final String TAG = "HelloAccessory(C)";
    private static final Class<ServiceConnection> SASOCKET_CLASS = ServiceConnection.class;
    private final IBinder mBinder = new LocalBinder();
    private ServiceConnection mConnectionHandler = null;
    Handler mHandler = new Handler();

    public ConsumerService() {
        super(TAG, SASOCKET_CLASS);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        SA mAccessory = new SA();
        try {
            mAccessory.initialize(this);
        } catch (SsdkUnsupportedException e) {
            if (processUnsupportedException(e) == true) {
                return;
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onFindPeerAgentsResponse(SAPeerAgent[] peerAgents, int result) {
        if ((result == SAAgent.PEER_AGENT_FOUND) && (peerAgents != null)) {
            for(SAPeerAgent peerAgent:peerAgents)
                requestServiceConnection(peerAgent);
        } else if (result == SAAgent.FINDPEER_DEVICE_NOT_CONNECTED) {
            Toast.makeText(getApplicationContext(), "FINDPEER_DEVICE_NOT_CONNECTED", Toast.LENGTH_LONG).show();
            updateTextView("Disconnected");
        } else if (result == SAAgent.FINDPEER_SERVICE_NOT_FOUND) {
            Toast.makeText(getApplicationContext(), "FINDPEER_SERVICE_NOT_FOUND", Toast.LENGTH_LONG).show();
            updateTextView("Disconnected");
        } else {
            Toast.makeText(getApplicationContext(), R.string.NoPeersFound, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onServiceConnectionRequested(SAPeerAgent peerAgent) {
        if (peerAgent != null) {
            acceptServiceConnectionRequest(peerAgent);
        }
    }

    @Override
    protected void onServiceConnectionResponse(SAPeerAgent peerAgent, SASocket socket, int result) {
        if (result == SAAgent.CONNECTION_SUCCESS) {
            this.mConnectionHandler = (ServiceConnection) socket;
            updateTextView("Connected");
        } else if (result == SAAgent.CONNECTION_ALREADY_EXIST) {
            updateTextView("Connected");
            Toast.makeText(getBaseContext(), "CONNECTION_ALREADY_EXIST", Toast.LENGTH_LONG).show();
        } else if (result == SAAgent.CONNECTION_DUPLICATE_REQUEST) {
            Toast.makeText(getBaseContext(), "CONNECTION_DUPLICATE_REQUEST", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getBaseContext(), R.string.ConnectionFailure, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onError(SAPeerAgent peerAgent, String errorMessage, int errorCode) {
        super.onError(peerAgent, errorMessage, errorCode);
    }

    @Override
    protected void onPeerAgentsUpdated(SAPeerAgent[] peerAgents, int result) {
        final SAPeerAgent[] peers = peerAgents;
        final int status = result;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (peers != null) {
                    if (status == SAAgent.PEER_AGENT_AVAILABLE) {
                        Toast.makeText(getApplicationContext(), "PEER_AGENT_AVAILABLE", Toast.LENGTH_LONG).show();
                        // findpeers added here to have automatic connection on peer agent available if Tizen is enabled in preferences
                        findPeers();
                    } else {
                        Toast.makeText(getApplicationContext(), "PEER_AGENT_UNAVAILABLE", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }

    public class ServiceConnection extends SASocket {
        public ServiceConnection() {
            super(ServiceConnection.class.getName());
        }

        @Override
        public void onError(int channelId, String errorMessage, int errorCode) {
        }

        @Override
        public void onReceive(int channelId, byte[] data) {
            final String message = new String(data);
            addMessage("Received: ", message);
        }

        @Override
        protected void onServiceConnectionLost(int reason) {
            updateTextView("Disconnected");
            closeConnection();
        }
    }

    public class LocalBinder extends Binder {
        public ConsumerService getService() {
            return ConsumerService.this;
        }
    }

    public void findPeers() {
        findPeerAgents();
    }

    public boolean sendData(final String data) {
        boolean retvalue = false;
        if (mConnectionHandler != null) {
            try {
                mConnectionHandler.send(getServiceChannelId(0), data.getBytes());
                retvalue = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            addMessage("Sent: ", data);
        }
        return retvalue;
    }

    public boolean closeConnection() {
        if (mConnectionHandler != null) {
            mConnectionHandler.close();
            mConnectionHandler = null;
            return true;
        } else {
            return false;
        }
    }

    private boolean processUnsupportedException(SsdkUnsupportedException e) {
        e.printStackTrace();
        int errType = e.getType();
        if (errType == SsdkUnsupportedException.VENDOR_NOT_SUPPORTED
                || errType == SsdkUnsupportedException.DEVICE_NOT_SUPPORTED) {
            /*
             * Your application can not use Samsung Accessory SDK. You application should work smoothly
             * without using this SDK, or you may want to notify user and close your app gracefully (release
             * resources, stop Service threads, close UI thread, etc.)
             */
            stopSelf();
        } else if (errType == SsdkUnsupportedException.LIBRARY_NOT_INSTALLED) {
            Log.e(TAG, "You need to install Samsung Accessory SDK to use this application.");
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_REQUIRED) {
            Log.e(TAG, "You need to update Samsung Accessory SDK to use this application.");
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_RECOMMENDED) {
            Log.e(TAG, "We recommend that you update your Samsung Accessory SDK before using this application.");
            return false;
        }
        return true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        // Log.d(TAG, logPrefix + "onStartCommand: " + action);
        if (mConnectionHandler != null) {
            if (mConnectionHandler.isConnected()) {

                sendData("Hello Accessory");

            } else { // not sure that line below is usefull or not if not connected try to find peers
                findPeers();
            }
        }

        return START_STICKY;
    }

    private void updateTextView(final String str) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ConsumerActivity.updateTextView(str);
            }
        });
    }

    private void addMessage(final String prefix, final String data) {
        final String strToUI = prefix.concat(data);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ConsumerActivity.addMessage(strToUI);
            }
        });
    }


}
