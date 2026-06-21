package com.foxdebug.acode.rk.rocdex;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
 * Manages the codexapp (Codex CLI web UI) lifecycle:
 * - Check Node.js availability
 * - Start / stop the codexapp HTTP server
 * - Query server status
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

            default:
                return false;
        }
    }

    // ── Public API implementations ───────────────────────────────────────

    /**
     * Check whether Node.js is available on the device.
     */
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

    /**
     * Start the codexapp server on the given port.
     */
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

                // Mark as running immediately so the JS side can proceed
                serverRunning = true;

                JSONObject result = new JSONObject();
                result.put("port", serverPort);
                callback.success(result);

                // Wait for process to finish (blocks this executor thread)
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

    /**
     * Stop the running codexapp server.
     */
    private void stopServer(final CallbackContext callback) {
        if (!serverRunning) {
            callback.error("Server is not running");
            return;
        }

        executor.execute(() -> {
            try {
                // Kill any running codexapp / node processes related to our server
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

    /**
     * Check whether the server is currently running by pinging localhost.
     */
    private void isRunning(final CallbackContext callback) {
        executor.execute(() -> {
            try {
                boolean alive = false;
                if (serverRunning) {
                    // Double-check with an HTTP probe
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

    /**
     * Return the server URL.
     */
    private void getServerUrl(final CallbackContext callback) {
        try {
            JSONObject result = new JSONObject();
            result.put("url", "http://127.0.0.1:" + serverPort);
            callback.success(result);
        } catch (JSONException e) {
            callback.error(e.getMessage());
        }
    }

    /**
     * Install / update codexapp globally via npm.
     */
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
        // Best-effort cleanup
        try {
            Runtime.getRuntime().exec(
                    new String[]{"pkill", "-f", "codexapp.*--port " + serverPort});
        } catch (Exception ignored) {
        }
        serverRunning = false;
    }
}
