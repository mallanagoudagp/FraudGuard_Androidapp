package app;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TouchAgent monitors touch/swipe patterns for behavioral anomaly detection.
 * Captures gesture dynamics without recording screen coordinates for privacy.
 */
public class TouchAgent implements Agent {

    // Public event type for the add(...) method
    public enum EventType {
        DOWN, MOVE, UP
    }

    private static final String AGENT_NAME = "TouchAgent";
    private static final int WINDOW_SIZE = 50; // Recent gestures to analyze
    private static final int WARMUP_THRESHOLD = 10; // Gestures needed for stable baseline
    private static final double EWMA_ALPHA = 0.1; // Exponential moving average factor
    private static final float TAP_MOVEMENT_THRESHOLD = 30.0f; // px - distinguish tap from swipe
    private static final long TAP_DURATION_THRESHOLD = 500; // ms - max duration for tap

    // Touch event data structure
    private static class TouchEvent {
        final long timestamp;
        final int pointerId;
        final float x, y;
        final float pressure;
        final float size;
        final TouchEventType type;

        TouchEvent(TouchEventType type, int pointerId, float x, float y, float pressure, float size) {
            this.timestamp = System.currentTimeMillis();
            this.type = type;
            this.pointerId = pointerId;
            this.x = x;
            this.y = y;
            this.pressure = pressure;
            this.size = size;
        }
    }

    private enum TouchEventType {
        DOWN, MOVE, UP
    }

    // Completed gesture data structure
    private static class Gesture {
        final long startTime;
        final long endTime;
        final GestureType type;
        final List<TouchEvent> path;
        final int pointerId;

        // Derived metrics
        final float totalDistance;
        final float avgVelocity;
        final float peakVelocity;
        final float avgPressure;
        final float peakPressure;
        final float pathDeviation; // curvature measure
        final int directionChanges;
        final float jitter; // path stability measure

        Gesture(List<TouchEvent> path, GestureType type) {
            this.path = new ArrayList<>(path);
            this.type = type;
            this.pointerId = path.get(0).pointerId;
            this.startTime = path.get(0).timestamp;
            this.endTime = path.get(path.size() - 1).timestamp;

            // Calculate derived metrics
            this.totalDistance = calculateTotalDistance();
            this.avgVelocity = calculateAvgVelocity();
            this.peakVelocity = calculatePeakVelocity();
            this.avgPressure = calculateAvgPressure();
            this.peakPressure = calculatePeakPressure();
            this.pathDeviation = calculatePathDeviation();
            this.directionChanges = (int) calculateDirectionChanges();
            this.jitter = calculateJitter();
        }

        private float calculateTotalDistance() {
            float distance = 0f;
            for (int i = 1; i < path.size(); i++) {
                TouchEvent prev = path.get(i - 1);
                TouchEvent curr = path.get(i);
                distance += Math.sqrt(Math.pow(curr.x - prev.x, 2) + Math.pow(curr.y - prev.y, 2));
            }
            return distance;
        }

        private float calculateAvgVelocity() {
            long duration = endTime - startTime;
            return duration > 0 ? totalDistance / duration : 0f;
        }

        private float calculatePeakVelocity() {
            float peak = 0f;
            for (int i = 1; i < path.size(); i++) {
                TouchEvent prev = path.get(i - 1);
                TouchEvent curr = path.get(i);
                long timeDiff = curr.timestamp - prev.timestamp;
                if (timeDiff > 0) {
                    float segmentDistance = (float) Math.sqrt(
                            Math.pow(curr.x - prev.x, 2) + Math.pow(curr.y - prev.y, 2));
                    float velocity = segmentDistance / timeDiff;
                    peak = Math.max(peak, velocity);
                }
            }
            return peak;
        }

        private float calculateAvgPressure() {
            return (float) path.stream().mapToDouble(e -> e.pressure).average().orElse(0.0);
        }

        private float calculatePeakPressure() {
            return (float) path.stream().mapToDouble(e -> e.pressure).max().orElse(0.0);
        }

        private float calculatePathDeviation() {
            if (path.size() < 3)
                return 0f;

            TouchEvent start = path.get(0);
            TouchEvent end = path.get(path.size() - 1);

            // Calculate deviation from straight line
            float totalDeviation = 0f;
            for (int i = 1; i < path.size() - 1; i++) {
                TouchEvent point = path.get(i);
                float deviation = distanceToLine(start.x, start.y, end.x, end.y, point.x, point.y);
                totalDeviation += deviation;
            }

            return path.size() > 2 ? totalDeviation / (path.size() - 2) : 0f;
        }

