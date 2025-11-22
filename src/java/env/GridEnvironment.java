package env;

import jason.asSyntax.*;
import jason.environment.Environment;
import jason.environment.grid.GridWorldModel;
import jason.environment.grid.GridWorldView;
import jason.environment.grid.Location;
// import jason.stdlib.queue.add;

import java.util.*;
import java.awt.Color;
import java.awt.Graphics;

public class GridEnvironment extends Environment {

    private static int W =5, H =5, nbAgs = 1;

    public static final int OBSTACLE = 8;
    public static final int TOOL = 16;
    public static final int DOOR = 32;
    public static final int TABLE = 64;
    public static final int CHAIR = 128;
    
    private MyModel model;
    private Location agentPos;
    private static Set<Location> obstacles;
    private Set<String> inventory;
    private Set<String> goalsDone;
    public double cumulativeReward = 0.0; /* TODO: Implement reward tracking */

    private int currentEpisode = 1;
    private final int MAX_EPISODES = 10;
    // private Random random = new Random(); /* TODO: Implement randomization for target placement */

    public static int getWidth() { return W; }
    public static int getHeight() { return H; }

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
        
        // cumulativeReward = 0.0;
        
        System.out.println("Environment initialized.");
    }

    private void startNewEpisode() {
        System.out.println("--- STARTING EPISODE " + (currentEpisode) + " ---");

        inventory.clear();
        goalsDone.clear();

        agentPos = new Location(0, 4);

        try { model.setAgPos(0, agentPos); } catch (Exception e) {}
        model.resetObjects();

        updatePercepts("agent1");
        informAgsEnvironmentChanged();
    }

//     private Location getFreeRandomLocation() {
//     Location loc;
//     boolean isFree;
    
//     do {
//         isFree = true;
//         // Generate random X, Y
//         int x = random.nextInt(model.getWidth());
//         int y = random.nextInt(model.getHeight());
//         loc = new Location(x, y);

//         // Check 1: Is it a Wall or Agent? (Standard Grid Check)
//         if (!model.isFree(loc)) {
//             isFree = false;
//         }

//         // Check 2: Is it (0,4)? (Don't spawn on top of the agent start)
//         if (x == 0 && y == 4) {
//             isFree = false;
//         }

//         // Check 3: Is another object already there?
//         if (model.objects.containsValue(loc)) {
//             isFree = false;
//         }

//     } while (!isFree); /* Keep trying until we find a free spot */

//     return loc;
// }

    static class MyView extends GridWorldView {

        public MyView(MyModel model) {
            super(model, "Grid View", 600);
            setVisible(true);
            repaint();
        }

        @Override
        public void draw(Graphics g, int x, int y, int object) {
            if ((object & OBSTACLE) != 0) {
                drawObstacle(g, x, y);
                return;
            }
            if ((object & TOOL) != 0) {
                drawTools(g, x, y);
                return;
            }
            if ((object & DOOR) != 0) {
                drawDoor(g, x, y);
                return;
            }
            if ((object & TABLE) != 0) {
                drawTable(g, x, y);
                return;
            }
            if ((object & CHAIR) != 0) {
                drawChair(g, x, y);
                return;
            }
            
            super.draw(g, x, y, object);
        }

        @Override
        public void drawObstacle(Graphics g, int x, int y) {
            super.drawObstacle(g, x, y);
            g.setColor(Color.BLACK);
            g.fillRect(x * cellSizeW + 1, y * cellSizeH + 1, cellSizeW - 2, cellSizeH - 2);
        }

        public void drawTools(Graphics g, int x, int y) {
            g.setColor(Color.BLACK);
            g.fillRect(x * cellSizeW + 1, y * cellSizeH + 1, cellSizeW - 2, cellSizeH - 2);
        }

        public void drawDoor(Graphics g, int x, int y) {
            g.setColor(Color.RED);
            g.drawRect(x * cellSizeW + 2, y * cellSizeH + 2, cellSizeW - 4, cellSizeH - 4);
            g.drawString("D", x * cellSizeW + (cellSizeW/2) - 4, y * cellSizeH + (cellSizeH/2) + 4);
        }

        public void drawTable(Graphics g, int x, int y) {
            g.setColor(Color.RED);
            g.drawRect(x * cellSizeW + 2, y * cellSizeH + 2, cellSizeW - 4, cellSizeH - 4);
            g.drawString("T", x * cellSizeW + (cellSizeW/2) - 4, y * cellSizeH + (cellSizeH/2) + 4);
        }

        public void drawChair(Graphics g, int x, int y) {
            g.setColor(Color.RED);
            g.drawRect(x * cellSizeW + 2, y * cellSizeH + 2, cellSizeW - 4, cellSizeH - 4);
            g.drawString("Ch", x * cellSizeW + (cellSizeW/2) - 4, y * cellSizeH + (cellSizeH/2) + 4);
        }
    }
    
    class MyModel extends GridWorldModel {

        public Map<String, Location> objects = new HashMap<>();
        // public static final String[] DYNAMIC_ITEMS = {"door", "chair", "table"};
        
        public MyModel() {
            super(W, H, nbAgs);
        }

        public void resetObjects() {
            for(int i=0; i<W; i++){
                for(int j=0; j<H; j++){
                    remove(TOOL, i, j); 
                }
            }

            /* Reset logic map */
            objects.clear();
            objects.put("brush", new Location(0, 0));
            objects.put("key", new Location(0, 1));
            objects.put("code", new Location(2, 0));
            objects.put("color", new Location(4, 0));
            objects.put("door", new Location(2, 4));
            objects.put("chair", new Location(3, 3));
            objects.put("table", new Location(4, 4));

            add(TOOL, 0, 0);
            add(TOOL, 0, 1);
            add(TOOL, 2, 0);
            add(TOOL, 4, 0);

            add(DOOR, 2, 4);
            add(CHAIR, 3, 3);
            add(TABLE, 4, 4);
        }

        public boolean isFree(Location l) {
            return inGrid(l) && !hasObject(OBSTACLE, l);
        }
    }

    public static boolean isBlocked(int x, int y) {
    if (x < 0 || y < 0 || x >= W || y >= H) return true;
    return obstacles != null && obstacles.contains(new Location(x, y));
    }
    
