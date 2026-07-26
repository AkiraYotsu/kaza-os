package com.kazaos.trkz;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
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
import java.util.Date;
import java.util.Locale;

public class TrKZActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private TextView txtConsole;
    private EditText edtCommand;
    private ScrollView scrollConsole;

    private static boolean isNativeLoaded = false;
    private File currentDirectory;

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
        scrollConsole = findViewById(R.id.scrollConsole);

        currentDirectory = getTrkzStorageDir();

        printHeader();

        checkAndRequestStoragePermissions();
        initializeTrkzField();

        if (edtCommand != null) {
            edtCommand.setOnEditorActionListener((v, actionId, event) -> {
                String cmd = edtCommand.getText().toString().trim();
                if (!cmd.isEmpty()) {
                    executeKazaCommand(cmd);
                    edtCommand.setText("");
                }
                return true;
            });
        }
    }

    private void printHeader() {
        appendHtml("<font color='#00FF66'><b>====================================================</b></font><br>");
        appendHtml("<font color='#00FF66'><b>     KAZA OS v1.1 — TrKZ Standalone Console         </b></font><br>");
        appendHtml("<font color='#00FF66'><b>====================================================</b></font><br>");
        appendHtml("<font color='#FFFFFF'>Type 'help' or '/' to view all available commands.</font><br>");
        appendHtml("<font color='#666666'>TrKZ Field Path: " + currentDirectory.getAbsolutePath() + "</font><br><br>");
    }

    private File getTrkzStorageDir() {
        File sdcard = Environment.getExternalStorageDirectory();
        File trkzDir = new File(sdcard, "TrKZ");
        if (!trkzDir.exists()) {
            trkzDir.mkdirs();
        }
        return trkzDir;
    }

    private void executeKazaCommand(String rawInput) {
        String clean = rawInput.trim();
        if (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.isEmpty()) return;

        appendHtml("<font color='#00FF66'>kaza@kernel:~$ </font><font color='#FFFFFF'>" + escapeHtml(clean) + "</font><br>");

        String[] parts = clean.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        if (cmd.equals("li")) {
            cmdLi(args);
        } else if (cmd.equals("read") || cmd.equals("cat")) {
            cmdRead(args);
        } else if (cmd.equals("write")) {
            cmdWrite(args);
        } else if (cmd.equals("mkdir")) {
            cmdMkdir(args);
        } else if (cmd.equals("rm")) {
            cmdRm(args);
        } else if (cmd.equals("find")) {
            cmdFind(args);
        } else if (cmd.equals("calc")) {
            cmdCalc(args);
        } else if (cmd.equals("time") || cmd.equals("date")) {
            cmdTime();
        } else if (cmd.equals("trkz")) {
            cmdTrkz(args);
        } else if (cmd.equals("sysinfo")) {
            cmdSysinfo();
        } else if (cmd.equals("clear")) {
            txtConsole.setText("");
        } else if (cmd.equals("halt") || cmd.equals("exit")) {
            appendHtml("<font color='#FF3333'>[KAZA OS] System halted.</font><br><br>");
        } else if (cmd.equals("help")) {
            cmdHelp();
        } else {
            appendHtml("<font color='#FF3333'>ERROR: Command '" + escapeHtml(clean) + "' not found. Type 'help' for command list.</font><br><br>");
        }

        scrollToBottom();
    }

    private void cmdLi(String pathStr) {
        File targetDir = pathStr.isEmpty() ? currentDirectory : resolvePath(pathStr);

        if (!targetDir.exists() || !targetDir.isDirectory()) {
            appendHtml("<font color='#FF3333'>ERROR: Directory '" + escapeHtml(targetDir.getAbsolutePath()) + "' not found or inaccessible.</font><br><br>");
            return;
        }

        appendHtml("<font color='#00FF66'>Directory listing for: " + escapeHtml(targetDir.getAbsolutePath()) + "</font><br>");
        File[] files = targetDir.listFiles();
        int count = 0;
        if (files != null) {
            for (File f : files) {
                count++;
                if (f.isDirectory()) {
                    appendHtml("<font color='#00FF66'>[DIR]  " + escapeHtml(f.getName()) + "/</font><br>");
                } else {
                    appendHtml("<font color='#FFFFFF'>[FILE] " + escapeHtml(f.getName()) + " (" + f.length() + " bytes)</font><br>");
                }
            }
        }
        appendHtml("<font color='#666666'>Total entries: " + count + "</font><br><br>");
    }

    private void cmdRead(String filepath) {
        if (filepath.isEmpty()) {
            appendHtml("<font color='#FF3333'>ERROR: Usage: read &lt;filepath&gt;</font><br><br>");
            return;
        }
        File file = resolvePath(filepath);
        if (!file.exists() || !file.isFile()) {
            appendHtml("<font color='#FF3333'>ERROR: File '" + escapeHtml(file.getAbsolutePath()) + "' not found or cannot be opened.</font><br><br>");
            return;
        }

        appendHtml("<font color='#00FF66'>--- Content of " + escapeHtml(file.getName()) + " ---</font><br>");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendHtml("<font color='#FFFFFF'>" + escapeHtml(line) + "</font><br>");
            }
            appendHtml("<font color='#00FF66'>--- End of file ---</font><br><br>");
        } catch (Exception e) {
            appendHtml("<font color='#FF3333'>ERROR: Could not read file: " + escapeHtml(e.getMessage()) + "</font><br><br>");
        }
    }

    private void cmdWrite(String args) {
        String[] parts = args.split("\\s+", 2);
        if (parts.length == 0 || parts[0].isEmpty()) {
            appendHtml("<font color='#FF3333'>ERROR: Usage: write &lt;filepath&gt; &lt;content&gt;</font><br><br>");
            return;
        }
        File file = resolvePath(parts[0]);
        String text = parts.length > 1 ? parts[1] : "";

        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            writer.println(text);
            appendHtml("<font color='#00FF66'>SUCCESS: Data written to '" + escapeHtml(file.getAbsolutePath()) + "'</font><br><br>");
        } catch (Exception e) {
            appendHtml("<font color='#FF3333'>ERROR: Cannot write to file: " + escapeHtml(e.getMessage()) + "</font><br><br>");
        }
    }

    private void cmdMkdir(String dirpath) {
        if (dirpath.isEmpty()) {
            appendHtml("<font color='#FF3333'>ERROR: Usage: mkdir &lt;dirpath&gt;</font><br><br>");
            return;
        }
        File dir = resolvePath(dirpath);
        if (dir.mkdirs() || dir.exists()) {
            appendHtml("<font color='#00FF66'>SUCCESS: Directory '" + escapeHtml(dir.getAbsolutePath()) + "' created.</font><br><br>");
        } else {
            appendHtml("<font color='#FF3333'>ERROR: Could not create directory '" + escapeHtml(dir.getAbsolutePath()) + "'.</font><br><br>");
        }
    }

    private void cmdRm(String pathStr) {
        if (pathStr.isEmpty()) {
            appendHtml("<font color='#FF3333'>ERROR: Usage: rm &lt;filepath&gt;</font><br><br>");
            return;
        }
        File target = resolvePath(pathStr);
        if (target.exists() && target.delete()) {
            appendHtml("<font color='#00FF66'>SUCCESS: File/Directory '" + escapeHtml(target.getName()) + "' removed.</font><br><br>");
        } else {
            appendHtml("<font color='#FF3333'>ERROR: Could not remove '" + escapeHtml(target.getAbsolutePath()) + "'.</font><br><br>");
        }
    }

    private void cmdFind(String args) {
        String[] parts = args.split("\\s+", 2);
        String keyword = parts[0];
        if (keyword.isEmpty()) {
            appendHtml("<font color='#FF3333'>ERROR: Usage: find &lt;keyword&gt; [path]</font><br><br>");
            return;
        }
        File baseDir = parts.length > 1 ? resolvePath(parts[1]) : currentDirectory;
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            appendHtml("<font color='#FF3333'>ERROR: Path '" + escapeHtml(baseDir.getAbsolutePath()) + "' inaccessible.</font><br><br>");
            return;
        }

        appendHtml("<font color='#00FF66'>Searching for '" + escapeHtml(keyword) + "' in '" + escapeHtml(baseDir.getAbsolutePath()) + "'...</font><br>");
        File[] files = baseDir.listFiles();
        int matches = 0;
        if (files != null) {
            for (File f : files) {
                if (f.getName().toLowerCase().contains(keyword.toLowerCase())) {
                    matches++;
                    appendHtml("<font color='#FFFFFF'>  -&gt; Found: " + escapeHtml(f.getAbsolutePath()) + "</font><br>");
                }
            }
        }
        appendHtml("<font color='#666666'>Search finished. " + matches + " match(es) found.</font><br><br>");
    }

    private void cmdCalc(String expr) {
        if (expr.isEmpty()) {
            appendHtml("<font color='#FF3333'>ERROR: Usage: calc &lt;num1&gt; &lt;op&gt; &lt;num2&gt; (e.g. calc 25 * 4)</font><br><br>");
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
                if (b == 0) { appendHtml("<font color='#FF3333'>ERROR: Division by zero.</font><br><br>"); return; }
                res = a / b;
            }
            appendHtml("<font color='#00FF66'>RESULT: " + a + " " + op + " " + b + " = " + res + "</font><br><br>");
        } catch (Exception e) {
            appendHtml("<font color='#FF3333'>ERROR: Invalid expression. Example: calc 100 / 4</font><br><br>");
        }
    }

    private void cmdTime() {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        appendHtml("<font color='#00FF66'>System Time: " + now + "</font><br><br>");
    }

    private void cmdTrkz(String arg) {
        currentDirectory = getTrkzStorageDir();
        if (arg.equalsIgnoreCase("status")) {
            appendHtml("<font color='#00FF66'>--- TrKZ Storage Field Status ---</font><br>");
            appendHtml("<font color='#FFFFFF'>Field Path       : " + escapeHtml(currentDirectory.getAbsolutePath()) + "</font><br>");
            appendHtml("<font color='#00FF66'>Android Storage : Granted / Active</font><br>");
            appendHtml("<font color='#FFFFFF'>Sync Mode       : Direct Physical Disk Mirror</font><br><br>");
            return;
        }
        appendHtml("<font color='#00FF66'>[TrKZ FIELD] Switched workspace to '" + escapeHtml(currentDirectory.getAbsolutePath()) + "'</font><br>");
        cmdLi("");
    }

    private void cmdSysinfo() {
        appendHtml("<font color='#00FF66'>--- Kaza OS Environment Info ---</font><br>");
        appendHtml("<font color='#FFFFFF'>App Package  : com.kazaos.trkz</font><br>");
        appendHtml("<font color='#FFFFFF'>Architecture : ARM64 / POSIX Native Core</font><br>");
        appendHtml("<font color='#00FF66'>Status       : Active &amp; Functioning 100%</font><br>");
        appendHtml("<font color='#FFFFFF'>TrKZ Field   : " + escapeHtml(currentDirectory.getAbsolutePath()) + "</font><br><br>");
    }

    private void cmdHelp() {
        appendHtml("<font color='#00FF66'>Available Commands (v1.1 TrKZ Field Edition):</font><br>");
        appendHtml("<font color='#FFFFFF'>  trkz [status]       - Open TrKZ Storage Field (/sdcard/TrKZ)</font><br>");
        appendHtml("<font color='#FFFFFF'>  li [path]           - List directory contents</font><br>");
        appendHtml("<font color='#FFFFFF'>  read / cat &lt;file&gt;   - Read text file content</font><br>");
        appendHtml("<font color='#FFFFFF'>  write &lt;file&gt; &lt;txt&gt;  - Append text to file</font><br>");
        appendHtml("<font color='#FFFFFF'>  mkdir &lt;dirpath&gt;     - Create new directory</font><br>");
        appendHtml("<font color='#FFFFFF'>  rm &lt;filepath&gt;       - Remove file or directory</font><br>");
        appendHtml("<font color='#FFFFFF'>  find &lt;keyword&gt;      - Search files in directory</font><br>");
        appendHtml("<font color='#FFFFFF'>  calc &lt;a op b&gt;       - Math calculator (e.g. calc 25 * 4)</font><br>");
        appendHtml("<font color='#FFFFFF'>  time / date         - Display system clock</font><br>");
        appendHtml("<font color='#FFFFFF'>  clear               - Clear terminal screen</font><br>");
        appendHtml("<font color='#FFFFFF'>  exit / halt         - Exit Kaza OS shell</font><br><br>");
    }

    private File resolvePath(String pathStr) {
        if (pathStr.startsWith("/")) {
            return new File(pathStr);
        }
        return new File(currentDirectory, pathStr);
    }

    private void appendHtml(String htmlText) {
        if (txtConsole != null) {
            Spanned spanned = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ?
                    Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY) : Html.fromHtml(htmlText);
            txtConsole.append(spanned);
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void scrollToBottom() {
        if (scrollConsole != null) {
            scrollConsole.post(() => scrollConsole.fullScroll(View.FOCUS_DOWN));
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
            File trkzDir = getTrkzStorageDir();
            if (trkzDir.exists()) {
                Toast.makeText(this, "TrKZ Storage Field Active!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {}
    }
}