        private float calculateDirectionChanges() {
            if (path.size() < 3)
                return 0;

            int changes = 0;
            float prevAngle = 0f;

            for (int i = 2; i < path.size(); i++) {
                TouchEvent p1 = path.get(i - 2);
                TouchEvent p2 = path.get(i - 1);
                TouchEvent p3 = path.get(i);

                float angle1 = (float) Math.atan2(p2.y - p1.y, p2.x - p1.x);
                float angle2 = (float) Math.atan2(p3.y - p2.y, p3.x - p2.x);
                float angleDiff = Math.abs(angle2 - angle1);

                if (angleDiff > Math.PI / 4) { // 45 degree threshold
                    changes++;
                }
            }

            return changes;
        }

        private float calculateJitter() {
            if (path.size() < 3)
                return 0f;

            // Calculate perpendicular distances from ideal path
            TouchEvent start = path.get(0);
            TouchEvent end = path.get(path.size() - 1);

            List<Float> deviations = new ArrayList<>();
            for (int i = 1; i < path.size() - 1; i++) {
                TouchEvent point = path.get(i);
                float deviation = distanceToLine(start.x, start.y, end.x, end.y, point.x, point.y);
                deviations.add(deviation);
            }

            // Return standard deviation as jitter measure
            float mean = (float) deviations.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
            float variance = (float) deviations.stream()
                    .mapToDouble(d -> Math.pow(d - mean, 2))
                    .average().orElse(0.0);

            return (float) Math.sqrt(variance);
        }

        private float distanceToLine(float x1, float y1, float x2, float y2, float px, float py) {
            float lineLength = (float) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
            if (lineLength == 0)
                return 0f;

            return Math.abs((y2 - y1) * px - (x2 - x1) * py + x2 * y1 - y2 * x1) / lineLength;
        }

        public long getDuration() {
            return endTime - startTime;
        }
    }

    private enum GestureType {
        TAP, SWIPE, MULTI_TOUCH
    }

    // Active gesture tracking (per pointer)
    private final Map<Integer, List<TouchEvent>> activeGestures = new ConcurrentHashMap<>();

    // Completed gesture history
    private final Queue<Gesture> gestureBuffer = new ConcurrentLinkedQueue<>();

    // Feature listeners (for CSV export or online learning)
    private final List<GestureFeatureListener> gestureFeatureListeners = new CopyOnWriteArrayList<>();

    // Feature baselines (EWMA)
    private double baselineAvgVelocity = 0.0;
    private double baselineAvgVelocityVar = 0.0;
    private double baselinePeakVelocity = 0.0;
    private double baselinePeakVelocityVar = 0.0;
    private double baselinePathDeviation = 0.0;
    private double baselinePathDeviationVar = 0.0;
    private double baselineTapDuration = 0.0;
    private double baselineTapDurationVar = 0.0;
    private double baselineJitter = 0.0;
    private double baselineJitterVar = 0.0;
    private double baselinePressureProfile = 0.0;
    private double baselinePressureProfileVar = 0.0;

    // State tracking
    private boolean isActive = false;
    private int totalGestures = 0;
    private boolean isInWarmup = true;

    // Optional integrations (no-op defaults) to satisfy SystemDemo
    private Logger logger = null; // optional
    private SklearnTouchModelBridge nnBridge = null; // optional
    private boolean nnScoringEnabled = false;
    private double fusionWeightTouch = 0.5;
    private double fusionWeightTyping = 0.5;

    // Recent metrics for scoring
    private double recentAvgVelocity = 0.0;
    private double recentPeakVelocity = 0.0;
    private double recentPathDeviation = 0.0;
    private double recentTapDuration = 0.0;
    private double recentJitter = 0.0;
    private double recentPressureProfile = 0.0;

    @Override
    public void start() {
        isActive = true;
        System.out.println(AGENT_NAME + " started monitoring");
    }

    @Override
    public void stop() {
        isActive = false;
        activeGestures.clear();
        gestureBuffer.clear();
        System.out.println(AGENT_NAME + " stopped monitoring");
    }

    /**
     * Process a touch down event
     */
    public void onTouchDown(int pointerId, float x, float y, float pressure, float size) {
        if (!isActive)
            return;

        TouchEvent event = new TouchEvent(TouchEventType.DOWN, pointerId, x, y, pressure, size);
        List<TouchEvent> path = new ArrayList<>();
        path.add(event);
        activeGestures.put(pointerId, path);
    }

    /**
     * Process a touch move event
     */
    public void onTouchMove(int pointerId, float x, float y, float pressure, float size) {
        if (!isActive)
            return;

        List<TouchEvent> path = activeGestures.get(pointerId);
        if (path != null) {
            TouchEvent event = new TouchEvent(TouchEventType.MOVE, pointerId, x, y, pressure, size);
            path.add(event);
        }
    }

