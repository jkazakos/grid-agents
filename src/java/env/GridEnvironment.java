package env;

import jason.asSyntax.*;
import jason.environment.Environment;
import jason.environment.grid.GridWorldModel;
import jason.environment.grid.GridWorldView;
import jason.environment.grid.Location;
import java.util.*;
import java.awt.Color;
import java.awt.Graphics;

// * Class that defines a grid world environment for an agent.
public class GridEnvironment extends Environment {

    private static int W = 5, H = 5, nbAgs = 1;

    public static final int OBSTACLE = 8;
    public static final int DOOR = 16;
    public static final int TABLE = 32;
    public static final int CHAIR = 64;
    public static final int BRUSH = 128;
    public static final int COLOR = 256;
    public static final int KEY = 512;
    public static final int CODE = 1024;

    private MyModel model;
    private Location agentPos;
    private static Set<Location> obstacles;
    private Set<String> inventory;
    private Set<String> goalsDone;

    private static double totalScore = 0.0;
    private static int episodeCount = 0;

    private int currentEpisode = 1;
    private final int MAX_EPISODES = 100;
    private Random random = new Random();

    public static int getWidth() {
        return W;
    }

    public static int getHeight() {
        return H;
    }

    public static void addEpisodeScore(double score) {
        totalScore += score;
        episodeCount++;
    }

    // * Converts object name to its corresponding bitmask
    private int getMask(String obj) {
        switch (obj) {
            case "brush":
                return BRUSH;
            case "color":
                return COLOR;
            case "key":
                return KEY;
            case "code":
                return CODE;
            default:
                return 0; // ! Not a valid tool
        }
    }

    @Override
    public void init(String[] args) {
        model = new MyModel();
        new MyView(model);

        obstacles = new HashSet<>();
        obstacles.add(new Location(1, 3));
        obstacles.add(new Location(1, 4));
        obstacles.add(new Location(3, 0));
        obstacles.add(new Location(3, 1));

        for (Location loc : obstacles) {
            model.add(OBSTACLE, loc.x, loc.y);
        }

        inventory = new HashSet<>();
        goalsDone = new HashSet<>();

        startNewEpisode();

        System.out.println("Environment initialized.");
    }

    private void startNewEpisode() {
        System.out.println("--- STARTING EPISODE " + (currentEpisode) + " ---");

        inventory.clear();
        goalsDone.clear();

        agentPos = new Location(0, 4);

        try {
            model.setAgPos(0, agentPos);
        } catch (Exception e) {
        }
        model.resetObjects();

        updatePercepts("agent1");
        informAgsEnvironmentChanged();
    }

    private void triggerNextEpisode() {
        System.out.println("SUCCESS! Agent completed Episode " + currentEpisode);

        currentEpisode++;

        if (currentEpisode <= MAX_EPISODES) {
            // try { Thread.sleep(500); } catch (Exception e) {}
            startNewEpisode();
        } else {
            printFinalStatistics();
        }
    }

    private static void printFinalStatistics() {
        double averageScore = (double) totalScore / (double) episodeCount;
        double utility = averageScore;

        System.out.println("#############################################");
        System.out.println("      EXPERIMENT COMPLETE (" + episodeCount + " EPISODES)     ");
        System.out.println("      Average Expected Utility: " + utility);
        System.out.println("#############################################");
    }

    private Location getFreeLocation(Set<Location> forbidden) {
        Location loc;
        boolean isBlocked;

        do {
            isBlocked = false;
            int x = random.nextInt(W);
            int y = random.nextInt(H);
            loc = new Location(x, y);

            // * Constraint 1: Check against forbidden locations
            if (forbidden.contains(loc)) {
                isBlocked = true;
            }

            // * Constraint 2: Double check the model for static obstacles
            if (!model.isFree(loc)) {
                isBlocked = true;
            }

        } while (isBlocked);

        return loc;
    }

    static class MyView extends GridWorldView {

        public MyView(MyModel model) {
            super(model, "Grid Environment", 800);
            setVisible(true);
            repaint();
        }

        @Override
        public void draw(Graphics g, int x, int y, int object) {
            if ((object & OBSTACLE) != 0) {
                drawObstacle(g, x, y);
            }
            if ((object & DOOR) != 0) {
                drawDoor(g, x, y);
            }
            if ((object & TABLE) != 0) {
                drawTable(g, x, y);
            }
            if ((object & CHAIR) != 0) {
                drawChair(g, x, y);
            }
            if ((object & BRUSH) != 0) {
                drawBrush(g, x, y);
            }
            if ((object & KEY) != 0) {
                drawKey(g, x, y);
            }
            if ((object & CODE) != 0) {
                drawCode(g, x, y);
            }
            if ((object & COLOR) != 0) {
                drawColor(g, x, y);
            }

            super.draw(g, x, y, object);
        }

