package com.botforge;

import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BotRunner — opens a Discord Gateway WebSocket connection for one bot.
 * Handles HELLO, heartbeat, IDENTIFY, and auto-reconnect on disconnect.
 */
public class BotRunner extends WebSocketListener implements Runnable {

    private static final String TAG            = "BotRunner";
    private static final String GATEWAY_URL    = "wss://gateway.discord.gg/?v=10&encoding=json";
    private static final int    RECONNECT_WAIT = 5000; // ms

    private final String name;
    private final String token;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final long startTime = System.currentTimeMillis();

    private OkHttpClient client;
    private WebSocket    ws;
    private int          heartbeatInterval = 41250;
    private Integer      lastSequence      = null;
    private Thread       heartbeatThread;

    public BotRunner(String name, String token) {
        this.name  = name;
        this.token = token;
    }

    @Override
    public void run() {
        running.set(true);
        client = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)  // OkHttp-level ping
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

        while (running.get()) {
            connect();
            if (running.get()) {
                Log.w(TAG, "[" + name + "] Reconnecting in " + RECONNECT_WAIT + "ms...");
                try { Thread.sleep(RECONNECT_WAIT); } catch (InterruptedException ignored) {}
            }
        }
        client.dispatcher().executorService().shutdown();
    }

    private void connect() {
        Request request = new Request.Builder().url(GATEWAY_URL).build();
        ws = client.newWebSocket(request, this);
        // Block this thread until onClosed / onFailure fires
        synchronized (this) {
            try { wait(); } catch (InterruptedException ignored) {}
        }
    }

    // ── WebSocket callbacks ───────────────────────────────────────

    @Override
    public void onOpen(WebSocket ws, okhttp3.Response response) {
        Log.d(TAG, "[" + name + "] Gateway connected");
    }

    @Override
    public void onMessage(WebSocket ws, String text) {
        try {
            JSONObject payload = new JSONObject(text);
            int op = payload.getInt("op");
            if (!payload.isNull("s")) lastSequence = payload.getInt("s");

            switch (op) {
                case 10: // HELLO — start heartbeat
                    heartbeatInterval = payload.getJSONObject("d").getInt("heartbeat_interval");
                    startHeartbeat();
                    identify();
                    break;
                case 11: // Heartbeat ACK
                    Log.d(TAG, "[" + name + "] Heartbeat ACK");
                    break;
                case 0: // Dispatch
                    handleDispatch(payload);
                    break;
                case 7: // Reconnect requested
                    ws.close(1000, "Reconnect requested");
                    break;
                case 9: // Invalid session
                    Log.w(TAG, "[" + name + "] Invalid session — re-identifying");
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    identify();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "[" + name + "] Message parse error: " + e.getMessage());
        }
    }

    @Override
    public void onMessage(WebSocket ws, ByteString bytes) {
        // Discord sends zlib-compressed frames in some configs — ignore for now
    }

    @Override
    public void onClosing(WebSocket ws, int code, String reason) {
        Log.w(TAG, "[" + name + "] Closing: " + code + " " + reason);
        ws.close(1000, null);
    }

    @Override
    public void onClosed(WebSocket ws, int code, String reason) {
        Log.w(TAG, "[" + name + "] Closed: " + code);
        stopHeartbeat();
        synchronized (this) { notifyAll(); }
    }

    @Override
    public void onFailure(WebSocket ws, Throwable t, okhttp3.Response response) {
        Log.e(TAG, "[" + name + "] Failure: " + t.getMessage());
        stopHeartbeat();
        synchronized (this) { notifyAll(); }
    }

    // ── Gateway ops ─────────────────────────────────────────────

    private void identify() {
        try {
            JSONObject props = new JSONObject()
                .put("os", "android")
                .put("browser", "botforge")
                .put("device", "botforge");
            JSONObject d = new JSONObject()
                .put("token", token)
                .put("intents", 33281)  // GUILDS | GUILD_MESSAGES | MESSAGE_CONTENT
                .put("properties", props);
            JSONObject payload = new JSONObject()
                .put("op", 2)
                .put("d", d);
            ws.send(payload.toString());
            Log.d(TAG, "[" + name + "] IDENTIFY sent");
        } catch (Exception e) {
            Log.e(TAG, "[" + name + "] IDENTIFY failed: " + e.getMessage());
        }
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatThread = new Thread(() -> {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                sendHeartbeat();
                try {
                    Thread.sleep(heartbeatInterval);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "heartbeat-" + name);
        heartbeatThread.start();
    }

    private void stopHeartbeat() {
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }
    }

    private void sendHeartbeat() {
        try {
            JSONObject payload = new JSONObject()
                .put("op", 1)
                .put("d", lastSequence == null ? JSONObject.NULL : lastSequence);
            if (ws != null) ws.send(payload.toString());
            Log.d(TAG, "[" + name + "] Heartbeat sent");
        } catch (Exception e) {
            Log.e(TAG, "[" + name + "] Heartbeat error: " + e.getMessage());
        }
    }

    private void handleDispatch(JSONObject payload) throws Exception {
        String t = payload.optString("t");
        Log.d(TAG, "[" + name + "] Event: " + t);
        // Add your bot's event handling logic here
        // e.g.: if ("MESSAGE_CREATE".equals(t)) { ... }
    }

    // ── Control ──────────────────────────────────────────────────

    public void stop() {
        running.set(false);
        stopHeartbeat();
        if (ws != null) ws.close(1000, "Bot stopped");
        synchronized (this) { notifyAll(); }
    }

    public boolean isRunning() { return running.get(); }

    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }
}
