package actions;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.*;
import env.GridEnvironment;
import jason.environment.grid.Location;

public class IsBlocked extends DefaultInternalAction {
    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        // args[0]: X
        // args[1]: Y
        // args[2]: Result (true/false)

        int x = (int) ((NumberTerm) args[0]).solve();
        int y = (int) ((NumberTerm) args[1]).solve();
        String agName = ts.getAgArch().getAgName();

        GridEnvironment env = GridEnvironment.getInstance();
        if (env == null) return false;

        boolean blocked = false;

        // 1. Check bounds and static obstacles
        if (x < 0 || y < 0 || x >= GridEnvironment.getWidth() || y >= GridEnvironment.getHeight()) {
            blocked = true;
        } else if (GridEnvironment.isBlocked(x, y)) {
            blocked = true;
        } else {
            // 2. Check if another agent is there
            String otherAgName = agName.equals("agent1") ? "agent2" : "agent1";
            Location otherPos = env.getAgPos(otherAgName);
            if (otherPos != null && otherPos.x == x && otherPos.y == y) {
                blocked = true;
            }
        }

        return un.unifies(args[2], Literal.parseLiteral(String.valueOf(blocked)));
    }
}
