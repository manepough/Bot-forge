package com.botforge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BotService extends Service {

    private static final String TAG            = "BotService";
    private static final String CHANNEL_ID     = "botforge_channel";
    private static final int    NOTIF_ID       = 1001;

    // Holds all running bot threads
    private static final Map<String, BotRunner> runningBots = new HashMap<>();

    // UDP keep-alive scheduler
    private ScheduledExecutorService scheduler;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        // UDP keep-alive every 25 seconds to prevent process sleeping
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(
            () -> UdpKeepAlive.ping("8.8.8.8", 53),
            0, 25, TimeUnit.SECONDS
        );

        Log.d(TAG, "BotService started — 24/7 mode ON");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        if (action == null) return START_STICKY;

        switch (action) {
            case "ACTION_DEPLOY":
                String token = intent.getStringExtra("token");
                String name  = intent.getStringExtra("name");
                deployBot(name, token);
                break;
            case "ACTION_STOP":
                String stopName = intent.getStringExtra("name");
                stopBot(stopName);
                break;
        }

        updateNotification();
        // START_STICKY = Android restarts this service if it ever gets killed
        return START_STICKY;
    }

    private void deployBot(String name, String token) {
        if (runningBots.containsKey(name)) {
            Log.w(TAG, "Bot already running: " + name);
            return;
        }
        BotRunner runner = new BotRunner(name, token);
        runningBots.put(name, runner);
        new Thread(runner, "bot-" + name).start();
        Log.d(TAG, "Deployed bot: " + name);
    }

    private void stopBot(String name) {
        BotRunner runner = runningBots.remove(name);
        if (runner != null) {
            runner.stop();
            Log.d(TAG, "Stopped bot: " + name);
        }
    }

    // Called from JS bridge to get bot list as JSON
    public static String getRunningBotsJson() {
        try {
            JSONArray arr = new JSONArray();
            for (Map.Entry<String, BotRunner> e : runningBots.entrySet()) {
                JSONObject obj = new JSONObject();
                obj.put("name",   e.getKey());
                obj.put("status", e.getValue().isRunning() ? "running" : "stopped");
                obj.put("uptime", e.getValue().getUptimeSeconds());
                arr.put(obj);
            }
            return arr.toString();
        } catch (Exception ex) {
            return "[]";
        }
    }

    // ── Notification helpers ──────────────────
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "BotForge Running",
                NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("Keeps your Discord bots alive 24/7");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BotForge")
            .setContentText(runningBots.size() + " bot(s) running • 24/7 active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification());
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (scheduler != null) scheduler.shutdownNow();
        for (BotRunner r : runningBots.values()) r.stop();
        runningBots.clear();
        super.onDestroy();
    }
}
