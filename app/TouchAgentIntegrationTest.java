package app;

/**
 * Integration test for TouchAgent with FusionEngine
 */
public class TouchAgentIntegrationTest {
    public static void main(String[] args) {
        System.out.println("=== TouchAgent Integration Test ===");

        // Create and configure agents
        TouchAgent touchAgent = new TouchAgent();
        TypingAgent typingAgent = new TypingAgent();
        FusionEngine fusionEngine = new FusionEngine();
        Logger logger = new Logger("integration_test.log");

        // Start agents
        touchAgent.start();
        typingAgent.start();

        System.out.println("Agents started successfully");

        // Simulate normal baseline establishment
        System.out.println("\n=== Establishing Baselines ===");
        establishTouchBaseline(touchAgent);
        establishTypingBaseline(typingAgent);

        // Test scenario 1: Normal behavior
        System.out.println("\n=== Test 1: Normal Behavior ===");
        simulateNormalBehavior(touchAgent, typingAgent);
        testFusion(touchAgent, typingAgent, fusionEngine, logger, "Normal");

        // Test scenario 2: Touch anomalies
        System.out.println("\n=== Test 2: Touch Anomalies ===");
        simulateTouchAnomalies(touchAgent);
        testFusion(touchAgent, typingAgent, fusionEngine, logger, "TouchAnomaly");

        // Test scenario 3: Mixed anomalies
        System.out.println("\n=== Test 3: Mixed Anomalies ===");
        simulateTypingAnomalies(typingAgent);
        testFusion(touchAgent, typingAgent, fusionEngine, logger, "MixedAnomaly");

        // Cleanup
        touchAgent.stop();
        typingAgent.stop();

        System.out.println("\nIntegration test completed successfully!");
        System.out.println("Check integration_test.log for detailed results");
    }

    private static void establishTouchBaseline(TouchAgent agent) {
        // Simulate 60 normal touch gestures
        for (int i = 0; i < 60; i++) {
            if (i % 2 == 0) {
                agent.simulateTap(100 + (i % 5) * 20, 100 + (i % 3) * 30, 120 + (i % 50));
            } else {
                agent.simulateTouch(50 + (i % 10) * 10, 50, 200 + (i % 15) * 20, 60, 300 + (i % 100));
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println("Touch baseline established");
    }

    private static void establishTypingBaseline(TypingAgent agent) {
        // Simulate normal typing patterns
        for (int i = 0; i < 120; i++) {
            agent.simulateTyping("normal typing pattern ");
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println("Typing baseline established");
    }

    private static void simulateNormalBehavior(TouchAgent touchAgent, TypingAgent typingAgent) {
        // Normal touch patterns
        for (int i = 0; i < 10; i++) {
            touchAgent.simulateTap(150 + i * 5, 150, 100 + i * 10);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Normal typing
        typingAgent.simulateTyping("This is normal typing behavior.");
    }

    private static void simulateTouchAnomalies(TouchAgent agent) {
        // Simulate robotic touch patterns
        for (int i = 0; i < 8; i++) {
            // Perfectly linear swipes
            agent.simulateTouch(100, 100, 300, 100, 200); // Identical timing and path
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Ultra-fast taps
        for (int i = 0; i < 5; i++) {
            agent.simulateTap(150 + i * 20, 150, 5); // Very short taps
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void simulateTypingAnomalies(TypingAgent agent) {
        // Simulate rapid/paste-like typing
        for (int i = 0; i < 5; i++) {
            // Very fast typing
            agent.onKeyEvent(true, 65 + i, 0.5f); // Key down
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            agent.onKeyEvent(false, 65 + i, 0.0f); // Key up immediately
            try {
                Thread.sleep(3);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void testFusion(TouchAgent touchAgent, TypingAgent typingAgent,
            FusionEngine fusionEngine, Logger logger, String scenario) {

        // Get agent results
        Agent.AgentResult touchResult = touchAgent.getResult();
        Agent.AgentResult typingResult = typingAgent.getResult();

        System.out.println(scenario + " Results:");
        System.out.println("  Touch Score: " + String.format("%.3f", touchResult.score));
        System.out.println("  Touch Explanations: " + touchResult.explanations);
        System.out.println("  Typing Score: " + String.format("%.3f", typingResult.score));
        System.out.println("  Typing Explanations: " + typingResult.explanations);

        // Fuse scores (no usage agent for this test)
        FusionEngine.FusionResult fusionResult = fusionEngine.fuseScores(
                touchResult.score,
                typingResult.score,
                null // No usage score
        );

        System.out.println("  Fusion Score: " + String.format("%.3f", fusionResult.finalScore));
        System.out.println("  Risk Level: " + fusionResult.riskLevel);
        System.out.println("  Fusion Explanations: " + fusionResult.explanations);

        // Log results
        logger.logAgentResult("TouchAgent", touchResult);
        logger.logAgentResult("TypingAgent", typingResult);
        logger.logFusionResult(fusionResult, touchResult.score, typingResult.score, null);

        // Determine expected action
        String action = getExpectedAction(fusionResult.riskLevel);
        logger.logResponseAction(fusionResult.riskLevel, action, scenario + " test scenario");

        System.out.println("  Expected Action: " + action);
        System.out.println();
    }

    private static String getExpectedAction(FusionEngine.RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW:
                return "CONTINUE_MONITORING";
            case MEDIUM:
                return "BIOMETRIC_PROMPT";
            case HIGH:
                return "ACCOUNT_LOCKOUT";
            default:
                return "UNKNOWN";
        }
    }
}
