package app;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class NNBridgeSmokeTest {
    public static void main(String[] args) {
        try {
            File root = new File(".").getAbsoluteFile();
            File cfgFile = new File(root, "config/app.properties");
            if (!cfgFile.exists()) {
                System.out.println("Config missing: " + cfgFile.getAbsolutePath());
                return;
            }
            Config cfg = new Config(root, cfgFile);
            boolean enable = cfg.getBoolean("enable_nn_scoring", false);
            System.out.println("enable_nn_scoring=" + enable);
            String py = cfg.getString("python_exe", ".venv/Scripts/python.exe");
            String modelDir = cfg.getString("model_dir", "models/touch_ae_sklearn");
            boolean persistent = "persistent".equalsIgnoreCase(cfg.getString("nn_mode", "oneshot"));
            long timeout = cfg.getNNTimeoutMs();

            SklearnTouchModelBridge bridge = new SklearnTouchModelBridge(
                    cfg.resolveUnderRoot(py).getAbsolutePath(),
                    root,
                    cfg.resolveUnderRoot(modelDir),
                    persistent,
                    timeout);

            Map<String, Object> feat = new HashMap<>();
            feat.put("duration_ms", 120);
            feat.put("total_distance", 180.0);
            feat.put("avg_velocity", 0.8);
            feat.put("peak_velocity", 2.3);
            feat.put("avg_pressure", 0.7);
            feat.put("peak_pressure", 0.95);
            feat.put("path_deviation", 0.9);
            feat.put("direction_changes", 2);
            feat.put("jitter", 0.5);

            long t0 = System.currentTimeMillis();
            SklearnTouchModelBridge.ScoreResult r = bridge.score(feat);
            long dt = System.currentTimeMillis() - t0;

            if (r != null && r.ok) {
                System.out
                        .println("OK mse=" + r.mse + " thr=" + r.threshold + " score=" + r.score + " latency_ms=" + dt);
            } else {
                System.out.println("ERROR: " + (r != null ? r.error : "null_result"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
