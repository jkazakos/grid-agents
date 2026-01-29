package actions;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.*;
import env.GridEnvironment;
import java.util.*;

public class GetSafeTile extends DefaultInternalAction {

    static class Point {
        int x, y;
        public Point(int x, int y) { this.x = x; this.y = y; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Point point = (Point) o;
            return x == point.x && y == point.y;
        }
        @Override
        public int hashCode() { return Objects.hash(x, y); }
    }

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        // args[0]: Forbidden List [loc(1,2), loc(1,3)...]
        // args[1]: Result X
        // args[2]: Result Y

        ListTerm forbiddenTerm = (ListTerm) args[0];
        Set<Point> forbidden = new HashSet<>();
        
        for (Term t : forbiddenTerm) {
            Literal l = (Literal) t; // loc(x,y)
            int fx = (int) ((NumberTerm) l.getTerm(0)).solve();
            int fy = (int) ((NumberTerm) l.getTerm(1)).solve();
            forbidden.add(new Point(fx, fy));
        }

        GridEnvironment env = GridEnvironment.getInstance();
        String agName = ts.getAgArch().getAgName();
        var currentPos = env.getAgPos(agName);
        Point start = new Point(currentPos.x, currentPos.y);

        // BFS for nearest safe tile
        Queue<Point> queue = new LinkedList<>();
        Set<Point> visited = new HashSet<>();
        
        queue.add(start);
        visited.add(start);

        int[][] dirs = {{0,1}, {0,-1}, {1,0}, {-1,0}};

        while (!queue.isEmpty()) {
            Point p = queue.poll();

            boolean isStart = (p.x == start.x && p.y == start.y);
            
            if (!isStart) {
                if (env.isFree(p.x, p.y) && !forbidden.contains(p)) {
                    boolean xOk = un.unifies(args[1], new NumberTermImpl(p.x));
                    boolean yOk = un.unifies(args[2], new NumberTermImpl(p.y));
                    return xOk && yOk;
                }
            }

            // Expand neighbors
            for (int[] d : dirs) {
                Point neighbor = new Point(p.x + d[0], p.y + d[1]);
                if (neighbor.x >= 0 && neighbor.x < GridEnvironment.getWidth() &&
                    neighbor.y >= 0 && neighbor.y < GridEnvironment.getHeight() &&
                    !visited.contains(neighbor)) {
                        if (!GridEnvironment.isBlocked(neighbor.x, neighbor.y)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                }
            }
        }

        return false; // No escape found
    }
}
