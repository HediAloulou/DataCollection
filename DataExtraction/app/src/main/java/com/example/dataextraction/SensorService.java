package com.example.dataextraction;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SensorService extends Service implements SensorEventListener {

    private static final String CHANNEL_ID = "SensorServiceChannel";
    private static final String TAG = "SensorService";

    private SensorManager sensorManager;
    private Sensor linearAccelerationSensor;
    private Sensor rotationVectorSensor;

    private List<float[]> linearAccelData = new ArrayList<>();
    private List<float[]> rotationData = new ArrayList<>();

    private String username;

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sensor Recording")
                .setContentText("Recording sensor data...")
                .setSmallIcon(com.google.firebase.database.collection.R.drawable.common_google_signin_btn_icon_light_normal)
                .setContentIntent(pendingIntent);

        startForeground(1, notificationBuilder.build());

        sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        username = intent.getStringExtra("username");
        Log.d(TAG, "Service started with username: " + username);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        Log.d(TAG, "Service destroyed, sensor listeners unregistered");
        sendSensorDataToFirestore();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] values = event.values.clone();
        switch (event.sensor.getType()) {
            case Sensor.TYPE_LINEAR_ACCELERATION:
                linearAccelData.add(values);
                Log.d(TAG, "Linear Acceleration Sensor Data: " + values[0] + ", " + values[1] + ", " + values[2]);
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                rotationData.add(values);
                Log.d(TAG, "Rotation Vector Sensor Data: " + values[0] + ", " + values[1] + ", " + values[2]);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not implemented but can be added if needed
    }

    private void sendSensorDataToFirestore() {
        sendDataToFirestore("Linear Acceleration", linearAccelData);
        sendDataToFirestore("Rotation Vector", rotationData);
    }

    private void sendDataToFirestore(String sensorName, List<float[]> data) {
        if (data.isEmpty()) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        for (float[] values : data) {
            Map<String, Object> sensorData = new HashMap<>();
            Date date = new Date();
            long timestamp = date.getTime();
            @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String formattedTimestamp = dateFormat.format(date);
            sensorData.put("timestamp" , formattedTimestamp);
            sensorData.put("username", username);
            sensorData.put("sensor", sensorName);
            sensorData.put("x", values[0]);
            sensorData.put("y", values[1]);
            sensorData.put("z", values[2]);

            db.collection("sensor_data")
                    .add(sensorData)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "Data sent to Firestore successfully");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send data to Firestore", e);
                    });
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sensor Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}