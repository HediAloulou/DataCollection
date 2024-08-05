package com.example.prediction;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private Interpreter tflite;
    private TextView resultTextView;
    private BroadcastReceiver sensorDataReceiver;
    private SharedPreferences sharedPreferences;

    private static final Set<String> blockedAppsForKids = new HashSet<>(Arrays.asList(
            "com.facebook.katana",
            "com.google.android.youtube"
            // Add more blocked apps for kids
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultTextView = findViewById(R.id.resultTextView);
        Button startButton = findViewById(R.id.startButton);
        sharedPreferences = getSharedPreferences("com.example.prediction", MODE_PRIVATE);

        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            e.printStackTrace();
        }

        sensorDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                float[] calculatedValues = intent.getFloatArrayExtra("calculatedValues");
                if (calculatedValues != null) {
                    Log.d("MainActivity", "Received calculated values: " + Arrays.toString(calculatedValues));
                    float[] predictionResult = makePrediction(calculatedValues);
                    boolean isKid = predictionResult[0] <= 0.5;
                    String result = isKid ? "Kid" : "Adult";
                    Log.d("MainActivity", "Prediction result: " + result);
                    sharedPreferences.edit().putString("predictionResult", result).apply();
                    resultTextView.setText("Prediction Result: " + result);

                    sharedPreferences.edit().putBoolean("isKid", isKid).apply();

                    if (isKid) {
                        Toast.makeText(context, "Kid mode activated. Some apps are blocked.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.example.prediction.SENSOR_VALUES");
        registerReceiver(sensorDataReceiver, filter);

        // Retrieve and display last prediction result
        String lastResult = sharedPreferences.getString("predictionResult", "No prediction yet");
        resultTextView.setText("Last Prediction: " + lastResult);

        startButton.setOnClickListener(v -> {
            // Start sensor service
            Intent sensorServiceIntent = new Intent(MainActivity.this, SensorService.class);
            startService(sensorServiceIntent);
        });

        // Prompt the user to enable the Accessibility Service
        promptAccessibilityService();
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("modeltf_2.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private float[] makePrediction(float[] inputValues) {
        float[][] outputValues = new float[1][1];
        tflite.run(inputValues, outputValues);
        return outputValues[0];
    }

    private void promptAccessibilityService() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Please enable the Accessibility Service for this app.", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(sensorDataReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if the current app is in the blocked list and if the user is predicted as a kid
        checkBlockedApps();
    }

    private void checkBlockedApps() {
        boolean isKid = sharedPreferences.getBoolean("isKid", false);
        if (isKid) {
            PackageManager pm = getPackageManager();
            String currentApp = getTopAppPackageName(pm);
            if (currentApp != null && blockedAppsForKids.contains(currentApp)) {
                Toast.makeText(this, "This app is blocked for kids.", Toast.LENGTH_SHORT).show();
                // Optionally, close the blocked app and return to the main activity
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        }
    }

    private String getTopAppPackageName(PackageManager pm) {
        // Logic to get the current top app package name
        // This may involve using UsageStatsManager, Accessibility Service, or other methods
        return null; // Placeholder, implement actual logic
    }
}