        @Override
        public void drawObstacle(Graphics g, int x, int y) {
            super.drawObstacle(g, x, y);
            g.setColor(Color.BLACK);
            g.fillRect(x * cellSizeW + 1, y * cellSizeH + 1, cellSizeW - 2, cellSizeH - 2);
        }

        public void drawTool(Graphics g, int x, int y, String string) {
            g.setColor(Color.red);
            g.drawRect(x * cellSizeW + 1, y * cellSizeH + 1, cellSizeW - 2, cellSizeH - 2);
            g.setColor(Color.red);
            g.drawString(string, x * cellSizeW + 10, y * cellSizeH + 20);
        }

        public void drawGoal(Graphics g, int x, int y, String string) {
            g.setColor(Color.YELLOW);
            g.fillRect(x * cellSizeW + 1, y * cellSizeH + 1, cellSizeW - 2, cellSizeH - 2);
            g.setColor(Color.BLACK);
            g.drawString(string, x * cellSizeW + (cellSizeW / 2) - 4, y * cellSizeH + (cellSizeH / 2) + 4);
        }

        public void drawBrush(Graphics g, int x, int y) {
            drawTool(g, x, y, "B");
        }

        public void drawKey(Graphics g, int x, int y) {
            drawTool(g, x, y, "K");
        }

        public void drawCode(Graphics g, int x, int y) {
            drawTool(g, x, y, "Cd");
        }

        public void drawColor(Graphics g, int x, int y) {
            drawTool(g, x, y, "Cl");
        }

        public void drawDoor(Graphics g, int x, int y) {
            drawGoal(g, x, y, "D");
        }

        public void drawTable(Graphics g, int x, int y) {
            drawGoal(g, x, y, "T");
        }

        public void drawChair(Graphics g, int x, int y) {
            drawGoal(g, x, y, "Ch");
        }
    }

    class MyModel extends GridWorldModel {

        public Map<String, Location> objects = new HashMap<>();

        public MyModel() {
            super(W, H, nbAgs);
        }

        public void resetObjects() {
            for (int i = 0; i < W; i++) {
                for (int j = 0; j < H; j++) {
                    remove(BRUSH, i, j);
                    remove(KEY, i, j);
                    remove(CODE, i, j);
                    remove(COLOR, i, j);
                    remove(DOOR, i, j);
                    remove(CHAIR, i, j);
                    remove(TABLE, i, j);
                }
            }

            Set<Location> forbidden = new HashSet<>();
            forbidden.addAll(obstacles);
            forbidden.add(new Location(0, 4));

            Location brushLoc = new Location(0, 0);
            Location keyLoc = new Location(0, 1);
            Location codeLoc = new Location(2, 0);
            Location colorLoc = new Location(4, 0);

            forbidden.add(brushLoc);
            forbidden.add(keyLoc);
            forbidden.add(codeLoc);
            forbidden.add(colorLoc);

            // * Reset logic map
            objects.clear();
            objects.put("brush", new Location(0, 0));
            objects.put("key", new Location(0, 1));
            objects.put("code", new Location(2, 0));
            objects.put("color", new Location(4, 0));
            objects.put("door", new Location(2, 4));
            objects.put("chair", new Location(3, 3));
            objects.put("table", new Location(4, 4));

            add(BRUSH, 0, 0);
            add(KEY, 0, 1);
            add(CODE, 2, 0);
            add(COLOR, 4, 0);

            // * Place Door
            Location doorLoc = getFreeLocation(forbidden);
            objects.put("door", doorLoc);
            add(DOOR, doorLoc.x, doorLoc.y);
            forbidden.add(doorLoc);

            // * Place Chair
            Location chairLoc = getFreeLocation(forbidden);
            objects.put("chair", chairLoc);
            add(CHAIR, chairLoc.x, chairLoc.y);
            forbidden.add(chairLoc);

            // * Place Table
            Location tableLoc = getFreeLocation(forbidden);
            objects.put("table", tableLoc);
            add(TABLE, tableLoc.x, tableLoc.y);
            forbidden.add(tableLoc);
        }

        public boolean isFree(Location l) {
            return inGrid(l) && !hasObject(OBSTACLE, l);
        }
    }

    public static boolean isBlocked(int x, int y) {
        if (x < 0 || y < 0 || x >= W || y >= H)
            return true;
        return obstacles != null && obstacles.contains(new Location(x, y));
    }

