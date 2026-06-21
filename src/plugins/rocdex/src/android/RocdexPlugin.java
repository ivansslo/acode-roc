package com.foxdebug.acode.rk.rocdex;

import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Rocdex Cordova Plugin
 *
 * Manages the codexapp (Codex CLI web UI) lifecycle and license validation.
 */
public class RocdexPlugin extends CordovaPlugin {

    private static final String TAG = "RocdexPlugin";
    private static final int DEFAULT_PORT = 18923;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> serverProcess;
    private int serverPort = DEFAULT_PORT;
    private boolean serverRunning = false;

    // ── Cordova entry point ──────────────────────────────────────────────

    @Override
    public boolean execute(
            final String action,
            final JSONArray args,
            final CallbackContext callback
    ) throws JSONException {
        switch (action) {
            case "checkNode":
                checkNode(callback);
                return true;

            case "startServer":
                startServer(args.optInt(0, DEFAULT_PORT), callback);
                return true;

            case "stopServer":
                stopServer(callback);
                return true;

            case "isRunning":
                isRunning(callback);
                return true;

            case "getServerUrl":
                getServerUrl(callback);
                return true;

            case "installCodexapp":
                installCodexapp(callback);
                return true;

            case "checkLicense":
                checkLicense(callback);
                return true;

            case "setLicense":
                setLicense(args.optString(0, ""), callback);
                return true;

            case "getLicenseInfo":
                getLicenseInfo(callback);
                return true;

            default:
                return false;
        }
    }

    // ── License Methods ─────────────────────────────────────────────────

    /**
     * Get device fingerprint for license binding.
     */
    private String getDeviceFingerprint() {
        try {
            String androidId = Settings.Secure.getString(
                cordova.getContext().getContentResolver(),
                Settings.Secure.ANDROID_ID
            );
            String packageName = cordova.getContext().getPackageName();
            String raw = androidId + ":" + packageName + ":rocdex2026";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            Log.e(TAG, "Fingerprint error", e);
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Check if a valid license is stored.
     */
    private void checkLicense(final CallbackContext callback) {
        try {
            SharedPreferences prefs = cordova.getContext()
                .getSharedPreferences("rocdex_license", 0);
            String storedKey = prefs.getString("license_key", "");
            String storedUser = prefs.getString("licensed_user", "");
            String fingerprint = getDeviceFingerprint();

            boolean valid = storedKey.equals(fingerprint) && !storedUser.isEmpty();

            JSONObject result = new JSONObject();
            result.put("valid", valid);
            result.put("licensedUser", valid ? storedUser : "");
            callback.success(result);
        } catch (Exception e) {
            try {
                JSONObject result = new JSONObject();
                result.put("valid", false);
                result.put("error", e.getMessage());
                callback.success(result);
            } catch (JSONException je) {
                callback.error("License check failed");
            }
        }
    }

    /**
     * Set a license key and user name.
     * License key is validated from a master secret + user identifier.
     * Format: base64(sha256(user + ":rocdex:secret"))
     */
    private void setLicense(final String licenseKey, final CallbackContext callback) {
        executor.execute(() -> {
            try {
                if (licenseKey == null || licenseKey.isEmpty()) {
                    callback.error("License key cannot be empty");
                    return;
                }

                // Validate license key format: user|signature
                String[] parts = licenseKey.split("\\|", 2);
                if (parts.length != 2) {
                    callback.error("Invalid license format");
                    return;
                }

                String user = parts[0];
                String signature = parts[1];

                // Verify signature using SHA-256
                String raw = user + ":rocdex:secret2026";
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(raw.getBytes("UTF-8"));
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) {
                    hex.append(String.format("%02x", b));
                }
                String expectedSig = hex.toString();

                if (!signature.equals(expectedSig)) {
                    JSONObject result = new JSONObject();
                    result.put("valid", false);
                    result.put("message", "Invalid license key");
                    callback.success(result);
                    return;
                }

                // Store license bound to this device
                String fingerprint = getDeviceFingerprint();
                SharedPreferences prefs = cordova.getContext()
                    .getSharedPreferences("rocdex_license", 0);
                prefs.edit()
                    .putString("license_key", fingerprint)
                    .putString("licensed_user", user)
                    .putString("license_raw", licenseKey)
                    .apply();

                JSONObject result = new JSONObject();
                result.put("valid", true);
                result.put("licensedUser", user);
                callback.success(result);
            } catch (Exception e) {
                callback.error("License activation failed: " + e.getMessage());
            }
        });
    }

    /**
     * Get stored license info (without exposing the raw key).
     */
    private void getLicenseInfo(final CallbackContext callback) {
        try {
            SharedPreferences prefs = cordova.getContext()
                .getSharedPreferences("rocdex_license", 0);
            String storedKey = prefs.getString("license_key", "");
            String storedUser = prefs.getString("licensed_user", "");
            String fingerprint = getDeviceFingerprint();
            boolean valid = storedKey.equals(fingerprint) && !storedUser.isEmpty();

            JSONObject result = new JSONObject();
            result.put("valid", valid);
            result.put("licensedUser", valid ? storedUser : "");
            result.put("packageName", cordova.getContext().getPackageName());
            callback.success(result);
        } catch (Exception e) {
            try {
                JSONObject result = new JSONObject();
                result.put("valid", false);
                callback.success(result);
            } catch (JSONException je) {
                callback.error(e.getMessage());
            }
        }
    }

    // ── Server Methods (unchanged) ──────────────────────────────────────

    private void checkNode(final CallbackContext callback) {
        executor.execute(() -> {
            try {
                Process process = Runtime.getRuntime().exec("node --version");
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                String version = reader.readLine();
                reader.close();

                int exit = process.waitFor();
                JSONObject result = new JSONObject();
                result.put("available", exit == 0 && version != null);
                if (version != null) {
                    result.put("version", version.trim());
                }
                callback.success(result);
            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("available", false);
                    callback.success(result);
                } catch (JSONException je) {
                    callback.error("Failed to check node: " + e.getMessage());
                }
            }
        });
    }

