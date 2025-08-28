package app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * TypingAgent monitors keystroke dynamics for behavioral anomaly detection.
 * Captures timing patterns without recording actual keystrokes for privacy.
 */
public class TypingAgent implements Agent {

    private static final String AGENT_NAME = "TypingAgent";
    private static final int WINDOW_SIZE = 50; // Recent keystrokes to analyze
    private static final int WARMUP_THRESHOLD = 100; // Keystrokes needed for stable baseline
    private static final double EWMA_ALPHA = 0.1; // Exponential moving average factor

    // Keystroke event data structure
    private static class KeyEvent {
        final long timestamp;
        final boolean isKeyDown;
        final int keyCode;
        final float pressure; // Optional, may be 0 if not available

        KeyEvent(long timestamp, boolean isKeyDown, int keyCode, float pressure) {
            this.timestamp = timestamp;
            this.isKeyDown = isKeyDown;
            this.keyCode = keyCode;
            this.pressure = pressure;
        }
    }

    // Ring buffer for recent events
    private final Queue<KeyEvent> eventBuffer = new ConcurrentLinkedQueue<>();
    private final Queue<Double> dwellTimes = new ConcurrentLinkedQueue<>();
    private final Queue<Double> flightTimes = new ConcurrentLinkedQueue<>();

    // Baseline statistics (exponential moving averages)
    private double baselineDwellMean = 0.0;
    private double baselineDwellVariance = 0.0;
    private double baselineFlightMean = 0.0;
    private double baselineFlightVariance = 0.0;
    private double baselineBackspaceRate = 0.0;

    // State tracking
    private boolean isActive = false;
    private int totalKeystrokes = 0;
    private long lastKeyDownTime = -1;
    private int lastKeyCode = -1;
    private boolean isInWarmup = true;

    // Recent metrics for scoring
    private double recentDwellMean = 0.0;
    private double recentDwellVariance = 0.0;
    private double recentFlightMean = 0.0;
    private double recentFlightVariance = 0.0;
    private double recentBackspaceRate = 0.0;
    private boolean recentPasteDetected = false;

    @Override
    public void start() {
        isActive = true;
        System.out.println(AGENT_NAME + " started monitoring");
    }

    @Override
    public void stop() {
        isActive = false;
        eventBuffer.clear();
        dwellTimes.clear();
        flightTimes.clear();
        System.out.println(AGENT_NAME + " stopped monitoring");
    }

    /**
     * Process a keystroke event (to be called by input system)
     * 
     * @param isKeyDown true for key press, false for key release
     * @param keyCode   the key code (no text content)
     * @param pressure  optional pressure value (0 if not available)
     */
    public void onKeyEvent(boolean isKeyDown, int keyCode, float pressure) {
        if (!isActive)
            return;

        long timestamp = System.currentTimeMillis();
        KeyEvent event = new KeyEvent(timestamp, isKeyDown, keyCode, pressure);

        // Add to buffer and maintain window size
        eventBuffer.offer(event);
        while (eventBuffer.size() > WINDOW_SIZE * 2) { // *2 for down+up pairs
            eventBuffer.poll();
        }

        processKeystroke(event);
        totalKeystrokes++;

        // Update warmup status
        if (isInWarmup && totalKeystrokes >= WARMUP_THRESHOLD) {
            isInWarmup = false;
            System.out.println(AGENT_NAME + " completed warmup phase");
        }
    }

    private void processKeystroke(KeyEvent event) {
        if (event.isKeyDown) {
            // Key press - calculate flight time if we have a previous key
            if (lastKeyDownTime > 0) {
                double flightTime = event.timestamp - lastKeyDownTime;
                addFlightTime(flightTime);
            }
            lastKeyDownTime = event.timestamp;
            lastKeyCode = event.keyCode;
        } else {
            // Key release - calculate dwell time
            if (lastKeyDownTime > 0 && event.keyCode == lastKeyCode) {
                double dwellTime = event.timestamp - lastKeyDownTime;
                addDwellTime(dwellTime);
            }
        }

        // Detect special patterns
        detectSpecialPatterns(event);

        // Recalculate recent metrics
        updateRecentMetrics();
    }

