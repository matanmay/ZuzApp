package com.haifa.zuzapp;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.haifa.zuzapp.MovementLogger;

import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // UI Components
    private TextInputEditText etExperimenterCode;
    private TextInputEditText etSessionId;
    private Button btnToggleSession;
    private TextView tvStatus;
    private TextView tvSensorData;

    // Sensor Logic
    private SensorManager sensorManager;
    private Sensor gyroscope;

    // Logging Logic
    private MovementLogger logger;
    private boolean isRecording = false;
    private String currentSessionId;

    private float lastLoggedDelta = -1.0f;

    // Threshold to ignore tiny vibrations and force 0.0
    // NOTE: Since we converted to degrees, this value might need adjustment.
    private static final float MOVEMENT_THRESHOLD = 0.11f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep screen on so sensors don't doze off during experiment
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initializeViews();
        initializeSensors();
        logger = new MovementLogger();
    }

    private void initializeViews() {
        etExperimenterCode = findViewById(R.id.etExperimenterCode);
        etSessionId = findViewById(R.id.etSessionId);
        btnToggleSession = findViewById(R.id.btnToggleSession);
        tvStatus = findViewById(R.id.tvStatus);
        tvSensorData = findViewById(R.id.tvSensorData);

        btnToggleSession.setOnClickListener(v -> toggleSession());
    }

    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    private void toggleSession() {
        if (isRecording) {
            stopExperiment();
        } else {
            startExperiment();
        }
    }

    private void startExperiment() {
        // Get the Subject Name / Experimenter Code
        String code = etExperimenterCode.getText().toString().trim();
        if (code.isEmpty()) {
            etExperimenterCode.setError("Code required");
            return;
        }

        // Handle Session ID: Use manual input if provided, else generate UUID
        String manualSessionId = etSessionId.getText().toString().trim();
        if (!manualSessionId.isEmpty()) {
            currentSessionId = manualSessionId;
        } else {
            currentSessionId = UUID.randomUUID().toString().substring(0, 8);
        }

        try {
            // Pass the context, the subject name (code), and the session ID
            logger.startSession(this, code, currentSessionId);

            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
            }

            // UI Updates
            isRecording = true;
            etExperimenterCode.setEnabled(false);
            etSessionId.setEnabled(false);
            btnToggleSession.setText("STOP SESSION");
            btnToggleSession.setBackgroundColor(Color.RED);
            tvStatus.setText("Recording... (Session: " + currentSessionId + ")");

        } catch (Exception e) {
            Toast.makeText(this, "Error starting: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void stopExperiment() {
        // Stop Logger
        logger.stopSession();

        // Unregister Sensor to save battery
        sensorManager.unregisterListener(this);

        // UI Updates
        isRecording = false;
        etExperimenterCode.setEnabled(true);
        etSessionId.setEnabled(true);
        btnToggleSession.setText("START SESSION");
        btnToggleSession.setBackgroundColor(getColor(com.google.android.material.R.color.design_default_color_primary));

        tvStatus.setText("Saved to: " + logger.getFilePath());
        tvSensorData.setText("Gyro: 0.00 deg/s");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isRecording) return;

        // Check that the event is from the gyroscope
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            // 1. Calculate Total Velocity Magnitude (Result is in Radians per Second)
            double magnitudeRad = Math.sqrt(x * x + y * y + z * z);

            // 2. Convert from Radians to Degrees
            double magnitudeDeg = Math.toDegrees(magnitudeRad);

            float delta = (float) magnitudeDeg;

            // Optional: Noise filtering
            // If you use this, you might need to increase MOVEMENT_THRESHOLD
            // because 0.11 degrees/second is very small.
            /*
            if (delta < MOVEMENT_THRESHOLD) {
                delta = 0.0f;
            }
            */

            // Update the UI
            tvSensorData.setText(String.format("Gyro Delta: %.2f deg/s", delta));

            String expCode = etExperimenterCode.getText().toString();
            logger.logMovement(currentSessionId, expCode, delta);

            lastLoggedDelta = delta;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isRecording) {
            sensorManager.unregisterListener(this);
        }
    }
}