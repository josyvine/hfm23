package com.vineyard.hfm.app;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AppLogger {

    private static final String TAG = "AppLogger";
    private static final String LOG_DIR_NAME = "hfm log report";
    private static final String LOG_FILE_NAME = "hfm_diagnostic_log.txt";
    private static final Object LOCK = new Object();

    private static Context sAppContext = null;

    /**
     * Initializes AppLogger with the Application Context.
     * Enables immediate MediaScanner notifications so log files appear instantly in File Managers.
     */
    public static void init(Context context) {
        if (context != null) {
            sAppContext = context.getApplicationContext();
            logSystemInfo(TAG);
        }
    }

    /**
     * Resolves the log directory location targeting /storage/emulated/0/hfm log report/ directly.
     */
    private static File getLogDir() {
        File externalStorage = Environment.getExternalStorageDirectory();
        File primaryLogDir = new File(externalStorage, LOG_DIR_NAME);

        if (!primaryLogDir.exists()) {
            boolean created = primaryLogDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Primary log directory creation failed at: " + primaryLogDir.getAbsolutePath());
            }
        }

        if (primaryLogDir.exists()) {
            return primaryLogDir;
        }

        // App-specific external storage fallback for Scoped Storage compatibility
        if (sAppContext != null) {
            File appExtDir = new File(sAppContext.getExternalFilesDir(null), LOG_DIR_NAME);
            if (!appExtDir.exists()) {
                appExtDir.mkdirs();
            }
            return appExtDir;
        }

        return primaryLogDir;
    }

    private static File getLogFile() {
        File logDir = getLogDir();
        return new File(logDir, LOG_FILE_NAME);
    }

    public static void log(String tag, String message) {
        log(tag, message, null);
    }

    public static void logError(String tag, String message, Throwable throwable) {
        log(tag, "ERROR | " + message, throwable);
    }

    public static void logMetric(String tag, String operation, long durationMs, String details) {
        String metricMessage = String.format(Locale.US, "[METRIC] %s executed in %d ms | %s", operation, durationMs, details != null ? details : "");
        log(tag, metricMessage, null);
    }

    public static void logSystemInfo(String tag) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SYSTEM DIAGNOSTIC INFO ===\n");
        sb.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        sb.append("Model: ").append(Build.MODEL).append("\n");
        sb.append("Device: ").append(Build.DEVICE).append("\n");
        sb.append("Brand: ").append(Build.BRAND).append("\n");
        sb.append("Android SDK: ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Build Release: ").append(Build.VERSION.RELEASE).append("\n");
        sb.append("Display Build: ").append(Build.DISPLAY).append("\n");
        sb.append("Log File Path: ").append(getLogFilePath()).append("\n");
        sb.append("==============================");
        log(tag, sb.toString());
    }

    public static String getLogFilePath() {
        synchronized (LOCK) {
            File logFile = getLogFile();
            return (logFile != null) ? logFile.getAbsolutePath() : "Unknown";
        }
    }

    public static void log(String tag, String message, Throwable throwable) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append(timestamp)
                .append(" [")
                .append(Thread.currentThread().getName())
                .append("] ")
                .append(tag)
                .append(": ")
                .append(message);

        if (throwable != null) {
            logBuilder.append("\n");
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            throwable.printStackTrace(printWriter);
            logBuilder.append(stringWriter.toString());
        }
        logBuilder.append("\n");

        String formattedLog = logBuilder.toString();
        Log.d(tag, message, throwable);

        synchronized (LOCK) {
            FileWriter writer = null;
            try {
                File logFile = getLogFile();
                File parentDir = logFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                writer = new FileWriter(logFile, true);
                writer.write(formattedLog);
                writer.flush();

                // Force MediaScanner indexing so the file manager updates and displays new entries
                notifyMediaScanner(logFile);

            } catch (IOException e) {
                Log.e(TAG, "Error writing entry to diagnostic log file: " + e.getMessage(), e);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    /**
     * Notifies Android's MediaScanner framework to re-index the log file instantly.
     */
    private static void notifyMediaScanner(File file) {
        if (file == null || !file.exists()) return;

        try {
            if (sAppContext != null) {
                MediaScannerConnection.scanFile(sAppContext,
                        new String[]{file.getAbsolutePath()}, null, null);
            } else {
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(file));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error notifying MediaScanner", e);
        }
    }

    public static String readLog() {
        synchronized (LOCK) {
            StringBuilder content = new StringBuilder();
            try {
                File logFile = getLogFile();
                if (logFile != null && logFile.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(logFile));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    reader.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading diagnostic log file: " + e.getMessage(), e);
            }
            return content.toString();
        }
    }

    public static boolean clearLog() {
        synchronized (LOCK) {
            try {
                File logFile = getLogFile();
                if (logFile != null && logFile.exists()) {
                    boolean deleted = logFile.delete();
                    if (deleted) {
                        notifyMediaScanner(logFile);
                    }
                    return deleted;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error clearing diagnostic log file: " + e.getMessage(), e);
            }
            return false;
        }
    }
}