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
// import java.awt.Font;
// import java.util.logging.Logger;

public class GridEnvironment extends Environment {

    private static int W =5, H =5, nbAgs = 1;

    public static final int OBSTACLE = 16;
    public static final int TOOL = 8;
    /* FIXME: Figure out a mask that works for the targets */
    // public static final int TARGET_OBJECT = 1;
    
    
    private MyModel model;
    private Location agentPos = new Location(0, 4);
    private Set<Location> obstacles;
    private static Set<Location> staticObstacles;
    private Set<String> inventory;
    private Set<String> goalsDone;
    private Map<String, Location> objects;

    // private int currentEpisode = 0;
    // private final int MAX_EPISODES = 100;
    // private Random random = new Random();

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
        staticObstacles = obstacles;
        
        /* Tracking objects' locations for pathfinding */
        objects = new HashMap<>();
        objects.put("brush", new Location(0, 0));
        objects.put("key", new Location(0, 1));
        objects.put("code", new Location(2, 0));
        objects.put("door", new Location(2, 4));
        objects.put("chair", new Location(3, 3));
        objects.put("color", new Location(4, 0));
        objects.put("table", new Location(4, 4));
        
        inventory = new HashSet<>();
        goalsDone = new HashSet<>();
        // cumulativeReward = 0.0;
        
