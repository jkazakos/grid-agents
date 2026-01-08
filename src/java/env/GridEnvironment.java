package env;

import jason.asSyntax.*;
import jason.environment.Environment;
import jason.environment.grid.GridWorldModel;
import jason.environment.grid.GridWorldView;
import jason.environment.grid.Location;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.Color;
import java.awt.Graphics;

// Class that defines a grid world environment for an agent.
public class GridEnvironment extends Environment {

    private static int W = 5, H = 5, nbAgs = 2;
    private static GridEnvironment instance = null;

    public static GridEnvironment getInstance() {
        return instance;
    }

    // Reservation System ---
    private Map<Location, Reservation> reservationTable = new ConcurrentHashMap<>();

    static class Reservation {
        String agentName;
        int priority;
        long timestamp; 

        public Reservation(String agentName, int priority) {
            this.agentName = agentName;
            this.priority = priority;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static final int OBSTACLE = 8;
    public static final int DOOR = 16;
    public static final int TABLE = 32;
    public static final int CHAIR = 64;
    public static final int BRUSH = 128;
    public static final int COLOR = 256;
    public static final int KEY = 512;
    public static final int CODE = 1024;

    private MyModel model;
    private Location agentPos1;
    private Location agentPos2;
    private static Set<Location> obstacles;
    private Set<String> inventory1;
    private Set<String> inventory2;
    private Set<String> goalsDone;

    private static double totalScore = 0.0;
    private static int episodeCount = 0;

    private int currentEpisode = 1;
    private final int MAX_EPISODES = 10;
    private Random random = new Random();

    public static int getWidth() {
        return W;
    }

    public static int getHeight() {
        return H;
    }

    public boolean isFree(int x, int y) {
        if (model == null) return false;
        return model.isFree(new Location(x, y));
    }

    public static void addEpisodeScore(double score) {
        totalScore += score;
        episodeCount++;
    }

    // Converts object name to its corresponding bitmask
    private int getMask(String obj) {
        switch (obj) {
            case "brush": return BRUSH;
            case "color": return COLOR;
            case "key": return KEY;
            case "code": return CODE;
            default: return 0; //  Not a valid tool
        }
    }

    @Override
    public void init(String[] args) {
        instance = this;
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

        inventory1 = new HashSet<>();
        inventory2 = new HashSet<>();
        goalsDone = new HashSet<>();

        startNewEpisode();

        System.out.println("Environment initialized.");
    }

    public Location getAgPos(String agName) {
        int agId = (agName.equals("agent1")) ? 0 : 1;
        return model.getAgPos(agId);
    }

    public synchronized boolean reservePath(String agName, int priority, List<Location> path) {
        // 1. Validation Phase
        for (Location loc : path) {
            // Check Static Obstacles
            if (!model.inGrid(loc) || model.hasObject(OBSTACLE, loc)) return false;

            // Check if another agent is PHYSICALLY there (cannot reserve occupied space)
            // (Unless it's the agent itself, obviously)
            for(int i=0; i<nbAgs; i++) {
                Location otherPos = model.getAgPos(i);
                String otherName = (i==0) ? "agent1" : "agent2";
                if(otherPos.equals(loc) && !otherName.equals(agName)) {
                     // Path blocked by physical agent
                     return false; 
                }
            }

            // Check Reservations
            if (reservationTable.containsKey(loc)) {
                Reservation res = reservationTable.get(loc);
                if (res.agentName.equals(agName)) continue; // I already own it, extend/keep

                // Conflict detected!
                if (priority > res.priority) {
                    // I am heavier. I will OVERWRITE this reservation in the next phase.
                    // This is the "Bully" logic.
                    continue; 
                } else {
                    // I am lighter or equal. I must yield.
                    return false;
                }
            }
        }

        // 2. Booking Phase (Commit)
        // Clear previous reservations for this agent to avoid clutter? 
        // No, we might want to keep the current tile reserved. 
        // Ideally, we clear old path reservations not in the new path, but for simplicity:
        // We just overwrite the path.
        
        for (Location loc : path) {
            reservationTable.put(loc, new Reservation(agName, priority));
        }
        
        return true;
    }

    // Clears reservations for a specific tile (used after moving out of it)
    private void clearReservation(Location loc, String agName) {
        if (reservationTable.containsKey(loc)) {
            if (reservationTable.get(loc).agentName.equals(agName)) {
                reservationTable.remove(loc);
            }
        }
    }

    private void startNewEpisode() {
        System.out.println("--- STARTING EPISODE " + (currentEpisode) + " ---");

        inventory1.clear();
        inventory2.clear();
        goalsDone.clear();
        reservationTable.clear(); // Clear all locks

        agentPos1 = new Location(0, 4);
        agentPos2 = new Location(2, 2);

        try {
            model.setAgPos(0, agentPos1);
            model.setAgPos(1, agentPos2);
        } catch (Exception e) {}
        model.resetObjects();

        updatePercepts("agent1");
        updatePercepts("agent2");
        informAgsEnvironmentChanged();
    }

    private void triggerNextEpisode(String agName) {
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

            // Constraint 1: Check against forbidden locations
            if (forbidden.contains(loc)) {
                isBlocked = true;
            }

            // Constraint 2: Double check the model for static obstacles
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

            // Reset logic map
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

            // Place Door
            Location doorLoc = getFreeLocation(forbidden);
            objects.put("door", doorLoc);
            add(DOOR, doorLoc.x, doorLoc.y);
            forbidden.add(doorLoc);

            // Place Chair
            Location chairLoc = getFreeLocation(forbidden);
            objects.put("chair", chairLoc);
            add(CHAIR, chairLoc.x, chairLoc.y);
            forbidden.add(chairLoc);

            // Place Table
            Location tableLoc = getFreeLocation(forbidden);
            objects.put("table", tableLoc);
            add(TABLE, tableLoc.x, tableLoc.y);
            forbidden.add(tableLoc);
        }

        public boolean isFree(Location l) {
            if (!inGrid(l)) return false;
            if (hasObject(OBSTACLE, l)) return false;
            for (int i = 0; i < nbAgs; i++) {
                Location agLoc = getAgPos(i);
                if (agLoc != null && agLoc.equals(l)) {
                    return false; // Occupied by an agent -> Not free
                }
            }
            return true;
        }
    }

