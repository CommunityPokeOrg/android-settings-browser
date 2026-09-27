package org.communitypoke.settingsbrowser.root;

import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Persistent root execution context — the app's root daemon.
 *
 * A single long-lived {@code su} shell is spawned on first use and kept alive;
 * every privileged command is piped into it instead of paying the cost (and
 * re-triggering the su grant UI) of a fresh {@code su -c} per call. Commands
 * are shipped to the shell as base64 scripts, run with stdout/stderr captured
 * to temp files under /data/local/tmp, and framed by sentinel markers so the
 * reader can split the streams and recover the exit code.
 */
public final class RootDaemon {

    private static final String TAG = "SettingsBrowserRoot";
    private static final String TMP = "/data/local/tmp";
    private static final long DEFAULT_TIMEOUT_MS = 15_000;

    private static RootDaemon instance;

    public static synchronized RootDaemon get() {
        if (instance == null) {
            instance = new RootDaemon();
        }
        return instance;
    }

    public static final class Result {
        public int code = -1;
        public String stdout = "";
        public String stderr = "";
        public String error;
        public boolean timedOut;
    }

    private Process process;
    private BufferedWriter stdin;
    private BufferedReader stdout;
    private Boolean rootAvailable;

    private RootDaemon() {
    }

    /** True once a root shell has answered {@code id -u} with 0. Cached. */
    public synchronized boolean isAvailable() {
        if (rootAvailable == null) {
            Result r = exec("id -u", 5_000);
            rootAvailable = r != null && r.code == 0
                    && "0".equals(r.stdout.trim());
        }
        return rootAvailable;
    }

    /** Run a shell script in the persistent root shell. Never returns null. */
    public synchronized Result exec(String script) {
        return exec(script, DEFAULT_TIMEOUT_MS);
    }

    public synchronized Result exec(String script, long timeoutMs) {
        Result out = new Result();
        if (!ensureAlive()) {
            out.error = "su unavailable";
            rootAvailable = false;
            return out;
        }

        String tag = "sbx" + Long.toHexString(System.nanoTime());
        String scriptFile = TMP + "/." + tag + ".sh";
        String outFile = TMP + "/." + tag + ".o";
        String errFile = TMP + "/." + tag + ".e";
        String beginMark = "__" + tag + "_B";
        String errMark = "__" + tag + "_E";
        String doneMark = "__" + tag + "_D_";

        String b64 = Base64.encodeToString(
                script.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String command = "echo " + b64 + " | base64 -d > " + scriptFile
                + " && sh " + scriptFile + " >" + outFile + " 2>" + errFile
                + "; __rc=$?; echo " + beginMark + "; cat " + outFile
                + "; echo " + errMark + "; cat " + errFile
                + "; echo " + doneMark + "$__rc"
                + "; rm -f " + scriptFile + " " + outFile + " " + errFile;

        StringBuilder outBuf = new StringBuilder();
        StringBuilder errBuf = new StringBuilder();
        try {
            stdin.write(command);
            stdin.write('\n');
            stdin.flush();

            int section = 0; // 0 = before stdout, 1 = stdout, 2 = stderr
            long deadline = System.nanoTime()
                    + timeoutMs * 1_000_000L;
            while (true) {
                if (!stdout.ready()) {
                    if (System.nanoTime() > deadline) {
                        out.timedOut = true;
                        out.error = "timed out after " + timeoutMs + "ms";
                        reset();
                        return out;
                    }
                    if (!alive()) {
                        out.error = "root shell died";
                        reset();
                        return out;
                    }
                    sleep(10);
                    continue;
                }
                String line = stdout.readLine();
                if (line == null) {
                    out.error = "root shell closed";
                    reset();
                    return out;
                }
                if (line.startsWith(doneMark)) {
                    try {
                        out.code = Integer.parseInt(
                                line.substring(doneMark.length()).trim());
                    } catch (NumberFormatException ignored) {
                        out.code = -1;
                    }
                    out.stdout = outBuf.toString();
                    out.stderr = errBuf.toString();
                    return out;
                }
                if (section == 0) {
                    if (line.equals(beginMark)) {
                        section = 1;
                    }
                    continue; // stray su noise before our markers
                }
                if (section == 1 && line.equals(errMark)) {
                    section = 2;
                    continue;
                }
                StringBuilder target = section == 1 ? outBuf : errBuf;
                if (target.length() > 0) {
                    target.append('\n');
                }
                target.append(line);
            }
        } catch (IOException e) {
            out.error = String.valueOf(e);
            reset();
            return out;
        }
    }

    /** Single-quote a string for embedding inside a shell script. */
    public static String shellQuote(String value) {
        return "'" + (value == null ? "" : value.replace("'", "'\\''")) + "'";
    }

    private boolean alive() {
        return process != null && process.isAlive();
    }

    private boolean ensureAlive() {
        if (alive()) {
            return true;
        }
        try {
            process = new ProcessBuilder("su")
                    .redirectErrorStream(true)
                    .start();
            stdin = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
            stdout = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "could not spawn su", t);
            process = null;
            return false;
        }
    }

    private void reset() {
        try {
            if (process != null) {
                process.destroy();
            }
        } catch (Throwable ignored) {
        }
        process = null;
        stdin = null;
        stdout = null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
