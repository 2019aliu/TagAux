package com.example.tagaux;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.BleSignal;
import com.google.android.gms.nearby.messages.Distance;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TagAuxActivity";

    // Database objects
    private DatabaseReference mDatabase;

    // GPS variables
    private FusedLocationProviderClient mFusedLocationClient;
    private double wayLatitude = 0.0, wayLongitude = 0.0;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private boolean isContinue = false;
    private boolean isGPS = false;

    // elements
    private TextView txtContinueLocation;
    private StringBuilder stringBuilder;
    private TextView txtBTWifiInfo;
    private Button mFlashButton;
    private Button mVibrateButton;
    private CameraManager mCameraManager;
    private String mCameraId;

    // Connection vars
    private MessageListener mMessageListener;
    private Message mMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Database
        mDatabase = FirebaseDatabase.getInstance().getReference("test");
        final DatabaseReference mUserItems = mDatabase.child("testUser");

        // Inflate all elements
        this.txtContinueLocation = (TextView) findViewById(R.id.txtContinueLocation);
        this.txtBTWifiInfo = findViewById(R.id.txtWifiBT);
        this.mFlashButton = findViewById(R.id.buttonFlash);
        this.mVibrateButton = findViewById(R.id.buttonVibrate);

        mUserItems.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                // Generate UUID and get device name, BT address, WIFI MAC address
                String deviceId = UUID.randomUUID().toString();
                String deviceName = Build.MODEL;

                // Read from the database
                HashMap<String, String> deviceIds = new HashMap<>();

                HashMap<String, HashMap<String, Object>> items =
                        (HashMap<String, HashMap<String, Object>>) dataSnapshot.getValue();
                // Populate the arraylist with PENDING items
                for (String itemID : items.keySet()) {
                    if (!(Boolean) items.get(itemID).get("pending") && items.get(itemID).get("device").equals(deviceName)) {
                        deviceId = itemID;
                    }
                }

                Log.d(TAG, deviceId);

                mUserItems.child(deviceId).child("device").setValue(deviceName);
                String wifimac = getMacAddr();
                mUserItems.child(deviceId).child("wifiMAC").setValue(wifimac);
                String btAddr = getBluetoothMacAddress();
                txtBTWifiInfo.setText(String.format("Bluetooth Address: %s\nWifi MAC address: %s",
                        btAddr, wifimac));
                mUserItems.child(deviceId).child("btAddress").setValue(btAddr);
                mUserItems.child(deviceId).child("pending").setValue(true);

                // GPS locating thingy
                mFusedLocationClient = LocationServices.getFusedLocationProviderClient(MainActivity.this);

                locationRequest = LocationRequest.create();
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                locationRequest.setInterval(10 * 1000); // 10 seconds
                locationRequest.setFastestInterval(5 * 1000); // 5 seconds

                new GpsUtils(MainActivity.this).turnGPSOn(new GpsUtils.onGpsListener() {
                    @Override
                    public void gpsStatus(boolean isGPSEnable) {
                        // turn on GPS
                        isGPS = isGPSEnable;
                    }
                });

                String finalDeviceId = deviceId;
                locationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        if (locationResult == null) {
                            return;
                        }
                        for (Location location : locationResult.getLocations()) {
                            if (location != null) {
                                wayLatitude = location.getLatitude();
                                wayLongitude = location.getLongitude();
                                stringBuilder.append(wayLatitude);
                                stringBuilder.append("-");
                                stringBuilder.append(wayLongitude);
                                stringBuilder.append("\n\n");
                                txtContinueLocation.setText(stringBuilder.toString());
                                mUserItems.child(finalDeviceId).child("location").setValue(String.format("(%s, %s)", wayLatitude, wayLongitude));
                                if (!isContinue && mFusedLocationClient != null) {
                                    mFusedLocationClient.removeLocationUpdates(locationCallback);
                                }
                            }
                        }
                    }
                };

                if (!isGPS) {
                    Toast.makeText(MainActivity.this, "Please turn on GPS", Toast.LENGTH_SHORT).show();
                    return;
                }
                isContinue = true;
                stringBuilder = new StringBuilder();
                getLocation();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });

        mMessageListener = new MessageListener() {
            @Override
            public void onFound(Message message) {
                String content = new String(message.getContent());
                Log.d(TAG, "Found message: " + new String(message.getContent()));
                String[] messageSplit = content.split("\\s+");

                if (messageSplit[0].toLowerCase().equals("vibrate")) {
                    Log.d(TAG, "Activating vibration");
                    unsubscribe();

                    // Vibrate the phone
                    int vibrate_duration = Integer.parseInt(messageSplit[1]);
                    Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    // Vibrate for 500 milliseconds
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(vibrate_duration, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        //deprecated in API 26
                        vibrator.vibrate(vibrate_duration);
                    }

                    // resubscribe to the main app
                    subscribe();
                }
            }

            @Override
            public void onLost(Message message) {
                Log.d(TAG, "Lost sight of message: " + new String(message.getContent()));
            }
        };

        mMessage = new Message("Hello World from Tag Aux".getBytes());
    }

    @Override
    public void onStart() {
        super.onStart();
        Nearby.getMessagesClient(this).publish(mMessage);
        Nearby.getMessagesClient(this).subscribe(mMessageListener);
    }

    @Override
    public void onStop() {
        Nearby.getMessagesClient(this).unpublish(mMessage);
        Nearby.getMessagesClient(this).unsubscribe(mMessageListener);
        super.onStop();
    }

    private void publish(String message) {
        Log.i(TAG, "Publishing message: " + message);
        mMessage = new Message(message.getBytes());
        Nearby.getMessagesClient(this).publish(mMessage);
    }

    private void unpublish() {
        Log.i(TAG, "Unpublishing.");
        if (mMessage != null) {
            Nearby.getMessagesClient(this).unpublish(mMessage);
            mMessage= null;
        }
    }

    // Subscribe to receive messages.
    private void subscribe() {
        Log.i(TAG, "Subscribing.");
        Nearby.getMessagesClient(this).subscribe(mMessageListener);
    }

    private void unsubscribe() {
        Log.i(TAG, "Unsubscribing.");
        Nearby.getMessagesClient(this).unsubscribe(mMessageListener);
    }

    /*
    Location finding methods
     */

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    AppConstants.LOCATION_REQUEST);

        } else {
            if (isContinue) {
                mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            } else {
                mFusedLocationClient.getLastLocation().addOnSuccessListener(MainActivity.this, location -> {
                    if (location != null) {
                        wayLatitude = location.getLatitude();
                        wayLongitude = location.getLongitude();
//                        txtLocation.setText(String.format(Locale.US, "%s - %s", wayLatitude, wayLongitude));
                    } else {
                        mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
                    }
                });
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1000: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if (isContinue) {
                        mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
                    } else {
                        mFusedLocationClient.getLastLocation().addOnSuccessListener(MainActivity.this, location -> {
                            if (location != null) {
                                wayLatitude = location.getLatitude();
                                wayLongitude = location.getLongitude();
                            } else {
                                mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
                            }
                        });
                    }
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == AppConstants.GPS_REQUEST) {
                isGPS = true; // flag maintain before get location
            }
        }
    }

    public static String getMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    String hex = Integer.toHexString(b & 0xFF);
                    if (hex.length() == 1)
                        hex = "0".concat(hex);
                    res1.append(hex.concat(":"));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) {
        }
        return "";
    }

    /**
     * get bluetooth adapter MAC address
     * @return MAC address String
     */
    public static String getBluetoothMacAddress() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // if device does not support Bluetooth
        if(mBluetoothAdapter==null){
            Log.d(TAG,"device does not support bluetooth");
            return null;
        }

        return mBluetoothAdapter.getAddress();
    }

//    public void showNoFlashError() {
//        AlertDialog alert = new AlertDialog.Builder(this)
//                .create();
//        alert.setTitle("Oops!");
//        alert.setMessage("Flash not available in this device...");
//        alert.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
//            public void onClick(DialogInterface dialog, int which) {
//                finish();
//            }
//        });
//        alert.show();
//    }

//    @RequiresApi(api = Build.VERSION_CODES.M)
//    public void switchFlashLight(boolean status) {
//        try {
//            mCameraManager.setTorchMode(mCameraId, status);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
//
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    public void flashRecentAPI() {
//        // Check flash availability
//        boolean isFlashAvailable = getApplicationContext().getPackageManager()
//                .hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
//        if (!isFlashAvailable) {
//            showNoFlashError();
//        }
//
//        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//        try {
//            mCameraId = mCameraManager.getCameraIdList()[0];
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//
//        switchFlashLight(true);
//        new android.os.Handler().postDelayed(
//                () -> {
//                    switchFlashLight(false);
//                },
//                FLASH_DURATION_MILLISECONDS);
//    }
}
