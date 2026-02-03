package com.haifa.zuzapp;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
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
    private static final String CSV_HEADER = "SessionID,ExperimenterCode,Timestamp,ElapsedTimeMs,Magnitude\n";
    private static final int BATCH_SIZE = 20;

    private File currentLogFile;
    private FileWriter writer;
    private long sessionStartTime;

    // Firebase
    private FirebaseFirestore db;
    private List<Map<String, Object>> logBuffer;

    // Supabase
    private SupabaseClient supabaseClient;
    private List<JSONObject> supabaseBuffer;

    // Track the last value sent to Firebase to avoid duplicates
    private float lastFirebaseDelta = -1.0f;

    // Session tracking for Firestore
    private String currentSessionId;
    private String currentExperimenterCode;
    private DocumentReference currentSessionDocRef;

    public MovementLogger() {
        db = FirebaseFirestore.getInstance();
        logBuffer = new ArrayList<>();

        // Initialize Supabase
        supabaseClient = new SupabaseClient();
        supabaseBuffer = new ArrayList<>();
    }

    /**
     * Starts the session and creates the CSV file with the specific naming convention:
     * SubjectName__SessionID__Date_Time.csv
     *
     * ALSO logs session START to Firestore AND Supabase
     */
    public void startSession(Context context, String subjectName, String sessionId) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

        // Store current session info
        this.currentSessionId = sessionId;
        this.currentExperimenterCode = subjectName;

        // Construct the filename: Subject__Session__Timestamp.csv
        String fileName = subjectName + "__" + sessionId + "__" + timeStamp + ".csv";

        File directory = context.getFilesDir();
        currentLogFile = new File(directory, fileName);

        writer = new FileWriter(currentLogFile, true);
        writer.append(CSV_HEADER);
        writer.flush();

        sessionStartTime = System.currentTimeMillis();
        logBuffer.clear();
        supabaseBuffer.clear();

        // Reset the last delta so the first record is always logged to Firebase
        lastFirebaseDelta = -1.0f;

        // ======================================================
        // LOG SESSION START TO FIRESTORE
        // ======================================================
        logSessionStartToFirestore(subjectName, sessionId, timeStamp);

        // ======================================================
        // LOG SESSION START TO SUPABASE
        // ======================================================
        logSessionStartToSupabase(subjectName, sessionId, timeStamp);

        Log.d(TAG, "Session started. File created: " + currentLogFile.getAbsolutePath());
    }

    /**
     * Log session START event to Firestore
     */
    private void logSessionStartToFirestore(String experimenterCode, String sessionId, String timestamp) {
        try {
            // Create reference to this session's document
            currentSessionDocRef = db.collection("experiments")
                    .document(experimenterCode)
                    .collection("sessions")
                    .document(sessionId);

            // Prepare session start data
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("sessionId", sessionId);
            sessionData.put("experimenterCode", experimenterCode);
            sessionData.put("startTime", timestamp);
            sessionData.put("startTimeMillis", sessionStartTime);
            sessionData.put("status", "started");
            sessionData.put("deviceModel", android.os.Build.MODEL);
            sessionData.put("androidVersion", android.os.Build.VERSION.RELEASE);
            sessionData.put("filePath", currentLogFile.getAbsolutePath());
            sessionData.put("createdAt", com.google.firebase.Timestamp.now());

            // Write to Firestore
            currentSessionDocRef.set(sessionData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Session START logged to Firestore successfully");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error logging session START to Firestore", e);
                    });

        } catch (Exception e) {
            Log.e(TAG, "Exception in logSessionStartToFirestore", e);
        }
    }

    /**
     * Log session START event to Supabase
     */
    private void logSessionStartToSupabase(String experimenterCode, String sessionId, String timestamp) {
        try {
            supabaseClient.insertSessionStart(
                    sessionId,
                    experimenterCode,
                    timestamp,
                    sessionStartTime,
                    android.os.Build.MODEL,
                    android.os.Build.VERSION.RELEASE,
                    currentLogFile.getAbsolutePath()
            );
            Log.d(TAG, "Session START sent to Supabase");
        } catch (Exception e) {
            Log.e(TAG, "Exception in logSessionStartToSupabase", e);
        }
    }

    public void logMovement(String sessionId, String experimenterCode, float magnitude) {

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - sessionStartTime;
        String timeString = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date(currentTime));

        // ---------------------------------------------------------
        // 1. Write to local CSV file (ALWAYS write, even if 0.0)
        // ---------------------------------------------------------
        if (writer != null) {
            String entry = String.format(Locale.US, "%s,%s,%s,%d,%.4f\n",
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

        // ---------------------------------------------------------
        // 2. Upload to Firebase (ONLY if delta changed)
        // ---------------------------------------------------------
        if (Float.compare(magnitude, lastFirebaseDelta) != 0) {

            Map<String, Object> record = new HashMap<>();
            record.put("sessionId", sessionId);
            record.put("experimenterCode", experimenterCode);
            record.put("timestamp", timeString);
            record.put("elapsedTimeMs", elapsedTime);
            record.put("magnitude", magnitude);
            record.put("serverTimestamp", com.google.firebase.Timestamp.now());

            logBuffer.add(record);

            // Update the last logged value
            lastFirebaseDelta = magnitude;

            // Upload batch if buffer reached threshold
            if (logBuffer.size() >= BATCH_SIZE) {
                uploadFirebaseBuffer(sessionId);
            }
        }

        // ---------------------------------------------------------
        // 3. Upload to Supabase (ONLY if delta changed)
        // ---------------------------------------------------------
        if (Float.compare(magnitude, lastFirebaseDelta) == 0) {
            try {
                JSONObject supabaseRecord = new JSONObject();
                supabaseRecord.put("session_id", sessionId);
                supabaseRecord.put("experimenter_code", experimenterCode);
                supabaseRecord.put("timestamp", timeString);
                supabaseRecord.put("elapsed_time_ms", elapsedTime);
                supabaseRecord.put("magnitude", magnitude);

                supabaseBuffer.add(supabaseRecord);

                // Upload batch if buffer reached threshold
                if (supabaseBuffer.size() >= BATCH_SIZE) {
                    uploadSupabaseBuffer();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error preparing Supabase record", e);
            }
        }
    }

    private void uploadFirebaseBuffer(String sessionId) {
        if (logBuffer.isEmpty()) return;

        WriteBatch batch = db.batch();
        List<Map<String, Object>> recordsToUpload = new ArrayList<>(logBuffer);
        logBuffer.clear();

        for (Map<String, Object> data : recordsToUpload) {
            String expCode = (String) data.get("experimenterCode");
            if (expCode == null) expCode = "Unknown_Experiment";

            DocumentReference docRef = db.collection("experiments")
                    .document(expCode)
                    .collection("sessions")
                    .document(sessionId)
                    .collection("records")
                    .document();

            batch.set(docRef, data);
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Firebase batch successfully saved"))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving Firebase batch", e));
    }

    private void uploadSupabaseBuffer() {
        if (supabaseBuffer.isEmpty()) return;

        try {
            JSONArray recordsArray = new JSONArray();
            for (JSONObject record : supabaseBuffer) {
                recordsArray.put(record);
            }

            supabaseClient.insertMovementRecords(recordsArray);
            supabaseBuffer.clear();

            Log.d(TAG, "Supabase batch uploaded");
        } catch (Exception e) {
            Log.e(TAG, "Error uploading Supabase batch", e);
        }
    }

    /**
     * Log session END event to Firestore
     */
    private void logSessionEndToFirestore() {
        if (currentSessionDocRef == null) {
            Log.w(TAG, "No active session document reference to update");
            return;
        }

        try {
            long sessionEndTime = System.currentTimeMillis();
            long sessionDuration = sessionEndTime - sessionStartTime;
            String endTimeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date(sessionEndTime));

            // Prepare session end updates
            Map<String, Object> updates = new HashMap<>();
            updates.put("endTime", endTimeStamp);
            updates.put("endTimeMillis", sessionEndTime);
            updates.put("durationMs", sessionDuration);
            updates.put("status", "completed");
            updates.put("updatedAt", com.google.firebase.Timestamp.now());

            // Update the session document
            currentSessionDocRef.update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Session END logged to Firestore successfully");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error logging session END to Firestore", e);
                    });

        } catch (Exception e) {
            Log.e(TAG, "Exception in logSessionEndToFirestore", e);
        }
    }

    /**
     * Log session END event to Supabase
     */
    private void logSessionEndToSupabase() {
        try {
            long sessionEndTime = System.currentTimeMillis();
            long sessionDuration = sessionEndTime - sessionStartTime;
            String endTimeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date(sessionEndTime));

            supabaseClient.updateSessionEnd(
                    currentSessionId,
                    currentExperimenterCode,  // Pass experimenter code too
                    endTimeStamp,
                    sessionEndTime,
                    sessionDuration
            );

            Log.d(TAG, "Session END sent to Supabase");
        } catch (Exception e) {
            Log.e(TAG, "Exception in logSessionEndToSupabase", e);
        }
    }

    public void stopSession() {
        try {
            // ======================================================
            // LOG SESSION END TO FIRESTORE
            // ======================================================
            logSessionEndToFirestore();

            // ======================================================
            // LOG SESSION END TO SUPABASE
            // ======================================================
            logSessionEndToSupabase();

            // Upload any remaining buffered logs before stopping
            if (!logBuffer.isEmpty()) {
                uploadFirebaseBuffer(currentSessionId);
            }

            if (!supabaseBuffer.isEmpty()) {
                uploadSupabaseBuffer();
            }

            // Close the CSV file writer
            if (writer != null) {
                writer.close();
                writer = null;
            }

            Log.d(TAG, "Session stopped.");
        } catch (IOException e) {
            Log.e(TAG, "Error closing log file", e);
        }
    }

    public String getFilePath() {
        return currentLogFile != null ? currentLogFile.getAbsolutePath() : "Unknown";
    }

    /**
     * Clean up resources when logger is no longer needed
     */
    public void cleanup() {
        if (supabaseClient != null) {
            supabaseClient.shutdown();
        }
    }
}