package com.haifa.zuzapp;
import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles the file operations for logging experiment data.
 * Writes to App-Specific Internal Storage (no dangerous permissions needed).
 */
public class MovementLogger {

    private static final String TAG = "MovementLogger";
    private static final String CSV_HEADER = "SessionID,ExperimenterCode,Timestamp,ElapsedTimeMs,Magnitude\n";

    private File currentLogFile;
    private FileWriter writer;
    private long sessionStartTime;

    /**
     * Starts a new logging session.
     * Creates a file named "Experiment_{Timestamp}.csv"
     */
    public void startSession(Context context) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "Experiment_" + timeStamp + ".csv";

        // Using getFilesDir() (Internal Storage) ensures we don't need EXTERNAL_STORAGE permissions
        File directory = context.getFilesDir();
        currentLogFile = new File(directory, fileName);

        writer = new FileWriter(currentLogFile, true);
        writer.append(CSV_HEADER);
        writer.flush();

        sessionStartTime = System.currentTimeMillis();
        Log.d(TAG, "Session started: " + currentLogFile.getAbsolutePath());
    }

    /**
     * Logs a single movement event.
     */
    public void logMovement(String sessionId, String experimenterCode, float magnitude) {
        if (writer == null) return;

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - sessionStartTime;
        String timeString = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date(currentTime));

        // CSV Format: SessionID, ExperimenterCode, Timestamp, ElapsedTime, Magnitude
        String entry = String.format(Locale.US, "%s,%s,%s,%d,%.4f\n",
                sessionId, experimenterCode, timeString, elapsedTime, magnitude);

        try {
            writer.append(entry);
            // In a real high-frequency app, you might batch these or flush periodically
            // rather than flushing on every line to save battery.
            // For safety of data in this example, we flush immediately.
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to log", e);
        }
    }

    public void stopSession() {
        try {
            if (writer != null) {
                writer.close();
                writer = null;
                Log.d(TAG, "Session stopped and file closed.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing log file", e);
        }
    }

    public String getFilePath() {
        return currentLogFile != null ? currentLogFile.getAbsolutePath() : "Unknown";
    }
}