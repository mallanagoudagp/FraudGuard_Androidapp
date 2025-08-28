package app;

/**
 * Simple test to demonstrate TypingAgent functionality
 */
public class TypingAgentTest {

    public static void main(String[] args) {
        System.out.println("=== FraudGuard TypingAgent Test ===");

        // Create and start typing agent
        TypingAgent typingAgent = new TypingAgent();
        typingAgent.start();

        System.out.println("Agent started: " + typingAgent.isActive());
        System.out.println("Agent name: " + typingAgent.getName());

        // Test initial state (should have no signals)
        Agent.AgentResult initialResult = typingAgent.getResult();
        System.out.println("\nInitial state:");
        System.out.println("Score: " + initialResult.score);
        System.out.println("Explanations: " + initialResult.explanations);

        // Simulate normal typing
        System.out.println("\n=== Simulating Normal Typing ===");
        typingAgent.simulateTyping("Hello");
        System.out.println("Normal typing simulation done");

        Agent.AgentResult normalResult = typingAgent.getResult();
        System.out.println("Normal typing score: " + normalResult.score);
        System.out.println("Explanations: " + normalResult.explanations);

        // Reset and simulate anomalous typing (very fast - like paste)
        System.out.println("\n=== Simulating Anomalous Typing (Fast/Robotic) ===");
        typingAgent.resetBaseline();

        // First establish a baseline with normal typing
        // Note: keep this short so the test completes quickly
        System.out.print("Establishing baseline: ");
        for (int i = 0; i < 20; i++) { // reduced from 150 to 20
            typingAgent.simulateTyping("baseline "); // shorter pattern to reduce time
            System.out.print(".");
            try {
                Thread.sleep(5); // tiny delay between typing sessions
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println(" done");

        // Now simulate very fast typing (anomalous)
        for (int i = 0; i < 10; i++) {
            simulateRapidTyping(typingAgent, "PASTE_LIKE_INPUT");
        }

        Agent.AgentResult anomalousResult = typingAgent.getResult();
        System.out.println("Anomalous typing score: " + anomalousResult.score);
        System.out.println("Explanations: " + anomalousResult.explanations);

        // Test fusion engine
        System.out.println("\n=== Testing Fusion Engine ===");
        FusionEngine fusionEngine = new FusionEngine();

        // Test with all three agents
        FusionEngine.FusionResult fusionResult = fusionEngine.fuseScores(
                0.3, // touch_score (low anomaly)
                anomalousResult.score, // typing_score (from our test)
                0.7 // usage_score (high anomaly)
        );

        System.out.println("Fusion result:");
        System.out.println("Final score: " + fusionResult.finalScore);
        System.out.println("Risk level: " + fusionResult.riskLevel);
        System.out.println("Explanations: " + fusionResult.explanations);
        System.out.println("\nJSON representation:");
        System.out.println(fusionEngine.toJson(fusionResult));

        // Test logging
        System.out.println("\n=== Testing Logger ===");
        Logger logger = new Logger("typing_agent_test.log");
        logger.logAgentResult("TypingAgent", anomalousResult);
        logger.logFusionResult(fusionResult, 0.3, anomalousResult.score, 0.7);
        logger.logResponseAction(fusionResult.riskLevel, "BIOMETRIC_PROMPT", "User verification requested");

        // Stop agent
        typingAgent.stop();
        System.out.println("\nAgent stopped: " + !typingAgent.isActive());
        System.out.println("Test completed successfully!");
    }

    /**
     * Simulate very rapid typing (like paste or bot behavior)
     */
    private static void simulateRapidTyping(TypingAgent agent, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int keyCode = Character.toUpperCase(c);

            // Very fast key events (simulate paste-like behavior)
            agent.onKeyEvent(true, keyCode, 0.5f);
            try {
                Thread.sleep(1); // Very short dwell time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            agent.onKeyEvent(false, keyCode, 0.0f);

            // Very short flight time
            if (i < text.length() - 1) {
                try {
                    Thread.sleep(2); // Very short flight time
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
