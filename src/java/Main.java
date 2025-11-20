import jason.infra.local.RunLocalMAS;
import jason.JasonException;

public class Main {
    public static void main(String[] args) throws JasonException {
        String[] jasonArgs = {"intelligent_agent.mas2j"};
        RunLocalMAS.main(jasonArgs);
    }
}