        updatePercepts();
        informAgsEnvironmentChanged();
        System.out.println("Environment initialized with percepts");
    }

    // private void startNewEpisode() {
    //     // 1. Reset Agent Position (e.g., back to 0,0 or random)
    //     model.setAgPos(0, new Location(0, 0)); 

    //     // 2. Generate a Valid Random Target
    //     do {
    //         int x = random.nextInt(model.getWidth());
    //         int y = random.nextInt(model.getHeight());
    //         targetLoc = new Location(x, y);
            
    //         // Ensure we don't put the target on a wall or on the agent
    //     } while (!model.isFree(targetLoc) || (targetLoc.x == 0 && targetLoc.y == 0));

    //     // 3. Update Percepts immediately so agent knows the new goal
    //     updatePercepts();
    // }

    static class MyView extends GridWorldView {

        public MyView(MyModel model) {
            super(model, "Grid View", 600);
            setVisible(true);
        }

        @Override
        public void draw(Graphics g, int x, int y, int object) {
            if (object == MyModel.OBSTACLE) {
                drawObstacle(g, x, y);
                return;
            }
            if (object == TOOL) {
                drawTools(g, x, y);
                return;
            }
            // if (object == TARGET_OBJECT) {
            //     drawTargetObjects(g, x, y);
            //     return;
            // }
            
            super.draw(g, x, y, object);
        }

        @Override
        public void drawObstacle(Graphics g, int x, int y) {
            g.setColor(Color.BLACK);
            g.fillRect(x * cellSizeW + 1, y * cellSizeH + 1, cellSizeW - 2, cellSizeH - 2);
        }

        public void drawTools(Graphics g, int x, int y) {
            g.setColor(Color.YELLOW);
            g.fillRect(x * cellSizeW + 1, y * cellSizeH + 1, cellSizeW - 2, cellSizeH - 2);
            drawString(g, x, y, g.getFont(), "Tool");
        }

        // public void drawTargetObjects(Graphics g, int x, int y) {
        //     g.setColor(Color.CYAN);
        //     g.fillRect(x * cellSizeW + 1, y * cellSizeH + 1, cellSizeW - 2, cellSizeH - 2);
        //     drawString(g, x, y, g.getFont(), "Obj");
        // }
    }
    
    class MyModel extends GridWorldModel {
        
        public MyModel() {
            super(W, H, nbAgs);

            try {
                setAgPos(0, agentPos);
            } catch (Exception e) {
                e.printStackTrace();
            }

            add(OBSTACLE, 1, 3);
            add(OBSTACLE, 1, 4);
            add(OBSTACLE, 3, 0);
            add(OBSTACLE, 3, 1);

            add(TOOL, 0, 0); /* Brush */
            add(TOOL, 0, 1); /* Key */
            add(TOOL, 2, 0); /* Code */
            add(TOOL, 4, 0); /* Color */
            // add(TARGET_OBJECT, 2, 4); /* Door */
            // add(TARGET_OBJECT, 3, 3); /* Chair */
            // add(TARGET_OBJECT, 4, 4); /* Table */
        }
        public boolean isFree(Location l) {
            return super.isFree(l.x, l.y);
        }
    }

    

    public static boolean isBlocked(int x, int y) {
    if (x < 0 || y < 0 || x >= W || y >= H) return true;
    return staticObstacles != null && staticObstacles.contains(new Location(x, y));
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
        try {
            Thread.sleep(300); /* Pause for visualization */
        } catch (Exception e) {
            e.printStackTrace();
        }

        String actName = action.getFunctor();
        boolean result = false;
        // double reward = 0;
        
        switch(actName) {
            case "move":
                System.out.println("Processing move: " + action.getTerm(0));
                result = move(termToId(action.getTerm(0)));
                System.out.println("Move result: " + result);
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
        
        // cumulativeReward += reward;
        updatePercepts();
        informAgsEnvironmentChanged();
        return result;
    }

    // public double getCumulativeReward() {
    // return cumulativeReward;
    // }

    public boolean isGoalAchieved() {
    return goalsDone.contains("painted_table") && goalsDone.contains("painted_chair") && 
           goalsDone.contains("open_door");
    }

    /* Remove surrounding quotes from a Term string (if needed) */
    private String termToId(Term t) {
        String s = t.toString();
        if (s.startsWith("'") && s.endsWith("'")) s = s.substring(1, s.length() - 1);
        return s;
    }

    private boolean hasObstacle(Location l) {
    return obstacles.contains(l);
    }

    private boolean cellIsFree(Location l) {
    return model.isFree(l.x, l.y) && !hasObstacle(l);
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

        System.out.println("Current pos: " + agentPos + ", New pos: " + newPos);
        System.out.println("In grid: " + model.inGrid(newPos) + ", Cell free: " + cellIsFree(newPos));
        
        if (model.inGrid(newPos) && cellIsFree(newPos)) {
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
        
        Location objLoc = objects.get(obj);

        if (objLoc == null) {
            System.out.println("PICKUP FAILED: Object " + obj + " not found. (Location is null)");
            return false;
        }

        if(agentPos.equals(objLoc)) {
            inventory.add(obj);
            System.out.println("PICKUP SUCCESS: Picked up " + obj + ". Inventory: " + inventory);
            return true;
         } else {
            System.out.println("PICKUP FAILED: Location mismatch. Agent at " + agentPos + ", Object at " + objLoc);
            return false;
        }
    }
    
    private boolean drop(String obj) {
        return inventory.remove(obj);
    }
    
    private boolean openDoor() {
        Location doorLoc = objects.get("door");
        if(agentPos.equals(doorLoc) && 
           inventory.contains("key") && 
           inventory.contains("code")) {
            goalsDone.add("open_door");
            return true;
        }
        return false;
    }
    
    private boolean paint(String obj) {
        Location objLoc = objects.get(obj);
        if(agentPos.equals(objLoc) && 
           inventory.contains("brush") && 
           inventory.contains("color")) {
            goalsDone.add("painted_" + obj);
            return true;
        }
        return false;
    }
    
    private void updatePercepts() {
        clearPercepts();
        String agentName = "agent1";

        int count = 0;
        
        /* Add position percept */
        addPercept(agentName, Literal.parseLiteral("pos(" + agentPos.x + "," + agentPos.y + ")"
        ));
        count++;
        
        /* Add inventory percepts */
        for(String item : inventory) {
            addPercept(agentName, Literal.parseLiteral("holding(" + item + ")"));
            count++;
        }
        
        /* Add nearby objects */
        for(Map.Entry<String, Location> entry : objects.entrySet()) {
            Location loc = entry.getValue();
            addPercept(agentName, Literal.parseLiteral("at(" + entry.getKey() + "," + loc.x + "," + loc.y + ")"));
            count++;
        }

        /* Add obstacle percepts */
        for(Location obs : obstacles) {
            addPercept(agentName, Literal.parseLiteral("obstacle(" + obs.x + "," + obs.y + ")"));
            count++;
        }
        
        /* Add goal percepts */
        for(String goal : goalsDone) {addPercept(agentName, Literal.parseLiteral(goal));
            count++;
        }
        
        System.out.println("Updated percepts, total count: " + count);
    }
}