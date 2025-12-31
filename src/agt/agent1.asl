//? === INITIAL BELIEFS === */

// Tasks: task(ID, Type, Target, [RequiredTools])
task(t1, painting, table, [brush, color]).
task(t2, painting, chair, [brush, color]).
task(t3, opening,  door,  [key, code]).

completed(none).

!start.

// Check for max episodes
+episode(Ep) : Ep > 10
   <- .print(">>> MAX RUNS REACHED (", Ep-1, "). Stopping agent. <<<").

+episode(Ep) : true 
   <- .print("--- NEW EPISODE ", Ep, " ---");
      .drop_all_intentions;
      .abolish(holding(_));
      .abolish(completed(_));
      !start.

//? === CONTROL LOOP === */

+!start : true
   <- .print("Waiting for environment restart...");
      .wait(pos(0, 4));
      .print("Environment restarted. Beginning new episode.");
      !decide_next_action.

// End if no more tasks
+!decide_next_action 
   : not task(_, _, _, _)
   <- !finish_episode.

// Main decision loop
+!decide_next_action
   : task(_, _, _, _)
   <- 
      [cite_start]
      // Find all pending tasks
      .findall(id(ID, Type, Target, Tools), task(ID, Type, Target, Tools) & not completed(ID), PendingTasks);
      
      // Evaluate options
      !evaluate_options(PendingTasks, BestTaskID, BestCost);
      
      // Pick the best one
      .print("Best option found: ", BestTaskID, " with cost ", BestCost);
      !execute_task(BestTaskID);
      
      // Loop
      !decide_next_action.

//? === COST EVALUATION === */

+!evaluate_options([], none, 99999). // Base case

+!evaluate_options([id(ID, Type, Target, Tools)|Rest], BestID, MinCost)
   <- 
      // Calculate cost for current task
      !calculate_task_cost(Target, Tools, CurrentCost);
      // Evaluate rest
      !evaluate_options(Rest, RecID, RecCost);
      // Compare and select the best
      !compare_and_select(ID, CurrentCost, RecID, RecCost, BestID, MinCost).

// Cost Detection
+!calculate_task_cost(Target, Tools, Cost)
   : pos(Ax, Ay) & at(Target, Tx, Ty)
   <- 
      // A. Cost of moving to the target
      actions.PathCost(Ax, Ay, Tx, Ty, DistToTarget);
      // B. Cost of acquiring tools (Heuristic)
      // Here we call an internal logic: Do I have the tools? If not, how far are they?
      !tool_acquisition_cost(Tools, ToolCost);
      // TOTAL COST = (Distance * Factor) + ToolCost
      // If I don't have the tools, the ToolCost will be high
      Cost = DistToTarget + ToolCost.

//? === EXECUTION LOGIC === */

+!execute_task(ID)
   : task(ID, Type, Target, Tools)
   <- 
      .print(">>> EXECUTING TASK: ", ID);
      
      // Manage inventory
      !prepare_inventory(Tools);
      
      // Go to target
      ?at(Target, Tx, Ty);
      !go_to(Tx, Ty);
      
      // Do the task
      !perform_action(Type, Target);

      [cite_start]
      
      // Update the task as completed
      +completed(ID);

      // *** MULTI-AGENT HOOK ***
      // .broadcast(tell, task_completed(ID));  <-- Broadcast to other agents
      .print("Task ", ID, " completed.").

//? === INVENTORY & TOOL MANAGER === */

+!prepare_inventory(RequiredTools)
   <- 
      // Drop unneeded items
      .findall(Item, holding(Item), CurrentItems);
      !drop_unneeded(CurrentItems, RequiredTools);
      
      // Pick up missing tools
      !acquire_missing(RequiredTools).

+!drop_unneeded([], _).
+!drop_unneeded([Item|Rest], Needed) 
   : .member(Item, Needed) // Keep if needed
   <- !drop_unneeded(Rest, Needed).
+!drop_unneeded([Item|Rest], Needed)
   <- .print("Dropping incompatible item: ", Item);
      drop(Item); 
      !drop_unneeded(Rest, Needed).

+!acquire_missing([]).
+!acquire_missing([Tool|Rest])
   : holding(Tool)
   <- !acquire_missing(Rest).
+!acquire_missing([Tool|Rest])
   <- ?at(Tool, Tx, Ty);
      !go_to(Tx, Ty);
      pickup(Tool);
      .print("Picked up: ", Tool);
      !acquire_missing(Rest).

//? === HELPERS === */

+!perform_action(painting, Obj) <- paint(Obj).
+!perform_action(opening, Obj)  <- open_door.

+!go_to(X, Y) : pos(X, Y).
+!go_to(Tx, Ty) : pos(Sx, Sy) 
   <- actions.PathTo(Sx, Sy, Tx, Ty, PathList);
      !move_path(PathList).

+!move_path([]).
+!move_path([M|Rest]) <- move(M); !move_path(Rest).

+!finish_episode <- .print("EPISODE FINISHED").

// Compare and select the better option
+!compare_and_select(ID1, C1, ID2, C2, ID1, C1) : C1 <= C2.
+!compare_and_select(ID1, C1, ID2, C2, ID2, C2) : C2 < C1.

//? === COST CALCULATION LOGIC === */

// Wrapper: Start calculation from the agent's current position
+!tool_acquisition_cost(Tools, TotalCost)
   : pos(AgX, AgY)
   <- !calc_tools_path(Tools, AgX, AgY, TotalCost).

[cite_start]
+!tool_acquisition_cost(Tools, TotalCost)
   : pos(AgX, AgY)
   <- !calc_tools_path(Tools, AgX, AgY, TotalCost).

// Base case: No tools -> Cost 0
+!calc_tools_path([], _, _, 0).

// Case 1: Already holding the tool
// Position doesn't change, no cost added
+!calc_tools_path([Tool|Rest], CurrX, CurrY, TotalCost)
   : holding(Tool)
   <- !calc_tools_path(Rest, CurrX, CurrY, TotalCost).

// Case 2: Not holding the tool (Need to go get it)
+!calc_tools_path([Tool|Rest], CurrX, CurrY, TotalCost)
   : not holding(Tool)
   <- 
      // 1. Find where the tool is NOW
      ?at(Tool, ToolX, ToolY);
      
      // 2. Calculate distance from current position (CurrX, CurrY)
      actions.PathCost(CurrX, CurrY, ToolX, ToolY, Dist);
      
      // 3. Continue calculation for the rest of the tools 
      // starting now from the tool's position (ToolX, ToolY)
      !calc_tools_path(Rest, ToolX, ToolY, RestCost);
      
      // 4. Sum the cost
      TotalCost = Dist + RestCost.