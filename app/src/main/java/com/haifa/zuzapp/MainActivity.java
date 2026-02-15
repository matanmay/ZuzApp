package com.haifa.zuzapp;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
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
    private Button btnCalibrate;
    private TextView tvStatus;
    private TextView tvSensorData;
    private TextView tvCalibrationStatus;

    // Sensor Logic
    private SensorManager sensorManager;
    private Sensor gyroscope;
    private Sensor rotation;

    // Logging Logic
    private MovementLogger logger;
    private boolean isRecording = false;
    private String currentSessionId;

    private float lastLoggedDelta = -1.0f;

    // Rotation sensor values
    private float pitch = 0.0f;
    private float roll = 0.0f;
    private float yaw = 0.0f;

    // Calibration variables
    private float baselineNoise = 0.0f;
    private float baselineYaw = 0.0f;
    private boolean isCalibrating = false;
    private int calibrationSamples = 0;
    private float calibrationSum = 0.0f;
    private float yawCalibrationSum = 0.0f;
    private static final int CALIBRATION_SAMPLE_COUNT = 50;

    // Threshold to filter noise (applied after baseline subtraction)
    private static final float MOVEMENT_THRESHOLD = 0.5f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep screen on so sensors don't doze off during experiment
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initializeViews();
        initializeSensors();
        logger = new MovementLogger();

        // Auto-calibrate on startup
        startCalibration();
    }

    private void initializeViews() {
        etExperimenterCode = findViewById(R.id.etExperimenterCode);
        etSessionId = findViewById(R.id.etSessionId);
        btnToggleSession = findViewById(R.id.btnToggleSession);
        btnCalibrate = findViewById(R.id.btnCalibrate);
        tvStatus = findViewById(R.id.tvStatus);
        tvSensorData = findViewById(R.id.tvSensorData);
        tvCalibrationStatus = findViewById(R.id.tvCalibrationStatus);

        btnToggleSession.setOnClickListener(v -> toggleSession());

        // Add calibration button listener
        if (btnCalibrate != null) {
            btnCalibrate.setOnClickListener(v -> {
                if (!isRecording) {
                    startCalibration();
                } else {
                    Toast.makeText(this, "Cannot calibrate during recording", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (gyroscope == null) {
                Toast.makeText(this, "Gyroscope not available on this device", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startCalibration() {
        if (gyroscope == null) {
            Toast.makeText(this, "Gyroscope not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (rotation == null) {
            Toast.makeText(this, "Rotation Vector not available", Toast.LENGTH_SHORT).show();
            return;
        }

        isCalibrating = true;
        calibrationSamples = 0;
        calibrationSum = 0.0f;
        yawCalibrationSum = 0.0f;

        // Register sensor listener if not already registered
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME);

        // Update UI
        if (tvCalibrationStatus != null) {
            tvCalibrationStatus.setText("Calibrating... Keep device STILL!");
            tvCalibrationStatus.setTextColor(Color.parseColor("#FF9800")); // Orange
        }

        Toast.makeText(this, "Calibrating... Keep device still!", Toast.LENGTH_SHORT).show();

        if (btnCalibrate != null) {
            btnCalibrate.setEnabled(false);
        }
    }

    private void finishCalibration() {
        baselineNoise = Math.abs(calibrationSum) / CALIBRATION_SAMPLE_COUNT;
        baselineYaw = yawCalibrationSum / CALIBRATION_SAMPLE_COUNT;
        isCalibrating = false;

        // Update UI
        if (tvCalibrationStatus != null) {
            tvCalibrationStatus
                    .setText(String.format("Calibrated! Baseline: %.2f deg/s, Yaw: %.2f°", baselineNoise, baselineYaw));
            tvCalibrationStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
        }

        Toast.makeText(this,
                String.format("Calibration complete! Baseline: %.2f deg/s, Yaw: %.2f°", baselineNoise, baselineYaw),
                Toast.LENGTH_LONG).show();

        if (btnCalibrate != null) {
            btnCalibrate.setEnabled(true);
        }

        // Unregister sensor if not recording
        if (!isRecording) {
            sensorManager.unregisterListener(this);
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
        // Check if calibration has been done
        if (baselineNoise == 0.0f && !isCalibrating) {
            Toast.makeText(this, "Please wait for calibration to complete", Toast.LENGTH_SHORT).show();
            return;
        }

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
            } else {
                Toast.makeText(this, "Gyroscope not available", Toast.LENGTH_SHORT).show();
                return;
            }

            if (rotation != null) {
                sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME);
            }

            // UI Updates
            isRecording = true;
            etExperimenterCode.setEnabled(false);
            etSessionId.setEnabled(false);
            btnToggleSession.setText("STOP SESSION");
            btnToggleSession.setBackgroundColor(Color.RED);
            tvStatus.setText("Recording... (Session: " + currentSessionId + ")");

            if (btnCalibrate != null) {
                btnCalibrate.setEnabled(false);
            }

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

        if (btnCalibrate != null) {
            btnCalibrate.setEnabled(true);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Check that the event is from the gyroscope
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);
            pitch = (float) Math.toDegrees(orientation[1]);
            roll = (float) Math.toDegrees(orientation[2]);
            yaw = (float) Math.toDegrees(orientation[0]);
            Log.println(Log.DEBUG, "ROTATION", "Pitch: " + pitch + " Roll: " + roll + " Yaw: " + yaw);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            // Log.println(Log.DEBUG, "GYRO", "X: " + x + " Y: " + y + " Z: " + z);
            // Log.println(Log.DEBUG, "GYROZ", " Z: " + z);

            // Track Z-axis rotation (can be positive or negative for direction)
            // Convert from radians/sec to degrees/sec
            float rawDelta = (float) Math.toDegrees(z);

            // Log.println(Log.DEBUG, "GYROZ", " Z: " + Math.toDegrees(z));
            // Log.println(Log.DEBUG, "GYROX", " X: " + Math.toDegrees(x));

            // Handle Calibration Phase
            if (isCalibrating) {
                calibrationSum += Math.abs(rawDelta);
                yawCalibrationSum += Math.abs(yaw);
                calibrationSamples++;



                // Update progress
                if (tvCalibrationStatus != null) {
                    tvCalibrationStatus.setText(String.format("Calibrating... %d/%d samples",
                            calibrationSamples, CALIBRATION_SAMPLE_COUNT));
                }

                if (calibrationSamples >= CALIBRATION_SAMPLE_COUNT) {
                    finishCalibration();
                }
                return;
            }

            // If not recording, just return (but don't show data)
            if (!isRecording)
                return;

            // Subtract baseline offset from calibration
            float magnitude = Math.max(0.0f, Math.abs(rawDelta) - baselineNoise);
            float delta = Math.copySign(magnitude, rawDelta);

            // Apply threshold to filter noise while preserving direction
            if (Math.abs(delta) < Math.abs(MOVEMENT_THRESHOLD)) {
                delta = 0.0f;
            }

            // Update the UI
            tvSensorData.setText(String.format("Gyro Delta: %.2f deg/s (Raw: %.2f)", delta, rawDelta));

            // Apply yaw calibration (subtract baseline)
            float calibratedYaw = Math.abs(Math.abs(yaw) - Math.abs(baselineYaw));
            calibratedYaw = Math.copySign(calibratedYaw, yaw);
            tvSensorData.append(String.format(" | Yaw: %.2f°", calibratedYaw));

            // Log the movement
            String expCode = etExperimenterCode.getText().toString();
            logger.logMovement(currentSessionId, expCode, delta, rawDelta, pitch, roll, calibratedYaw, yaw);

            lastLoggedDelta = delta;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used, but you could log accuracy changes if needed
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If we have calibrated but not recording, we can show real-time data
        // by registering the listener (optional)
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Only unregister if not recording and not calibrating
        if (!isRecording && !isCalibrating) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Make sure to unregister listener and stop any ongoing session
        if (isRecording) {
            stopExperiment();
        }
        sensorManager.unregisterListener(this);
    }
}