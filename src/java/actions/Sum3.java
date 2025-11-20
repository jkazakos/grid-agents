// package actions;

// import jason.asSemantics.DefaultInternalAction;
// import jason.asSemantics.TransitionSystem;
// import jason.asSemantics.Unifier;
// import jason.asSyntax.*;

// public class Sum3 extends DefaultInternalAction {
//     @Override
//     public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
//         double c1 = ((NumberTerm) args[0]).solve();
//         double c2 = ((NumberTerm) args[1]).solve();
//         double c3 = ((NumberTerm) args[2]).solve();
//         double sum = c1 + c2 + c3;
//         return un.unifies(args[3], new NumberTermImpl(sum));
//     }
// }