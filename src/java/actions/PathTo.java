package actions;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.*;
import env.GridEnvironment;
import java.util.*;

/* 
 * Internal action that calculates the path (as a list of steps) between two points in the grid.
 * If no path exists, returns false.
 */
public class PathTo extends DefaultInternalAction {

    static class Node {
        int x, y;
        int g, f;
        Node parent;
        Node(int x, int y, int g, int f, Node p) {
            this.x = x; this.y = y; this.g = g; this.f = f; this.parent = p;
        }
    }

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        try {
            int x1 = (int) Math.round(((NumberTerm) args[0]).solve());
            int y1 = (int) Math.round(((NumberTerm) args[1]).solve());
            int x2 = (int) Math.round(((NumberTerm) args[2]).solve());
            int y2 = (int) Math.round(((NumberTerm) args[3]).solve());

            System.out.println("PathTo: calculating path from (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")");
            List<String> steps = aStarPath(x1, y1, x2, y2);
            if (steps == null) {
                System.out.println("PathTo: no path found.");
                return false;
            }

            System.out.println("PathTo: path found with " + steps.size() + " steps: " + steps);
            ListTerm list = new ListTermImpl();
            for (String s : steps) {
                list.add(new Atom(s));
            }
            System.out.println("PathTo: returning path list: " + list);
            return un.unifies(args[4], list);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("PathTo: error in path calculation.");
        return false;
    }

    private List<String> aStarPath(int sx, int sy, int tx, int ty) {
        if (GridEnvironment.isBlocked(tx, ty)) {
            System.out.println("aStarPath: target (" + tx + "," + ty + ") is blocked.");
            return null;
        }

        int W = GridEnvironment.getWidth();
        int H = GridEnvironment.getHeight();

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.f));
        boolean[][] closed = new boolean[W][H];

        Node start = new Node(sx, sy, 0, manhattan(sx, sy, tx, ty), null);
        open.add(start);

        Node end = null;
        while (!open.isEmpty()) {
            Node cur = open.poll();
            if (cur.x == tx && cur.y == ty) { end = cur; break; }
            if (closed[cur.x][cur.y]) continue;
            closed[cur.x][cur.y] = true;

            for (int i = 0; i < DIRS.length; i++) {
                int nx = cur.x + DIRS[i][0], ny = cur.y + DIRS[i][1];
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
                if (GridEnvironment.isBlocked(nx, ny)) continue;
                if (closed[nx][ny]) continue;
                int ng = cur.g + 1;
                int nf = ng + manhattan(nx, ny, tx, ty);
                Node n = new Node(nx, ny, ng, nf, cur);
                n.parent = cur;
                n.f = nf;
                open.add(n);
            }
        }

        if (end == null) return null;

        // * reconstruct steps (reverse)
        List<String> rev = new ArrayList<>();
        Node cur = end;
        while (cur.parent != null) {
            String step = dir(cur.parent.x, cur.parent.y, cur.x, cur.y);
            rev.add(step);
            cur = cur.parent;
        }
        Collections.reverse(rev);
        System.out.println("PathTo: reconstructed path: " + rev);
        return rev;
    }

    private static String dir(int x1, int y1, int x2, int y2) {
        if (x2 == x1 + 1 && y2 == y1) return "right";
        if (x2 == x1 - 1 && y2 == y1) return "left";
        if (y2 == y1 + 1 && x2 == x1) return "down";
        if (y2 == y1 - 1 && x2 == x1) return "up";
        System.out.println("dir: invalid move from (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")");
        return "right"; //! fallback; should not happen
    }

    private static int manhattan(int x1, int y1, int x2, int y2) {
        // System.out.println("manhattan: calculating distance from (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + "): " + (Math.abs(x1 - x2) + Math.abs(y1 - y2)));
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    private static final int[][] DIRS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };
}