    public static boolean isBlocked(int x, int y) {
        if (x < 0 || y < 0 || x >= W || y >= H) return true;
        return obstacles != null && obstacles.contains(new Location(x, y));
    }

    @Override
    public boolean executeAction(String agName, Structure action) {

        try { Thread.sleep(300); } catch (Exception e) {} // Pauses for better visualization

        String actName = action.getFunctor();
        boolean result = false;

        switch (actName) {
            case "move":
                result = move(agName, termToId(action.getTerm(0)));
                break;
            case "pickup":
                result = pickup(agName, termToId(action.getTerm(0)));
                break;
            case "drop":
                result = drop(agName, termToId(action.getTerm(0)));
                break;
            case "open_door":
                result = openDoor(agName);
                break;
            case "paint":
                result = paint(agName, termToId(action.getTerm(0)));
                break;
            case "finish_episode":
                result = true;
                triggerNextEpisode(agName);
                break;
        }

        updatePercepts("agent1");
        updatePercepts("agent2");
        informAgsEnvironmentChanged();

        if (result && goalsDone.size() >= 3) {
            try { Thread.sleep(1000); } catch (Exception e) {}
            triggerNextEpisode(agName);
        }

        return result;
    }

    private boolean move(String agName, String direction) {
        // Identify which agent is moving
        int agId = (agName.equals("agent1")) ? 0 : 1;
        // Determine current position
        Location currentPos = model.getAgPos(agId);
        Location newPos = (Location) currentPos.clone();

        switch (direction) {
            case "up": newPos.y--; break;
            case "down": newPos.y++; break;
            case "right": newPos.x++; break;
            case "left": newPos.x--; break;
        }

        if (!model.inGrid(newPos)) return false;
        if (model.hasObject(OBSTACLE, newPos)) return false;

        for (int i = 0; i < 2; i++) {
            if (i != agId) {
                Location otherAg = model.getAgPos(i);
                if (otherAg != null && otherAg.equals(newPos)) {
                    return false; // Collision detected, move not allowed
                }
            }
        }

        // --- NEW: Reservation Check ---
        // You can only move if you hold the reservation OR if the tile is free and unreserved.
        // But strictly enforcing: You MUST reserve before moving.
        if (reservationTable.containsKey(newPos)) {
            Reservation res = reservationTable.get(newPos);
            if (!res.agentName.equals(agName)) {
                System.out.println("MOVE BLOCKED: " + agName + " tried to enter " + newPos.x+","+newPos.y + " reserved by " + res.agentName);
                return false; 
            }
        }

        try {
            // Free the old tile reservation
            clearReservation(currentPos, agName);
            
            model.setAgPos(agId, newPos);
            if (agId == 0) agentPos1 = newPos;
            if (agId == 1) agentPos2 = newPos;
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private boolean pickup(String agName, String obj) {
        int agId = (agName.equals("agent1")) ? 0 : 1;
        Set<String> currentInv = getInventory(agId);
        Location currentPos = model.getAgPos(agId);

        if (currentInv.size() >= 3) {
            System.out.println("PICKUP FAILED: Inventory full (" + currentInv.size() + "/3). Holding: " + currentInv);
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

        if (currentPos.equals(objLoc)) {
            currentInv.add(obj);
            model.remove(mask, objLoc.x, objLoc.y);
            System.out.println( "PICKUP SUCCESS: " + agName + " picked up " + obj + ". Inventory now: " + currentInv);
            return true;
        }

        System.out.println("PICKUP FAILED: Agent not at object location. " + agName + " is at " + currentPos + ", Object: " + objLoc);
        return false;
    }

    private boolean drop(String agName, String obj) {
        int agId = (agName.equals("agent1")) ? 0 : 1;
        Set<String> currentInv = getInventory(agId);
        Location currentPos = model.getAgPos(agId);

        if (currentInv.contains(obj)) {
            int mask = getMask(obj);
            if (mask == 0) return false;

            currentInv.remove(obj);
            model.add(mask, currentPos.x, currentPos.y);
            model.objects.put(obj, (Location) currentPos.clone());
            System.out.println(agName + " dropped " + obj);
            return true;
        }
        return false;
    }

    private boolean openDoor(String agName) {
        int agId = (agName.equals("agent1")) ? 0 : 1;
        Set<String> currentInv = getInventory(agId);

        Location currentPos = model.getAgPos(agId);
        Location doorLoc = model.objects.get("door");

        if ( currentPos.equals(doorLoc) && currentInv.contains("key") && currentInv.contains("code")) {
            goalsDone.add("open_door");
            return true;
        }
        return false;
    }

    private boolean paint(String agName, String obj) {
        int agId = (agName.equals("agent1")) ? 0 : 1;
        Set<String> currentInv = getInventory(agId);

        Location currentPos = model.getAgPos(agId);
        Location objLoc = model.objects.get(obj);

        if (currentPos.equals(objLoc) && currentInv.contains("brush") && currentInv.contains("color")) {
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

    private Set<String> getInventory(int agId) {
        return (agId == 0) ? inventory1 : inventory2;
    }

    private void updatePercepts(String agName) {
        clearPercepts(agName);

        int agId = (agName.equals("agent1")) ? 0 : 1;
        Location l = model.getAgPos(agId);
        Set<String> currentInv = getInventory(agId);

        // Add episode percept
        addPercept(agName, Literal.parseLiteral("episode(" + currentEpisode + ")"));

        // Add home percept
        if (agId == 0) {
            addPercept(agName, Literal.parseLiteral("home(0, 4)")); // Agent 1 Home
        } else {
            addPercept(agName, Literal.parseLiteral("home(2, 2)")); // Agent 2 Home
        }

        // Add position percept
        addPercept(agName, Literal.parseLiteral("pos(" + l.x + "," + l.y + ")"));

        // Add inventory percepts
        for (String item : currentInv) {
            addPercept(agName, Literal.parseLiteral("holding(" + item + ")"));
        }

        // Add nearby objects percepts
        for (Map.Entry<String, Location> entry : model.objects.entrySet()) {
            String objName = entry.getKey();
            Location loc = entry.getValue();
            boolean isHeld = inventory1.contains(objName) || inventory2.contains(objName);
            if (!isHeld) {
                addPercept(agName, Literal.parseLiteral("at(" + objName + "," + loc.x + "," + loc.y + ")"));
            }
        }

        // Add obstacle percepts
        for (Location obs : obstacles) {
            addPercept(agName, Literal.parseLiteral("obstacle(" + obs.x + "," + obs.y + ")"));
        }

        // Add goal percepts
        for (String goal : goalsDone) {
            addPercept(agName, Literal.parseLiteral(goal));
        }
    }
}