    private void startServer(final int port, final CallbackContext callback) {
        if (serverRunning) {
            callback.error("Server is already running");
            return;
        }

        serverPort = port > 0 ? port : DEFAULT_PORT;

        executor.execute(() -> {
            try {
                Log.d(TAG, "Starting codexapp server on port " + serverPort);

                Process process = Runtime.getRuntime().exec(
                        new String[]{
                                "npx", "codexapp",
                                "--port", String.valueOf(serverPort),
                                "--no-tunnel"
                        });

                serverRunning = true;

                JSONObject result = new JSONObject();
                result.put("port", serverPort);
                callback.success(result);

                process.waitFor();
                Log.d(TAG, "codexapp process exited");
                serverRunning = false;

            } catch (Exception e) {
                serverRunning = false;
                Log.e(TAG, "Failed to start server", e);
                callback.error("Failed to start server: " + e.getMessage());
            }
        });
    }

    private void stopServer(final CallbackContext callback) {
        if (!serverRunning) {
            callback.error("Server is not running");
            return;
        }

        executor.execute(() -> {
            try {
                Runtime.getRuntime().exec(
                        new String[]{"pkill", "-f", "codexapp.*--port " + serverPort});

                serverRunning = false;

                JSONObject result = new JSONObject();
                result.put("stopped", true);
                callback.success(result);
            } catch (Exception e) {
                callback.error("Failed to stop server: " + e.getMessage());
            }
        });
    }

    private void isRunning(final CallbackContext callback) {
        executor.execute(() -> {
            try {
                boolean alive = false;
                if (serverRunning) {
                    URL url = new URL("http://127.0.0.1:" + serverPort);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    alive = conn.getResponseCode() == 200;
                    conn.disconnect();
                }

                JSONObject result = new JSONObject();
                result.put("running", alive);
                if (alive) {
                    result.put("port", serverPort);
                }
                callback.success(result);
            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("running", false);
                    callback.success(result);
                } catch (JSONException je) {
                    callback.error(e.getMessage());
                }
            }
        });
    }

    private void getServerUrl(final CallbackContext callback) {
        try {
            JSONObject result = new JSONObject();
            result.put("url", "http://127.0.0.1:" + serverPort);
            callback.success(result);
        } catch (JSONException e) {
            callback.error(e.getMessage());
        }
    }

    private void installCodexapp(final CallbackContext callback) {
        executor.execute(() -> {
            try {
                Process process = Runtime.getRuntime().exec(
                        new String[]{"npm", "install", "-g", "codexapp"});
                int exit = process.waitFor();

                JSONObject result = new JSONObject();
                result.put("installed", exit == 0);
                callback.success(result);
            } catch (Exception e) {
                callback.error("Install failed: " + e.getMessage());
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            Runtime.getRuntime().exec(
                    new String[]{"pkill", "-f", "codexapp.*--port " + serverPort});
        } catch (Exception ignored) {
        }
        serverRunning = false;
    }
}
