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
import java.util.List;
import java.util.Locale;

public class MovementLogger {

    private static final String TAG = "MovementLogger";
    private static final String CSV_HEADER = "SessionID,ExperimenterCode,Timestamp,ElapsedTimeMs,Magnitude,RawDelta,Pitch,Roll,Yaw\n";
    private static final int BATCH_SIZE = 20;

    private File currentLogFile;
    private FileWriter writer;
    private long sessionStartTime;

    // Supabase
    private SupabaseClient supabaseClient;
    private List<JSONObject> supabaseBuffer;

    // Session tracking
    private String currentSessionId;
    private String currentExperimenterCode;

    public MovementLogger() {
        // Initialize Supabase
        supabaseClient = new SupabaseClient();
        supabaseBuffer = new ArrayList<>();
    }

    /**
     * Starts the session and creates the CSV file with the specific naming
     * convention:
     * SubjectName__SessionID__Date_Time.csv
     *
     * ALSO logs session START to Supabase
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
        supabaseBuffer.clear();

        // ======================================================
        // LOG SESSION START TO SUPABASE
        // ======================================================
        logSessionStartToSupabase(subjectName, sessionId, timeStamp);

        Log.d(TAG, "Session started. File created: " + currentLogFile.getAbsolutePath());
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
                    currentLogFile.getAbsolutePath());
            Log.d(TAG, "Session START sent to Supabase");
        } catch (Exception e) {
            Log.e(TAG, "Exception in logSessionStartToSupabase", e);
        }
    }

    public void logMovement(String sessionId, String experimenterCode, float magnitude, float rawDelta, float pitch,
            float roll, float yaw) {

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - sessionStartTime;
        String timeString = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date(currentTime));

        // ---------------------------------------------------------
        // 1. Write to local CSV file (ALWAYS write, even if 0.0)
        // ---------------------------------------------------------
        if (writer != null) {
            String entry = String.format(Locale.US, "%s,%s,%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f\n",
                    sessionId,
                    experimenterCode,
                    timeString,
                    elapsedTime,
                    magnitude,
                    rawDelta,
                    pitch,
                    roll,
                    yaw);
            try {
                writer.append(entry);
                writer.flush();
            } catch (IOException e) {
                Log.e(TAG, "Error writing to CSV log", e);
            }
        }

        // ---------------------------------------------------------
        // 2. Upload to Supabase
        // ---------------------------------------------------------
        try {
            JSONObject supabaseRecord = new JSONObject();
            supabaseRecord.put("session_id", sessionId);
            supabaseRecord.put("experimenter_code", experimenterCode);
            supabaseRecord.put("timestamp", timeString);
            supabaseRecord.put("elapsed_time_ms", elapsedTime);
            supabaseRecord.put("magnitude", magnitude);
            supabaseRecord.put("raw_delta", rawDelta);
            supabaseRecord.put("pitch", pitch);
            supabaseRecord.put("roll", roll);
            supabaseRecord.put("yaw", yaw);

            supabaseBuffer.add(supabaseRecord);

            // Upload batch if buffer reached threshold
            if (supabaseBuffer.size() >= BATCH_SIZE) {
                uploadSupabaseBuffer();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error preparing Supabase record", e);
        }
    }

    private void uploadSupabaseBuffer() {
        if (supabaseBuffer.isEmpty())
            return;

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
                    currentExperimenterCode, // Pass experimenter code too
                    endTimeStamp,
                    sessionEndTime,
                    sessionDuration);

            Log.d(TAG, "Session END sent to Supabase");
        } catch (Exception e) {
            Log.e(TAG, "Exception in logSessionEndToSupabase", e);
        }
    }

    public void stopSession() {
        try {
            // ======================================================
            // LOG SESSION END TO SUPABASE
            // ======================================================
            logSessionEndToSupabase();

            // Upload any remaining buffered logs before stopping
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
