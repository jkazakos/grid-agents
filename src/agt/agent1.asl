/* Initial Goal: Choose ONE task to test manually for now */
!start.

/* Change this line to !task_open if you want to test the door logic */
/* Both tasks work as expected right now, without any logic for cost efficiency */
+!start <- !task_open_door.

// TODO: Separate the painting tasks into different plans
/* ================================================================== */
/* TASK 1: PAINTING                                                   */
/* ================================================================== */
+!task_paint <-
    .print("=== STARTING TASK: PAINTING ===");
    
    // Step 1: Clean Inventory (Avoid -0.03 penalty)
    !drop_item(key);
    !drop_item(code);

    // Step 2: Get Tools (Fixed Order)
    .print("Step 1: Getting Brush");
    !ensure_have(brush); // Will block here until holding brush

    .print("Step 2: Getting Color");
    !ensure_have(color); // Will block here until holding color

    // Step 3: Execute Painting (Fixed Order)
    .print("Step 3: Painting Table");
    !go_to_and_paint(table);

    .print("Step 4: Painting Chair");
    !go_to_and_paint(chair);
    
    .print("=== TASK PAINTING COMPLETE ===").

/* ================================================================== */
/* TASK 2: OPEN DOOR                                                  */
/* ================================================================== */
+!task_open_door <-
    .print("=== STARTING TASK: OPEN DOOR ===");

    // Step 1: Clean Inventory
    !drop_item(brush);
    !drop_item(color);

    // Step 2: Get Tools
    .print("Step 1: Getting Key");
    !ensure_have(key);

    .print("Step 2: Getting Code");
    !ensure_have(code);

    // Step 3: Execute Opening
    .print("Step 3: Opening Door");
    !go_to_and_open(door);

    .print("=== TASK OPEN DOOR COMPLETE ===").

/* ================================================================== */
/* LOW LEVEL PRIMITIVES (The Engine)                                  */
/* These are the specific steps you asked to define separately        */
/* ================================================================== */

/* --- Primitive: Get Item --- */
// If we have it, do nothing
+!ensure_have(Item) : holding(Item) <- 
    .print("Check: Already have ", Item).

// If we don't have it, go get it
+!ensure_have(Item) : not holding(Item) <-
    ?at(Item, X, Y);           // 1. Find it
    !go_to(X, Y);              // 2. Go there
    .print("Action: Picking up ", Item);
    pickup(Item);              // 3. Pick it up
    .wait(200);                // 4. Wait for environment update (CRITICAL)
    ?holding(Item);            // 5. Verify success
    .print("Success: Holding ", Item).

/* --- Primitive: Paint Object --- */
+!go_to_and_paint(Obj) : painted_object(Obj) <- .print("Already painted ", Obj).
+!go_to_and_paint(Obj) : not painted_object(Obj) <-
    ?at(Obj, X, Y);
    !go_to(X, Y);
    .print("Action: Painting ", Obj);
    paint(Obj);
    .wait(200);
    // ?painted_object(Obj); // Uncomment if your env sends this percept update immediately
    .print("Success: Painted ", Obj).

/* --- Primitive: Open Door --- */
+!go_to_and_open(door) : open_door <- .print("Door already open").
+!go_to_and_open(door) : not open_door <-
    ?at(door, X, Y);
    !go_to(X, Y);
    .print("Action: Opening Door");
    open_door;
    .wait(200);
    .print("Success: Door Opened").

/* --- Primitive: Drop Item --- */
+!drop_item(Item) : holding(Item) <- 
    .print("Cleaning: Dropping ", Item);
    drop(Item);
    .wait(200).
+!drop_item(Item) <- true.

/* --- Primitive: Navigation (A*) --- */
+!go_to(X, Y) : pos(X, Y) <- .print("Already at destination").
+!go_to(Tx, Ty) : pos(Sx, Sy) <-
    actions.PathTo(Sx, Sy, Tx, Ty, PathList);
    !execute_path(PathList).

-!go_to(X, Y) <-
    .print("ERROR: Pathfinding failed to ", X, ",", Y);
    .wait(1000);
    !go_to(X, Y).

+!execute_path([]).
+!execute_path([Move|Rest]) <-
    move(Move);
    !execute_path(Rest).

/* Error Handling */
-!ensure_have(Item) <-
    .print("ERROR: Failed to pick up ", Item, ". Retrying...");
    .wait(1000);
    !ensure_have(Item).
