package app;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Demo: compute a NN anomaly score via the sklearn Python bridge using a
 * synthetic gesture.
 */
public class SklearnBridgeDemo {
    public static void main(String[] args) {
        // Adjust python path if needed
        String python = new File(".venv/Scripts/python.exe").getAbsolutePath();
        File repo = new File(".").getAbsoluteFile();
        File modelDir = new File(repo, "models/touch_ae_sklearn");

        SklearnTouchModelBridge bridge = new SklearnTouchModelBridge(python, repo, modelDir);

        // Example feature vector (replace with real data)
        Map<String, Object> fv = new HashMap<>();
        fv.put("gesture_type", "SWIPE");
        fv.put("duration_ms", 280);
        fv.put("total_distance", 180.5);
        fv.put("avg_velocity", 0.62);
        fv.put("peak_velocity", 1.8);
        fv.put("avg_pressure", 0.45);
        fv.put("peak_pressure", 0.8);
        fv.put("path_deviation", 2.1);
        fv.put("direction_changes", 1);
        fv.put("jitter", 0.6);

        SklearnTouchModelBridge.ScoreResult res = bridge.score(fv);
        System.out.println("ok=" + res.ok + ", score=" + res.score + ", mse=" + res.mse + ", thr=" + res.threshold
                + ", err=" + res.error);
    }
}
