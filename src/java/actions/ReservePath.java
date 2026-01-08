package actions;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.*;
import env.GridEnvironment;
import jason.environment.grid.Location;
import java.util.*;

public class ReservePath extends DefaultInternalAction {
    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        // args[0]: The path list ["down", "down", "right"...]
        // args[1]: The priority weight (Number)
        // args[2]: Result unification (true/false)

        ListTerm pathTerm = (ListTerm) args[0];
        int priority = (int) ((NumberTerm) args[1]).solve();
        String agName = ts.getAgArch().getAgName();

        // Convert the list of directions into actual Grid Coordinates
        GridEnvironment env = GridEnvironment.getInstance();
        Location currentPos = env.getAgPos(agName);
        
        if (currentPos == null) return false;

        List<Location> plannedPath = new ArrayList<>();
        int cx = currentPos.x;
        int cy = currentPos.y;

        for (Term t : pathTerm) {
            String dir = t.toString().replaceAll("\"", ""); // cleanup quotes
            switch (dir) {
                case "up": cy--; break;
                case "down": cy++; break;
                case "left": cx--; break;
                case "right": cx++; break;
            }
            plannedPath.add(new Location(cx, cy));
        }

        // Ask Environment to Reserve
        boolean success = env.reservePath(agName, priority, plannedPath);
        
        // Return result
        return un.unifies(args[2], Literal.parseLiteral(String.valueOf(success)));
    }
}
