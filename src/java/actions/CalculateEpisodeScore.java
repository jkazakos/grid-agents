package actions;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.NumberTerm;
import jason.asSyntax.Term;
import jason.asSyntax.NumberTermImpl;
import env.GridEnvironment;

/*
 Internal action to calculate the score of an episode.
 Returns the final score of the episode to the agent and updates the total score in the environment.
 */
public class CalculateEpisodeScore extends DefaultInternalAction {

    private static int agent1Cost = -1;
    private static int agent2Cost = -1;

    public static void resetCosts() {
        agent1Cost = -1;
        agent2Cost = -1;
    }

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {

        String agName = ts.getAgArch().getAgName();
        int pathCost = (int) Math.round(((NumberTerm) args[0]).solve());

        if (agName.equals("agent1")) {
            agent1Cost = pathCost;
        } else {
            agent2Cost = pathCost;
        }

        // Only calculate final score when BOTH agents have reported
        if (agent1Cost != -1 && agent2Cost != -1) {
            int totalCost = agent1Cost + agent2Cost;

            // Actual cost number
            double actualCost = (double) totalCost / 100.0;

            // The rewards from completing the objectives
            double rewards = 2.8;

            // The final score of the episode
            double finalScore = rewards - actualCost;

            // Adding the score to the total score
            GridEnvironment.addEpisodeScore(finalScore);

            System.out.printf("  [SCORE] Total Step Cost = %d  |  Episode Score = %.4f%n",
                    totalCost, finalScore);

            // Reset for next episode
            agent1Cost = -1;
            agent2Cost = -1;

            return un.unifies(args[1], new NumberTermImpl(finalScore));
        }

        // First agent to report gets a 0 return (or it can be ignored)
        return un.unifies(args[1], new NumberTermImpl(0));
    }
}
