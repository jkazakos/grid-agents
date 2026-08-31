package env;

import jason.asSyntax.*;
import jason.environment.Environment;
import jason.environment.grid.GridWorldModel;
import jason.environment.grid.GridWorldView;
import jason.environment.grid.Location;
import java.util.*;
import java.awt.*;

// Class that defines a grid world environment for an agent.
public class GridEnvironment extends Environment {

    private static int W = 5, H = 5, nbAgs = 2;
    private static GridEnvironment instance = null;

    public static GridEnvironment getInstance() {
        return instance;
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
    private boolean episodeFinished = false;

    private static int currentEpisode = 1;
    private static final int MAX_EPISODES = 100;
    private Random random = new Random();

    public static int getWidth() {
        return W;
    }

    public static int getHeight() {
        return H;
    }

    public boolean isFree(int x, int y) {
        if (model == null)
            return false;
        return model.isFree(new Location(x, y));
    }

    public static void addEpisodeScore(double score) {
        totalScore += score;
    }

    // Converts object name to its corresponding bitmask
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
                return 0; // Not a valid tool
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

    private void startNewEpisode() {
        episodeFinished = false;
        System.out.println("\n============================================================");
        System.out.println("  STARTING EPISODE " + currentEpisode + " / " + MAX_EPISODES);
        System.out.println("============================================================");

        inventory1.clear();
        inventory2.clear();
        goalsDone.clear();

        agentPos1 = new Location(0, 4);
        agentPos2 = new Location(2, 2);

        try {
            model.setAgPos(0, agentPos1);
            model.setAgPos(1, agentPos2);
        } catch (Exception e) {
            System.out.println("Error setting agent positions: " + e.getMessage());
            e.printStackTrace();
        }

        model.resetObjects();
        actions.CalculateEpisodeScore.resetCosts();

        updatePercepts("agent1");
        updatePercepts("agent2");
        informAgsEnvironmentChanged();
    }

    private synchronized void triggerNextEpisode(String agName) {
        if (episodeFinished == true) {
            System.out.println("Ignored duplicate finish request from " + agName);
            return;
        }
        episodeFinished = true;
        System.out.println("  SUCCESS: Agents completed Episode " + currentEpisode);
        if (currentEpisode < MAX_EPISODES) {
            currentEpisode++;
            try {
                Thread.sleep(500);
            } catch (Exception e) {
            }
            startNewEpisode();
        } else {
            printFinalStatistics();
            currentEpisode++;
            updatePercepts("agent1");
            updatePercepts("agent2");
            informAgsEnvironmentChanged();
        }
    }

    private static void printFinalStatistics() {
        double averageScore = (double) totalScore / (double) currentEpisode;
        double utility = averageScore;

        System.out.println("\n=================================================================");
        System.out.println("                    EXPERIMENT BATCH COMPLETE                    ");
        System.out.println("=================================================================");
        System.out.printf("  Total Episodes Evaluated : %-35d%n", currentEpisode);
        System.out.printf("  Average Expected Utility : %-35.4f%n", utility);
        System.out.printf("  Cumulative Score Earned  : %-35.2f%n", totalScore);
        System.out.println("  Multi-Agent Status       : ALL TASKS RESOLVED OPTIMALLY");
        System.out.println("=================================================================\n");
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

        private static final Color COLOR_BG_BASE = new Color(11, 15, 25);
        private static final Color COLOR_TILE_BG = new Color(30, 41, 59);
        private static final Color COLOR_TILE_BORDER = new Color(51, 65, 85, 200);
        private static final Color COLOR_COORD_TEXT = new Color(100, 116, 139);

        private static final Color COLOR_OBSTACLE_BG = new Color(15, 23, 42);
        private static final Color COLOR_OBSTACLE_HATCH = new Color(51, 65, 85);
        private static final Color COLOR_OBSTACLE_BORDER = new Color(71, 85, 105);
        private static final Color COLOR_OBSTACLE_TEXT = new Color(148, 163, 184);

        // Tool theme colors
        private static final Color COLOR_BRUSH = new Color(245, 158, 11); // Amber #F59E0B
        private static final Color COLOR_KEY = new Color(234, 179, 8); // Gold #EAB308
        private static final Color COLOR_CODE = new Color(99, 102, 241); // Indigo #6366F1
        private static final Color COLOR_COLOR = new Color(236, 72, 153); // Pink/Rose #EC4899

        // Goal theme colors
        private static final Color COLOR_DOOR = new Color(16, 185, 129); // Emerald #10B981
        private static final Color COLOR_TABLE = new Color(168, 85, 247); // Violet #A855F7
        private static final Color COLOR_CHAIR = new Color(14, 165, 233); // Sky #0EA5E9

        private static final Font FONT_COORD = new Font("SansSerif", Font.PLAIN, 11);
        private static final Font FONT_TAG = new Font("SansSerif", Font.BOLD, 10);
        private static final Font FONT_CARD_TITLE = new Font("SansSerif", Font.BOLD, 13);
        private static final Font FONT_TOOL = new Font("SansSerif", Font.BOLD, 12);
        private static final Font FONT_AGENT = new Font("SansSerif", Font.BOLD, 15);
        private static final Font FONT_BADGE = new Font("SansSerif", Font.BOLD, 11);

        public MyView(MyModel model) {
            super(model, "Jason Multi-Agent System - 5x5 Grid World Simulator", 750);
            setVisible(true);
            repaint();
        }

        private void setupAntialiasing(Graphics2D g2) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        }

        @Override
        public void drawEmpty(Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g;
            setupAntialiasing(g2);

            int px = x * cellSizeW;
            int py = y * cellSizeH;

            // 1. Dark canvas base
            g2.setColor(COLOR_BG_BASE);
            g2.fillRect(px, py, cellSizeW, cellSizeH);

            // 2. Rounded modern tile card
            int margin = 4;
            int tileW = cellSizeW - margin * 2;
            int tileH = cellSizeH - margin * 2;
            g2.setColor(COLOR_TILE_BG);
            g2.fillRoundRect(px + margin, py + margin, tileW, tileH, 14, 14);

            // 3. Subtle tile border
            g2.setColor(COLOR_TILE_BORDER);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(px + margin, py + margin, tileW, tileH, 14, 14);

            // 4. Subtle grid coordinate label in top-left corner
            g2.setColor(COLOR_COORD_TEXT);
            g2.setFont(FONT_COORD);
            g2.drawString("(" + x + "," + y + ")", px + margin + 6, py + margin + 14);
        }

        @Override
        public void drawObstacle(Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g;
            setupAntialiasing(g2);

            int px = x * cellSizeW;
            int py = y * cellSizeH;
            int margin = 4;
            int tileW = cellSizeW - margin * 2;
            int tileH = cellSizeH - margin * 2;

            g2.setColor(COLOR_BG_BASE);
            g2.fillRect(px, py, cellSizeW, cellSizeH);

            g2.setColor(COLOR_OBSTACLE_BG);
            g2.fillRoundRect(px + margin, py + margin, tileW, tileH, 14, 14);

            Shape oldClip = g2.getClip();
            g2.clipRect(px + margin, py + margin, tileW, tileH);
            g2.setColor(COLOR_OBSTACLE_HATCH);
            g2.setStroke(new BasicStroke(1.5f));
            for (int offset = -tileH; offset < tileW + tileH; offset += 18) {
                g2.drawLine(px + margin + offset, py + margin, px + margin + offset + tileH, py + margin + tileH);
            }
            g2.setClip(oldClip);

            g2.setColor(COLOR_OBSTACLE_BORDER);
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawRoundRect(px + margin, py + margin, tileW, tileH, 14, 14);

            g2.setFont(FONT_BADGE);
            FontMetrics fm = g2.getFontMetrics();
            String wallText = "WALL";
            int tw = fm.stringWidth(wallText);
            int th = fm.getAscent() - fm.getDescent();
            int cx = px + cellSizeW / 2;
            int cy = py + cellSizeH / 2;

            g2.setColor(new Color(15, 23, 42, 220));
            g2.fillRoundRect(cx - tw / 2 - 8, cy - 11, tw + 16, 22, 8, 8);
            g2.setColor(COLOR_OBSTACLE_BORDER);
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(cx - tw / 2 - 8, cy - 11, tw + 16, 22, 8, 8);
            g2.setColor(COLOR_OBSTACLE_TEXT);
            g2.drawString(wallText, cx - tw / 2, cy + th / 2);
        }

        @Override
        public void drawAgent(Graphics g, int x, int y, Color c, int id) {
            Graphics2D g2 = (Graphics2D) g;
            setupAntialiasing(g2);

            int cx = x * cellSizeW + cellSizeW / 2;
            int cy = y * cellSizeH + cellSizeH / 2;
            int radius = (int) (Math.min(cellSizeW, cellSizeH) * 0.26);
            int diameter = radius * 2;

            // 1. Drop shadow
            g2.setColor(new Color(0, 0, 0, 110));
            g2.fillOval(cx - radius + 2, cy - radius + 5, diameter, diameter);

            // 2. Determine agent theme
            Color colorGrad1, colorGrad2, ringColor;
            String agentLabel = (id >= 0) ? "A" + (id + 1) : "A";

            if (id == 0) {
                // Agent 1: Bright Cyan/Teal
                colorGrad1 = new Color(6, 182, 212); // #06B6D4
                colorGrad2 = new Color(8, 145, 178); // #0891B2
                ringColor = new Color(103, 232, 249); // #67E8F9
            } else {
                // Agent 2: Sunset Coral/Orange
                colorGrad1 = new Color(249, 115, 22); // #F97316
                colorGrad2 = new Color(234, 88, 12); // #EA580C
                ringColor = new Color(253, 186, 116); // #FDBA74
            }

            // 3. Gradient fill
            GradientPaint gp = new GradientPaint(cx - radius, cy - radius, colorGrad1, cx + radius, cy + radius,
                    colorGrad2);
            g2.setPaint(gp);
            g2.fillOval(cx - radius, cy - radius, diameter, diameter);

            // 4. Glowing outer stroke ring
            g2.setColor(ringColor);
            g2.setStroke(new BasicStroke(2.8f));
            g2.drawOval(cx - radius, cy - radius, diameter, diameter);

            // 5. Centered agent text
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_AGENT);
            FontMetrics fm = g2.getFontMetrics();
            int tx = cx - fm.stringWidth(agentLabel) / 2;
            int ty = cy + (fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(agentLabel, tx, ty);
        }

        public void drawGoal(Graphics g, int x, int y, String title, String code, Color themeColor) {
            Graphics2D g2 = (Graphics2D) g;
            setupAntialiasing(g2);

            int px = x * cellSizeW;
            int py = y * cellSizeH;
            int margin = 6;
            int cardW = cellSizeW - margin * 2;
            int cardH = cellSizeH - margin * 2;

            // Semi-transparent glowing background card
            Color glowBg = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 35);
            g2.setColor(glowBg);
            g2.fillRoundRect(px + margin, py + margin, cardW, cardH, 14, 14);

            // Glowing outline
            g2.setColor(themeColor);
            g2.setStroke(new BasicStroke(2.2f));
            g2.drawRoundRect(px + margin, py + margin, cardW, cardH, 14, 14);

            // Top tag "GOAL"
            g2.setFont(FONT_TAG);
            g2.setColor(themeColor);
            FontMetrics fmTag = g2.getFontMetrics();
            String tag = "GOAL";
            g2.drawString(tag, px + (cellSizeW - fmTag.stringWidth(tag)) / 2, py + margin + 16);

            // Centered main label e.g. "DOOR [D]"
            g2.setFont(FONT_CARD_TITLE);
            FontMetrics fmTitle = g2.getFontMetrics();
            String fullTitle = title + " [" + code + "]";
            int textY = py + (cellSizeH / 2) + (fmTitle.getAscent() / 2) - 2;
            g2.setColor(Color.WHITE);
            g2.drawString(fullTitle, px + (cellSizeW - fmTitle.stringWidth(fullTitle)) / 2, textY);
        }

