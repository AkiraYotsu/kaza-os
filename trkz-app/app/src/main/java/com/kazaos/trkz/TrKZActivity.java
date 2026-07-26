package com.kazaos.trkz;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Html;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrKZActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMISSION = 1001;

    private TextView txtConsole;
    private ScrollView scrollConsole;
    private TextView txtPromptLabel;
    private EditText edtPromptInput;
    private TextView txtSessionTag;
    private Button btnNewSession;
    private Button btnCtrl;
    private float currentTextSizeSp = 13.0f;
    private ScaleGestureDetector scaleGestureDetector;

    private boolean isCtrlActive = false;

    // Multi-Session Management
    private static class Session {
        int id;
        String name;
        File currentDir;
        StringBuilder historyBuffer = new StringBuilder();
        List<String> cmdHistory = new ArrayList<>();
        int historyIndex = -1;

        Session(int id, File initialDir) {
            this.id = id;
            this.name = "Session " + id;
            this.currentDir = initialDir;
        }
    }

    private final List<Session> sessions = new ArrayList<>();
    private int activeSessionIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trkz);

        txtConsole = findViewById(R.id.txtConsole);
        scrollConsole = findViewById(R.id.scrollConsole);
        txtPromptLabel = findViewById(R.id.txtPromptLabel);
        edtPromptInput = findViewById(R.id.edtPromptInput);
        txtSessionTag = findViewById(R.id.txtSessionTag);
        btnNewSession = findViewById(R.id.btnNewSession);
        btnCtrl = findViewById(R.id.keyCtrl);

        // Initialize Session 1
        File defaultHome = getTrkzStorageDir();
        sessions.add(new Session(1, defaultHome));

        // Pinch-to-zoom setup
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                currentTextSizeSp *= scaleFactor;
                if (currentTextSizeSp < 10.0f) currentTextSizeSp = 10.0f;
                if (currentTextSizeSp > 26.0f) currentTextSizeSp = 26.0f;
                txtConsole.setTextSize(currentTextSizeSp);
                if (txtPromptLabel != null) txtPromptLabel.setTextSize(currentTextSizeSp);
                if (edtPromptInput != null) edtPromptInput.setTextSize(currentTextSizeSp);
                return true;
            }
        });

        // Tap terminal screen to focus prompt input
        scrollConsole.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                focusPromptInput();
            }
            return false;
        });

        txtConsole.setOnClickListener(v -> focusPromptInput());

        // Handle Enter key for continuous typing without losing focus
        if (edtPromptInput != null) {
            edtPromptInput.setOnEditorActionListener((v, actionId, event) -> {
                String cmd = edtPromptInput.getText().toString().trim();
                edtPromptInput.setText("");
                if (!cmd.isEmpty()) {
                    executeKazaCommand(cmd);
                } else {
                    Session ses = getActiveSession();
                    appendHistory("<font color='#34d399'>kaza@kernel:" + getPromptPath(ses) + "$ </font><br>");
                }
                return true;
            });
        }

        setupExtraKeys();
        btnNewSession.setOnClickListener(v -> createNewSession());

        checkAndRequestStoragePermissions();
        renderActiveSession();

        scrollConsole.postDelayed(this::focusPromptInput, 300);
    }

    private File getTrkzStorageDir() {
        File sdcard = Environment.getExternalStorageDirectory();
        File trkzDir = new File(sdcard, "TrKZ");
        if (!trkzDir.exists()) trkzDir.mkdirs();
        return trkzDir;
    }

    private Session getActiveSession() {
        if (activeSessionIndex >= sessions.size()) activeSessionIndex = 0;
        return sessions.get(activeSessionIndex);
    }

    private void createNewSession() {
        int nextId = sessions.size() + 1;
        Session s = new Session(nextId, getTrkzStorageDir());
        sessions.add(s);
        activeSessionIndex = sessions.size() - 1;
        renderActiveSession();
        Toast.makeText(this, "Opened " + s.name, Toast.LENGTH_SHORT).show();
    }

    private String getPromptPath(Session s) {
        String trkzPath = getTrkzStorageDir().getAbsolutePath();
        String currentPath = s.currentDir.getAbsolutePath();
        if (currentPath.equals(trkzPath)) {
            return "~";
        } else if (currentPath.startsWith(trkzPath)) {
            return "~" + currentPath.substring(trkzPath.length());
        } else if (currentPath.startsWith("/sdcard")) {
            return "~" + currentPath.substring(7);
        }
        return currentPath;
    }

    private void renderActiveSession() {
        Session s = getActiveSession();
        txtSessionTag.setText("  [" + s.name + "]");
        updatePromptLabel(s);
        if (s.historyBuffer.length() == 0) {
            printHeader(s);
        } else {
            updateTerminalDisplay();
        }
        scrollToBottom();
    }

    private void updatePromptLabel(Session s) {
        if (txtPromptLabel != null) {
            txtPromptLabel.setText(Html.fromHtml("<font color='#34d399'>kaza@kernel:" + getPromptPath(s) + "$ </font>"));
        }
    }

    private void printHeader(Session s) {
        appendHistory("TrKZ Terminal v1.1 (" + s.name + ")<br>");
        appendHistory("<font color='#888888'>Real POSIX Shell &amp; Native Network Downloader Active</font><br>");
        appendHistory("<font color='#888888'>Working Dir: " + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br>");
        appendHistory("<font color='#888888'>Type 'help', 'curl &lt;url&gt;', or 'pkg install node'</font><br><br>");
    }

    private void updateTerminalDisplay() {
        Session s = getActiveSession();
        Spanned spanned = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ?
                Html.fromHtml(s.historyBuffer.toString(), Html.FROM_HTML_MODE_LEGACY) :
                Html.fromHtml(s.historyBuffer.toString());
        txtConsole.setText(spanned);
        updatePromptLabel(s);
        scrollToBottom();
    }

    private void appendHistory(String htmlText) {
        Session s = getActiveSession();
        s.historyBuffer.append(htmlText);
        updateTerminalDisplay();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            Session s = getActiveSession();

            // CTRL Key Combinations
            if (isCtrlActive) {
                if (keyCode == KeyEvent.KEYCODE_C) {
                    appendHistory("<font color='#34d399'>kaza@kernel:" + getPromptPath(s) + "$ </font><font color='#ffffff'>^C</font><br>");
                    if (edtPromptInput != null) edtPromptInput.setText("");
                    resetCtrlState();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_V) {
                    pasteClipboardText();
                    resetCtrlState();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_L) {
                    s.historyBuffer.setLength(0);
                    printHeader(s);
                    resetCtrlState();
                    return true;
                }
                resetCtrlState();
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                navigateHistory(-1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                navigateHistory(1);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // COMMAND EXECUTION ENGINE WITH BUILT-IN CURL & SHELL
    private void executeKazaCommand(String rawInput) {
        String clean = rawInput.trim();
        if (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.isEmpty()) return;

        Session s = getActiveSession();
        s.cmdHistory.add(clean);
        s.historyIndex = s.cmdHistory.size();

        appendHistory("<font color='#34d399'>kaza@kernel:" + getPromptPath(s) + "$ </font><font color='#ffffff'>" + escapeHtml(clean) + "</font><br>");

        String[] parts = clean.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        // Handle built-in commands & curl
        if (cmd.equals("pwd")) {
            appendHistory("<font color='#ffffff'>" + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br><br>");
        } else if (cmd.equals("cd")) {
            cmdCd(s, args);
        } else if (cmd.equals("trkz")) {
            s.currentDir = getTrkzStorageDir();
            appendHistory("<font color='#34d399'>Switched workspace -> " + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br>");
            executeRealShellProcess("ls -la");
        } else if (cmd.equals("curl")) {
            cmdNativeCurl(args);
        } else if (cmd.equals("session")) {
            cmdSession(args);
        } else if (cmd.equals("clear")) {
            s.historyBuffer.setLength(0);
            txtConsole.setText("");
            printHeader(s);
        } else if (cmd.equals("halt") || cmd.equals("exit")) {
            appendHistory("<font color='#f87171'>[KAZA OS] Session stopped.</font><br><br>");
        } else if (cmd.equals("help")) {
            cmdHelp();
        } else {
            // Execute via Linux System Process (/system/bin/sh)
            executeRealShellProcess(clean);
        }

        scrollToBottom();
    }

    // BUILT-IN NATIVE CURL (HttpURLConnection)
    private void cmdNativeCurl(String args) {
        if (args.isEmpty()) {
            appendHistory("<font color='#f87171'>curl: try 'curl --help' or 'curl &lt;url&gt;'</font><br><br>");
            return;
        }

        // Clean flags like -sSL
        String urlStr = args.replaceAll("-[a-zA-Z]+\\s+", "").trim();
        if (urlStr.contains("|")) {
            urlStr = urlStr.split("\\|")[0].trim();
        }

        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "https://" + urlStr;
        }

        final String targetUrl = urlStr;
        appendHistory("<font color='#34d399'>Connecting to " + escapeHtml(targetUrl) + "...</font><br>");

        new Thread(() -> {
            try {
                URL url = new URL(targetUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                final int responseCode = conn.getResponseCode();
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder content = new StringBuilder();
                String inputLine;
                int lines = 0;
                while ((inputLine = in.readLine()) != null && lines < 30) {
                    content.append(escapeHtml(inputLine)).append("<br>");
                    lines++;
                }
                in.close();

                final int finalLines = lines;
                runOnUiThread(() -> {
                    appendHistory("<font color='#888888'>HTTP Response: " + responseCode + " OK</font><br>");
                    appendHistory("<font color='#ffffff'>" + content.toString() + "</font>");
                    if (finalLines >= 30) {
                        appendHistory("<font color='#888888'>... (content truncated for display)</font><br>");
                    }
                    appendHistory("<br>");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendHistory("<font color='#f87171'>curl: error: " + escapeHtml(e.getMessage()) + "</font><br><br>");
                });
            }
        }).start();
    }

    // Execute Real Shell Binaries (/system/bin/sh)
    private void executeRealShellProcess(String commandLine) {
        Session s = getActiveSession();
        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", commandLine);
            pb.directory(s.currentDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            boolean hasOutput = false;
            while ((line = reader.readLine()) != null) {
                hasOutput = true;
                if (line.contains("Permission denied") || line.contains("not found") || line.contains("Error")) {
                    appendHistory("<font color='#f87171'>" + escapeHtml(line) + "</font><br>");
                } else if (line.endsWith("/")) {
                    appendHistory("<font color='#60a5fa'><b>" + escapeHtml(line) + "</b></font><br>");
                } else {
                    appendHistory("<font color='#ffffff'>" + escapeHtml(line) + "</font><br>");
                }
            }

            process.waitFor();

            if (!hasOutput) {
                appendHistory("<font color='#888888'>Process exited with status " + process.exitValue() + "</font><br>");
            }
            appendHistory("<br>");
        } catch (Exception e) {
            appendHistory("<font color='#f87171'>sh: " + escapeHtml(e.getMessage()) + "</font><br><br>");
        }
    }

    private void cmdCd(Session s, String pathStr) {
        if (pathStr.isEmpty() || pathStr.equals("~")) {
            s.currentDir = getTrkzStorageDir();
            appendHistory("<font color='#888888'>" + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br><br>");
            return;
        }

        File target = resolvePath(s, pathStr);
        if (target.exists() && target.isDirectory()) {
            s.currentDir = target;
            appendHistory("<font color='#888888'>" + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br><br>");
        } else {
            appendHistory("<font color='#f87171'>cd: no such file or directory: " + escapeHtml(pathStr) + "</font><br><br>");
        }
    }

    private void cmdSession(String arg) {
        if (arg.isEmpty()) {
            appendHistory("<font color='#888888'>Sessions:</font><br>");
            for (int i = 0; i < sessions.size(); i++) {
                Session s = sessions.get(i);
                String activeTag = (i == activeSessionIndex) ? " *" : "";
                appendHistory("<font color='#ffffff'>  " + s.name + activeTag + " (" + escapeHtml(s.currentDir.getName()) + ")</font><br>");
            }
            appendHistory("<br>");
        } else if (arg.equalsIgnoreCase("new")) {
            createNewSession();
        } else {
            try {
                int idx = Integer.parseInt(arg) - 1;
                if (idx >= 0 && idx < sessions.size()) {
                    activeSessionIndex = idx;
                    renderActiveSession();
                } else {
                    appendHistory("<font color='#f87171'>session: invalid session index</font><br><br>");
                }
            } catch (Exception e) {
                appendHistory("<font color='#f87171'>session: usage: session [new | <number>]</font><br><br>");
            }
        }
    }

    private void cmdHelp() {
        appendHistory("<font color='#888888'>Commands:</font><br>");
        appendHistory("<font color='#ffffff'>  pwd               - Print working directory</font><br>");
        appendHistory("<font color='#ffffff'>  cd &lt;path&gt;         - Change directory (e.g. cd Download / cd /sdcard)</font><br>");
        appendHistory("<font color='#ffffff'>  ls / li [path]    - List files vertically (Folders in BLUE)</font><br>");
        appendHistory("<font color='#ffffff'>  curl &lt;url&gt;        - Built-in Native HTTP Downloader</font><br>");
        appendHistory("<font color='#ffffff'>  mkdir / rm / cat  - Standard Linux filesystem commands</font><br>");
        appendHistory("<font color='#ffffff'>  session [new|n]   - Manage multi-sessions</font><br>");
        appendHistory("<font color='#ffffff'>  clear / exit      - Clear or exit session</font><br><br>");
    }

    private File resolvePath(Session s, String pathStr) {
        if (pathStr.startsWith("/")) {
            return new File(pathStr);
        }
        if (pathStr.equals("..")) {
            File parent = s.currentDir.getParentFile();
            return parent != null ? parent : s.currentDir;
        }
        File rel = new File(s.currentDir, pathStr);
        if (rel.exists()) return rel;

        File[] children = s.currentDir.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.getName().equalsIgnoreCase(pathStr)) {
                    return c;
                }
            }
        }
        return rel;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void scrollToBottom() {
        if (scrollConsole != null) {
            scrollConsole.post(new Runnable() {
                @Override
                public void run() {
                    scrollConsole.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    private void focusPromptInput() {
        if (edtPromptInput != null) {
            edtPromptInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(edtPromptInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void setupExtraKeys() {
        btnCtrl = findViewById(R.id.keyCtrl);
        btnCtrl.setOnClickListener(v -> {
            isCtrlActive = !isCtrlActive;
            if (isCtrlActive) {
                btnCtrl.setBackgroundColor(Color.parseColor("#3B82F6"));
                btnCtrl.setTextColor(Color.parseColor("#FFFFFF"));
            } else {
                btnCtrl.setBackgroundColor(Color.parseColor("#181818"));
                btnCtrl.setTextColor(Color.parseColor("#FFFFFF"));
            }
        });

        findViewById(R.id.keySlash).setOnClickListener(v -> insertInputString("/"));
        findViewById(R.id.keyTab).setOnClickListener(v -> insertInputString("  "));

        findViewById(R.id.keyEsc).setOnClickListener(v -> {
            if (edtPromptInput != null) edtPromptInput.setText("");
            resetCtrlState();
        });

        findViewById(R.id.keyHome).setOnClickListener(v -> {
            Session s = getActiveSession();
            s.currentDir = getTrkzStorageDir();
            updateTerminalDisplay();
        });

        findViewById(R.id.keyUp).setOnClickListener(v -> navigateHistory(-1));
        findViewById(R.id.keyDown).setOnClickListener(v -> navigateHistory(1));
        findViewById(R.id.keyLeft).setOnClickListener(v -> moveInputCursor(-1));
        findViewById(R.id.keyRight).setOnClickListener(v -> moveInputCursor(1));
        findViewById(R.id.keyPgUp).setOnClickListener(v -> scrollConsole.pageScroll(View.FOCUS_UP));
        findViewById(R.id.keyPgDn).setOnClickListener(v -> scrollConsole.pageScroll(View.FOCUS_DOWN));
    }

    private void resetCtrlState() {
        isCtrlActive = false;
        if (btnCtrl != null) {
            btnCtrl.setBackgroundColor(Color.parseColor("#181818"));
            btnCtrl.setTextColor(Color.parseColor("#FFFFFF"));
        }
    }

    private void insertInputString(String str) {
        if (edtPromptInput != null) {
            int start = Math.max(edtPromptInput.getSelectionStart(), 0);
            int end = Math.max(edtPromptInput.getSelectionEnd(), 0);
            edtPromptInput.getText().replace(Math.min(start, end), Math.max(start, end), str, 0, str.length());
        }
    }

    private void moveInputCursor(int offset) {
        if (edtPromptInput != null) {
            int pos = edtPromptInput.getSelectionStart() + offset;
            if (pos < 0) pos = 0;
            if (pos > edtPromptInput.length()) pos = edtPromptInput.length();
            edtPromptInput.setSelection(pos);
        }
    }

    private void navigateHistory(int direction) {
        Session s = getActiveSession();
        if (s.cmdHistory.isEmpty()) return;

        s.historyIndex += direction;
        if (s.historyIndex < 0) s.historyIndex = 0;
        if (s.historyIndex >= s.cmdHistory.size()) {
            s.historyIndex = s.cmdHistory.size();
            if (edtPromptInput != null) edtPromptInput.setText("");
            return;
        }

        String historicalCmd = s.cmdHistory.get(s.historyIndex);
        if (edtPromptInput != null) {
            edtPromptInput.setText(historicalCmd);
            edtPromptInput.setSelection(historicalCmd.length());
        }
    }

    private void pasteClipboardText() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clipData = clipboard.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    CharSequence pasteText = clipData.getItemAt(0).getText();
                    if (pasteText != null) {
                        insertInputString(pasteText.toString());
                    }
                }
            }
        } catch (Exception ignored) {}
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
}
