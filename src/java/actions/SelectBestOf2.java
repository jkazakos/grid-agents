// package actions;

// import jason.asSemantics.DefaultInternalAction;
// import jason.asSemantics.TransitionSystem;
// import jason.asSemantics.Unifier;
// import jason.asSyntax.*;

// public class SelectBestOf2 extends DefaultInternalAction {
//     @Override
//     public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
//         double c1 = ((NumberTerm) args[0]).solve();
//         double c2 = ((NumberTerm) args[1]).solve();
        
//         String best = c1 <= c2 ? args[2].toString() : args[3].toString();
//         return un.unifies(args[4], new Atom(best));
//     }
// }