package app;

import java.util.Random;

/**
 * Comprehensive test suite for TouchAgent functionality
 */
public class TouchAgentTest {

    public static void main(String[] args) {
        System.out.println("=== FraudGuard TouchAgent Test Suite ===");

        // Create and start touch agent
        TouchAgent touchAgent = new TouchAgent();
        touchAgent.start();

        System.out.println("Agent started: " + touchAgent.isActive());
        System.out.println("Agent name: " + touchAgent.getName());

        // Quick smoke test of the new add() API
        System.out.println("\n=== Test 1.5: add() API Smoke Test ===");
        runQuickAddApiSmokeTest(touchAgent);

        // Test 1: Initial state (should have no signals)
        System.out.println("\n=== Test 1: Initial State ===");
        Agent.AgentResult initialResult = touchAgent.getResult();
        System.out.println("Score: " + initialResult.score);
        System.out.println("Explanations: " + initialResult.explanations);

        // Test 2: Normal touch patterns
        System.out.println("\n=== Test 2: Normal Touch Patterns ===");
        simulateNormalTouchPatterns(touchAgent);

        Agent.AgentResult normalResult = touchAgent.getResult();
        System.out.println("Normal touch score: " + normalResult.score);
        System.out.println("Explanations: " + normalResult.explanations);

        // Test 3: Establish baseline with extended normal usage
        System.out.println("\n=== Test 3: Baseline Establishment ===");
        establishBaseline(touchAgent);

        Agent.AgentResult baselineResult = touchAgent.getResult();
        System.out.println("Post-baseline score: " + baselineResult.score);
        System.out.println("Explanations: " + baselineResult.explanations);

        // Test 4: Bot-like patterns
        System.out.println("\n=== Test 4: Bot-like Touch Patterns ===");
        simulateBotPatterns(touchAgent);

        Agent.AgentResult botResult = touchAgent.getResult();
        System.out.println("Bot-like score: " + botResult.score);
        System.out.println("Explanations: " + botResult.explanations);

        // Test 5: Mixed anomalous patterns
        System.out.println("\n=== Test 5: Mixed Anomalous Patterns ===");
        simulateAnomalousPatterns(touchAgent);

        Agent.AgentResult anomalousResult = touchAgent.getResult();
        System.out.println("Anomalous score: " + anomalousResult.score);
        System.out.println("Explanations: " + anomalousResult.explanations);

        // Test 6: Fusion Engine Integration
        System.out.println("\n=== Test 6: Fusion Engine Integration ===");
        testFusionIntegration(touchAgent);

        // Test 7: Baseline reset
        System.out.println("\n=== Test 7: Baseline Reset ===");
        touchAgent.resetBaseline();
        Agent.AgentResult resetResult = touchAgent.getResult();
        System.out.println("Post-reset score: " + resetResult.score);
        System.out.println("Explanations: " + resetResult.explanations);

        // Test 8: Logger integration
        System.out.println("\n=== Test 8: Logger Integration ===");
        testLogging(touchAgent, anomalousResult);

        // Cleanup
        touchAgent.stop();
        System.out.println("\nAgent stopped: " + !touchAgent.isActive());
        System.out.println("TouchAgent test suite completed successfully!");
    }

