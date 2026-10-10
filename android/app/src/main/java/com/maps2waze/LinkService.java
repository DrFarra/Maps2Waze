package com.maps2waze;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Keeps a streaming connection to the ntfy.sh topic and opens every link that arrives. */
public class LinkService extends Service implements Runnable {
    private static final String TAG = "Maps2Waze";
    private Thread thread;

    static void start(Context c) {
        Intent i = new Intent(c, LinkService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notification());
        if (thread == null) {
            thread = new Thread(this);
            thread.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (thread != null) thread.interrupt();
        thread = null;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                listen();
            } catch (Exception e) {
                Log.w(TAG, "connection lost, retrying", e);
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void listen() throws Exception {
        SharedPreferences prefs = MainActivity.prefs(this);
        String topic = MainActivity.topic(this);
        // since=10m: links sent shortly before the radio came online still open.
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "https://ntfy.sh/" + Uri.encode(topic) + "/json?since=10m").openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(90000); // ntfy sends a keepalive every 45s; silence longer than this means a dead link
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = in.readLine()) != null) {
                JSONObject ev = new JSONObject(line);
                if (!"message".equals(ev.optString("event"))) continue;
                long time = ev.optLong("time");
                // shortcut: two links sent in the same second could be deduped as one; fine for manual sharing.
                if (time <= prefs.getLong("lastTime", 0)) continue;
                prefs.edit().putLong("lastTime", time).apply();
                open(ev.optString("message"));
            }
        } finally {
            conn.disconnect();
        }
    }

    private void open(String text) {
        String uri = LinkParser.toUri(text);
        if (uri == null) {
            Log.i(TAG, "ignored: " + text);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w(TAG, "no app can open " + uri, e);
        }
    }

    private Notification notification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("main", "Maps2Waze", NotificationManager.IMPORTANCE_MIN);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
            b = new Notification.Builder(this, "main");
        } else {
            b = new Notification.Builder(this);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        return b.setSmallIcon(R.drawable.ic_fg)
                .setContentTitle("Maps2Waze escuchando enlaces")
                .setContentIntent(pi)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
