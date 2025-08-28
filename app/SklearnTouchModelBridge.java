package app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Java bridge to the sklearn autoencoder scorer (Python).
 * It runs ml/touch/score_once_sklearn.py for a single feature vector
 * and returns the normalized anomaly score.
 */
public class SklearnTouchModelBridge {
    private final String pythonExe;
    private final File repoRoot;
    private final File modelDir;
    private final boolean persistent;
    private final long timeoutMs;

    // Persistent process state
    private Process persistentProc = null;
    private BufferedWriter persistentIn = null;
    private BufferedReader persistentOut = null;

    public SklearnTouchModelBridge(String pythonExePath, File repoRootDir, File modelDirectory) {
        this(pythonExePath, repoRootDir, modelDirectory, false, 0);
    }

    public SklearnTouchModelBridge(String pythonExePath, File repoRootDir, File modelDirectory,
            boolean persistent, long timeoutMs) {
        this.pythonExe = pythonExePath;
        this.repoRoot = repoRootDir;
        this.modelDir = modelDirectory;
        this.persistent = persistent;
        this.timeoutMs = timeoutMs;
    }

    public static class ScoreResult {
        public final boolean ok;
        public final double score;
        public final double mse;
        public final double threshold;
        public final String error;

        public ScoreResult(boolean ok, double score, double mse, double threshold, String error) {
            this.ok = ok;
            this.score = score;
            this.mse = mse;
            this.threshold = threshold;
            this.error = error;
        }
    }

    public ScoreResult score(Map<String, Object> features) {
        try {
            String resp;
            if (!persistent) {
                ProcessBuilder pb = new ProcessBuilder(
                        pythonExe,
                        new File(repoRoot, "ml/touch/score_once_sklearn.py").getAbsolutePath(),
                        "--model",
                        modelDir.getAbsolutePath());
                pb.directory(repoRoot);
                pb.redirectErrorStream(true);
                Process p = pb.start();

                // Write JSON to stdin
                try (BufferedWriter w = new BufferedWriter(
                        new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8))) {
                    String json = toJson(features);
                    w.write(json);
                    w.flush();
                }

                // Read JSON with optional timeout
                resp = readAllWithTimeout(p, timeoutMs);
                if (resp == null) {
                    p.destroyForcibly();
                    return new ScoreResult(false, 0.0, 0.0, 0.0, "timeout");
                }
                try {
                    p.waitFor();
                } catch (InterruptedException ie) {
                    /* ignore */ }
                p.destroyForcibly();
            } else {
                ensurePersistent();
                String json = toJson(features);
                persistentIn.write(json);
                persistentIn.write("\n");
                persistentIn.flush();
                resp = readLineWithTimeout(persistentOut, timeoutMs);
                if (resp == null) {
                    restartPersistent();
                    return new ScoreResult(false, 0.0, 0.0, 0.0, "timeout");
                }
            }

            // Very small ad-hoc JSON parse to avoid deps
            if (resp.contains("\"ok\": true")) {
                double mse = extractDouble(resp, "mse");
                double thr = extractDouble(resp, "threshold");
                double score = extractDouble(resp, "score");
                return new ScoreResult(true, score, mse, thr, null);
            } else {
                String err = extractString(resp, "error");
                return new ScoreResult(false, 0.0, 0.0, 0.0, err != null ? err : "unknown_error");
            }
        } catch (Exception e) {
            return new ScoreResult(false, 0.0, 0.0, 0.0, e.getMessage());
        }
    }

    private void ensurePersistent() throws Exception {
        if (persistentProc != null)
            return;
        ProcessBuilder pb = new ProcessBuilder(
                pythonExe,
                new File(repoRoot, "ml/touch/serve_sklearn.py").getAbsolutePath(),
                "--model",
                modelDir.getAbsolutePath());
        pb.directory(repoRoot);
        pb.redirectErrorStream(true);
        persistentProc = pb.start();
        persistentIn = new BufferedWriter(
                new OutputStreamWriter(persistentProc.getOutputStream(), StandardCharsets.UTF_8));
        persistentOut = new BufferedReader(
                new InputStreamReader(persistentProc.getInputStream(), StandardCharsets.UTF_8));
    }

    private void restartPersistent() {
        try {
            if (persistentProc != null)
                persistentProc.destroyForcibly();
        } catch (Exception ignored) {
        }
        persistentProc = null;
        persistentIn = null;
        persistentOut = null;
    }

    private static String readAllWithTimeout(Process p, long timeoutMs) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        long start = System.currentTimeMillis();
        while (true) {
            while (r.ready()) {
                String line = r.readLine();
                if (line == null)
                    break;
                out.append(line);
            }
            if (!p.isAlive())
                break;
            if (timeoutMs > 0 && System.currentTimeMillis() - start > timeoutMs) {
                return null;
            }
            Thread.sleep(5);
        }
        return out.toString();
    }

    private static String readLineWithTimeout(BufferedReader r, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        while (true) {
            if (r.ready()) {
                return r.readLine();
            }
            if (timeoutMs > 0 && System.currentTimeMillis() - start > timeoutMs) {
                return null;
            }
            Thread.sleep(5);
        }
    }

    private static double extractDouble(String json, String key) {
        try {
            int i = json.indexOf('"' + key + '"');
            if (i < 0)
                return 0.0;
            int c = json.indexOf(':', i);
            int end = json.indexOf(',', c);
            if (end < 0)
                end = json.indexOf('}', c);
            String num = json.substring(c + 1, end).trim();
            return Double.parseDouble(num);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String extractString(String json, String key) {
        try {
            int i = json.indexOf('"' + key + '"');
            if (i < 0)
                return null;
            int c = json.indexOf(':', i);
            int q1 = json.indexOf('"', c + 1);
            int q2 = json.indexOf('"', q1 + 1);
            return json.substring(q1 + 1, q2);
        } catch (Exception e) {
            return null;
        }
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first)
                sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append('"').append(':');
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v.toString());
            } else {
                sb.append('"').append(String.valueOf(v)).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
