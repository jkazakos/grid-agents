import jason.JasonException;
import jason.infra.centralised.RunCentralisedMAS;

public class Main {
    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        try {
            RunCentralisedMAS mas = new RunCentralisedMAS();
            mas.init(new String[] { "grid_agent.mas2j" });
            mas.create();
            mas.start();
            mas.waitEnd();
        } catch (JasonException e) {
            e.printStackTrace();
        }
    }
}
