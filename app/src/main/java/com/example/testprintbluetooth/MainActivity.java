package com.example.testprintbluetooth;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private int BLUETOOTH_PERMISSION_CODE = 1;

    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    BluetoothDevice bluetoothDevice;
    OutputStream outputStream;
    InputStream inputStream;
    Thread thread;

    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;

    TextView tvPrinterName;
    EditText editText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnConnect = (Button) findViewById(R.id.btnConnectPrinter);
        Button btnDisconnect = (Button) findViewById(R.id.btnDisconnectPrinter);
        Button btnPrint = (Button) findViewById(R.id.btnPrint);

        editText = (EditText) findViewById(R.id.editTextDocument);

        tvPrinterName = (TextView) findViewById(R.id.tvPrinterName);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View view) {
                try {
                    //API LEVEL 23
                    findBluetoothDevice();
                    openBluetoothPrinter();

                }catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    disconnectPrinter();
                }catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        });

        btnPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    printData();
                }catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        });





    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    private void findBluetoothDevice() {
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                tvPrinterName.setText("No bluetooth device found");
            }

            if (bluetoothAdapter.isEnabled()) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "Bluetooth permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    requestBluetoothPermission();
                }

            }

            Set<BluetoothDevice> pairedDevice = bluetoothAdapter.getBondedDevices();

            if (pairedDevice.size() > 0) {
                for (BluetoothDevice device : pairedDevice) {

                    //Device name
                    if (device.getName().equals("SOL58_E93A")) {

                        bluetoothDevice = device;
                        tvPrinterName.setText("Bluethoot print: " + bluetoothDevice.getName());
                        break;
                    }
                }
            }
            tvPrinterName.setText("Bluethoot print attached");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    @SuppressLint("MissingPermission")
    private void openBluetoothPrinter() throws Exception {
        try {
            UUID uuidSting = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
            if (permissionGranted()) {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuidSting);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();

                beginListenData();

            } else {
                requestBluetoothPermission();
            }

        }catch (Exception ex){
            ex.printStackTrace();
        }
    }


    private boolean permissionGranted(){
        boolean permission = false;

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED){
            permission = true;
        }

        return permission;
    }

    private void beginListenData(){
        try{
            final Handler handler = new Handler();
            byte delimit = 10;
            stopWorker = false;
            readBufferPosition =0;
            readBuffer = new byte[1024];

            thread = new Thread(new Runnable() {
                @Override
                public void run() {

                    while(!Thread.currentThread().isInterrupted() && !stopWorker){
                        try {
                            int byteAvailable = inputStream.available();
                            if(byteAvailable > 0){
                                byte [] packetByte = new byte[byteAvailable];
                                inputStream.read(packetByte);

                                for(int i = 0; i < byteAvailable; i++){
                                    byte bByte = packetByte[i];
                                    if(bByte == delimit){
                                        byte[] encodedByte = new byte[readBufferPosition];
                                        System.arraycopy(
                                                readBuffer,0,
                                                encodedByte,0,
                                                encodedByte.length
                                        );

                                        final String data = new String(encodedByte, "US-ASCII");
                                        readBufferPosition=0;
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                tvPrinterName.setText(data);
                                            }
                                        });
                                    }else{
                                        readBuffer[readBufferPosition++] = bByte;
                                    }
                                }
                            }
                        }catch (Exception ex){
                            ex.printStackTrace();
                            stopWorker = true;
                        }
                    }

                }
            });

            thread.start();

        }catch (Exception ex){
            ex.printStackTrace();
        }
    }


    private void printData() throws IOException{
        try{
                String output = editText.getText().toString();
                output+="\n";
                outputStream.write(output.getBytes());
                tvPrinterName.setText("Printing text...");

        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void disconnectPrinter() throws IOException{
        try {
            stopWorker = true;
            outputStream.close();
            inputStream.close();
            bluetoothSocket.close();
            tvPrinterName.setText("Printer disconnected");

        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void requestBluetoothPermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.BLUETOOTH)){
           new AlertDialog.Builder(this)
                   .setTitle("Permisssion needed")
                   .setMessage("This permission is need")
                   .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(DialogInterface dialogInterface, int i) {
                           ActivityCompat.requestPermissions(MainActivity.this,new String[] {Manifest.permission.BLUETOOTH}, BLUETOOTH_PERMISSION_CODE);
                       }
                   })
                   .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(DialogInterface dialogInterface, int i) {
                           dialogInterface.dismiss();
                       }
                   }).create().show();
        }else{
            ActivityCompat.requestPermissions(this,new String[] {Manifest.permission.BLUETOOTH}, BLUETOOTH_PERMISSION_CODE);
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
         if (requestCode == BLUETOOTH_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}