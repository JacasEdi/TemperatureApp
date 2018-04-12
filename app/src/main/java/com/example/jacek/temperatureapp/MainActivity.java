package com.example.jacek.temperatureapp;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    Button btnDisconnect;
    Button btnAbout;
    TextView tvInput;
    String address;

    private boolean isBtConnected;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth;
    ConnectedThread ct;
    BluetoothSocket btSocket;

    Handler handler;

    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler();

        btnDisconnect = findViewById(R.id.btn_disconnect);
        btnAbout = findViewById(R.id.btn_about);
        tvInput = findViewById(R.id.tv_input);

        Intent intent = getIntent();

        // Retrieve the address of the connected bluetooth device that was sent from DeviceList
        address = intent.getStringExtra(DeviceList.EXTRA_ADDRESS);

        // Connect to other device via Bluetooth
        new Connect().execute();
    }

    public void onClickRgbOn(View v)
    {
        if (btSocket != null) {
            byte[] msg = "1".getBytes();
            ct.write(msg);
            Log.d(TAG, "Message: " + msg);
        }
    }

    public void onClickRgbOff(View v)
    {
        if (btSocket != null) {
            byte[] msg = "0".getBytes();
            ct.write(msg);
            Log.d(TAG, "Message: " + msg);
        }
    }

    // fast way to call Toast
    private void displayToast(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }

    public void onClickDisconnect(View v) {
        if (btSocket != null) {
            try {
                btSocket.close();
            } catch (IOException e) {
                displayToast("Error");
            }
        }

        // Close this activity
        finish();
    }

    public void onClickAbout(View v) {
        if (v.getId() == R.id.btn_about) {
            Intent i = new Intent(this, AboutActivity.class);
            startActivity(i);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_led_control, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will automatically handle clicks on
        // the Home/Up button, so long as you specify a parent activity in AndroidManifest.xml
        int id = item.getItemId();

        return id == R.id.action_settings || super.onOptionsItemSelected(item);
    }


    @SuppressLint("StaticFieldLeak")
    private class Connect extends AsyncTask<Void, Void, Void> {
        private boolean isConnected = true;

        @Override
        protected void onPreExecute() {
            progress = ProgressDialog.show(MainActivity.this, "Connecting", "Please wait...");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try {
                if (btSocket == null || !isBtConnected) {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device

                    // Get the list of paired devices
                    Set<BluetoothDevice> pairedDevices = myBluetooth.getBondedDevices();
                    if (pairedDevices.size() > 0) {
                        // There are paired devices. Get the name and address of each paired device.
                        for (BluetoothDevice device : pairedDevices) {
                            String deviceName = device.getName();
                            String deviceHardwareAddress = device.getAddress(); // MAC address
                        }
                    }

                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection

                    myBluetooth.cancelDiscovery();

                    btSocket.connect();//start connection
                }
            } catch (IOException e) {
                isConnected = false;
            }
            return null;
        }


        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!isConnected) {
                displayToast("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            } else {
                displayToast("Connected.");
                isBtConnected = true;

                ct = new ConnectedThread(btSocket);
                ct.start();
            }

            progress.dismiss();
        }
    }


    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using tmp objects because member streams are final
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
            int numBytes;

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    numBytes = mmInStream.read(mmBuffer);
                    final String readMessage = new String(mmBuffer, 0, numBytes);

                    Log.d(TAG, "Message: " + readMessage);

                    /*try {
                        int temp = Integer.parseInt(readMessage);
                    }
                    catch (Exception e){
                        Log.d(TAG, "Error when parsing integer", e);
                    }*/

                    if(!readMessage.equals(" "))
                    {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                tvInput.setText(readMessage.trim());
                            }
                        });
                    }
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);
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