    private static void simulateNormalTouchPatterns(TouchAgent agent) {
        Random random = new Random();

        // Simulate 20 normal gestures with natural variation
        for (int i = 0; i < 20; i++) {
            if (random.nextBoolean()) {
                // Normal tap
                float x = 100 + random.nextFloat() * 200;
                float y = 100 + random.nextFloat() * 200;
                long duration = 80 + random.nextInt(120); // 80-200ms tap duration
                agent.simulateTap(x, y, duration);
            } else {
                // Normal swipe
                float startX = 50 + random.nextFloat() * 100;
                float startY = 50 + random.nextFloat() * 100;
                float endX = startX + 50 + random.nextFloat() * 150; // 50-200px swipe
                float endY = startY + (random.nextFloat() - 0.5f) * 100; // Some vertical variation
                long duration = 200 + random.nextInt(300); // 200-500ms swipe duration
                agent.simulateTouch(startX, startY, endX, endY, duration);
            }

            // Add natural delay between gestures
            try {
                Thread.sleep(100 + random.nextInt(200));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void establishBaseline(TouchAgent agent) {
        Random random = new Random();

        // Simulate 60 gestures to establish stable baseline
        for (int i = 0; i < 60; i++) {
            // Mix of taps and swipes with consistent patterns
            if (i % 3 == 0) {
                // Consistent tap pattern
                float x = 150 + random.nextFloat() * 50;
                float y = 150 + random.nextFloat() * 50;
                agent.simulateTap(x, y, 120 + random.nextInt(40)); // Consistent tap duration
            } else {
                // Consistent swipe pattern
                float startX = 100 + random.nextFloat() * 50;
                float startY = 100 + random.nextFloat() * 50;
                float endX = startX + 100 + random.nextFloat() * 50;
                float endY = startY + (random.nextFloat() - 0.5f) * 30;
                agent.simulateTouch(startX, startY, endX, endY, 300 + random.nextInt(100));
            }

            try {
                Thread.sleep(50 + random.nextInt(100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void simulateBotPatterns(TouchAgent agent) {
        // Simulate robotic touch patterns

        // 1. Perfectly linear swipes with identical timing
        for (int i = 0; i < 10; i++) {
            simulateRobotSwipe(agent, 100, 100, 300, 100, 250); // Perfectly horizontal, identical timing
            try {
                Thread.sleep(100); // Identical intervals
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // 2. Ultra-fast taps with identical duration
        for (int i = 0; i < 8; i++) {
            agent.simulateTap(150 + i * 20, 150, 10); // Ultra-short taps
            try {
                Thread.sleep(50); // Very fast succession
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Simulate perfectly robotic swipe (for testing bot detection)
     */
    private static void simulateRobotSwipe(TouchAgent agent, float startX, float startY, float endX, float endY,
            long duration) {
        int pointerId = 0;

        // Perfect linear movement with no variation using add() API
        agent.add(TouchAgent.EventType.DOWN, pointerId, startX, startY, 0.5f, 20f);

        int steps = (int) (duration / 16); // 16ms intervals (60fps)
        for (int i = 1; i < steps; i++) {
            float progress = (float) i / steps;
            float x = startX + (endX - startX) * progress;
            float y = startY + (endY - startY) * progress;

            agent.add(TouchAgent.EventType.MOVE, pointerId, x, y, 0.5f, 20f);

            try {
                Thread.sleep(16); // Exact 60fps timing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        agent.add(TouchAgent.EventType.UP, pointerId, endX, endY, 0.0f, 0.0f);
    }

    private static void simulateAnomalousPatterns(TouchAgent agent) {
        Random random = new Random();

        // 1. Extremely jittery movements
        for (int i = 0; i < 5; i++) {
            simulateJitterySwipe(agent, 100, 100, 200, 150);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // 2. Unusually long tap durations
        for (int i = 0; i < 3; i++) {
            agent.simulateTap(100 + i * 30, 100, 800 + random.nextInt(200)); // Very long taps
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // 3. Extremely fast swipes
        for (int i = 0; i < 4; i++) {
            agent.simulateTouch(50, 50 + i * 30, 300, 50 + i * 30, 20); // Ultra-fast swipes
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void simulateJitterySwipe(TouchAgent agent, float startX, float startY, float endX, float endY) {
        Random random = new Random();
        int pointerId = 0;

        // Start gesture
        agent.add(TouchAgent.EventType.DOWN, pointerId, startX, startY, 0.5f, 20f);

        // Simulate very jittery movement
        int steps = 20;
        for (int i = 1; i < steps; i++) {
            float progress = (float) i / steps;
            float x = startX + (endX - startX) * progress;
            float y = startY + (endY - startY) * progress;

            // Add significant jitter
            x += (random.nextFloat() - 0.5f) * 30f; // High jitter
            y += (random.nextFloat() - 0.5f) * 30f;

            agent.add(TouchAgent.EventType.MOVE, pointerId, x, y, 0.5f, 20f);

            try {
                Thread.sleep(10); // Fast updates
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // End gesture
        agent.add(TouchAgent.EventType.UP, pointerId, endX, endY, 0.0f, 0.0f);
    }

    // New: Quick smoke test to demonstrate the add() API
    private static void runQuickAddApiSmokeTest(TouchAgent agent) {
        int pid = 99;
        agent.add(TouchAgent.EventType.DOWN, pid, 10f, 10f, 0.6f, 18f);
        agent.add(TouchAgent.EventType.MOVE, pid, 60f, 60f, 0.65f, 19f);
        agent.add(TouchAgent.EventType.UP, pid, 65f, 65f, 0.0f, 0f);

        Agent.AgentResult r = agent.getResult();
        System.out.println("add() smoke score: " + r.score);
        System.out.println("add() smoke explanations: " + r.explanations);
    }

    private static void testFusionIntegration(TouchAgent touchAgent) {
        // Test integration with fusion engine
        FusionEngine fusionEngine = new FusionEngine();

        // Get current touch score
        Agent.AgentResult touchResult = touchAgent.getResult();

        // Create mock typing and usage scores for fusion test
        Double mockTypingScore = 0.4; // Medium typing anomaly
        Double mockUsageScore = 0.2; // Low usage anomaly

        // Test fusion with all three agents
        FusionEngine.FusionResult fusionResult = fusionEngine.fuseScores(
                touchResult.score, // touch_score
                mockTypingScore, // typing_score
                mockUsageScore // usage_score
        );

        System.out.println("Touch Agent Score: " + touchResult.score);
        System.out.println("Mock Typing Score: " + mockTypingScore);
        System.out.println("Mock Usage Score: " + mockUsageScore);
        System.out.println("Fusion Result:");
        System.out.println("  Final Score: " + fusionResult.finalScore);
        System.out.println("  Risk Level: " + fusionResult.riskLevel);
        System.out.println("  Explanations: " + fusionResult.explanations);

        // Test JSON output
        System.out.println("JSON Output:");
        System.out.println(fusionEngine.toJson(fusionResult));
    }

    private static void testLogging(TouchAgent touchAgent, Agent.AgentResult result) {
        // Test logger integration
        Logger logger = new Logger("touch_agent_test.log");

        // Log agent result
        logger.logAgentResult("TouchAgent", result);

        // Create a fusion result for logging
        FusionEngine fusionEngine = new FusionEngine();
        FusionEngine.FusionResult fusionResult = fusionEngine.fuseScores(
                result.score, 0.3, 0.5);

        // Log fusion result
        logger.logFusionResult(fusionResult, result.score, 0.3, 0.5);

        // Log response action
        logger.logResponseAction(fusionResult.riskLevel, "BIOMETRIC_PROMPT", "TouchAgent anomaly detected");

        System.out.println("Logging test completed - check touch_agent_test.log");
    }
}