    private void addDwellTime(double dwellTime) {
        dwellTimes.offer(dwellTime);
        while (dwellTimes.size() > WINDOW_SIZE) {
            dwellTimes.poll();
        }

        // Update baseline if not in warmup
        if (!isInWarmup) {
            updateBaseline(dwellTime, true);
        }
    }

    private void addFlightTime(double flightTime) {
        flightTimes.offer(flightTime);
        while (flightTimes.size() > WINDOW_SIZE) {
            flightTimes.poll();
        }

        // Update baseline if not in warmup
        if (!isInWarmup) {
            updateBaseline(flightTime, false);
        }
    }

    private void updateBaseline(double value, boolean isDwell) {
        if (isDwell) {
            baselineDwellMean = EWMA_ALPHA * value + (1 - EWMA_ALPHA) * baselineDwellMean;
            double variance = Math.pow(value - baselineDwellMean, 2);
            baselineDwellVariance = EWMA_ALPHA * variance + (1 - EWMA_ALPHA) * baselineDwellVariance;
        } else {
            baselineFlightMean = EWMA_ALPHA * value + (1 - EWMA_ALPHA) * baselineFlightMean;
            double variance = Math.pow(value - baselineFlightMean, 2);
            baselineFlightVariance = EWMA_ALPHA * variance + (1 - EWMA_ALPHA) * baselineFlightVariance;
        }
    }

    private void detectSpecialPatterns(KeyEvent event) {
        // Reset paste detection
        recentPasteDetected = false;

        // Detect rapid succession (potential paste)
        if (event.isKeyDown && lastKeyDownTime > 0) {
            double timeSinceLastKey = event.timestamp - lastKeyDownTime;
            if (timeSinceLastKey < 10) { // Very fast typing suggests paste
                recentPasteDetected = true;
            }
        }
    }

    private void updateRecentMetrics() {
        // Calculate recent dwell time statistics
        if (!dwellTimes.isEmpty()) {
            recentDwellMean = dwellTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double sumSquaredDiffs = dwellTimes.stream()
                    .mapToDouble(d -> Math.pow(d - recentDwellMean, 2))
                    .sum();
            recentDwellVariance = sumSquaredDiffs / dwellTimes.size();
        }

        // Calculate recent flight time statistics
        if (!flightTimes.isEmpty()) {
            recentFlightMean = flightTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double sumSquaredDiffs = flightTimes.stream()
                    .mapToDouble(d -> Math.pow(d - recentFlightMean, 2))
                    .sum();
            recentFlightVariance = sumSquaredDiffs / flightTimes.size();
        }

        // Calculate backspace rate (approximate using keyCode patterns)
        long backspaceCount = eventBuffer.stream()
                .filter(e -> e.isKeyDown && (e.keyCode == 8 || e.keyCode == 67)) // Backspace/Delete
                .count();
        recentBackspaceRate = eventBuffer.isEmpty() ? 0.0 : (double) backspaceCount / eventBuffer.size();
    }

    @Override
    public AgentResult getResult() {
        if (!isActive) {
            return new AgentResult(0.0, Arrays.asList("agent not active"), System.currentTimeMillis());
        }

        if (isInWarmup || totalKeystrokes < 5) {
            return new AgentResult(0.0, Arrays.asList("insufficient data for analysis"), System.currentTimeMillis());
        }

        // Calculate anomaly score based on deviations from baseline
        double score = calculateAnomalyScore();
        List<String> explanations = generateExplanations(score);

        return new AgentResult(score, explanations, System.currentTimeMillis());
    }

    private double calculateAnomalyScore() {
        double totalScore = 0.0;
        int components = 0;

        // Dwell time anomaly (weight: 0.3)
        if (baselineDwellVariance > 0 && !dwellTimes.isEmpty()) {
            double dwellZScore = Math.abs(recentDwellMean - baselineDwellMean) / Math.sqrt(baselineDwellVariance);
            totalScore += 0.3 * Math.min(1.0, dwellZScore / 3.0); // Normalize to [0,1]
            components++;
        }

        // Flight time anomaly (weight: 0.3)
        if (baselineFlightVariance > 0 && !flightTimes.isEmpty()) {
            double flightZScore = Math.abs(recentFlightMean - baselineFlightMean) / Math.sqrt(baselineFlightVariance);
            totalScore += 0.3 * Math.min(1.0, flightZScore / 3.0); // Normalize to [0,1]
            components++;
        }

        // Backspace rate anomaly (weight: 0.2)
        if (recentBackspaceRate > baselineBackspaceRate * 2) {
            totalScore += 0.2 * Math.min(1.0, recentBackspaceRate / 0.3); // Cap at 30% backspace rate
            components++;
        }

        // Paste detection (weight: 0.2)
        if (recentPasteDetected) {
            totalScore += 0.2;
            components++;
        }

        // Return average if we have components, otherwise neutral
        return components > 0 ? totalScore : 0.0;
    }

