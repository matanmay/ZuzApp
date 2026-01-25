package com.haifa.zuzapp;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Firebase Imports
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.DocumentReference;

public class MovementLogger {

    private static final String TAG = "MovementLogger";
    private static final String CSV_HEADER =
            "SessionID,ExperimenterCode,Timestamp,ElapsedTimeMs,Magnitude\n";
    private static final int BATCH_SIZE = 20;

    private File currentLogFile;
    private FileWriter writer;
    private long sessionStartTime;

    private FirebaseFirestore db;
    private List<Map<String, Object>> logBuffer;

    private float lastFirebaseDelta = -1.0f;

    public MovementLogger() {
        db = FirebaseFirestore.getInstance();
        logBuffer = new ArrayList<>();
    }

    public void startSession(Context context) throws IOException {
        String timeStamp =
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "Experiment_" + timeStamp + ".csv";

        File directory = context.getFilesDir();
        currentLogFile = new File(directory, fileName);

        writer = new FileWriter(currentLogFile, true);
        writer.append(CSV_HEADER);
        writer.flush();

        sessionStartTime = System.currentTimeMillis();
        logBuffer.clear();

        lastFirebaseDelta = -1.0f;

        Log.d(TAG, "Session started: " + currentLogFile.getAbsolutePath());
    }

    public void logMovement(String sessionId,
                            String experimenterCode,
                            float magnitude) {

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - sessionStartTime;
        String timeString =
                new SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                        .format(new Date(currentTime));

        // 1. Write to local CSV file
        if (writer != null) {
            String entry = String.format(
                    Locale.US,
                    "%s,%s,%s,%d,%.4f\n",
                    sessionId,
                    experimenterCode,
                    timeString,
                    elapsedTime,
                    magnitude
            );
            try {
                writer.append(entry);
                writer.flush();
            } catch (IOException e) {
                Log.e(TAG, "Error writing to CSV log", e);
            }
        }

        // 2. Add record to Firebase buffer
        if (Float.compare(magnitude, lastFirebaseDelta) != 0) {

            Map<String, Object> record = new HashMap<>();
            record.put("sessionId", sessionId);
            record.put("experimenterCode", experimenterCode);
            record.put("timestamp", timeString);
            record.put("elapsedTimeMs", elapsedTime);
            record.put("magnitude", magnitude);
            record.put("serverTimestamp", com.google.firebase.Timestamp.now());

            logBuffer.add(record);

            // update the delta
            lastFirebaseDelta = magnitude;

            // batch test
            if (logBuffer.size() >= BATCH_SIZE) {
                uploadBuffer(sessionId);
            }
        }
    }

    // Upload buffered records to Firebase using a batch write
    private void uploadBuffer(String sessionId) {
        if (logBuffer.isEmpty()) return;

        WriteBatch batch = db.batch();

        // Create a safe copy in case new logs arrive during upload
        List<Map<String, Object>> recordsToUpload =
                new ArrayList<>(logBuffer);

        // Clear main buffer immediately
        logBuffer.clear();

        for (Map<String, Object> data : recordsToUpload) {

            // Retrieve experimenter code from the stored data
            String expCode = (String) data.get("experimenterCode");

            // Fallback protection (should not normally occur)
            if (expCode == null) {
                expCode = "Unknown_Experiment";
            }

            /*
             * Firestore structure:
             * experiments/{experimenterCode}/
             *      sessions/{sessionId}/
             *          records/{autoId}
             */
            DocumentReference docRef =
                    db.collection("experiments")
                            .document(expCode)
                            .collection("sessions")
                            .document(sessionId)
                            .collection("records")
                            .document();

            batch.set(docRef, data);
        }

        batch.commit()
                .addOnSuccessListener(
                        aVoid -> Log.d(TAG,
                                "Batch successfully saved to Firebase"))
                .addOnFailureListener(
                        e -> Log.e(TAG,
                                "Error saving batch to Firebase", e));
    }

    public void stopSession() {
        try {
            if (writer != null) {
                writer.close();
                writer = null;
            }

            // Upload any remaining buffered logs
            if (!logBuffer.isEmpty()) {
                // Retrieve sessionId from the first buffered record
                String sessionId =
                        (String) logBuffer.get(0).get("sessionId");
                uploadBuffer(sessionId);
            }

            Log.d(TAG, "Session stopped.");
        } catch (IOException e) {
            Log.e(TAG, "Error closing log file", e);
        }
    }

    public String getFilePath() {
        return currentLogFile != null
                ? currentLogFile.getAbsolutePath()
                : "Unknown";
    }
}
