package actions;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.*;

public class DirsToLocs extends DefaultInternalAction {
    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        // args[0]: Start X (Number)
        // args[1]: Start Y (Number)
        // args[2]: List of directions ["up", "left"]
        // args[3]: Output variable

        int x = (int) ((NumberTerm) args[0]).solve();
        int y = (int) ((NumberTerm) args[1]).solve();
        ListTerm path = (ListTerm) args[2];
        ListTerm locs = new ListTermImpl();

        for (Term t : path) {
            String dir = t.toString().replaceAll("\"", "");
            switch (dir) {
                case "up": y--; break;
                case "down": y++; break;
                case "left": x--; break;
                case "right": x++; break;
            }
            // Use createLiteral to form loc(x,y)
            Literal loc = ASSyntax.createLiteral("loc", new NumberTermImpl(x), new NumberTermImpl(y));
            locs.add(loc);
        }
        return un.unifies(args[3], locs);
    }
}
