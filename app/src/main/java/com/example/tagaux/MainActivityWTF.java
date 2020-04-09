package com.example.tagaux;

public class MainActivityWTF {
}

//package com.example.tagaux;
//
//import android.Manifest;
//import android.annotation.SuppressLint;
//import android.app.Activity;
//import android.bluetooth.BluetoothAdapter;
//import android.content.Context;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.hardware.Camera;
//import android.hardware.camera2.CameraAccessException;
//import android.hardware.camera2.CameraManager;
//import android.location.Location;
//import android.net.wifi.WifiInfo;
//import android.net.wifi.WifiManager;
//import android.os.Build;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.RequiresApi;
//import androidx.appcompat.app.AlertDialog;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//
//import com.google.android.gms.location.FusedLocationProviderClient;
//import com.google.android.gms.location.LocationCallback;
//import com.google.android.gms.location.LocationRequest;
//import com.google.android.gms.location.LocationResult;
//import com.google.android.gms.location.LocationServices;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//
//import java.net.NetworkInterface;
//import java.util.Collections;
//import java.util.List;
//import java.util.UUID;
//
//@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//public class MainActivity extends AppCompatActivity {
//
//    private static final String TAG = "TagAuxActivity";
//
//    // Database objects
//    private DatabaseReference mDatabase;
//
//    private FusedLocationProviderClient mFusedLocationClient;
//
//    private double wayLatitude = 0.0, wayLongitude = 0.0;
//    private LocationRequest locationRequest;
//    private LocationCallback locationCallback;
//    private TextView txtBTWifiInfo;
//    private TextView txtContinueLocation;
//    private String locationString = "";
//
//    private final static int REQUEST_ENABLE_BT = 1;
//
//    private boolean isGPS = false;
//
//    private Button mFlashButton;
//    private Button mVibrateButton;
//
//    private CameraManager mCameraManager;
//    private String mCameraId;
//
//    private final int FLASH_REPS = 5;
//    private final int FLASH_DURATION_MILLISECONDS = 3 * 1000;
//    private final int VIBRATION_REPS = 5;
//    private final int VIBRATION_DURATION_MILLISECONDS = 3 * 1000;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        // Database
//        mDatabase = FirebaseDatabase.getInstance().getReference("test");
//        final DatabaseReference mUserItems = mDatabase.child("testUser");
//
//        // Inflate all elements
//        this.txtContinueLocation = findViewById(R.id.txtContinueLocation);
//        this.txtBTWifiInfo = findViewById(R.id.txtWifiBT);
//        this.mFlashButton = findViewById(R.id.buttonFlash);
//        this.mVibrateButton = findViewById(R.id.buttonVibrate);
//
//        // Flash, Vibrate buttons
//        mFlashButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Camera cam = Camera.open();
//                Camera.Parameters p = cam.getParameters();
//                p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
//                cam.setParameters(p);
//                cam.startPreview();
//                new android.os.Handler().postDelayed(
//                        () -> {
//                            cam.stopPreview();
//                            cam.release();
//                        },
//                        FLASH_DURATION_MILLISECONDS);
////                // Camera2 package only available for Android API 23 and above
////                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
////                    flashRecentAPI();
////                } else {
////                    Camera cam = Camera.open();
////                    Camera.Parameters p = cam.getParameters();
////                    p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
////                    cam.setParameters(p);
////                    cam.startPreview();
////                    new android.os.Handler().postDelayed(
////                            () -> {
////                                cam.stopPreview();
////                                cam.release();
////                            },
////                            FLASH_DURATION_MILLISECONDS);
////                }
//            }
//        });
//
//        // Enable Bluetooth, WiFi
//        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//        if (!mBluetoothAdapter.isEnabled()) {
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//        }
//        WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
//        wifiManager.setWifiEnabled(true);  // this will become deprecated, watch out
//
//        // Generate UUID and get device name, BT address, WIFI MAC address
//        String deviceId = UUID.randomUUID().toString();
//        String deviceName = Build.MODEL;
//        mUserItems.child(deviceId).child("device").setValue(deviceName);
//        String wifimac = getMacAddr();
//        mUserItems.child(deviceId).child("wifiMAC").setValue(wifimac);
//        String btAddr = getBluetoothMacAddress();
//        txtBTWifiInfo.setText(String.format("Bluetooth Address: %s\nWifi MAC address: %s",
//                btAddr, wifimac));
//        mUserItems.child(deviceId).child("btAddress").setValue(btAddr);
//        mUserItems.child(deviceId).child("pending").setValue(true);
//
//        // GPS get continuous location
//        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
//
//        locationRequest = LocationRequest.create();
//        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
//        locationRequest.setInterval(10 * 1000); // 10 seconds
//        locationRequest.setFastestInterval(5 * 1000); // 5 seconds
//
//        new GpsUtils(this).turnGPSOn(isGPSEnable -> {
//            // turn on GPS
//            isGPS = isGPSEnable;
//        });
//
//        locationCallback = new LocationCallback() {
//            @Override
//            public void onLocationResult(LocationResult locationResult) {
//                if (locationResult == null) {
//                    return;
//                }
//                for (Location location : locationResult.getLocations()) {
//                    if (location != null) {
//                        wayLatitude = location.getLatitude();
//                        wayLongitude = location.getLongitude();
//                        locationString = String.format("Latitude: %s\nLongitude: %s\n\n", wayLatitude, wayLongitude);
//                        mUserItems.child(deviceId).child("location").setValue(String.format("(%s, %s)", wayLatitude, wayLongitude));
//                        txtContinueLocation.setText(locationString);
//                        if (mFusedLocationClient != null) {
//                            mFusedLocationClient.removeLocationUpdates(locationCallback);
//                        }
//                    }
//                }
//            }
//        };
//
//        if (!isGPS) {
//            Toast.makeText(this, "Please turn on GPS", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        getLocation();
//    }
//
//    private void getLocation() {
//        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
//                && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
//                    AppConstants.LOCATION_REQUEST);
//        } else {
//            mFusedLocationClient.getLastLocation().addOnSuccessListener(MainActivity.this, location -> {
//                mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
//            });
////            mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        switch (requestCode) {
//            case 1000: {
//                // If request is cancelled, the result arrays are empty.
//                if (grantResults.length > 0
//                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    mFusedLocationClient.getLastLocation().addOnSuccessListener(MainActivity.this, location -> {
//                        mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
//                    });
////                    mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
//                } else {
//                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
//                }
//                break;
//            }
//        }
//    }
//
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (resultCode == Activity.RESULT_OK) {
//            if (requestCode == AppConstants.GPS_REQUEST) {
//                isGPS = true; // flag maintain before get location
//            }
//        }
//    }
//
//    public static String getMacAddr() {
//        try {
//            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
//            for (NetworkInterface nif : all) {
//                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;
//
//                byte[] macBytes = nif.getHardwareAddress();
//                if (macBytes == null) {
//                    return "";
//                }
//
//                StringBuilder res1 = new StringBuilder();
//                for (byte b : macBytes) {
//                    String hex = Integer.toHexString(b & 0xFF);
//                    if (hex.length() == 1)
//                        hex = "0".concat(hex);
//                    res1.append(hex.concat(":"));
//                }
//
//                if (res1.length() > 0) {
//                    res1.deleteCharAt(res1.length() - 1);
//                }
//                return res1.toString();
//            }
//        } catch (Exception ex) {
//        }
//        return "";
//    }
//
//    /**
//     * get bluetooth adapter MAC address
//     * @return MAC address String
//     */
//    public static String getBluetoothMacAddress() {
//        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//
//        // if device does not support Bluetooth
//        if(mBluetoothAdapter==null){
//            Log.d(TAG,"device does not support bluetooth");
//            return null;
//        }
//
//        return mBluetoothAdapter.getAddress();
//    }
//
////    public void showNoFlashError() {
////        AlertDialog alert = new AlertDialog.Builder(this)
////                .create();
////        alert.setTitle("Oops!");
////        alert.setMessage("Flash not available in this device...");
////        alert.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
////            public void onClick(DialogInterface dialog, int which) {
////                finish();
////            }
////        });
////        alert.show();
////    }
////
////    @RequiresApi(api = Build.VERSION_CODES.M)
////    public void switchFlashLight(boolean status) {
////        try {
////            mCameraManager.setTorchMode(mCameraId, status);
////        } catch (CameraAccessException e) {
////            e.printStackTrace();
////        }
////    }
////
////    @RequiresApi(api = Build.VERSION_CODES.M)
////    public void flashRecentAPI() {
////        // Check flash availability
////        boolean isFlashAvailable = getApplicationContext().getPackageManager()
////                .hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
////        if (!isFlashAvailable) {
////            showNoFlashError();
////        }
////
////        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
////        try {
////            mCameraId = mCameraManager.getCameraIdList()[0];
////        } catch (CameraAccessException e) {
////            e.printStackTrace();
////        }
////
////        switchFlashLight(true);
////        new android.os.Handler().postDelayed(
////                () -> {
////                    switchFlashLight(false);
////                },
////                FLASH_DURATION_MILLISECONDS);
////    }
//}
//
//// unused code
////            } else {
////                mFusedLocationClient.getLastLocation().addOnSuccessListener(MainActivity.this, location -> {
////                    if (location != null) {
////                        wayLatitude = location.getLatitude();
////                        wayLongitude = location.getLongitude();
//////                        txtLocation.setText(String.format(Locale.US, "Latitude: %s\nLongitude: %s", wayLatitude, wayLongitude));
////                    } else {
////                        mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
////                    }
////                });
////            }