    private List<String> generateExplanations(double score) {
        List<String> explanations = new ArrayList<>();

        if (score < 0.3) {
            explanations.add("normal typing rhythm");
        } else if (score < 0.6) {
            explanations.add("moderate typing anomalies detected");

            // Add specific details
            if (baselineDwellVariance > 0
                    && Math.abs(recentDwellMean - baselineDwellMean) > Math.sqrt(baselineDwellVariance)) {
                explanations.add("irregular key hold times");
            }
            if (baselineFlightVariance > 0
                    && Math.abs(recentFlightMean - baselineFlightMean) > Math.sqrt(baselineFlightVariance)) {
                explanations.add("unusual inter-key timing");
            }
            if (recentBackspaceRate > baselineBackspaceRate * 1.5) {
                explanations.add("elevated correction rate");
            }
        } else {
            explanations.add("significant typing behavior anomalies");

            if (recentPasteDetected) {
                explanations.add("rapid input detected");
            }
            if (recentBackspaceRate > 0.2) {
                explanations.add("high error rate");
            }
            if (baselineDwellVariance > 0
                    && Math.abs(recentDwellMean - baselineDwellMean) > 2 * Math.sqrt(baselineDwellVariance)) {
                explanations.add("highly irregular key timing");
            }
        }

        return explanations;
    }

    @Override
    public void resetBaseline() {
        baselineDwellMean = 0.0;
        baselineDwellVariance = 0.0;
        baselineFlightMean = 0.0;
        baselineFlightVariance = 0.0;
        baselineBackspaceRate = 0.0;
        totalKeystrokes = 0;
        isInWarmup = true;
        eventBuffer.clear();
        dwellTimes.clear();
        flightTimes.clear();
        System.out.println(AGENT_NAME + " baseline reset");
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    // Utility method for testing/simulation
    public void simulateTyping(String pattern) {
        Random random = new Random();
        long baseTime = System.currentTimeMillis();

        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            int keyCode = Character.toUpperCase(c);

            // Simulate key down
            onKeyEvent(true, keyCode, 0.5f + random.nextFloat() * 0.5f);

            // Wait for dwell time (50-150ms)
            try {
                Thread.sleep(50 + random.nextInt(100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // Simulate key up
            onKeyEvent(false, keyCode, 0.0f);

            // Wait for flight time (10-200ms)
            if (i < pattern.length() - 1) {
                try {
                    Thread.sleep(10 + random.nextInt(190));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    // --- Persistence: export/import baseline state ---
    public static class State {
        public double baselineDwellMean;
        public double baselineDwellVariance;
        public double baselineFlightMean;
        public double baselineFlightVariance;
        public double baselineBackspaceRate;
        public int totalKeystrokes;
        public boolean isInWarmup;
    }

    public State getState() {
        State s = new State();
        s.baselineDwellMean = baselineDwellMean;
        s.baselineDwellVariance = baselineDwellVariance;
        s.baselineFlightMean = baselineFlightMean;
        s.baselineFlightVariance = baselineFlightVariance;
        s.baselineBackspaceRate = baselineBackspaceRate;
        s.totalKeystrokes = totalKeystrokes;
        s.isInWarmup = isInWarmup;
        return s;
    }

    public void applyState(State s) {
        if (s == null)
            return;
        baselineDwellMean = s.baselineDwellMean;
        baselineDwellVariance = s.baselineDwellVariance;
        baselineFlightMean = s.baselineFlightMean;
        baselineFlightVariance = s.baselineFlightVariance;
        baselineBackspaceRate = s.baselineBackspaceRate;
        totalKeystrokes = s.totalKeystrokes;
        isInWarmup = s.isInWarmup;
    }
}