    @Override
    public boolean executeAction(String agName, Structure action) {

        try {
            Thread.sleep(300);
        } catch (Exception e) {
        } // * Pauses for better visualization

        String actName = action.getFunctor();
        boolean result = false;

        switch (actName) {
            case "move":
                result = move(termToId(action.getTerm(0)));
                break;
            case "pickup":
                result = pickup(termToId(action.getTerm(0)));
                break;
            case "drop":
                result = drop(termToId(action.getTerm(0)));
                break;
            case "open_door":
                result = openDoor();
                break;
            case "paint":
                result = paint(termToId(action.getTerm(0)));
                break;
            case "finish_episode":
                result = true;
                triggerNextEpisode();
                break;
        }

        updatePercepts("agent1");
        informAgsEnvironmentChanged();

        return result;
    }

    private boolean move(String direction) {
        Location newPos = (Location) agentPos.clone();
        switch (direction) {
            case "up":
                newPos.y--; // * Y axis is inverted in GridWorld
                break;
            case "down":
                newPos.y++; // * Y axis is inverted in GridWorld
                break;
            case "right":
                newPos.x++;
                break;
            case "left":
                newPos.x--;
                break;
        }

        if (model.inGrid(newPos) && !model.hasObject(OBSTACLE, newPos)) {
            agentPos = newPos;
            try {
                model.setAgPos(0, agentPos);
            } catch (Exception e) {
                return false;
            }
            return true;
        }
        return false;
    }

    private boolean pickup(String obj) {
        if (inventory.size() >= 3) {
            System.out.println("PICKUP FAILED: Inventory full (" + inventory.size() + "/3). Holding: " + inventory);
            return false;
        }

        Location objLoc = model.objects.get(obj);

        if (objLoc == null) {
            System.out.println("PICKUP ERROR: Object " + obj + " does not exist in model.");
            return false;
        }

        int mask = getMask(obj);
        if (mask == 0) {
            System.out.println("PICKUP ERROR: " + obj + " is not a pickup-able tool.");
            return false;
        }

        if (agentPos.equals(objLoc)) {
            inventory.add(obj);
            model.remove(mask, objLoc.x, objLoc.y);
            return true;
        }

        System.out.println("PICKUP FAILED: Agent not at object location. Agent: " + agentPos + ", Object: " + objLoc);
        return false;
    }

    private boolean drop(String obj) {
        if (inventory.contains(obj)) {
            int mask = getMask(obj);
            if (mask == 0)
                return false;

            inventory.remove(obj);
            model.add(mask, agentPos.x, agentPos.y);
            model.objects.put(obj, (Location) agentPos.clone());
            return true;
        }
        return false;
    }

    private boolean openDoor() {
        Location doorLoc = model.objects.get("door");
        if (agentPos.equals(doorLoc) && inventory.contains("key") && inventory.contains("code")) {
            goalsDone.add("open_door");
            return true;
        }
        return false;
    }

    private boolean paint(String obj) {
        Location objLoc = model.objects.get(obj);
        if (agentPos.equals(objLoc) && inventory.contains("brush") && inventory.contains("color")) {
            goalsDone.add("painted_" + obj);
            return true;
        }
        return false;
    }

    private String termToId(Term t) {
        String s = t.toString();
        if (s.startsWith("'") && s.endsWith("'"))
            s = s.substring(1, s.length() - 1);
        return s;
    }

    private void updatePercepts(String agName) {
        clearPercepts(agName);

        Location l = model.getAgPos(0);

        // * Add episode percept
        addPercept(agName, Literal.parseLiteral("episode(" + currentEpisode + ")"));

        // * Add position percept
        addPercept(agName, Literal.parseLiteral("pos(" + l.x + "," + l.y + ")"));

        // * Add inventory percepts
        for (String item : inventory) {
            addPercept(agName, Literal.parseLiteral("holding(" + item + ")"));
        }

        // * Add nearby objects percepts
        for (Map.Entry<String, Location> entry : model.objects.entrySet()) {
            String objName = entry.getKey();
            Location loc = entry.getValue();
            if (!inventory.contains(objName)) {
                addPercept(agName, Literal.parseLiteral("at(" + objName + "," + loc.x + "," + loc.y + ")"));
            }
        }

        // * Add obstacle percepts
        for (Location obs : obstacles) {
            addPercept(agName, Literal.parseLiteral("obstacle(" + obs.x + "," + obs.y + ")"));
        }

        // * Add goal percepts
        for (String goal : goalsDone) {
            addPercept(agName, Literal.parseLiteral(goal));
        }
    }
}