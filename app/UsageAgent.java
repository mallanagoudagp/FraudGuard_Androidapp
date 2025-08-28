package app;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * UsageAgent monitors app usage patterns (launches, switches, session lengths)
 * to detect anomalies in user behavior. It is privacy-first (no content) and
 * outputs a normalized anomaly score [0-1] with human-readable explanations.
 */
public class UsageAgent implements Agent {

    private static final String AGENT_NAME = "UsageAgent";

    // Tuning knobs
    private static final int WARMUP_SESSIONS = 20; // sessions to build baseline
    private static final long RATE_WINDOW_MS = 120_000; // window for rate calcs (2 minutes)
    private static final double EWMA_ALPHA = 0.1; // exponential smoothing

    // Event types handled by the agent
    public enum EventType {
        APP_OPENED,
        APP_CLOSED,
        APP_SWITCH,
        SCREEN_ON,
        SCREEN_OFF,
        UNLOCK
    }

    // Internal structure to track sessions
    private static class Session {
        final String app; // app identifier (optionally hashed)
        final long start;
        long end; // set on close

        Session(String app, long start) {
            this.app = app;
            this.start = start;
        }

        long duration() {
            long e = end > 0 ? end : System.currentTimeMillis();
            return Math.max(0, e - start);
        }
    }

    // State
    private boolean isActive = false;
    private boolean inWarmup = true;

    private final Deque<Long> launchTimestamps = new ArrayDeque<>(); // for launch rate
    private final Deque<Long> switchTimestamps = new ArrayDeque<>(); // for switch rate
    private final Deque<Long> sessionDurations = new ArrayDeque<>(); // recent durations (ms)

    private final Map<String, Integer> recentAppCounts = new HashMap<>(); // for simple app mix
    private final Set<String> knownApps = new HashSet<>();

    private Session currentSession = null;
    private int totalSessions = 0;

    // Baselines (EWMA)
    private double baselineLaunchRatePerMin = 0.0;
    private double baselineSwitchRatePerMin = 0.0;
    private double baselineAvgSessionMs = 0.0;
    private double baselineSessionVar = 0.0;

    // Recent metrics
    private double recentLaunchRatePerMin = 0.0;
    private double recentSwitchRatePerMin = 0.0;
    private double recentAvgSessionMs = 0.0;
    private double recentSessionVar = 0.0;
    private boolean recentNewAppUsed = false;

    // Privacy option: hash package names (can be made configurable later)
    private final boolean hashAppIds = false;

    @Override
    public void start() {
        isActive = true;
        System.out.println(AGENT_NAME + " started monitoring");
    }

