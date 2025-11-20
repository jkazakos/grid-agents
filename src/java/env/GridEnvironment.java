package env;

import jason.asSyntax.*;
import jason.environment.Environment;
import jason.environment.grid.GridWorldModel;
import jason.environment.grid.GridWorldView;
import jason.environment.grid.Location;
import java.util.*;

// import java.awt.Color;
// import java.awt.Font;
// import java.awt.Graphics;
// import java.util.logging.Logger;

public class GridEnvironment extends Environment {
    
    static class MyModel extends GridWorldModel {
        public static final int OBSTACLE = 16;
        public MyModel(int w, int h, int nbAgs) {
            super(w, h, nbAgs);
        }
        public boolean isFree(Location l) {
            return super.isFree(l.x, l.y);
        }
    }

    static class MyView extends GridWorldView {

        public MyView(MyModel model) {
            super(model, "Grid View", 600);
            setVisible(true);
        }
    }
    
    private MyModel model;
    private Location agentPos;
    private Map<String, Location> objects;
    private Map<String, Boolean> paintedObjects;
    private Set<String> inventory;
    private Set<String> goalsDone;
    private Set<Location> obstacles;
    private double cumulativeReward;

    private static int W =5, H =5;
    private static Set<Location> staticObstacles;

    public static int getWidth() { return W; }

    public static int getHeight() { return H; }

    public static boolean isBlocked(int x, int y) {
    if (x < 0 || y < 0 || x >= W || y >= H) return true;
    return staticObstacles != null && staticObstacles.contains(new Location(x, y));
    }
    
    @Override
    public void init(String[] args) {
        model = new MyModel(W, H, 1); // 5x5 grid, 1 agent

        new MyView(model);
        
        agentPos = new Location(0, 4);
        try {
            model.setAgPos(0, agentPos);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        obstacles = new HashSet<>();
        obstacles.add(new Location(1, 3));
        obstacles.add(new Location(1, 4));
        obstacles.add(new Location(3, 0));
        obstacles.add(new Location(3, 1));
        staticObstacles = obstacles;

        // Adding obstacles to the visual grid
        // model.add(MyModel.OBSTACLE, 1, 3);
        // model.add(MyModel.OBSTACLE, 1, 4);
        // model.add(MyModel.OBSTACLE, 3, 0);
        // model.add(MyModel.OBSTACLE, 3, 1);
        
        // Tracking objects' locations for pathfinding
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
        paintedObjects = new HashMap<>();
        paintedObjects.put("table", false);
        paintedObjects.put("chair", false);
        cumulativeReward = 0.0;
        
        updatePercepts();
        informAgsEnvironmentChanged();
        System.out.println("Environment initialized with percepts");
    }

    private double calculateStepReward() {
    int carrying = inventory.size();
    if (carrying == 0) return -0.01;
    
    int paintTools = 0; // Brush, Color
    int doorTools = 0;  // Key, Code
    
    if (inventory.contains("brush")) paintTools++;
    if (inventory.contains("color")) paintTools++;
    if (inventory.contains("key")) doorTools++;
    if (inventory.contains("code")) doorTools++;

    int incompatibleCount = 0;
    
    if (paintTools == 0 && doorTools == 0) {
        incompatibleCount = 0;
    } else if (paintTools >= doorTools) {
        incompatibleCount = doorTools;
    } else {
        incompatibleCount = paintTools;
    }

    double penalty = -0.02 * carrying - 0.03 * incompatibleCount;
    
    return penalty;
}

    @Override
    public boolean executeAction(String agName, Structure action) {
        try {
            Thread.sleep(300); // Pause for visualization
        } catch (Exception e) {
            e.printStackTrace();
        }

        String actName = action.getFunctor();
        boolean result = false;
        double reward = 0;
        
        switch(actName) {
            case "move":
                System.out.println("Processing move: " + action.getTerm(0));
                result = move(termToId(action.getTerm(0)));
                System.out.println("Move result: " + result);
                reward = calculateStepReward();
                break;
            case "pickup":
                result = pickup(termToId(action.getTerm(0)));
                reward = result ? calculateStepReward() : 0;
                break;
            case "drop":
                result = drop(termToId(action.getTerm(0)));
                reward = calculateStepReward();
                break;
            case "open_door":
                result = openDoor();
                reward = result ? 0.8 : 0;
                break;
            case "paint":
                result = paint(termToId(action.getTerm(0)));
                reward = result ? 1.0 : 0;
                break;
        }
        
        cumulativeReward += reward;
        updatePercepts();
        informAgsEnvironmentChanged();
        return result;
    }

    public double getCumulativeReward() {
    return cumulativeReward;
    }

    public boolean isGoalAchieved() {
    return paintedObjects.get("table") && paintedObjects.get("chair") && 
           goalsDone.contains("open_door");
    }

    // Remove surrounding quotes from a Term string (if needed)
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
                newPos.y--; // Y axis is inverted in GridWorld
                break;
            case "down":
                newPos.y++; // Y axis is inverted in GridWorld
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
            paintedObjects.put(obj, true);
            goalsDone.add("painted_" + obj);
            return true;
        }
        return false;
    }
    
    private void updatePercepts() {
        clearPercepts();
        String agentName = "agent1";

        int count = 0;
        
        // Add position percept
        addPercept(agentName, Literal.parseLiteral("pos(" + agentPos.x + "," + agentPos.y + ")"
        ));
        count++;
        
        // Add inventory percepts
        for(String item : inventory) {
            addPercept(agentName, Literal.parseLiteral("holding(" + item + ")"));
            count++;
        }
        
        // Add nearby objects
        for(Map.Entry<String, Location> entry : objects.entrySet()) {
            Location loc = entry.getValue();
            addPercept(agentName, Literal.parseLiteral("at(" + entry.getKey() + "," + loc.x + "," + loc.y + ")"));
            count++;
        }

        // Add obstacle percepts
        for(Location obs : obstacles) {
            addPercept(agentName, Literal.parseLiteral("obstacle(" + obs.x + "," + obs.y + ")"));
            count++;
        }
        
        // Add goal percepts
        for(String goal : goalsDone) {addPercept(agentName, Literal.parseLiteral(goal));
            count++;
        }
        
        System.out.println("Updated percepts, total count: " + count);
    }
}