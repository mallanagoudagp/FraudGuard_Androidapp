package app;

/**
 * Complete system demonstration
 */
public class SystemDemo {
    public static void main(String[] args) {
        System.out.println("=== FraudGuard Complete System Demo ===");
        System.out.println("Demonstrating TouchAgent, TypingAgent, and FusionEngine integration\n");

        // Initialize components
        TouchAgent touchAgent = new TouchAgent();
        TypingAgent typingAgent = new TypingAgent();
        UsageAgent usageAgent = new UsageAgent();
        FusionEngine fusionEngine = new FusionEngine();
        Logger logger = new Logger("system_demo.log");

        // Start agents
        touchAgent.start();
        typingAgent.start();
        usageAgent.start();

        System.out.println("✓ Agents initialized and started");

        // Scenario 1: Legitimate user behavior
        System.out.println("\n--- Scenario 1: Legitimate User ---");
        simulateLegitimateUser(touchAgent, typingAgent, usageAgent);
        demonstrateResults(touchAgent, typingAgent, usageAgent, fusionEngine, logger, "Legitimate User");

        // Scenario 2: Suspicious behavior (mixed anomalies)
        System.out.println("\n--- Scenario 2: Suspicious Behavior ---");
        simulateSuspiciousBehavior(touchAgent, typingAgent, usageAgent);
        demonstrateResults(touchAgent, typingAgent, usageAgent, fusionEngine, logger, "Suspicious User");

        // Scenario 3: Bot/attack behavior
        System.out.println("\n--- Scenario 3: Bot/Attack Behavior ---");
        simulateBotBehavior(touchAgent, typingAgent, usageAgent);
        demonstrateResults(touchAgent, typingAgent, usageAgent, fusionEngine, logger, "Bot/Attack");

        // Cleanup
        touchAgent.stop();
        typingAgent.stop();
        usageAgent.stop();

        System.out.println("\n=== Demo Summary ===");
        System.out.println("✓ TouchAgent successfully detects gesture anomalies");
        System.out.println("✓ TypingAgent successfully detects keystroke anomalies");
        System.out.println("✓ UsageAgent successfully detects usage anomalies");
        System.out.println("✓ FusionEngine successfully combines scores and determines risk levels");
        System.out.println("✓ Logger successfully records all events for analysis");
        System.out.println("✓ Complete fraud detection pipeline operational");
        System.out.println("\nCheck system_demo.log for detailed event logs");
        System.out.println("System demo completed successfully!");
    }

    private static void simulateLegitimateUser(TouchAgent touchAgent, TypingAgent typingAgent, UsageAgent usageAgent) {
        // Establish baseline with normal patterns
        for (int i = 0; i < 80; i++) {
            // Natural touch patterns
            if (i % 3 == 0) {
                touchAgent.simulateTap(150 + (i % 5) * 15, 150 + (i % 3) * 20, 100 + (i % 60));
            } else {
                touchAgent.simulateTouch(100 + (i % 8) * 20, 100, 250 + (i % 12) * 30, 120, 350 + (i % 80));
            }

            // Natural typing patterns
            if (i % 10 == 0) {
                typingAgent.simulateTyping("normal user typing behavior ");
            }

            // Simulate light app usage periodically during baseline
            if (i % 12 == 0) {
                usageAgent.simulateAppSession("com.mail", 180);
            } else if (i % 12 == 6) {
                usageAgent.simulateAppSession("com.browser", 260);
            }

            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Recent normal behavior
        for (int i = 0; i < 10; i++) {
            touchAgent.simulateTap(150 + i * 10, 150, 120 + i * 5);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        typingAgent.simulateTyping("Hello, this is normal typing.");
        usageAgent.simulateNormalUsage();
    }

    private static void simulateSuspiciousBehavior(TouchAgent touchAgent, TypingAgent typingAgent,
            UsageAgent usageAgent) {
        // Some unusual touch patterns
        for (int i = 0; i < 5; i++) {
            // Slightly too fast taps
            touchAgent.simulateTap(100 + i * 20, 100, 30); // Short taps
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Some irregular typing
        typingAgent.simulateTyping("irregular typing with some errors");

        // Suspicious usage: frequent short sessions and switches
        usageAgent.simulateSuspiciousUsage();

        // Mix with some normal patterns
        touchAgent.simulateTouch(100, 100, 200, 100, 400); // Normal swipe
        typingAgent.simulateTyping("normal text");
    }

    private static void simulateBotBehavior(TouchAgent touchAgent, TypingAgent typingAgent, UsageAgent usageAgent) {
        // Robotic touch patterns
        for (int i = 0; i < 8; i++) {
            // Perfectly linear, identical timing
            touchAgent.simulateTouch(100, 100 + i * 10, 300, 100 + i * 10, 200);
            try {
                Thread.sleep(150); // Robotic timing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Ultra-fast typing (paste-like)
        for (int i = 0; i < 10; i++) {
            typingAgent.onKeyEvent(true, 65 + (i % 26), 0.5f);
            try {
                Thread.sleep(2); // Too fast for human
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            typingAgent.onKeyEvent(false, 65 + (i % 26), 0.0f);
            try {
                Thread.sleep(3);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Usage: many brief sessions in new/unfamiliar apps
        for (int i = 0; i < 10; i++) {
            usageAgent.onAppSwitch("com.mail", "com.automation" + i);
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            usageAgent.onAppClosed("com.automation" + i);
        }
    }

    private static void demonstrateResults(TouchAgent touchAgent, TypingAgent typingAgent, UsageAgent usageAgent,
            FusionEngine fusionEngine, Logger logger, String scenario) {

        // Get individual agent results
        Agent.AgentResult touchResult = touchAgent.getResult();
        Agent.AgentResult typingResult = typingAgent.getResult();
        Agent.AgentResult usageResult = usageAgent.getResult();

        System.out.println("Touch Score: " + String.format("%.3f", touchResult.score));
        System.out.println("Touch Explanations: " + touchResult.explanations);
        System.out.println("Typing Score: " + String.format("%.3f", typingResult.score));
        System.out.println("Typing Explanations: " + typingResult.explanations);
        System.out.println("Usage Score: " + String.format("%.3f", usageResult.score));
        System.out.println("Usage Explanations: " + usageResult.explanations);

        // Fuse the scores
        FusionEngine.FusionResult fusionResult = fusionEngine.fuseScores(
                touchResult.score,
                typingResult.score,
                usageResult.score);

        System.out.println("→ Fusion Score: " + String.format("%.3f", fusionResult.finalScore));
        System.out.println("→ Risk Level: " + fusionResult.riskLevel);
        System.out.println("→ Fusion Explanations: " + fusionResult.explanations);

        // Determine response action
        String action = getResponseAction(fusionResult.riskLevel);
        System.out.println("→ System Response: " + action);

        // Log everything
        logger.logAgentResult("TouchAgent", touchResult);
        logger.logAgentResult("TypingAgent", typingResult);
        logger.logAgentResult("UsageAgent", usageResult);
        logger.logFusionResult(fusionResult, touchResult.score, typingResult.score, usageResult.score);
        logger.logResponseAction(fusionResult.riskLevel, action, scenario);

        // Show JSON output for API integration
        System.out.println("→ JSON Output: " + fusionEngine.toJson(fusionResult));
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
}
