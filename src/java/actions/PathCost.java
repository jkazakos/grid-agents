package actions;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.NumberTerm;
import jason.asSyntax.Term;
import jason.asSyntax.NumberTermImpl;
import env.GridEnvironment;
import java.util.*;

/*
 Internal action that calculates the cost of the shortest path between two points in the grid.
 Returns the cost as an integer number of steps.
 If no path exists, returns a very high cost (9999).
 */
public class PathCost extends DefaultInternalAction {

    static class Node {
        int x, y;
        int g, f;
        Node parent;

        Node(int x, int y, int g, int f, Node p) {
            this.x = x;
            this.y = y;
            this.g = g;
            this.f = f;
            this.parent = p;
        }
    }

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        try {
            int x1 = (int) Math.round(((NumberTerm) args[0]).solve());
            int y1 = (int) Math.round(((NumberTerm) args[1]).solve());
            int x2 = (int) Math.round(((NumberTerm) args[2]).solve());
            int y2 = (int) Math.round(((NumberTerm) args[3]).solve());

            Integer cost = aStarCost(x1, y1, x2, y2);
            if (cost == null) {
                return un.unifies(args[4], new NumberTermImpl(9999)); // Very high cost for no path => Don't choose this
            }
            return un.unifies(args[4], new NumberTermImpl(cost));
        } catch (Exception e) {
            System.out.println("PathCost Error: " + e.getMessage());
            return un.unifies(args[4], new NumberTermImpl(9999));
        }
    }

    private Integer aStarCost(int sx, int sy, int tx, int ty) {
        if (GridEnvironment.isBlocked(tx, ty))
            return null;

        int W = GridEnvironment.getWidth();
        int H = GridEnvironment.getHeight();

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.f));
        boolean[][] closed = new boolean[W][H];

        Node start = new Node(sx, sy, 0, manhattan(sx, sy, tx, ty), null);
        open.add(start);

        while (!open.isEmpty()) {
            Node cur = open.poll();
            if (cur.x == tx && cur.y == ty) {
                return cur.g; // Path length in steps
            }
            if (closed[cur.x][cur.y])
                continue;
            closed[cur.x][cur.y] = true;

            for (int[] d : DIRS) {
                int nx = cur.x + d[0], ny = cur.y + d[1];
                if (nx < 0 || ny < 0 || nx >= W || ny >= H)
                    continue;
                if (GridEnvironment.isBlocked(nx, ny))
                    continue;
                if (closed[nx][ny])
                    continue;
                int ng = cur.g + 1;
                int nf = ng + manhattan(nx, ny, tx, ty);
                open.add(new Node(nx, ny, ng, nf, cur));
            }
        }
        return null;
    }

    private static int manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    private static final int[][] DIRS = {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }
    };
}