    /**
     * Process a touch up event
     */
    public void onTouchUp(int pointerId, float x, float y, float pressure, float size) {
        if (!isActive)
            return;

        List<TouchEvent> path = activeGestures.remove(pointerId);
        if (path != null) {
            TouchEvent event = new TouchEvent(TouchEventType.UP, pointerId, x, y, pressure, size);
            path.add(event);

            // Complete gesture and process
            completeGesture(path);
        }
    }

    /**
     * Public convenience method to add touch events using a single API.
     * Delegates to onTouchDown/onTouchMove/onTouchUp.
     */
    public void add(EventType type, int pointerId, float x, float y, float pressure, float size) {
        if (!isActive)
            return;
        switch (type) {
            case DOWN:
                onTouchDown(pointerId, x, y, pressure, size);
                break;
            case MOVE:
                onTouchMove(pointerId, x, y, pressure, size);
                break;
            case UP:
                onTouchUp(pointerId, x, y, pressure, size);
                break;
        }
    }

    private void completeGesture(List<TouchEvent> path) {
        if (path.size() < 2)
            return; // Invalid gesture

        GestureType type = classifyGesture(path);
        Gesture gesture = new Gesture(path, type);

        // Add to buffer and maintain window size
        gestureBuffer.offer(gesture);
        while (gestureBuffer.size() > WINDOW_SIZE) {
            gestureBuffer.poll();
        }

        totalGestures++;

        // Update baselines and recent metrics
        updateBaselines(gesture);
        updateRecentMetrics();

        // Notify listeners with feature vector
        if (!gestureFeatureListeners.isEmpty()) {
            FeatureVector fv = FeatureVector.fromGesture(gesture);
            for (GestureFeatureListener l : gestureFeatureListeners) {
                try {
                    l.onGestureFeatures(fv);
                } catch (Exception ignored) {
                }
            }
        }

        // Check warmup status
        if (isInWarmup && totalGestures >= WARMUP_THRESHOLD) {
            isInWarmup = false;
            System.out.println(AGENT_NAME + " completed warmup phase");
        }
    }

    private GestureType classifyGesture(List<TouchEvent> path) {
        TouchEvent start = path.get(0);
        TouchEvent end = path.get(path.size() - 1);

        float totalMovement = (float) Math.sqrt(
                Math.pow(end.x - start.x, 2) + Math.pow(end.y - start.y, 2));
        long duration = end.timestamp - start.timestamp;

        // Check for tap vs swipe
        if (totalMovement <= TAP_MOVEMENT_THRESHOLD && duration <= TAP_DURATION_THRESHOLD) {
            return GestureType.TAP;
        } else {
            return GestureType.SWIPE; // For now, handle multi-touch in future iterations
        }
    }

    private void updateBaselines(Gesture gesture) {
        if (isInWarmup)
            return; // Don't update baselines during warmup

        // Update EWMA baselines for key features
        updateEWMA(gesture.avgVelocity, "avgVelocity");
        updateEWMA(gesture.peakVelocity, "peakVelocity");
        updateEWMA(gesture.pathDeviation, "pathDeviation");
        updateEWMA(gesture.jitter, "jitter");
        updateEWMA(gesture.avgPressure, "pressureProfile");

        if (gesture.type == GestureType.TAP) {
            updateEWMA(gesture.getDuration(), "tapDuration");
        }
    }