//     private double calculateStepReward() {
//     int carrying = inventory.size();
//     if (carrying == 0) return -0.01;
    
//     int paintTools = 0; // Brush, Color
//     int doorTools = 0;  // Key, Code
    
//     if (inventory.contains("brush")) paintTools++;
//     if (inventory.contains("color")) paintTools++;
//     if (inventory.contains("key")) doorTools++;
//     if (inventory.contains("code")) doorTools++;

//     int incompatibleCount = 0;
    
//     if (paintTools == 0 && doorTools == 0) {
//         incompatibleCount = 0;
//     } else if (paintTools >= doorTools) {
//         incompatibleCount = doorTools;
//     } else {
//         incompatibleCount = paintTools;
//     }

//     double penalty = -0.02 * carrying - 0.03 * incompatibleCount;
    
//     return penalty;
// }

    @Override
    public boolean executeAction(String agName, Structure action) {

        /* Pauses for visualization */
        // try { Thread.sleep(300); } catch (Exception e) {}

        String actName = action.getFunctor();
        boolean result = false;
        // double reward = 0;
        
        switch(actName) {
            case "move":
                result = move(termToId(action.getTerm(0)));
                // reward = calculateStepReward();
                break;
            case "pickup":
                result = pickup(termToId(action.getTerm(0)));
                // reward = result ? calculateStepReward() : 0;
                break;
            case "drop":
                result = drop(termToId(action.getTerm(0)));
                // reward = calculateStepReward();
                break;
            case "open_door":
                result = openDoor();
                // reward = result ? 0.8 : 0;
                break;
            case "paint":
                result = paint(termToId(action.getTerm(0)));
                // reward = result ? 1.0 : 0;
                break;
        }

        if (isGoalAchieved()) {
            System.out.println("SUCCESS! Episode " + (currentEpisode) + " cleared.");

            currentEpisode++;
            if (currentEpisode <= MAX_EPISODES) {   
                try { Thread.sleep(500); } catch (Exception e) {}             
                startNewEpisode();
            } else {
                System.out.println(">>> ALL RUNS FINISHED <<<");
            }
        } else {
            updatePercepts("agent1");
            informAgsEnvironmentChanged();
        }
        return result;
    }

    // public double getCumulativeReward() {
    // return cumulativeReward;
    // }

    public boolean isGoalAchieved() {
        return goalsDone.contains("painted_chair") && goalsDone.contains("painted_table");
    }
    
    private boolean move(String direction) {
        Location newPos = (Location) agentPos.clone();
        switch(direction) {
            case "up":
                newPos.y--; /* Y axis is inverted in GridWorld */
                break;
            case "down":
                newPos.y++; /* Y axis is inverted in GridWorld */
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

        if (agentPos.equals(objLoc)) {
            inventory.add(obj);
            model.remove(TOOL, objLoc.x, objLoc.y);
            return true;
        }

        System.out.println("PICKUP FAILED: Agent not at object location. Agent: " + agentPos + ", Object: " + objLoc);
        return false;
    }
    
    private boolean drop(String obj) {
        if (inventory.contains(obj)) {
            inventory.remove(obj);
            model.add(TOOL, agentPos.x, agentPos.y);
            model.objects.put(obj, (Location)agentPos.clone());
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

    /* Helper function: Remove surrounding quotes from a Term string (if needed) */
    private String termToId(Term t) {
        String s = t.toString();
        if (s.startsWith("'") && s.endsWith("'")) s = s.substring(1, s.length() - 1);
        return s;
    }
    
    private void updatePercepts(String agName) {
        clearPercepts(agName);

        Location l = model.getAgPos(0);
        int count = 0;

        /* Add episode percept */
        addPercept(agName, Literal.parseLiteral("episode(" + currentEpisode + ")"));
        
        /* Add position percept */
        addPercept(agName, Literal.parseLiteral("pos(" + l.x + "," + l.y + ")"));
        count++;
        
        /* Add inventory percepts */
        for(String item : inventory) {
            addPercept(agName, Literal.parseLiteral("holding(" + item + ")"));
            count++;
        }
        
        /* Add nearby objects */
        for (Map.Entry<String, Location> entry : model.objects.entrySet()) {
            String objName = entry.getKey();
            Location loc = entry.getValue();
            if (!inventory.contains(objName)) {
                addPercept(agName, Literal.parseLiteral("at(" + objName + "," + loc.x + "," + loc.y + ")"));
            }
    }

        /* Add obstacle percepts */
        for(Location obs : obstacles) {
            addPercept(agName, Literal.parseLiteral("obstacle(" + obs.x + "," + obs.y + ")"));
            count++;
        }
        
        /* Add goal percepts */
        for(String goal : goalsDone) {
            addPercept(agName, Literal.parseLiteral(goal));
            count++;
        }
        
        System.out.println("Updated percepts, total count: " + count);
    }
}