    @Override
    public void stop() {
        isActive = false;
        launchTimestamps.clear();
        switchTimestamps.clear();
        sessionDurations.clear();
        recentAppCounts.clear();
        currentSession = null;
        System.out.println(AGENT_NAME + " stopped monitoring");
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public void resetBaseline() {
        baselineLaunchRatePerMin = 0.0;
        baselineSwitchRatePerMin = 0.0;
        baselineAvgSessionMs = 0.0;
        baselineSessionVar = 0.0;
        totalSessions = 0;
        inWarmup = true;
        launchTimestamps.clear();
        switchTimestamps.clear();
        sessionDurations.clear();
        recentAppCounts.clear();
        knownApps.clear();
        currentSession = null;
        System.out.println(AGENT_NAME + " baseline reset");
    }

    /**
     * App opened event.
     */
    public void onAppOpened(String appId) {
        if (!isActive)
            return;
        String id = normalizeAppId(appId);
        long now = System.currentTimeMillis();
        launchTimestamps.addLast(now);
        pruneOld(launchTimestamps, now, RATE_WINDOW_MS);
        openSession(id, now);
        markAppSeen(id);
        recomputeRecents(now);
    }

    /**
     * App closed event.
     */
    public void onAppClosed(String appId) {
        if (!isActive)
            return;
        String id = normalizeAppId(appId);
        long now = System.currentTimeMillis();
        if (currentSession != null && Objects.equals(currentSession.app, id)) {
            closeCurrentSession(now);
        }
        recomputeRecents(now);
    }

    /**
     * App switch event.
     */
    public void onAppSwitch(String fromApp, String toApp) {
        if (!isActive)
            return;
        long now = System.currentTimeMillis();
        switchTimestamps.addLast(now);
        pruneOld(switchTimestamps, now, RATE_WINDOW_MS);
        // Close previous session if matches
        String fromId = normalizeAppId(fromApp);
        if (currentSession != null && Objects.equals(currentSession.app, fromId)) {
            closeCurrentSession(now);
        }
        // Open new session
        String toId = normalizeAppId(toApp);
        openSession(toId, now);
        markAppSeen(toId);
        recomputeRecents(now);
    }

    public void onScreenOn() {
        // no-op for MVP
    }

    public void onScreenOff() {
        if (!isActive)
            return;
        long now = System.currentTimeMillis();
        if (currentSession != null) {
            closeCurrentSession(now);
            recomputeRecents(now);
        }
    }

    public void onUnlock() {
        // could be used to reset burst counters
    }

    /** Convenience single-API add similar to TouchAgent.add */
    public void add(EventType type, String a, String b) {
        switch (type) {
            case APP_OPENED:
                onAppOpened(a);
                break;
            case APP_CLOSED:
                onAppClosed(a);
                break;
            case APP_SWITCH:
                onAppSwitch(a, b);
                break;
            case SCREEN_ON:
                onScreenOn();
                break;
            case SCREEN_OFF:
                onScreenOff();
                break;
            case UNLOCK:
                onUnlock();
                break;
        }
    }

    private void openSession(String app, long now) {
        currentSession = new Session(app, now);
    }

    private void closeCurrentSession(long now) {
        currentSession.end = now;
        long dur = currentSession.duration();
        sessionDurations.addLast(dur);
        while (sessionDurations.size() > 100) {
            sessionDurations.removeFirst();
        }
        recentAppCounts.merge(currentSession.app, 1, Integer::sum);
        totalSessions++;
        if (inWarmup && totalSessions >= WARMUP_SESSIONS) {
            inWarmup = false;
            System.out.println(AGENT_NAME + " completed warmup phase");
        }
        // update baseline only when not warming up
        if (!inWarmup) {
            updateBaselines(dur);
        }
        currentSession = null;
    }

    private void updateBaselines(long sessionMs) {
        // Launch rate baseline
        baselineLaunchRatePerMin = ewma(baselineLaunchRatePerMin, recentLaunchRatePerMin);
        // Switch rate baseline
        baselineSwitchRatePerMin = ewma(baselineSwitchRatePerMin, recentSwitchRatePerMin);
        // Session avg/var baseline via EWMA
        baselineAvgSessionMs = ewma(baselineAvgSessionMs, sessionMs);
        double variance = Math.pow(sessionMs - baselineAvgSessionMs, 2);
        baselineSessionVar = ewma(baselineSessionVar, variance);
    }

    private double ewma(double baseline, double value) {
        return EWMA_ALPHA * value + (1 - EWMA_ALPHA) * baseline;
    }

    private void recomputeRecents(long now) {
        // Rates per minute
        pruneOld(launchTimestamps, now, RATE_WINDOW_MS);
        pruneOld(switchTimestamps, now, RATE_WINDOW_MS);
        double minutes = RATE_WINDOW_MS / 60000.0;
        recentLaunchRatePerMin = launchTimestamps.size() / minutes;
        recentSwitchRatePerMin = switchTimestamps.size() / minutes;

        // Session stats
        if (!sessionDurations.isEmpty()) {
            double sum = 0.0;
            for (Long d : sessionDurations)
                sum += d;
            recentAvgSessionMs = sum / sessionDurations.size();
            double varSum = 0.0;
            for (Long d : sessionDurations)
                varSum += Math.pow(d - recentAvgSessionMs, 2);
            recentSessionVar = varSum / sessionDurations.size();
        }
    }

    private void pruneOld(Deque<Long> q, long now, long windowMs) {
        while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
            q.removeFirst();
        }
    }

    private void markAppSeen(String app) {
        if (!knownApps.contains(app)) {
            // mark only as recent new app if not in warmup (new app during baseline
            // learning is normal)
            recentNewAppUsed = !inWarmup;
            knownApps.add(app);
        } else {
            recentNewAppUsed = false;
        }
    }

