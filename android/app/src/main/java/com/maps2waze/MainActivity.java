package com.maps2waze;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;

public class MainActivity extends Activity {

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("maps2waze", MODE_PRIVATE);
    }

    /** Secret channel name; whoever knows it can send links to this radio. */
    static String topic(Context c) {
        SharedPreferences p = prefs(c);
        String t = p.getString("topic", null);
        if (t == null) {
            byte[] b = new byte[8];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder("auto-");
            for (byte x : b) sb.append(String.format("%02x", x));
            t = sb.toString();
            p.edit().putString("topic", t).apply();
        }
        return t;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView info = new TextView(this);
        info.setTextSize(18);
        info.setText("Canal (ponlo igual en el Atajo del iPhone):");
        root.addView(info);

        EditText topic = new EditText(this);
        topic.setTextSize(22);
        topic.setSingleLine();
        topic.setText(topic(this));
        root.addView(topic);

        Button save = new Button(this);
        save.setText("Guardar e iniciar");
        save.setOnClickListener(v -> {
            String t = topic.getText().toString().trim();
            if (!t.matches("[A-Za-z0-9_-]{8,64}")) {
                Toast.makeText(this, "Usa 8-64 letras, números, - o _", Toast.LENGTH_LONG).show();
                return;
            }
            prefs(this).edit().putString("topic", t).apply();
            stopService(new Intent(this, LinkService.class)); // reconnect to the new topic
            LinkService.start(this);
            Toast.makeText(this, "Listo, escuchando " + t, Toast.LENGTH_SHORT).show();
        });
        root.addView(save);

        // Android 10+ blocks opening apps from the background unless this is granted.
        if (Build.VERSION.SDK_INT >= 23) {
            Button overlay = new Button(this);
            overlay.setText("Permitir mostrar sobre otras apps (necesario)");
            overlay.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()))));
            root.addView(overlay);
        }

        setContentView(root);
        LinkService.start(this);
    }
}
