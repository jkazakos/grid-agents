// package actions;

// import jason.asSemantics.DefaultInternalAction;
// import jason.asSemantics.TransitionSystem;
// import jason.asSemantics.Unifier;
// import jason.asSyntax.*;

// public class SelectBestGoal extends DefaultInternalAction {

//     @Override
//     public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
//         try {
//             double ct = ((NumberTerm) args[0]).solve();
//             double cc = ((NumberTerm) args[1]).solve();
//             double co = ((NumberTerm) args[2]).solve() * 0.8;
            
//             String best;
//             if (ct <= cc && ct <= co) best = "paint_table";
//             else if (cc <= ct && cc <= co) best = "paint_chair";
//             else best = "open_door";
            
//             System.out.println("Selected best goal: returning " + best);
//             return un.unifies(args[3], new Atom(best));
//         } catch (Exception e) {
//             e.printStackTrace();
//             return false;
//         }
//     }
// }