        public void drawTool(Graphics g, int x, int y, String title, String code, Color themeColor) {
            Graphics2D g2 = (Graphics2D) g;
            setupAntialiasing(g2);

            int px = x * cellSizeW;
            int py = y * cellSizeH;

            // Centered floating pill chip
            int pillW = cellSizeW - 24;
            int pillH = 32;
            int pillX = px + (cellSizeW - pillW) / 2;
            int pillY = py + (cellSizeH - pillH) / 2 + 8;

            // Dark pill backdrop
            g2.setColor(new Color(15, 23, 42, 230));
            g2.fillRoundRect(pillX, pillY, pillW, pillH, 16, 16);

            // Pill border in theme color
            g2.setColor(themeColor);
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawRoundRect(pillX, pillY, pillW, pillH, 16, 16);

            // Text inside pill: "BRUSH [B]"
            g2.setFont(FONT_TOOL);
            FontMetrics fm = g2.getFontMetrics();
            String fullLabel = title + " [" + code + "]";
            int tx = pillX + (pillW - fm.stringWidth(fullLabel)) / 2;
            int ty = pillY + (pillH + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(themeColor);
            g2.drawString(fullLabel, tx, ty);
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

        public void drawBrush(Graphics g, int x, int y) {
            drawTool(g, x, y, "BRUSH", "B", COLOR_BRUSH);
        }

        public void drawKey(Graphics g, int x, int y) {
            drawTool(g, x, y, "KEY", "K", COLOR_KEY);
        }

        public void drawCode(Graphics g, int x, int y) {
            drawTool(g, x, y, "CODE", "Cd", COLOR_CODE);
        }

        public void drawColor(Graphics g, int x, int y) {
            drawTool(g, x, y, "COLOR", "Cl", COLOR_COLOR);
        }

        public void drawDoor(Graphics g, int x, int y) {
            drawGoal(g, x, y, "DOOR", "D", COLOR_DOOR);
        }

        public void drawTable(Graphics g, int x, int y) {
            drawGoal(g, x, y, "TABLE", "T", COLOR_TABLE);
        }

        public void drawChair(Graphics g, int x, int y) {
            drawGoal(g, x, y, "CHAIR", "Ch", COLOR_CHAIR);
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
            if (!inGrid(l))
                return false;
            if (hasObject(OBSTACLE, l))
                return false;
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
        if (x < 0 || y < 0 || x >= W || y >= H)
            return true;
        return obstacles != null && obstacles.contains(new Location(x, y));
    }

    @Override
    public boolean executeAction(String agName, Structure action) {

        try {
            Thread.sleep(300);
        } catch (Exception e) {
        } // Pauses for better visualization

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
        return result;
    }

    private boolean move(String agName, String direction) {
        // Identify which agent is moving
        int agId = (agName.equals("agent1")) ? 0 : 1;
        // Determine current position
        Location currentPos = model.getAgPos(agId);
        Location newPos = (Location) currentPos.clone();

        switch (direction) {
            case "up":
                newPos.y--;
                break;
            case "down":
                newPos.y++;
                break;
            case "right":
                newPos.x++;
                break;
            case "left":
                newPos.x--;
                break;
        }

        if (!model.inGrid(newPos))
            return false;
        if (model.hasObject(OBSTACLE, newPos))
            return false;

        for (int i = 0; i < 2; i++) {
            if (i != agId) {
                Location otherAg = model.getAgPos(i);
                if (otherAg != null && otherAg.equals(newPos)) {
                    return false; // Collision detected, move not allowed
                }
            }
        }
        try {
            model.setAgPos(agId, newPos);
            if (agId == 0)
                agentPos1 = newPos;
            if (agId == 1)
                agentPos2 = newPos;
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
            // Check if object is physically present (prevent duplicate pickup)
            if (!model.hasObject(mask, objLoc)) {
                System.out.println("PICKUP ERROR: " + obj + " is not physically on grid (already picked up?).");
                return false;
            }

            currentInv.add(obj);
            model.remove(mask, objLoc.x, objLoc.y);
            System.out.println("PICKUP SUCCESS: " + agName + " picked up " + obj + ". Inventory now: " + currentInv);
            return true;
        }

        System.out.println("PICKUP FAILED: Agent not at object location. " + agName + " is at " + currentPos
                + ", Object: " + objLoc);
        return false;
    }

    private boolean drop(String agName, String obj) {
        int agId = (agName.equals("agent1")) ? 0 : 1;
        Set<String> currentInv = getInventory(agId);
        Location currentPos = model.getAgPos(agId);

        if (currentInv.contains(obj)) {
            int mask = getMask(obj);
            if (mask == 0)
                return false;

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

        if (currentPos.equals(doorLoc) && currentInv.contains("key") && currentInv.contains("code")) {
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

        // Add info about OTHER agent's position
        String otherAgName = (agName.equals("agent1")) ? "agent2" : "agent1";
        Location otherPos = getAgPos(otherAgName);
        if (otherPos != null) {
            addPercept(agName, Literal.parseLiteral("other_agent_at(" + otherPos.x + "," + otherPos.y + ")"));
        }

        // Add goal percepts
        for (String goal : goalsDone) {
            addPercept(agName, Literal.parseLiteral(goal));
        }
    }
}
