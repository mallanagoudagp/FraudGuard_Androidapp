package app;

/**
 * Simple TouchAgent validation test
 */
public class TouchAgentSimpleTest {
    public static void main(String[] args) {
        System.out.println("=== Simple TouchAgent Test ===");

        TouchAgent agent = new TouchAgent();
        agent.start();

        System.out.println("Agent active: " + agent.isActive());

        // Initial result
        Agent.AgentResult initial = agent.getResult();
        System.out.println("Initial score: " + initial.score);
        System.out.println("Initial explanations: " + initial.explanations);

        // Simulate a few touches
        agent.simulateTap(100, 100, 100);
        agent.simulateTouch(50, 50, 150, 100, 200);

        Agent.AgentResult afterTouch = agent.getResult();
        System.out.println("After touches score: " + afterTouch.score);
        System.out.println("After touches explanations: " + afterTouch.explanations);

        agent.stop();
        System.out.println("Test completed successfully!");
    }
}
