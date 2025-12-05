package actions;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.NumberTerm;
import jason.asSyntax.Term;
import jason.asSyntax.NumberTermImpl;
import env.GridEnvironment;

/*
 * Internal action to calculate the score of an episode
 ? Updates the totalScore variable in GridEnvironment
 */
public class CalculateEpisodeScore extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {

        //* The cost of the selected path
        int pathCost = (int) Math.round(((NumberTerm) args[0]).solve());

        //* Actual cost number
        double actualCost = (double) pathCost / 100.0;

        //* The rewards from completing the objectives
        double rewards = 2.8;

        //* The final score of the episode
        double finalScore = rewards - actualCost;

        //* Adding the score to the total score
        GridEnvironment.addEpisodeScore(finalScore);

        return un.unifies(args[1], new NumberTermImpl(finalScore));
    }
}