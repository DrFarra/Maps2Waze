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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

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
        // ntfy fills in "triggered" when the request body was empty: the iPhone shortcut sent nothing.
        if (text.isEmpty() || "triggered".equals(text)) {
            toast("llegó un mensaje vacío; revisa el atajo del iPhone");
            return;
        }
        String uri = LinkParser.toUri(text);
        if (uri == null) {
            Log.i(TAG, "ignored: " + text);
            return;
        }
        // Google Maps isn't usable as GPS on the head unit, so send Google links to Waze when we can read the destination.
        String full = LinkParser.isShortLink(uri) ? expand(uri) : uri;
        String waze = LinkParser.toWaze(full);
        if (waze != null && LinkParser.isWazeSearch(waze)) {
            // Only an address/Plus Code in the link: ask Google for the exact pin so Waze lands on the same spot.
            String exact = exactPlace(full);
            if (exact != null) waze = exact;
        }
        if (waze != null) uri = waze;
        String scheme = LinkParser.wazeScheme(uri);
        // Some head-unit Waze builds open but drop the destination of https links; their own scheme always works.
        if (scheme != null && start(new Intent(Intent.ACTION_VIEW, Uri.parse(scheme)).setPackage("com.waze"))) {
            toast("Abriendo en Waze: " + scheme.substring("waze://?".length()));
            return;
        }
        if (start(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)))) toast("Abriendo: " + uri);
        else toast("Ninguna app puede abrir: " + uri);
    }

    private boolean start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            Log.w(TAG, "could not open " + i.getData(), e);
            return false;
        }
    }

    /** Shown on the radio so the user can see what arrived and what was opened. */
    private void toast(String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), "Maps2Waze: " + Uri.decode(msg), Toast.LENGTH_LONG).show());
    }

    /** Follows the redirects of a short link to the full Google Maps URL; returns the input if that fails. */
    private static String expand(String url) {
        try {
            for (int i = 0; i < 5; i++) {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setInstanceFollowRedirects(false);
                // Google's short links redirect plain browsers straight to the full maps URL.
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                String next = conn.getHeaderField("Location");
                conn.disconnect();
                if (next == null) break;
                url = new URL(new URL(url), next).toString();
            }
        } catch (Exception e) {
            Log.w(TAG, "could not expand " + url, e);
        }
        return url;
    }

    // shortcut: relies on Google's undocumented place-details data; if Google changes it we fall back to a Waze text search.
    private static String exactPlace(String mapsUrl) {
        try {
            String path = LinkParser.previewPath(get(mapsUrl));
            return path == null ? null : LinkParser.wazeFromPreview(get("https://www.google.com" + path));
        } catch (Exception e) {
            Log.w(TAG, "could not look up exact place", e);
            return null;
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            for (int n; (n = in.read(buf)) > 0; ) sb.append(buf, 0, n);
            return sb.toString();
        } finally {
            conn.disconnect();
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
