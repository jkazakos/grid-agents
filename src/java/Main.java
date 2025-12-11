import jason.JasonException;
import jason.infra.centralised.RunCentralisedMAS;

public class Main {
    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        try {
            RunCentralisedMAS mas = new RunCentralisedMAS();
            // * Initialize with the project file
            mas.init(new String[] { "grid_agent.mas2j" });
            // * Create the agents
            mas.create();
            // * Start the execution
            mas.start();
            // * Keep the application running
            mas.waitEnd();
        } catch (JasonException e) {
            e.printStackTrace();
        }
    }
}