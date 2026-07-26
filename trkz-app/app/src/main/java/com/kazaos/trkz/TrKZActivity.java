package com.kazaos.trkz;

import android.Manifest;
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
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrKZActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMISSION = 1001;

    private TextView txtConsole;
    private EditText edtCommand;
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
        edtCommand = findViewById(R.id.edtCommand);
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

        // Tap terminal screen to open keyboard
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

        // Setup extra key row
        setupExtraKeys();

        // New Session button
        btnNewSession.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createNewSession();
            }
        });

        // Enter key listener
        edtCommand.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                String cmd = edtCommand.getText().toString().trim();
                if (!cmd.isEmpty()) {
                    executeKazaCommand(cmd);
                    edtCommand.setText("");
                }
                return true;
            }
        });

        checkAndRequestStoragePermissions();
        renderActiveSession();
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

    private void renderActiveSession() {
        Session s = getActiveSession();
        txtSessionTag.setText("  [" + s.name + "]");
        if (s.historyBuffer.length() == 0) {
            printHeader(s);
        } else {
            txtConsole.setText(Html.fromHtml(s.historyBuffer.toString()));
        }
        scrollToBottom();
    }

    private void printHeader(Session s) {
        appendHtml("TrKZ Terminal v1.1 (" + s.name + ")<br>");
        appendHtml("<font color='#888888'>Working Dir: " + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br>");
        appendHtml("<font color='#888888'>Type 'help' or '/' for commands.</font><br><br>");
    }

    private void executeKazaCommand(String rawInput) {
        String clean = rawInput.trim();
        if (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.isEmpty()) return;

        Session s = getActiveSession();
        s.cmdHistory.add(clean);
        s.historyIndex = s.cmdHistory.size();

        appendHtml("<font color='#34d399'>kaza@kernel:~$ </font><font color='#ffffff'>" + escapeHtml(clean) + "</font><br>");

        String[] parts = clean.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        if (cmd.equals("pwd")) {
            appendHtml("<font color='#ffffff'>" + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br><br>");
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
            appendHtml("<font color='#34d399'>Switched workspace -> " + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br>");
            cmdLi(s, "");
        } else if (cmd.equals("session")) {
            cmdSession(args);
        } else if (cmd.equals("sysinfo")) {
            cmdSysinfo(s);
        } else if (cmd.equals("clear")) {
            s.historyBuffer.setLength(0);
            txtConsole.setText("");
            printHeader(s);
        } else if (cmd.equals("halt") || cmd.equals("exit")) {
            appendHtml("<font color='#f87171'>[KAZA OS] Session stopped.</font><br><br>");
        } else if (cmd.equals("help")) {
            cmdHelp();
        } else {
            appendHtml("<font color='#f87171'>kaza: command not found: " + escapeHtml(clean) + "</font><br><br>");
        }

        // Reset CTRL state if it was active
        if (isCtrlActive) {
            isCtrlActive = false;
            btnCtrl.setBackgroundColor(Color.parseColor("#181818"));
            btnCtrl.setTextColor(Color.parseColor("#FFFFFF"));
        }

        scrollToBottom();
    }

    private void cmdCd(Session s, String pathStr) {
        if (pathStr.isEmpty() || pathStr.equals("~")) {
            s.currentDir = getTrkzStorageDir();
            appendHtml("<font color='#888888'>" + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br><br>");
            return;
        }

        File target = resolvePath(s, pathStr);
        if (target.exists() && target.isDirectory()) {
            s.currentDir = target;
            appendHtml("<font color='#888888'>" + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br><br>");
        } else {
            appendHtml("<font color='#f87171'>cd: no such file or directory: " + escapeHtml(pathStr) + "</font><br><br>");
        }
    }

    private void cmdLi(Session s, String pathStr) {
        File targetDir = pathStr.isEmpty() ? s.currentDir : resolvePath(s, pathStr);

        if (!targetDir.exists() || !targetDir.isDirectory()) {
            appendHtml("<font color='#f87171'>ls: cannot access '" + escapeHtml(targetDir.getAbsolutePath()) + "': No such directory</font><br><br>");
            return;
        }

        File[] files = targetDir.listFiles();
        int count = 0;
        if (files != null) {
            for (File f : files) {
                count++;
                if (f.isDirectory()) {
                    // Vertical line-by-line directory listing in BLUE
                    appendHtml("<font color='#60a5fa'><b>[DIR]  " + escapeHtml(f.getName()) + "/</b></font><br>");
                } else if (f.canExecute() || f.getName().endsWith(".sh")) {
                    // Executables in GREEN
                    appendHtml("<font color='#34d399'>[EXEC] " + escapeHtml(f.getName()) + "</font><br>");
                } else {
                    // Regular files in WHITE with size in GRAY
                    appendHtml("<font color='#ffffff'>[FILE] " + escapeHtml(f.getName()) + "</font> <font color='#888888'>(" + f.length() + " B)</font><br>");
                }
            }
        }
        appendHtml("<font color='#888888'>Total entries: " + count + "</font><br><br>");
    }

    private void cmdRead(Session s, String filepath) {
        if (filepath.isEmpty()) {
            appendHtml("<font color='#f87171'>read: missing file operand</font><br><br>");
            return;
        }
        File file = resolvePath(s, filepath);
        if (!file.exists() || !file.isFile()) {
            appendHtml("<font color='#f87171'>read: " + escapeHtml(file.getAbsolutePath()) + ": No such file</font><br><br>");
            return;
        }

        appendHtml("<font color='#888888'>--- " + escapeHtml(file.getName()) + " ---</font><br>");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendHtml("<font color='#ffffff'>" + escapeHtml(line) + "</font><br>");
            }
            appendHtml("<font color='#888888'>--- EOF ---</font><br><br>");
        } catch (Exception e) {
            appendHtml("<font color='#f87171'>read: error reading file</font><br><br>");
        }
    }

    private void cmdWrite(Session s, String args) {
        String[] parts = args.split("\\s+", 2);
        if (parts.length == 0 || parts[0].isEmpty()) {
            appendHtml("<font color='#f87171'>write: missing file operand</font><br><br>");
            return;
        }
        File file = resolvePath(s, parts[0]);
        String text = parts.length > 1 ? parts[1] : "";

        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            writer.println(text);
            appendHtml("<font color='#34d399'>write: saved -> " + escapeHtml(file.getName()) + "</font><br><br>");
        } catch (Exception e) {
            appendHtml("<font color='#f87171'>write: permission denied</font><br><br>");
        }
    }

    private void cmdMkdir(Session s, String dirpath) {
        if (dirpath.isEmpty()) {
            appendHtml("<font color='#f87171'>mkdir: missing operand</font><br><br>");
            return;
        }
        File dir = resolvePath(s, dirpath);
        if (dir.mkdirs() || dir.exists()) {
            appendHtml("<font color='#34d399'>mkdir: created -> " + escapeHtml(dir.getName()) + "</font><br><br>");
        } else {
            appendHtml("<font color='#f87171'>mkdir: cannot create directory</font><br><br>");
        }
    }

    private void cmdRm(Session s, String pathStr) {
        if (pathStr.isEmpty()) {
            appendHtml("<font color='#f87171'>rm: missing operand</font><br><br>");
            return;
        }
        File target = resolvePath(s, pathStr);
        if (target.exists() && target.delete()) {
            appendHtml("<font color='#888888'>rm: removed -> " + escapeHtml(target.getName()) + "</font><br><br>");
        } else {
            appendHtml("<font color='#f87171'>rm: cannot remove '" + escapeHtml(pathStr) + "'</font><br><br>");
        }
    }

    private void cmdFind(Session s, String args) {
        String[] parts = args.split("\\s+", 2);
        String keyword = parts[0];
        if (keyword.isEmpty()) {
            appendHtml("<font color='#f87171'>find: missing keyword</font><br><br>");
            return;
        }
        File baseDir = parts.length > 1 ? resolvePath(s, parts[1]) : s.currentDir;
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            appendHtml("<font color='#f87171'>find: path inaccessible</font><br><br>");
            return;
        }

        File[] files = baseDir.listFiles();
        int matches = 0;
        if (files != null) {
            for (File f : files) {
                if (f.getName().toLowerCase().contains(keyword.toLowerCase())) {
                    matches++;
                    if (f.isDirectory()) {
                        appendHtml("<font color='#60a5fa'>  [DIR]  " + escapeHtml(f.getAbsolutePath()) + "</font><br>");
                    } else {
                        appendHtml("<font color='#ffffff'>  [FILE] " + escapeHtml(f.getAbsolutePath()) + "</font><br>");
                    }
                }
            }
        }
        appendHtml("<font color='#888888'>find: " + matches + " match(es)</font><br><br>");
    }

    private void cmdCalc(String expr) {
        if (expr.isEmpty()) {
            appendHtml("<font color='#f87171'>calc: missing expression</font><br><br>");
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
                if (b == 0) { appendHtml("<font color='#f87171'>calc: division by zero</font><br><br>"); return; }
                res = a / b;
            }
            appendHtml("<font color='#34d399'>= " + res + "</font><br><br>");
        } catch (Exception e) {
            appendHtml("<font color='#f87171'>calc: invalid format</font><br><br>");
        }
    }

    private void cmdTime() {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        appendHtml("<font color='#888888'>" + now + "</font><br><br>");
    }

    private void cmdSession(String arg) {
        if (arg.isEmpty()) {
            appendHtml("<font color='#888888'>Sessions:</font><br>");
            for (int i = 0; i < sessions.size(); i++) {
                Session s = sessions.get(i);
                String activeTag = (i == activeSessionIndex) ? " *" : "";
                appendHtml("<font color='#ffffff'>  " + s.name + activeTag + " (" + escapeHtml(s.currentDir.getName()) + ")</font><br>");
            }
            appendHtml("<br>");

        } else if (arg.equalsIgnoreCase("new")) {
            createNewSession();
        } else {
            try {
                int idx = Integer.parseInt(arg) - 1;
                if (idx >= 0 && idx < sessions.size()) {
                    activeSessionIndex = idx;
                    renderActiveSession();
                } else {
                    appendHtml("<font color='#f87171'>session: invalid session index</font><br><br>");
                }
            } catch (Exception e) {
                appendHtml("<font color='#f87171'>session: usage: session [new | <number>]</font><br><br>");
            }
        }
    }

    private void cmdSysinfo(Session s) {
        appendHtml("<font color='#888888'>--- System Info ---</font><br>");
        appendHtml("<font color='#ffffff'>OS          : Kaza OS v1.1</font><br>");
        appendHtml("<font color='#ffffff'>App         : TrKZ Terminal (com.kazaos.trkz)</font><br>");
        appendHtml("<font color='#ffffff'>Active Dir  : " + escapeHtml(s.currentDir.getAbsolutePath()) + "</font><br>");
        appendHtml("<font color='#34d399'>Storage     : Full System Access Granted</font><br><br>");
    }

    private void cmdHelp() {
        appendHtml("<font color='#888888'>Commands:</font><br>");
        appendHtml("<font color='#ffffff'>  pwd               - Print working directory</font><br>");
        appendHtml("<font color='#ffffff'>  cd &lt;path&gt;         - Change directory</font><br>");
        appendHtml("<font color='#ffffff'>  ls / li [path]    - List files vertically (Folders in BLUE)</font><br>");
        appendHtml("<font color='#ffffff'>  read / cat &lt;file&gt;  - Read text file</font><br>");
        appendHtml("<font color='#ffffff'>  write &lt;file&gt; &lt;txt&gt; - Append text to file</font><br>");
        appendHtml("<font color='#ffffff'>  mkdir / rm        - Create or remove files/folders</font><br>");
        appendHtml("<font color='#ffffff'>  find &lt;kw&gt; [path]  - Search files</font><br>");
        appendHtml("<font color='#ffffff'>  calc / time       - System tools</font><br>");
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
        return new File(s.currentDir, pathStr);
    }

    private void appendHtml(String htmlText) {
        Session s = getActiveSession();
        s.historyBuffer.append(htmlText);
        Spanned spanned = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ?
                Html.fromHtml(s.historyBuffer.toString(), Html.FROM_HTML_MODE_LEGACY) :
                Html.fromHtml(s.historyBuffer.toString());
        txtConsole.setText(spanned);
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
        if (edtCommand != null) {
            edtCommand.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(edtCommand, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void setupExtraKeys() {
        // Toggle CTRL key
        btnCtrl.setOnClickListener(v -> {
            isCtrlActive = !isCtrlActive;
            if (isCtrlActive) {
                btnCtrl.setBackgroundColor(Color.parseColor("#3B82F6")); // BLUE when active
                btnCtrl.setTextColor(Color.parseColor("#FFFFFF"));
            } else {
                btnCtrl.setBackgroundColor(Color.parseColor("#181818")); // Dark default
                btnCtrl.setTextColor(Color.parseColor("#FFFFFF"));
            }
        });

        findViewById(R.id.keySlash).setOnClickListener(v -> insertText("/"));
        findViewById(R.id.keyTab).setOnClickListener(v -> insertText("  "));
        
        // ESC key: Clears current line and resets CTRL
        findViewById(R.id.keyEsc).setOnClickListener(v -> {
            edtCommand.setText("");
            if (isCtrlActive) {
                isCtrlActive = false;
                btnCtrl.setBackgroundColor(Color.parseColor("#181818"));
                btnCtrl.setTextColor(Color.parseColor("#FFFFFF"));
            }
        });

        findViewById(R.id.keyHome).setOnClickListener(v -> executeKazaCommand("cd ~"));

        // UP key: Previous command in history
        findViewById(R.id.keyUp).setOnClickListener(v -> navigateHistory(-1));

        // DOWN key: Next command in history
        findViewById(R.id.keyDown).setOnClickListener(v -> navigateHistory(1));

        // LEFT key: Move cursor left
        findViewById(R.id.keyLeft).setOnClickListener(v -> moveCursor(-1));

        // RIGHT key: Move cursor right
        findViewById(R.id.keyRight).setOnClickListener(v -> moveCursor(1));

        findViewById(R.id.keyPgUp).setOnClickListener(v -> scrollConsole.pageScroll(View.FOCUS_UP));
        findViewById(R.id.keyPgDn).setOnClickListener(v -> scrollConsole.pageScroll(View.FOCUS_DOWN));
    }

    private void navigateHistory(int direction) {
        Session s = getActiveSession();
        if (s.cmdHistory.isEmpty()) return;

        s.historyIndex += direction;
        if (s.historyIndex < 0) s.historyIndex = 0;
        if (s.historyIndex >= s.cmdHistory.size()) {
            s.historyIndex = s.cmdHistory.size();
            edtCommand.setText("");
            return;
        }

        String historicalCmd = s.cmdHistory.get(s.historyIndex);
        edtCommand.setText(historicalCmd);
        edtCommand.setSelection(historicalCmd.length());
    }

    private void moveCursor(int offset) {
        int pos = edtCommand.getSelectionStart() + offset;
        if (pos < 0) pos = 0;
        if (pos > edtCommand.length()) pos = edtCommand.length();
        edtCommand.setSelection(pos);
    }

    private void insertText(String str) {
        if (edtCommand != null) {
            int start = Math.max(edtCommand.getSelectionStart(), 0);
            int end = Math.max(edtCommand.getSelectionEnd(), 0);
            edtCommand.getText().replace(Math.min(start, end), Math.max(start, end), str, 0, str.length());
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
}
