package com.example.thesisfitapp;

import android.bluetooth.*;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MainActivity extends AppCompatActivity {

    private String TAG = getClass().getName();
    private final static int REQUEST_ENABLE_BT = 1;
    private final static UUID MY_UUID = new UUID(1234, 5678);
    BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    BluetoothDevice smartwatch;
    BluetoothServerSocket btSocket;
    BluetoothHealth bluetoothHealthProfile;
    Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (setupBT().equals(BT_STATE.BT_NONE)) {
            Log.d(TAG, "BT not supported.");
            return;
        }

        smartwatch = getBondedWatchByMAC();
        if (smartwatch.equals(null)) {
            return;
        }

        (new AcceptThread()).run();
    }

    private BluetoothDevice getBondedWatchByMAC() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice tmpWatch = null;

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                Log.i(TAG, "Paired device found: " + device.getAddress() + " " + device.getName());

                //save my watch
                if (device.getAddress().equals("DC:F7:56:4D:46:B5")) {
                    tmpWatch = device;
                    Log.i(TAG, "Saved smartwatch " + tmpWatch.getAddress() + " " + tmpWatch.getName());
                }
            }
        } else {
            Log.e(TAG, "No paired devices!");
        }
        return tmpWatch;
    }

    private BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEALTH) {
                bluetoothHealthProfile = (BluetoothHealth) proxy;
            }
        }

        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HEALTH) {
                bluetoothHealthProfile = null;
            }
        }
    };

    BluetoothHealthCallback callback = new BluetoothHealthCallback() {
        @Override
        public void onHealthAppConfigurationStatusChange(BluetoothHealthAppConfiguration config, int status) {
            super.onHealthAppConfigurationStatusChange(config, status);
        }
    };

    // Closes the client socket and causes the thread to finish.
    public void cancel() {
        try {
            btSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the client socket", e);
        }
    }

    void manageMyConnectedSocket(BluetoothSocket socket) {

        Log.i(TAG, "Managing socket");
        (new ConnectedThread(socket)).run();

    }

    BT_STATE setupBT() {
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            return BT_STATE.BT_NONE;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            Log.i(TAG, "requesting enable Bluetooth");
        }
        Log.i(TAG, "Bluetooth is on");
        return BT_STATE.BT_ON;
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                Log.d(TAG, "Listening...");
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("ThesisFitAppService", MY_UUID);
                Log.i(TAG, "UUID: " + MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    Log.d(TAG, "Accepting connections...");
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    Log.i(TAG, "Connection was accepted.");
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    manageMyConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Socket's close() method failed", e);
                    }
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    private class ConnectedThread extends Thread {

        // Defines several constants used when transmitting messages between the
        // service and the UI.
       /* interface MessageConstants {
            int MESSAGE_READ = 0;
            int MESSAGE_WRITE = 1;
            int MESSAGE_TOAST = 2;

            // ... (Add other message types here as needed.)
        }*/

        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    // Send the obtained bytes to the UI activity.
                    /*Message readMsg = handler.obtainMessage(
                            MessageConstants.MESSAGE_READ, numBytes, -1,
                            mmBuffer);
                    readMsg.sendToTarget();*/
                    String input = new String(Arrays.copyOfRange(mmBuffer, 0, numBytes-1));
                    Log.i(TAG, "Read input: " + numBytes + "bytes, message: " + input);
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

                // Share the sent message with the UI activity.
                /*Message writtenMsg = handler.obtainMessage(
                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                writtenMsg.sendToTarget();*/
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                /*Message writeErrorMsg =
                        handler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                handler.sendMessage(writeErrorMsg);*/
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}


