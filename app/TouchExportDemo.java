package app;

/**
 * Simple demo to collect normal touch features into a CSV for ML training.
 * It uses the built-in simulators; replace with real on-device events in
 * Android.
 */
public class TouchExportDemo {
    public static void main(String[] args) throws Exception {
        TouchAgent agent = new TouchAgent();
        agent.start();

        try (TouchFeatureCsvExporter exporter = new TouchFeatureCsvExporter("data", "normal")) {
            agent.addGestureFeatureListener(exporter);

            // Generate a few hundred synthetic normal gestures
            for (int i = 0; i < 300; i++) {
                if (i % 3 == 0) {
                    agent.simulateTap(100 + (i % 7) * 10, 140 + (i % 5) * 8, 120 + (i % 60));
                } else {
                    agent.simulateTouch(80 + (i % 10) * 12, 120, 220 + (i % 15) * 8, 130, 260 + (i % 140));
                }
                Thread.sleep(10);
            }
        }

        agent.stop();
        System.out.println("Export complete.");
    }
}