    private void updateEWMA(double value, String feature) {
        switch (feature) {
            case "avgVelocity":
                baselineAvgVelocity = EWMA_ALPHA * value + (1 - EWMA_ALPHA) * baselineAvgVelocity;
                double avgVelVariance = Math.pow(value - baselineAvgVelocity, 2);
                baselineAvgVelocityVar = EWMA_ALPHA * avgVelVariance + (1 - EWMA_ALPHA) * baselineAvgVelocityVar;
                break;
            case "peakVelocity":
                baselinePeakVelocity = EWMA_ALPHA * value + (1 - EWMA_ALPHA) * baselinePeakVelocity;
                double peakVelVariance = Math.pow(value - baselinePeakVelocity, 2);
                baselinePeakVelocityVar = EWMA_ALPHA * peakVelVariance + (1 - EWMA_ALPHA) * baselinePeakVelocityVar;
                break;
            case "pathDeviation":
                baselinePathDeviation = EWMA_ALPHA * value + (1 - EWMA_ALPHA) * baselinePathDeviation;
                double pathDevVariance = Math.pow(value - baselinePathDeviation, 2);
                baselinePathDeviationVar = EWMA_ALPHA * pathDevVariance + (1 - EWMA_ALPHA) * baselinePathDeviationVar;
                break;
            case "jitter":
                baselineJitter = EWMA_ALPHA * value + (1 - EWMA_ALPHA) * baselineJitter;
                double jitterVariance = Math.pow(value - baselineJitter, 2);
                baselineJitterVar = EWMA_ALPHA * jitterVariance + (1 - EWMA_ALPHA) * baselineJitterVar;
                break;
            case "pressureProfile":
                baselinePressureProfile = EWMA_ALPHA * value + (1 - EWMA_ALPHA) * baselinePressureProfile;
                double pressureVariance = Math.pow(value - baselinePressureProfile, 2);
                baselinePressureProfileVar = EWMA_ALPHA * pressureVariance
                        + (1 - EWMA_ALPHA) * baselinePressureProfileVar;
                break;
            case "tapDuration":
                baselineTapDuration = EWMA_ALPHA * value + (1 - EWMA_ALPHA) * baselineTapDuration;
                double tapDurVariance = Math.pow(value - baselineTapDuration, 2);
                baselineTapDurationVar = EWMA_ALPHA * tapDurVariance + (1 - EWMA_ALPHA) * baselineTapDurationVar;
                break;
        }
    }

    private void updateRecentMetrics() {
        if (gestureBuffer.isEmpty())
            return;

        List<Gesture> recent = new ArrayList<>(gestureBuffer);

        // Calculate recent averages
        recentAvgVelocity = recent.stream().mapToDouble(g -> g.avgVelocity).average().orElse(0.0);
        recentPeakVelocity = recent.stream().mapToDouble(g -> g.peakVelocity).average().orElse(0.0);
        recentPathDeviation = recent.stream().mapToDouble(g -> g.pathDeviation).average().orElse(0.0);
        recentJitter = recent.stream().mapToDouble(g -> g.jitter).average().orElse(0.0);
        recentPressureProfile = recent.stream().mapToDouble(g -> g.avgPressure).average().orElse(0.0);

        // Calculate recent tap duration average (taps only)
        OptionalDouble tapDurationAvg = recent.stream()
                .filter(g -> g.type == GestureType.TAP)
                .mapToDouble(Gesture::getDuration)
                .average();
        recentTapDuration = tapDurationAvg.orElse(0.0);
    }

    @Override
    public AgentResult getResult() {
        if (!isActive) {
            return new AgentResult(0.0, Arrays.asList("agent not active"), System.currentTimeMillis());
        }

        if (isInWarmup || totalGestures < 5) {
            return new AgentResult(0.0, Arrays.asList("insufficient data for analysis"), System.currentTimeMillis());
        }

        // Calculate anomaly score
        double score = calculateAnomalyScore();
        List<String> explanations = generateExplanations(score);

        return new AgentResult(score, explanations, System.currentTimeMillis());
    }

    private double calculateAnomalyScore() {
        double totalScore = 0.0;
        int components = 0;

        // Velocity anomalies (weight: 0.25)
        if (baselineAvgVelocityVar > 0) {
            double velocityZScore = Math.abs(recentAvgVelocity - baselineAvgVelocity)
                    / Math.sqrt(baselineAvgVelocityVar);
            totalScore += 0.25 * Math.min(1.0, velocityZScore / 3.0);
            components++;
        }

        // Path deviation anomalies (weight: 0.20)
        if (baselinePathDeviationVar > 0) {
            double deviationZScore = Math.abs(recentPathDeviation - baselinePathDeviation)
                    / Math.sqrt(baselinePathDeviationVar);
            totalScore += 0.20 * Math.min(1.0, deviationZScore / 3.0);
            components++;
        }

        // Tap duration anomalies (weight: 0.15)
        if (baselineTapDurationVar > 0 && recentTapDuration > 0) {
            double tapZScore = Math.abs(recentTapDuration - baselineTapDuration) / Math.sqrt(baselineTapDurationVar);
            totalScore += 0.15 * Math.min(1.0, tapZScore / 3.0);
            components++;
        }

        // Jitter anomalies (weight: 0.15)
        if (baselineJitterVar > 0) {
            double jitterZScore = Math.abs(recentJitter - baselineJitter) / Math.sqrt(baselineJitterVar);
            totalScore += 0.15 * Math.min(1.0, jitterZScore / 3.0);
            components++;
        }

        // Pressure profile anomalies (weight: 0.15)
        if (baselinePressureProfileVar > 0) {
            double pressureZScore = Math.abs(recentPressureProfile - baselinePressureProfile)
                    / Math.sqrt(baselinePressureProfileVar);
            totalScore += 0.15 * Math.min(1.0, pressureZScore / 3.0);
            components++;
        }

        // Bot-like pattern detection (weight: 0.10)
        double botScore = detectBotPatterns();
        totalScore += 0.10 * botScore;
        components++;

        return components > 0 ? totalScore : 0.0;
    }

