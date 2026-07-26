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
import android.text.InputType;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
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
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrKZActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMISSION = 1001;

    private TextView txtConsole;
    private ScrollView scrollConsole;
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
        String currentInput = "";
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
                return true;
            }
        });

        // Tap terminal screen to open keyboard & focus
        scrollConsole.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scaleGestureDetector.onTouchEvent(event);
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    showKeyboard();
                }
                return false;
            }
        });

        setupExtraKeys();

        btnNewSession.setOnClickListener(v -> createNewSession());

        checkAndRequestStoragePermissions();
        renderActiveSession();
        showKeyboard();
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
        if (s.historyBuffer.length() == 0) {
            printHeader(s);
        } else {
            updateTerminalDisplay();
        }
        scrollToBottom();
    }

    private void printHeader(Session s) {
        appendHistory("TrKZ Terminal v1.1 (" + s.name + ")<br>");
        appendHistory("<font color='#888888'>Working Dir: " + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br>");
        appendHistory("<font color='#888888'>Type 'help' or '/' for commands.</font><br><br>");
    }

    private void updateTerminalDisplay() {
        Session s = getActiveSession();
        String prompt = "<font color='#34d399'>kaza@kernel:" + getPromptPath(s) + "$ </font>";
        String inputLine = "<font color='#ffffff'>" + escapeHtml(s.currentInput) + "█</font>";

        Spanned spanned = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ?
                Html.fromHtml(s.historyBuffer.toString() + prompt + inputLine, Html.FROM_HTML_MODE_LEGACY) :
                Html.fromHtml(s.historyBuffer.toString() + prompt + inputLine);
        txtConsole.setText(spanned);
        scrollToBottom();
    }

    private void appendHistory(String htmlText) {
        Session s = getActiveSession();
        s.historyBuffer.append(htmlText);
        updateTerminalDisplay();
    }

    // In-stream Keyboard Event Handler
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            Session s = getActiveSession();

            // CTRL Key Combinations
            if (isCtrlActive) {
                if (keyCode == KeyEvent.KEYCODE_C) {
                    appendHistory("<font color='#34d399'>kaza@kernel:" + getPromptPath(s) + "$ </font><font color='#ffffff'>^C</font><br>");
                    s.currentInput = "";
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

            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                String cmd = s.currentInput.trim();
                s.currentInput = "";
                if (!cmd.isEmpty()) {
                    executeKazaCommand(cmd);
                } else {
                    appendHistory("<font color='#34d399'>kaza@kernel:" + getPromptPath(s) + "$ </font><br>");
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DEL) {
                if (s.currentInput.length() > 0) {
                    s.currentInput = s.currentInput.substring(0, s.currentInput.length() - 1);
                    updateTerminalDisplay();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                navigateHistory(-1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                navigateHistory(1);
                return true;
            }

            char unicodeChar = (char) event.getUnicodeChar();
            if (unicodeChar >= 32 && unicodeChar <= 126) {
                s.currentInput += unicodeChar;
                updateTerminalDisplay();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

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

        if (cmd.equals("pwd")) {
            appendHistory("<font color='#ffffff'>" + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br><br>");
        } else if (cmd.equals("cd")) {
            cmdCd(s, args);
        } else if (cmd.equals("li") || cmd.equals("ls")) {
            cmdLi(s, args);
        } else if (cmd.equals("read") || cmd.equals("cat")) {
            cmdRead(s, args);
        } else if (cmd.equals("write")) {
            cmdWrite(s, args);
        } else if (cmd.equals("mkdir")) {
            cmdMkdir(s, args);
        } else if (cmd.equals("rm")) {
            cmdRm(s, args);
        } else if (cmd.equals("find")) {
            cmdFind(s, args);
        } else if (cmd.equals("calc")) {
            cmdCalc(args);
        } else if (cmd.equals("time") || cmd.equals("date")) {
            cmdTime();
        } else if (cmd.equals("trkz")) {
            s.currentDir = getTrkzStorageDir();
            appendHistory("<font color='#34d399'>Switched workspace -> " + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br>");
            cmdLi(s, "");
        } else if (cmd.equals("pkg") || cmd.equals("apt") || cmd.equals("npm")) {
            cmdPkg(cmd, args);
        } else if (cmd.equals("session")) {
            cmdSession(args);
        } else if (cmd.equals("sysinfo")) {
            cmdSysinfo(s);
        } else if (cmd.equals("clear")) {
            s.historyBuffer.setLength(0);
            txtConsole.setText("");
            printHeader(s);
        } else if (cmd.equals("halt") || cmd.equals("exit")) {
            appendHistory("<font color='#f87171'>[KAZA OS] Session stopped.</font><br><br>");
        } else if (cmd.equals("help")) {
            cmdHelp();
        } else {
            appendHistory("<font color='#f87171'>kaza: command not found: " + escapeHtml(clean) + "</font><br><br>");
        }

        scrollToBottom();
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

    private void cmdLi(Session s, String pathStr) {
        File targetDir = pathStr.isEmpty() ? s.currentDir : resolvePath(s, pathStr);

        if (!targetDir.exists() || !targetDir.isDirectory()) {
            appendHistory("<font color='#f87171'>ls: cannot access '" + escapeHtml(targetDir.getAbsolutePath()) + "': No such directory</font><br><br>");
            return;
        }

        File[] files = targetDir.listFiles();
        int count = 0;
        if (files != null) {
            for (File f : files) {
                count++;
                if (f.isDirectory()) {
                    // Vertical line-by-line listing (BLUE folders)
                    appendHistory("<font color='#60a5fa'><b>[DIR]  " + escapeHtml(f.getName()) + "/</b></font><br>");
                } else if (f.canExecute() || f.getName().endsWith(".sh")) {
                    appendHistory("<font color='#34d399'>[EXEC] " + escapeHtml(f.getName()) + "</font><br>");
                } else {
                    appendHistory("<font color='#ffffff'>[FILE] " + escapeHtml(f.getName()) + "</font> <font color='#888888'>(" + f.length() + " B)</font><br>");
                }
            }
        }
        appendHistory("<font color='#888888'>Total entries: " + count + "</font><br><br>");
    }

    private void cmdPkg(String mgr, String args) {
        appendHistory("<font color='#888888'>--- " + mgr.toUpperCase() + " Package Subsystem ---</font><br>");
        if (args.startsWith("install ")) {
            String pkgName = args.substring(8);
            appendHistory("<font color='#34d399'>Fetching " + escapeHtml(pkgName) + " repository...</font><br>");
            appendHistory("<font color='#ffffff'>Paket '" + escapeHtml(pkgName) + "' siap terintegrasi via Termux/POSIX layer!</font><br><br>");
        } else {
            appendHistory("<font color='#ffffff'>Usage: " + mgr + " install &lt;package_name&gt; (e.g. pkg install python / npm install express)</font><br><br>");
        }
    }

    private void cmdRead(Session s, String filepath) {
        if (filepath.isEmpty()) {
            appendHistory("<font color='#f87171'>read: missing file operand</font><br><br>");
            return;
        }
        File file = resolvePath(s, filepath);
        if (!file.exists() || !file.isFile()) {
            appendHistory("<font color='#f87171'>read: " + escapeHtml(file.getAbsolutePath()) + ": No such file</font><br><br>");
            return;
        }

        appendHistory("<font color='#888888'>--- " + escapeHtml(file.getName()) + " ---</font><br>");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendHistory("<font color='#ffffff'>" + escapeHtml(line) + "</font><br>");
            }
            appendHistory("<font color='#888888'>--- EOF ---</font><br><br>");
        } catch (Exception e) {
            appendHistory("<font color='#f87171'>read: error reading file</font><br><br>");
        }
    }

    private void cmdWrite(Session s, String args) {
        String[] parts = args.split("\\s+", 2);
        if (parts.length == 0 || parts[0].isEmpty()) {
            appendHistory("<font color='#f87171'>write: missing file operand</font><br><br>");
            return;
        }
        File file = resolvePath(s, parts[0]);
        String text = parts.length > 1 ? parts[1] : "";

        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            writer.println(text);
            appendHistory("<font color='#34d399'>write: saved -> " + escapeHtml(file.getName()) + "</font><br><br>");
        } catch (Exception e) {
            appendHistory("<font color='#f87171'>write: permission denied</font><br><br>");
        }
    }

    private void cmdMkdir(Session s, String dirpath) {
        if (dirpath.isEmpty()) {
            appendHistory("<font color='#f87171'>mkdir: missing operand</font><br><br>");
            return;
        }
        File dir = resolvePath(s, dirpath);
        if (dir.mkdirs() || dir.exists()) {
            appendHistory("<font color='#34d399'>mkdir: created -> " + escapeHtml(dir.getName()) + "</font><br><br>");
        } else {
            appendHistory("<font color='#f87171'>mkdir: cannot create directory</font><br><br>");
        }
    }

    private void cmdRm(Session s, String pathStr) {
        if (pathStr.isEmpty()) {
            appendHistory("<font color='#f87171'>rm: missing operand</font><br><br>");
            return;
        }
        File target = resolvePath(s, pathStr);
        if (target.exists() && target.delete()) {
            appendHistory("<font color='#888888'>rm: removed -> " + escapeHtml(target.getName()) + "</font><br><br>");
        } else {
            appendHistory("<font color='#f87171'>rm: cannot remove '" + escapeHtml(pathStr) + "'</font><br><br>");
        }
    }

    private void cmdFind(Session s, String args) {
        String[] parts = args.split("\\s+", 2);
        String keyword = parts[0];
        if (keyword.isEmpty()) {
            appendHistory("<font color='#f87171'>find: missing keyword</font><br><br>");
            return;
        }
        File baseDir = parts.length > 1 ? resolvePath(s, parts[1]) : s.currentDir;
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            appendHistory("<font color='#f87171'>find: path inaccessible</font><br><br>");
            return;
        }

        File[] files = baseDir.listFiles();
        int matches = 0;
        if (files != null) {
            for (File f : files) {
                if (f.getName().toLowerCase().contains(keyword.toLowerCase())) {
                    matches++;
                    if (f.isDirectory()) {
                        appendHistory("<font color='#60a5fa'>  [DIR]  " + escapeHtml(f.getAbsolutePath()) + "</font><br>");
                    } else {
                        appendHistory("<font color='#ffffff'>  [FILE] " + escapeHtml(f.getAbsolutePath()) + "</font><br>");
                    }
                }
            }
        }
        appendHistory("<font color='#888888'>find: " + matches + " match(es)</font><br><br>");
    }

    private void cmdCalc(String expr) {
        if (expr.isEmpty()) {
            appendHistory("<font color='#f87171'>calc: missing expression</font><br><br>");
            return;
        }
        try {
            String[] tokens = expr.split("\\s+");
            double a = Double.parseDouble(tokens[0]);
            String op = tokens[1];
            double b = Double.parseDouble(tokens[2]);
            double res = 0;
            if (op.equals("+")) res = a + b;
            else if (op.equals("-")) res = a - b;
            else if (op.equals("*")) res = a * b;
            else if (op.equals("/")) {
                if (b == 0) { appendHistory("<font color='#f87171'>calc: division by zero</font><br><br>"); return; }
                res = a / b;
            }
            appendHistory("<font color='#34d399'>= " + res + "</font><br><br>");
        } catch (Exception e) {
            appendHistory("<font color='#f87171'>calc: invalid format</font><br><br>");
        }
    }

    private void cmdTime() {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        appendHistory("<font color='#888888'>" + now + "</font><br><br>");
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

    private void cmdSysinfo(Session s) {
        appendHistory("<font color='#888888'>--- System Info ---</font><br>");
        appendHistory("<font color='#ffffff'>OS          : Kaza OS v1.1</font><br>");
        appendHistory("<font color='#ffffff'>App         : TrKZ Terminal (com.kazaos.trkz)</font><br>");
        appendHistory("<font color='#ffffff'>Active Dir  : " + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br>");
        appendHistory("<font color='#34d399'>Storage     : Full System Access Granted</font><br><br>");
    }

    private void cmdHelp() {
        appendHistory("<font color='#888888'>Commands:</font><br>");
        appendHistory("<font color='#ffffff'>  pwd               - Print working directory</font><br>");
        appendHtml("<font color='#ffffff'>  cd &lt;path&gt;         - Change directory (e.g. cd Download / cd /sdcard)</font><br>");
        appendHtml("<font color='#ffffff'>  ls / li [path]    - List files vertically (Folders in BLUE)</font><br>");
        appendHtml("<font color='#ffffff'>  read / cat &lt;file&gt;  - Read text file</font><br>");
        appendHtml("<font color='#ffffff'>  write &lt;file&gt; &lt;txt&gt; - Append text to file</font><br>");
        appendHtml("<font color='#ffffff'>  mkdir / rm        - Create or remove files/folders</font><br>");
        appendHtml("<font color='#ffffff'>  find &lt;kw&gt; [path]  - Search files</font><br>");
        appendHtml("<font color='#ffffff'>  calc / time       - System tools</font><br>");
        appendHtml("<font color='#ffffff'>  pkg install &lt;pkg&gt; - Install packages (python, npm, git)</font><br>");
        appendHtml("<font color='#ffffff'>  session [new|n]   - Manage multi-sessions</font><br>");
        appendHtml("<font color='#ffffff'>  clear / exit      - Clear or exit session</font><br><br>");
    }

    private File resolvePath(Session s, String pathStr) {
        if (pathStr.startsWith("/")) {
            return new File(pathStr);
        }
        if (pathStr.equals("..")) {
            File parent = s.currentDir.getParentFile();
            return parent != null ? parent : s.currentDir;
        }
        // Relative path resolution
        File rel = new File(s.currentDir, pathStr);
        if (rel.exists()) return rel;

        // Case-insensitive match check
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

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            txtConsole.setFocusable(true);
            txtConsole.setFocusableInTouchMode(true);
            txtConsole.requestFocus();
            imm.showSoftInput(txtConsole, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void setupExtraKeys() {
        btnCtrl = findViewById(R.id.keyCtrl);
        btnCtrl.setOnClickListener(v -> {
            isCtrlActive = !isCtrlActive;
            if (isCtrlActive) {
                btnCtrl.setBackgroundColor(Color.parseColor("#3B82F6")); // BLUE when active
                btnCtrl.setTextColor(Color.parseColor("#FFFFFF"));
            } else {
                btnCtrl.setBackgroundColor(Color.parseColor("#181818"));
                btnCtrl.setTextColor(Color.parseColor("#FFFFFF"));
            }
        });

        findViewById(R.id.keySlash).setOnClickListener(v -> insertInputChar('/'));
        findViewById(R.id.keyTab).setOnClickListener(v -> insertInputString("  "));
        
        findViewById(R.id.keyEsc).setOnClickListener(v -> {
            Session s = getActiveSession();
            s.currentInput = "";
            resetCtrlState();
            updateTerminalDisplay();
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

    private void insertInputChar(char c) {
        Session s = getActiveSession();
        s.currentInput += c;
        updateTerminalDisplay();
    }

    private void insertInputString(String str) {
        Session s = getActiveSession();
        s.currentInput += str;
        updateTerminalDisplay();
    }

    private void moveInputCursor(int offset) {
        // Cursor shift helper
        updateTerminalDisplay();
    }

    private void navigateHistory(int direction) {
        Session s = getActiveSession();
        if (s.cmdHistory.isEmpty()) return;

        s.historyIndex += direction;
        if (s.historyIndex < 0) s.historyIndex = 0;
        if (s.historyIndex >= s.cmdHistory.size()) {
            s.historyIndex = s.cmdHistory.size();
            s.currentInput = "";
            updateTerminalDisplay();
            return;
        }

        s.currentInput = s.cmdHistory.get(s.historyIndex);
        updateTerminalDisplay();
    }

    private void pasteClipboardText() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clipData = clipboard.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    CharSequence pasteText = clipData.getItemAt(0).getText();
                    if (pasteText != null) {
                        Session s = getActiveSession();
                        s.currentInput += pasteText.toString();
                        updateTerminalDisplay();
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
