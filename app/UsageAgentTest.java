package app;

/**
 * Minimal tests for UsageAgent behavior. Run with:
 * javac -d ..\app -cp ..\app UsageAgentTest.java
 * java -cp ..\app app.UsageAgentTest
 */
public class UsageAgentTest {
    public static void main(String[] args) {
        UsageAgent agent = new UsageAgent();
        agent.start();

        // Warmup: create baseline with normal usage
        for (int i = 0; i < 25; i++) {
            agent.simulateAppSession("com.mail", 150);
            agent.simulateAppSession("com.browser", 220);
        }

        Agent.AgentResult r1 = agent.getResult();
        System.out.println("After baseline -> score=" + String.format("%.3f", r1.score) + " exp=" + r1.explanations);

        // Inject anomaly: rapid switching & very short sessions
        for (int i = 0; i < 15; i++) {
            agent.onAppSwitch("com.mail", "com.social");
            agent.onAppClosed("com.social");
        }

        Agent.AgentResult r2 = agent.getResult();
        System.out.println("After anomaly -> score=" + String.format("%.3f", r2.score) + " exp=" + r2.explanations);

        agent.stop();
    }
}