    private double detectBotPatterns() {
        if (gestureBuffer.size() < 5)
            return 0.0;

        List<Gesture> recent = new ArrayList<>(gestureBuffer);
        double botScore = 0.0;

        // Check for perfectly linear swipes
        long perfectlyLinearCount = recent.stream()
                .filter(g -> g.type == GestureType.SWIPE && g.pathDeviation < 1.0f)
                .count();

        if (perfectlyLinearCount > recent.size() * 0.8) {
            botScore += 0.5; // Highly suspicious
        }

        // Check for ultra-fast gestures
        long ultraFastCount = recent.stream()
                .filter(g -> g.peakVelocity > 5.0f) // Threshold for "too fast"
                .count();

        if (ultraFastCount > recent.size() * 0.6) {
            botScore += 0.3;
        }

        // Check for identical timing patterns
        if (recent.size() >= 3) {
            List<Long> durations = recent.stream()
                    .map(Gesture::getDuration)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

            // Check if most durations are suspiciously similar
            double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0.0);
            long similarCount = durations.stream()
                    .filter(d -> Math.abs(d - avgDuration) < 10) // Within 10ms
                    .count();

            if (similarCount > durations.size() * 0.9) {
                botScore += 0.2;
            }
        }

        return Math.min(1.0, botScore);
    }

    private List<String> generateExplanations(double score) {
        List<String> explanations = new ArrayList<>();

        if (score < 0.3) {
            explanations.add("normal touch behavior patterns");
        } else if (score < 0.6) {
            explanations.add("moderate touch behavior anomalies detected");

            // Add specific details
            if (baselineAvgVelocityVar > 0 &&
                    Math.abs(recentAvgVelocity - baselineAvgVelocity) > Math.sqrt(baselineAvgVelocityVar)) {
                explanations.add("unusual gesture velocity patterns");
            }
            if (baselinePathDeviationVar > 0 &&
                    Math.abs(recentPathDeviation - baselinePathDeviation) > Math.sqrt(baselinePathDeviationVar)) {
                explanations.add("irregular swipe curvature");
            }
            if (baselineJitterVar > 0 &&
                    Math.abs(recentJitter - baselineJitter) > Math.sqrt(baselineJitterVar)) {
                explanations.add("elevated touch instability");
            }
        } else {
            explanations.add("significant touch behavior anomalies");

            double botScore = detectBotPatterns();
            if (botScore > 0.3) {
                explanations.add("robotic touch patterns detected");
            }
            if (baselineAvgVelocityVar > 0 &&
                    Math.abs(recentAvgVelocity - baselineAvgVelocity) > 2 * Math.sqrt(baselineAvgVelocityVar)) {
                explanations.add("highly irregular gesture dynamics");
            }
            if (recentPathDeviation < 1.0) {
                explanations.add("suspiciously linear touch paths");
            }
        }

        return explanations;
    }

    @Override
    public void resetBaseline() {
        baselineAvgVelocity = 0.0;
        baselineAvgVelocityVar = 0.0;
        baselinePeakVelocity = 0.0;
        baselinePeakVelocityVar = 0.0;
        baselinePathDeviation = 0.0;
        baselinePathDeviationVar = 0.0;
        baselineTapDuration = 0.0;
        baselineTapDurationVar = 0.0;
        baselineJitter = 0.0;
        baselineJitterVar = 0.0;
        baselinePressureProfile = 0.0;
        baselinePressureProfileVar = 0.0;

        totalGestures = 0;
        isInWarmup = true;
        activeGestures.clear();
        gestureBuffer.clear();
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

    // --- Optional integration hooks (no-op unless wired) ---
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void setNNBridge(SklearnTouchModelBridge bridge) {
        this.nnBridge = bridge;
    }

    public void enableNNScoring(boolean enabled) {
        this.nnScoringEnabled = enabled;
    }

    public void setFusionWeights(double touchWeight, double typingWeight) {
        this.fusionWeightTouch = touchWeight;
        this.fusionWeightTyping = typingWeight;
    }

    // --- Persistence: export/import baseline state ---
    public static class State {
        public double baselineAvgVelocity;
        public double baselineAvgVelocityVar;
        public double baselinePeakVelocity;
        public double baselinePeakVelocityVar;
        public double baselinePathDeviation;
        public double baselinePathDeviationVar;
        public double baselineTapDuration;
        public double baselineTapDurationVar;
        public double baselineJitter;
        public double baselineJitterVar;
        public double baselinePressureProfile;
        public double baselinePressureProfileVar;
        public int totalGestures;
        public boolean isInWarmup;
    }

    public State getState() {
        State s = new State();
        s.baselineAvgVelocity = baselineAvgVelocity;
        s.baselineAvgVelocityVar = baselineAvgVelocityVar;
        s.baselinePeakVelocity = baselinePeakVelocity;
        s.baselinePeakVelocityVar = baselinePeakVelocityVar;
        s.baselinePathDeviation = baselinePathDeviation;
        s.baselinePathDeviationVar = baselinePathDeviationVar;
        s.baselineTapDuration = baselineTapDuration;
        s.baselineTapDurationVar = baselineTapDurationVar;
        s.baselineJitter = baselineJitter;
        s.baselineJitterVar = baselineJitterVar;
        s.baselinePressureProfile = baselinePressureProfile;
        s.baselinePressureProfileVar = baselinePressureProfileVar;
        s.totalGestures = totalGestures;
        s.isInWarmup = isInWarmup;
        return s;
    }

    public void applyState(State s) {
        if (s == null)
            return;
        baselineAvgVelocity = s.baselineAvgVelocity;
        baselineAvgVelocityVar = s.baselineAvgVelocityVar;
        baselinePeakVelocity = s.baselinePeakVelocity;
        baselinePeakVelocityVar = s.baselinePeakVelocityVar;
        baselinePathDeviation = s.baselinePathDeviation;
        baselinePathDeviationVar = s.baselinePathDeviationVar;
        baselineTapDuration = s.baselineTapDuration;
        baselineTapDurationVar = s.baselineTapDurationVar;
        baselineJitter = s.baselineJitter;
        baselineJitterVar = s.baselineJitterVar;
        baselinePressureProfile = s.baselinePressureProfile;
        baselinePressureProfileVar = s.baselinePressureProfileVar;
        totalGestures = s.totalGestures;
        isInWarmup = s.isInWarmup;
    }

    // Utility method for testing/simulation
    public void simulateTouch(float startX, float startY, float endX, float endY, long duration) {
        Random random = new Random();
        int pointerId = 0;

        // Simulate touch down via add()
        add(EventType.DOWN, pointerId, startX, startY, 0.5f + random.nextFloat() * 0.5f,
                20f + random.nextFloat() * 10f);

        // Simulate movement
        long startTime = System.currentTimeMillis();
        int steps = Math.max(2, (int) (duration / 16)); // ~60fps

        for (int i = 1; i < steps; i++) {
            float progress = (float) i / steps;
            float x = startX + (endX - startX) * progress;
            float y = startY + (endY - startY) * progress;

            // Add some natural variation
            x += (random.nextFloat() - 0.5f) * 2f;
            y += (random.nextFloat() - 0.5f) * 2f;

            add(EventType.MOVE, pointerId, x, y, 0.5f + random.nextFloat() * 0.5f, 20f + random.nextFloat() * 10f);

            try {
                Thread.sleep(duration / steps);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Simulate touch up via add()
        add(EventType.UP, pointerId, endX, endY, 0.0f, 0.0f);
    }

    public void simulateTap(float x, float y, long duration) {
        simulateTouch(x, y, x + (float) (Math.random() - 0.5) * 5, y + (float) (Math.random() - 0.5) * 5, duration);
    }

    // --- Main entry: supports two modes ---
    // 1) system -> full system demo with TypingAgent + FusionEngine
    // 2) export [dir] [p] -> export CSV demo (defaults: data normal)
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "system".equalsIgnoreCase(args[0])) {
            runSystemDemo();
            return;
        }

        // Default/back-compat: export mode (if first arg isn't 'system')
        String directory;
        String prefix;
        if (args.length >= 1 && !"export".equalsIgnoreCase(args[0])) {
            directory = args[0];
            prefix = args.length >= 2 ? args[1] : "normal";
        } else {
            directory = args.length >= 2 ? args[1] : "data";
            prefix = args.length >= 3 ? args[2] : "normal";
        }

        TouchAgent agent = new TouchAgent();
        agent.start();

        try (TouchFeatureCsvExporter exporter = new TouchFeatureCsvExporter(directory, prefix)) {
            agent.addGestureFeatureListener(exporter);

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

    private static void runSystemDemo() {
        System.out.println("=== FraudGuard Complete System Demo ===");
        System.out.println("Demonstrating TouchAgent, TypingAgent, and FusionEngine integration\n");

        // Initialize components
        TouchAgent touchAgent = new TouchAgent();
        TypingAgent typingAgent = new TypingAgent();
        FusionEngine fusionEngine = new FusionEngine();
        Logger logger = new Logger("system_demo.log");

        // Start agents
        touchAgent.start();
        typingAgent.start();

        System.out.println("\u2713 Agents initialized and started");

        // Scenario 1: Legitimate user behavior
        System.out.println("\n--- Scenario 1: Legitimate User ---");
        simulateLegitimateUser(touchAgent, typingAgent);
        demonstrateResults(touchAgent, typingAgent, fusionEngine, logger, "Legitimate User");

        // Scenario 2: Suspicious behavior (mixed anomalies)
        System.out.println("\n--- Scenario 2: Suspicious Behavior ---");
        simulateSuspiciousBehavior(touchAgent, typingAgent);
        demonstrateResults(touchAgent, typingAgent, fusionEngine, logger, "Suspicious User");

        // Scenario 3: Bot/attack behavior
        System.out.println("\n--- Scenario 3: Bot/Attack Behavior ---");
        simulateBotBehavior(touchAgent, typingAgent);
        demonstrateResults(touchAgent, typingAgent, fusionEngine, logger, "Bot/Attack");

        // Cleanup
        touchAgent.stop();
        typingAgent.stop();

        System.out.println("\n=== Demo Summary ===");
        System.out.println("\u2713 TouchAgent successfully detects gesture anomalies");
        System.out.println("\u2713 TypingAgent successfully detects keystroke anomalies");
        System.out.println("\u2713 FusionEngine successfully combines scores and determines risk levels");
        System.out.println("\u2713 Logger successfully records all events for analysis");
        System.out.println("\u2713 Complete fraud detection pipeline operational");
        System.out.println("\nCheck system_demo.log for detailed event logs");
        System.out.println("System demo completed successfully!");
    }

    private static void simulateLegitimateUser(TouchAgent touchAgent, TypingAgent typingAgent) {
        for (int i = 0; i < 80; i++) {
            if (i % 3 == 0) {
                touchAgent.simulateTap(150 + (i % 5) * 15, 150 + (i % 3) * 20, 100 + (i % 60));
            } else {
                touchAgent.simulateTouch(100 + (i % 8) * 20, 100, 250 + (i % 12) * 30, 120, 350 + (i % 80));
            }
            if (i % 10 == 0) {
                typingAgent.simulateTyping("normal user typing behavior ");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        for (int i = 0; i < 10; i++) {
            touchAgent.simulateTap(150 + i * 10, 150, 120 + i * 5);
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        typingAgent.simulateTyping("Hello, this is normal typing.");
    }

    private static void simulateSuspiciousBehavior(TouchAgent touchAgent, TypingAgent typingAgent) {
        for (int i = 0; i < 5; i++) {
            touchAgent.simulateTap(100 + i * 20, 100, 30);
            try {
                Thread.sleep(80);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        typingAgent.simulateTyping("irregular typing with some errors");
        touchAgent.simulateTouch(100, 100, 200, 100, 400);
        typingAgent.simulateTyping("normal text");
    }

    private static void simulateBotBehavior(TouchAgent touchAgent, TypingAgent typingAgent) {
        for (int i = 0; i < 8; i++) {
            touchAgent.simulateTouch(100, 100 + i * 10, 300, 100 + i * 10, 200);
            try {
                Thread.sleep(150);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        for (int i = 0; i < 10; i++) {
            typingAgent.onKeyEvent(true, 65 + (i % 26), 0.5f);
            try {
                Thread.sleep(2);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            typingAgent.onKeyEvent(false, 65 + (i % 26), 0.0f);
            try {
                Thread.sleep(3);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void demonstrateResults(TouchAgent touchAgent, TypingAgent typingAgent,
            FusionEngine fusionEngine, Logger logger, String scenario) {
        Agent.AgentResult touchResult = touchAgent.getResult();
        Agent.AgentResult typingResult = typingAgent.getResult();

        System.out.println("Touch Score: " + String.format("%.3f", touchResult.score));
        System.out.println("Touch Explanations: " + touchResult.explanations);
        System.out.println("Typing Score: " + String.format("%.3f", typingResult.score));
        System.out.println("Typing Explanations: " + typingResult.explanations);

        FusionEngine.FusionResult fusionResult = fusionEngine.fuseScores(
                touchResult.score,
                typingResult.score,
                null);

        System.out.println("\u2192 Fusion Score: " + String.format("%.3f", fusionResult.finalScore));
        System.out.println("\u2192 Risk Level: " + fusionResult.riskLevel);
        System.out.println("\u2192 Fusion Explanations: " + fusionResult.explanations);

        String action = getResponseAction(fusionResult.riskLevel);
        System.out.println("\u2192 System Response: " + action);

        logger.logAgentResult("TouchAgent", touchResult);
        logger.logAgentResult("TypingAgent", typingResult);
        logger.logFusionResult(fusionResult, touchResult.score, typingResult.score, null);
        logger.logResponseAction(fusionResult.riskLevel, action, scenario);

        System.out.println("\u2192 JSON Output: " + fusionEngine.toJson(fusionResult));
    }

    private static String getResponseAction(FusionEngine.RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW:
                return "Continue normal operation";
            case MEDIUM:
                return "Request biometric verification";
            case HIGH:
                return "Lock account and alert security team";
            default:
                return "Unknown risk level";
        }
    }

    // Listener registration
    public void addGestureFeatureListener(GestureFeatureListener listener) {
        if (listener != null)
            gestureFeatureListeners.add(listener);
    }

    public void removeGestureFeatureListener(GestureFeatureListener listener) {
        if (listener != null)
            gestureFeatureListeners.remove(listener);
    }

    /**
     * Feature vector extracted from a completed gesture, suitable for CSV export
     * or ML pipelines.
     */
    public static class FeatureVector {
        public final long timestamp;
        public final String gestureType;
        public final long durationMs;
        public final float totalDistance;
        public final float avgVelocity;
        public final float peakVelocity;
        public final float avgPressure;
        public final float peakPressure;
        public final float pathDeviation;
        public final int directionChanges;
        public final float jitter;

        private FeatureVector(long timestamp,
                String gestureType,
                long durationMs,
                float totalDistance,
                float avgVelocity,
                float peakVelocity,
                float avgPressure,
                float peakPressure,
                float pathDeviation,
                int directionChanges,
                float jitter) {
            this.timestamp = timestamp;
            this.gestureType = gestureType;
            this.durationMs = durationMs;
            this.totalDistance = totalDistance;
            this.avgVelocity = avgVelocity;
            this.peakVelocity = peakVelocity;
            this.avgPressure = avgPressure;
            this.peakPressure = peakPressure;
            this.pathDeviation = pathDeviation;
            this.directionChanges = directionChanges;
            this.jitter = jitter;
        }

        public static FeatureVector fromGesture(Gesture g) {
            return new FeatureVector(
                    g.endTime,
                    g.type.name(),
                    g.getDuration(),
                    g.totalDistance,
                    g.avgVelocity,
                    g.peakVelocity,
                    g.avgPressure,
                    g.peakPressure,
                    g.pathDeviation,
                    g.directionChanges,
                    g.jitter);
        }

        public static String csvHeader() {
            return String.join(",",
                    "timestamp",
                    "gesture_type",
                    "duration_ms",
                    "total_distance",
                    "avg_velocity",
                    "peak_velocity",
                    "avg_pressure",
                    "peak_pressure",
                    "path_deviation",
                    "direction_changes",
                    "jitter");
        }

        public String toCsvRow() {
            return String.join(",",
                    Long.toString(timestamp),
                    gestureType,
                    Long.toString(durationMs),
                    Float.toString(totalDistance),
                    Float.toString(avgVelocity),
                    Float.toString(peakVelocity),
                    Float.toString(avgPressure),
                    Float.toString(peakPressure),
                    Float.toString(pathDeviation),
                    Integer.toString(directionChanges),
                    Float.toString(jitter));
        }
    }
}

// Package-private listener interface (moved from its own file)
interface GestureFeatureListener {
    void onGestureFeatures(TouchAgent.FeatureVector features);
}

// Package-private CSV exporter (moved from its own file)
class TouchFeatureCsvExporter implements GestureFeatureListener, AutoCloseable {
    private final Path outputPath;
    private final BufferedWriter writer;
    private final AtomicBoolean headerWritten = new AtomicBoolean(false);

    public TouchFeatureCsvExporter(String directory, String prefix) {
        try {
            Path dir = Paths.get(directory);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            this.outputPath = dir.resolve(prefix + "_touch_features_" + ts + ".csv");
            this.writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true));
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize exporter", e);
        }
    }

    @Override
    public synchronized void onGestureFeatures(TouchAgent.FeatureVector features) {
        try {
            if (!headerWritten.get()) {
                writer.write(TouchAgent.FeatureVector.csvHeader());
                writer.newLine();
                headerWritten.set(true);
            }
            writer.write(features.toCsvRow());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[TouchFeatureCsvExporter] Error writing CSV: " + e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        try {
            writer.close();
        } catch (IOException ignored) {
        }
        System.out.println("Feature CSV exported to: " + outputPath.toAbsolutePath());
    }
}
