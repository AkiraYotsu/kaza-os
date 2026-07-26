package com.kazaos.trkz;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

public class TrKZActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private TextView txtConsole;
    private EditText edtCommand;

    private static boolean isNativeLoaded = false;

    static {
        try {
            System.loadLibrary("kaza_native");
            isNativeLoaded = true;
        } catch (Throwable t) {
            isNativeLoaded = false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trkz);

        txtConsole = findViewById(R.id.txtConsole);
        edtCommand = findViewById(R.id.edtCommand);

        appendConsole("====================================================\n");
        appendConsole("     KAZA OS v1.1 — TrKZ Standalone Console        \n");
        appendConsole("====================================================\n");
        appendConsole("Native Engine: " + (isNativeLoaded ? "LOADED (NDK)" : "POSIX Userland Mode") + "\n");
        appendConsole("Type 'help' or 'trkz' to inspect Storage Field.\n\n");

        checkAndRequestStoragePermissions();
        initializeTrkzField();

        if (edtCommand != null) {
            edtCommand.setOnEditorActionListener((v, actionId, event) -> {
                String cmd = edtCommand.getText().toString().trim();
                if (!cmd.isEmpty()) {
                    processCommand(cmd);
                    edtCommand.setText("");
                }
                return true;
            });
        }
    }

    private void processCommand(String cmd) {
        appendConsole("kaza@kernel:~$ " + cmd + "\n");

        if (cmd.equalsIgnoreCase("clear")) {
            txtConsole.setText("");
        } else if (cmd.equalsIgnoreCase("trkz")) {
            File trkzDir = new File(Environment.getExternalStorageDirectory(), "TrKZ");
            appendConsole("Workspace: " + trkzDir.getAbsolutePath() + "\n");
            if (trkzDir.exists() && trkzDir.isDirectory()) {
                File[] files = trkzDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        appendConsole((f.isDirectory() ? "[DIR]  " : "[FILE] ") + f.getName() + "\n");
                    }
                }
            } else {
                appendConsole("ERROR: TrKZ directory inaccessible.\n");
            }
            appendConsole("\n");
        } else if (cmd.equalsIgnoreCase("sysinfo")) {
            appendConsole("--- TrKZ App Diagnostics ---\n");
            appendConsole("App Package : com.kazaos.trkz\n");
            appendConsole("Status      : Active & Running\n\n");
        } else if (cmd.equalsIgnoreCase("help")) {
            appendConsole("Available Commands:\n");
            appendConsole("  trkz       - Open TrKZ Storage Field (/sdcard/TrKZ)\n");
            appendConsole("  sysinfo    - App & System status\n");
            appendConsole("  clear      - Clear console\n\n");
        } else {
            appendConsole("Command executed: " + cmd + "\n\n");
        }
    }

    private void appendConsole(String text) {
        if (txtConsole != null) {
            txtConsole.append(text);
        }
    }

    private void checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_STORAGE_PERMISSION);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivity(intent);
                    } catch (Exception ignored) {}
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
            }
        }
    }

    private void initializeTrkzField() {
        try {
            File trkzDir = new File(Environment.getExternalStorageDirectory(), "TrKZ");
            if (!trkzDir.exists()) {
                boolean created = trkzDir.mkdirs();
                if (created) {
                    Toast.makeText(this, "TrKZ Storage Field Created!", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception ignored) {}
    }
}
