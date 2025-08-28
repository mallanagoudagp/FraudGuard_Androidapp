package app;

import java.util.List;

/**
 * Base interface for all FraudGuard behavioral agents.
 * Each agent monitors specific user behaviors and outputs normalized anomaly
 * scores.
 */
public interface Agent {

    /**
     * Result container for agent analysis
     */
    class AgentResult {
        public final double score; // Anomaly score [0-1], 0=normal, 1=highly anomalous
        public final List<String> explanations; // Human-readable reasons
        public final long timestamp; // Epoch milliseconds

        public AgentResult(double score, List<String> explanations, long timestamp) {
            this.score = Math.max(0.0, Math.min(1.0, score)); // Clamp to [0,1]
            this.explanations = explanations;
            this.timestamp = timestamp;
        }
    }

    /**
     * Start monitoring and data collection
     */
    void start();

    /**
     * Stop monitoring and cleanup resources
     */
    void stop();

    /**
     * Get current anomaly score and explanations
     * 
     * @return AgentResult with score [0-1] and explanations
     */
    AgentResult getResult();

    /**
     * Reset user baseline (e.g., after user change or calibration)
     */
    void resetBaseline();

    /**
     * Check if agent is active and collecting data
     */
    boolean isActive();

    /**
     * Get agent name for logging and identification
     */
    String getName();
}
