package app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FusionEngine combines anomaly signals from multiple behavioral agents
 * to compute a unified risk score and determine appropriate response level.
 */
public class FusionEngine {

    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }

    public static class FusionResult {
        public final double finalScore;
        public final RiskLevel riskLevel;
        public final List<String> explanations;
        public final long timestamp;

        public FusionResult(double finalScore, RiskLevel riskLevel, List<String> explanations, long timestamp) {
            this.finalScore = finalScore;
            this.riskLevel = riskLevel;
            this.explanations = explanations;
            this.timestamp = timestamp;
        }
    }

    // Default weights for agent fusion
    private double touchWeight = 0.5;
    private double typingWeight = 0.3;
    private double usageWeight = 0.2;

    // Risk level thresholds
    private static final double LOW_THRESHOLD = 0.40;
    private static final double HIGH_THRESHOLD = 0.70;

    public FusionEngine() {
        // Use default weights
    }

    public FusionEngine(double touchWeight, double typingWeight, double usageWeight) {
        this.touchWeight = touchWeight;
        this.typingWeight = typingWeight;
        this.usageWeight = usageWeight;
        normalizeWeights();
    }

    /**
     * Fuse scores from multiple agents into a unified risk assessment
     * 
     * @param touchScore  Touch agent score [0-1], null if not available
     * @param typingScore Typing agent score [0-1], null if not available
     * @param usageScore  Usage agent score [0-1], null if not available
     * @return FusionResult with final score, risk level, and explanations
     */
    public FusionResult fuseScores(Double touchScore, Double typingScore, Double usageScore) {
        List<String> explanations = new ArrayList<>();
        long timestamp = System.currentTimeMillis();

        // Check if no signals are available
        if (touchScore == null && typingScore == null && usageScore == null) {
            return new FusionResult(0.0, RiskLevel.LOW, Arrays.asList("no signals available"), timestamp);
        }

        // Calculate weighted average with dynamic weight adjustment
        double finalScore = 0.0;
        double totalWeight = 0.0;

        if (touchScore != null) {
            finalScore += touchWeight * touchScore * 10;
            totalWeight += touchWeight;
            addScoreExplanation(explanations, "touch", touchScore);
        }

        if (typingScore != null) {
            finalScore += typingWeight * typingScore * 10;
            totalWeight += typingWeight;
            addScoreExplanation(explanations, "typing", typingScore);
        }

        if (usageScore != null) {
            finalScore += usageWeight * usageScore * 10;
            totalWeight += usageWeight;
            addScoreExplanation(explanations, "usage", usageScore);
        }

        // Normalize by available weights
        if (totalWeight > 0) {
            finalScore = finalScore / totalWeight;
        }

        // Add fusion strategy explanation
        explanations.add(getFusionStrategyExplanation(touchScore, typingScore, usageScore));

        // Determine risk level
        RiskLevel riskLevel = determineRiskLevel(finalScore);

        // Add risk-level specific explanations
        addRiskLevelExplanation(explanations, riskLevel, finalScore);

        return new FusionResult(finalScore, riskLevel, explanations, timestamp);
    }

    private void addScoreExplanation(List<String> explanations, String agentType, double score) {
        if (score > 0.7) {
            explanations.add(agentType + " high anomaly");
        } else if (score > 0.4) {
            explanations.add(agentType + " moderate anomaly");
        } else {
            explanations.add(agentType + " normal");
        }
    }

    private String getFusionStrategyExplanation(Double touchScore, Double typingScore, Double usageScore) {
        int availableAgents = 0;
        StringBuilder strategy = new StringBuilder();

        if (touchScore != null) {
            availableAgents++;
            strategy.append("touch");
        }
        if (typingScore != null) {
            if (availableAgents > 0)
                strategy.append("+");
            availableAgents++;
            strategy.append("typing");
        }
        if (usageScore != null) {
            if (availableAgents > 0)
                strategy.append("+");
            availableAgents++;
            strategy.append("usage");
        }

        if (availableAgents == 1) {
            return strategy + "-only fusion";
        } else if (availableAgents == 2) {
            return strategy + " dual fusion";
        } else {
            return strategy + " triple fusion";
        }
    }

    private RiskLevel determineRiskLevel(double score) {
        if (score <= LOW_THRESHOLD) {
            return RiskLevel.LOW;
        } else if (score <= HIGH_THRESHOLD) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.HIGH;
        }
    }

    private void addRiskLevelExplanation(List<String> explanations, RiskLevel level, double score) {
        switch (level) {
            case LOW:
                explanations.add("risk score within normal range");
                break;
            case MEDIUM:
                explanations.add("elevated risk requires verification");
                break;
            case HIGH:
                explanations.add("high risk requires immediate action");
                break;
        }
    }

    private void normalizeWeights() {
        double total = touchWeight + typingWeight + usageWeight;
        if (total > 0) {
            touchWeight = touchWeight / total;
            typingWeight = typingWeight / total;
            usageWeight = usageWeight / total;
        }
    }

    // Getters for current weights
    public double getTouchWeight() {
        return touchWeight;
    }

    public double getTypingWeight() {
        return typingWeight;
    }

    public double getUsageWeight() {
        return usageWeight;
    }

    // Update weights and renormalize
    public void updateWeights(double touchWeight, double typingWeight, double usageWeight) {
        this.touchWeight = touchWeight;
        this.typingWeight = typingWeight;
        this.usageWeight = usageWeight;
        normalizeWeights();
    }

    /**
     * Create a JSON representation of the fusion result
     */
    public String toJson(FusionResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"final_score\": ").append(String.format("%.2f", result.finalScore)).append(",\n");
        json.append("  \"risk_level\": \"").append(result.riskLevel).append("\",\n");
        json.append("  \"timestamp\": ").append(result.timestamp).append(",\n");
        json.append("  \"explanations\": [");

        for (int i = 0; i < result.explanations.size(); i++) {
            json.append("\"").append(result.explanations.get(i)).append("\"");
            if (i < result.explanations.size() - 1) {
                json.append(", ");
            }
        }

        json.append("]\n");
        json.append("}");
        return json.toString();
    }
}