    private String normalizeAppId(String appId) {
        if (!hashAppIds)
            return appId == null ? "" : appId;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((appId == null ? "" : appId).getBytes());
            // return first 8 bytes as hex
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++)
                sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return appId == null ? "" : appId;
        }
    }

    @Override
    public AgentResult getResult() {
        if (!isActive) {
            return new AgentResult(0.0, Arrays.asList("agent not active"), System.currentTimeMillis());
        }
        if (inWarmup || totalSessions < 2) {
            return new AgentResult(0.0, Arrays.asList("insufficient data for analysis"), System.currentTimeMillis());
        }
        double score = calculateAnomalyScore();
        List<String> explanations = generateExplanations(score);
        return new AgentResult(score, explanations, System.currentTimeMillis());
    }

    // --- Persistence: export/import baseline state ---
    public static class State {
        public double baselineLaunchRatePerMin;
        public double baselineSwitchRatePerMin;
        public double baselineAvgSessionMs;
        public double baselineSessionVar;
        public int totalSessions;
        public boolean inWarmup;
    }

    public State getState() {
        State s = new State();
        s.baselineLaunchRatePerMin = baselineLaunchRatePerMin;
        s.baselineSwitchRatePerMin = baselineSwitchRatePerMin;
        s.baselineAvgSessionMs = baselineAvgSessionMs;
        s.baselineSessionVar = baselineSessionVar;
        s.totalSessions = totalSessions;
        s.inWarmup = inWarmup;
        return s;
    }

    public void applyState(State s) {
        if (s == null)
            return;
        baselineLaunchRatePerMin = s.baselineLaunchRatePerMin;
        baselineSwitchRatePerMin = s.baselineSwitchRatePerMin;
        baselineAvgSessionMs = s.baselineAvgSessionMs;
        baselineSessionVar = s.baselineSessionVar;
        totalSessions = s.totalSessions;
        inWarmup = s.inWarmup;
    }

    private double calculateAnomalyScore() {
        double total = 0.0;
        double weightSum = 0.0;

        // Component 1: Launch rate anomaly (0.3)
        double wLaunch = 0.3;
        double cLaunch = 0.0;
        if (baselineLaunchRatePerMin > 0) {
            double z = Math.abs(recentLaunchRatePerMin - baselineLaunchRatePerMin) /
                    Math.max(1e-6, Math.sqrt(baselineLaunchRatePerMin)); // Poisson ~ sqrt(mean)
            cLaunch = Math.min(1.0, z / 3.0);
            total += wLaunch * cLaunch;
            weightSum += wLaunch;
        }

        // Component 2: Switch rate anomaly (0.3)
        double wSwitch = 0.3;
        double cSwitch = 0.0;
        if (baselineSwitchRatePerMin > 0) {
            double z = Math.abs(recentSwitchRatePerMin - baselineSwitchRatePerMin) /
                    Math.max(1e-6, Math.sqrt(baselineSwitchRatePerMin));
            cSwitch = Math.min(1.0, z / 3.0);
            total += wSwitch * cSwitch;
            weightSum += wSwitch;
        }

        // Component 3: Session duration anomaly (0.3)
        double wDur = 0.3;
        double cDur = 0.0;
        if (baselineSessionVar > 0) {
            double z = Math.abs(recentAvgSessionMs - baselineAvgSessionMs) / Math.sqrt(baselineSessionVar);
            cDur = Math.min(1.0, z / 3.0);
            total += wDur * cDur;
            weightSum += wDur;
        }

        // Component 4: New/rare app flag (0.1)
        double wNew = 0.1;
        double cNew = recentNewAppUsed ? 1.0 : 0.0;
        total += wNew * cNew;
        weightSum += wNew;

        return weightSum > 0 ? Math.min(1.0, total / weightSum) : 0.0;
    }

    private List<String> generateExplanations(double score) {
        List<String> out = new ArrayList<>();
        if (score < 0.3) {
            out.add("normal usage behavior");
        } else if (score < 0.6) {
            out.add("moderate usage anomalies detected");
            if (baselineLaunchRatePerMin > 0 && Math.abs(recentLaunchRatePerMin - baselineLaunchRatePerMin) > Math
                    .sqrt(Math.max(1e-6, baselineLaunchRatePerMin))) {
                out.add("unusual app launch rate");
            }
            if (baselineSwitchRatePerMin > 0 && Math.abs(recentSwitchRatePerMin - baselineSwitchRatePerMin) > Math
                    .sqrt(Math.max(1e-6, baselineSwitchRatePerMin))) {
                out.add("frequent app switching");
            }
            if (baselineSessionVar > 0
                    && Math.abs(recentAvgSessionMs - baselineAvgSessionMs) > Math.sqrt(baselineSessionVar)) {
                out.add("atypical session durations");
            }
            if (recentNewAppUsed)
                out.add("previously unseen app used");
        } else {
            out.add("significant usage behavior anomalies");
            if (recentSwitchRatePerMin > baselineSwitchRatePerMin * 2) {
                out.add("rapid task switching bursts");
            }
            if (recentAvgSessionMs < baselineAvgSessionMs * 0.3) {
                out.add("very short sessions");
            }
            if (recentNewAppUsed)
                out.add("new/unrecognized app detected");
        }
        return out;
    }

    // ---------- Simulation helpers for demos ----------
    public void simulateAppSession(String appId, long durationMs) {
        onAppOpened(appId);
        try {
            Thread.sleep(Math.max(1, durationMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        onAppClosed(appId);
    }

    public void simulateNormalUsage() {
        Random rnd = new Random();
        String[] apps = new String[] { "com.chat", "com.mail", "com.browser" };
        for (int i = 0; i < 15; i++) {
            String app = apps[rnd.nextInt(apps.length)];
            simulateAppSession(app, 150 + rnd.nextInt(300));
            try {
                Thread.sleep(20 + rnd.nextInt(30));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void simulateSuspiciousUsage() {
        // many short sessions and frequent switches
        for (int i = 0; i < 20; i++) {
            String app = (i % 2 == 0) ? "com.browser" : "com.social";
            onAppSwitch(i % 2 == 0 ? "com.mail" : "com.browser", app);
            try {
                Thread.sleep(10);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            onAppClosed(app);
        }
    }
}
