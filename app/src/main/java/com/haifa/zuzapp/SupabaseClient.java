package com.haifa.zuzapp;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Supabase client for inserting movement records and session data
 */
public class SupabaseClient {

    private static final String TAG = "SupabaseClient";

    // TODO: Replace these with your actual Supabase project values
    private static final String SUPABASE_URL = Config.getSupabaseUrl();
    private static final String SUPABASE_ANON_KEY = Config.getSupabaseAnonKey();

    private final ExecutorService executorService;

    public SupabaseClient() {
        this.executorService = Executors.newFixedThreadPool(3);
    }

    /**
     * Insert a session start record
     */
    public void insertSessionStart(String sessionId, String experimenterCode,
                                   String startTime, long startTimeMillis,
                                   String deviceModel, String androidVersion,
                                   String filePath) {
        executorService.execute(() -> {
            try {
                JSONObject sessionData = new JSONObject();
                sessionData.put("session_id", sessionId);
                sessionData.put("experimenter_code", experimenterCode);
                sessionData.put("start_time", startTime);
                sessionData.put("start_time_millis", startTimeMillis);
                sessionData.put("status", "started");
                sessionData.put("device_model", deviceModel);
                sessionData.put("android_version", androidVersion);
                sessionData.put("file_path", filePath);

                String response = makePostRequest("/rest/v1/sessions", sessionData);
                Log.d(TAG, "Session start inserted to Supabase: " + response);

            } catch (Exception e) {
                Log.e(TAG, "Error inserting session start to Supabase", e);
            }
        });
    }

    /**
     * Update session with end time and duration
     */
    public void updateSessionEnd(String sessionId, String experimenterCode,
                                 String endTime, long endTimeMillis, long durationMs) {
        executorService.execute(() -> {
            try {
                JSONObject updateData = new JSONObject();
                updateData.put("end_time", endTime);
                updateData.put("end_time_millis", endTimeMillis);
                updateData.put("duration_ms", durationMs);
                updateData.put("status", "completed");

                // Use both session_id AND experimenter_code to uniquely identify the session
                String endpoint = "/rest/v1/sessions?session_id=eq." + sessionId
                        + "&experimenter_code=eq." + experimenterCode;
                String response = makePatchRequest(endpoint, updateData);
                Log.d(TAG, "Session end updated in Supabase: " + response);

            } catch (Exception e) {
                Log.e(TAG, "Error updating session end in Supabase", e);
            }
        });
    }

    /**
     * Insert a batch of movement records
     */
    public void insertMovementRecords(JSONArray records) {
        if (records.length() == 0) return;

        executorService.execute(() -> {
            try {
                String response = makePostRequest("/rest/v1/movement_records", records);
                Log.d(TAG, "Batch of " + records.length() + " records inserted to Supabase");

            } catch (Exception e) {
                Log.e(TAG, "Error inserting movement records to Supabase", e);
            }
        });
    }

    /**
     * Insert a single movement record
     */
    public void insertMovementRecord(String sessionId, String experimenterCode,
                                     String timestamp, long elapsedTimeMs,
                                     float magnitude) {
        executorService.execute(() -> {
            try {
                JSONObject record = new JSONObject();
                record.put("session_id", sessionId);
                record.put("experimenter_code", experimenterCode);
                record.put("timestamp", timestamp);
                record.put("elapsed_time_ms", elapsedTimeMs);
                record.put("magnitude", magnitude);

                String response = makePostRequest("/rest/v1/movement_records", record);
                Log.d(TAG, "Movement record inserted to Supabase");

            } catch (Exception e) {
                Log.e(TAG, "Error inserting movement record to Supabase", e);
            }
        });
    }

    /**
     * Make a POST request to Supabase
     */
    private String makePostRequest(String endpoint, Object data) throws Exception {
        URL url = new URL(SUPABASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_ANON_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Prefer", "return=minimal");
            conn.setDoOutput(true);

            String jsonData;
            if (data instanceof JSONObject) {
                jsonData = ((JSONObject) data).toString();
            } else if (data instanceof JSONArray) {
                jsonData = ((JSONArray) data).toString();
            } else {
                jsonData = data.toString();
            }

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                return "Success";
            } else {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                throw new Exception("HTTP " + responseCode + ": " + response.toString());
            }

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Make a PATCH request to Supabase
     */
    private String makePatchRequest(String endpoint, JSONObject data) throws Exception {
        URL url = new URL(SUPABASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("apikey", SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_ANON_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Prefer", "return=minimal");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = data.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                return "Success";
            } else {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                throw new Exception("HTTP " + responseCode + ": " + response.toString());
            }

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}