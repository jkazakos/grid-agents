//? INITIAL BELIEFS AND GOALS

// Tasks: task(ID, Type, Target, [RequiredTools])
task(paint_table, painting, table, [brush, color]).
task(paint_chair, painting, chair, [brush, color]).
task(open_door, opening,  door,  [key, code]).

completed(none).
episode_cost(0).
ignored_tasks([]).
tools_available(Tools) :- not (.member(T, Tools) & not holding(T) & not at(T, _, _)).


!start.

+episode(Ep) : Ep > 10
   <- .print(">>> MAX RUNS REACHED (", Ep-1, "). Stopping agent. <<<").

+episode(Ep) : true 
   <- .print("--- NEW EPISODE ", Ep, " ---");
      .drop_all_intentions;
      .abolish(holding(_));
      .abolish(completed(_));
      .abolish(ignored_tasks(_));
      -+episode_cost(0);
      +ignored_tasks([]);
      !start.

//? MAIN CONTROL LOOP
+!start : true
   <- .print("Waiting for environment restart...");
      .wait(home(Hx, Hy)); // Wait for home percept from environment
      .wait(pos(Hx, Hy));
      .print("Environment restarted. Beginning new episode.");
      !decide_next_action.

// If I have no tasks left, wait
+!decide_next_action : .count(task(ID,_,_,_) & not completed(ID), 0)
   <- .print("I am done. Waiting for episode end...");
      .wait(2000);
      !decide_next_action.

// Main decision loop
+!decide_next_action : true
   <- ?ignored_tasks(Blacklist); // Find candidates
      .findall(id(ID, Type, Target, Tools), (task(ID, Type, Target, Tools) & not completed(ID) & not .member(Tools, Blacklist) & tools_available(Tools)), Options);
      !select_best_option(Options).

// No options available
+!select_best_option([])
   <- .print("No valid tasks available right now. Waiting...");
      .wait(1000); // Wait for the other agent
      -+ignored_tasks([]); // Clear list and try again
      !decide_next_action.

// Options available -> Evaluate and negotiate
+!select_best_option(Options)
   <- !evaluate_options(Options, BestID, BestCost);
      .print("My best option: ", BestID, " Cost: ", BestCost);
      !negotiate(BestID, BestCost).

//? NEGOTIATION PROTOCOL
+!negotiate(MyTask, MyCost)
   <- .broadcast(tell, propose(MyTask, MyCost));
      !wait_for_opponent(MyTask, MyCost, OtherTask, OtherCost, Opponent);
      !compare_utilities(MyTask, MyCost, OtherTask, OtherCost, Opponent). // We pass MyCost to the comparison so we can use it if we win

+!wait_for_opponent(MyTask, MyCost, OT, OC, Ag)
   <- .wait(propose(OT, OC), 1000, _);
      if (.ground(OT)) {
         .print("Opponent wants: ", OT);
      } else {
         // Timeout: Other agent didn't reply (busy executing). I Win.
         !handle_outcome(win, MyTask, MyCost);
      }.

+!compare_utilities(MyTask, MyCost, OtherTask, OtherCost, Ag)
   <- .abolish(propose(_, _));
      ?task(MyTask, _, _, MyTools);
      ?task(OtherTask, _, _, OtherTools);
      .intersection(MyTools, OtherTools, SharedTools);
      // Case 1: Different task AND tools -> NO CONFLICT
      if (MyTask \== OtherTask & .empty(SharedTools)) {
          !handle_outcome(win, MyTask, MyCost);
      } 
      // Case 2: Same task OR tools -> CONFLICT -> Compare Costs
      else {
          if (MyCost < OtherCost) {
             !handle_outcome(win, MyTask, MyCost);
          } 
          elif (MyCost > OtherCost) {
             !handle_outcome(lose, MyTask, MyCost);
          } 
          else {
             // Tie -> Default to agent 1
             .my_name(Me);
             if (Me == agent1) {
                !handle_outcome(win, MyTask, MyCost);
             } else {
                !handle_outcome(lose, MyTask, MyCost);
             }
          }
      }.

//? OUTCOME HANDLERS
// I WON: Add Cost & Execute
+!handle_outcome(win, TaskID, Cost)
   <- .print("Negotiation WON for ", TaskID);
      ?task(TaskID, _, _, RequiredTools);
      ?ignored_tasks(List);
      .concat(List, [RequiredTools], NewList);
      -+ignored_tasks([]);
      !execute_task(TaskID);
      !decide_next_action.

// I LOST: Ignore Task & Retry
+!handle_outcome(lose, TaskID, Cost)
   <- .print("Negotiation LOST for ", TaskID, ". Ignoring it temporarily.");
      ?task(TaskID, _, _, RequiredTools);
      ?ignored_tasks(List);
      .concat(List, [RequiredTools], NewList);
      -+ignored_tasks(NewList);
      // Loop back immediately to find the NEXT best task
      !decide_next_action.

//? === COST EVALUATION === */

// No options left
+!evaluate_options([], none, 99999).

// Evaluate options recursively
+!evaluate_options([id(ID, Type, Target, Tools)|Rest], BestID, MinCost)
   <- // Calculate cost for current task
      !calculate_task_cost(Target, Tools, CurrentCost);
      // Print cost for debugging
      .print("Evaluated task ", ID, " with cost ", CurrentCost);
      // Evaluate the rest
      !evaluate_options(Rest, RecID, RecCost);
      // Compare and select the best
      !compare_and_select(ID, CurrentCost, RecID, RecCost, BestID, MinCost).

// Calculate cost for a single task
+!calculate_task_cost(Target, RequiredTools, FinalCost)
   : pos(Ax, Ay) & at(Target, Tx, Ty)
   <- // Find what tools are missing
      .findall(T, (.member(T, RequiredTools) & not holding(T)), MissingTools);
      .length(MissingTools, MissingCount);
      // Call specific case
      !calc_specific_case(MissingCount, MissingTools, Ax, Ay, Tx, Ty, FinalCost).

// CASE 1: HAVE EVERYTHING (MissingCount = 0)
+!calc_specific_case(0, [], Ax, Ay, Tx, Ty, FinalCost)
   <- // Agent -> Target
      actions.PathCost(Ax, Ay, Tx, Ty, Steps);
      FinalCost = Steps * 4.

// CASE 2: MISSING 1 TOOL (MissingCount = 1)
+!calc_specific_case(1, [Tool], Ax, Ay, Tx, Ty, FinalCost)
   <- ?at(Tool, ToolX, ToolY);
      // Leg 1: Agent -> Tool
      actions.PathCost(Ax, Ay, ToolX, ToolY, Dist1);
      Cost1 = Dist1 * 2;
      // Leg 2: Tool -> Target
      actions.PathCost(ToolX, ToolY, Tx, Ty, Dist2);
      Cost2 = Dist2 * 4;
      // Total Cost
      FinalCost = Cost1 + Cost2.


// CASE 3: MISSING 2 TOOLS (MissingCount = 2)
+!calc_specific_case(2, [T1, T2], Ax, Ay, Tx, Ty, FinalCost)
   <- // Sequence A: T1 -> T2 -> Target
      !calc_trip_sequence(T1, T2, Ax, Ay, Tx, Ty, CostA);
      // Sequence B: T2 -> T1 -> Target
      !calc_trip_sequence(T2, T1, Ax, Ay, Tx, Ty, CostB);
      .min([CostA, CostB], FinalCost).

// Helper for Case 3
+!calc_trip_sequence(First, Second, Ax, Ay, Tx, Ty, TotalCost)
   <- ?at(First, Fx, Fy);
      ?at(Second, Sx, Sy);
      // Leg 1: Agent -> First Tool
      actions.PathCost(Ax, Ay, Fx, Fy, D1);
      // Leg 2: First -> Second Tool
      actions.PathCost(Fx, Fy, Sx, Sy, D2);
      // Leg 3: Second -> Target
      actions.PathCost(Sx, Sy, Tx, Ty, D3);
      C1 = D1;
      C2 = 2 * D2;
      C3 = 4 * D3;
      TotalCost = C1 + C2 + C3.

//? === EXECUTION LOGIC === */

+!execute_task(ID)
   : task(ID, Type, Target, Tools)
   <- .print(">>> EXECUTING TASK: ", ID);
      .broadcast(tell, doing(ID));
      // Manage inventory
      !prepare_inventory(Tools);
      // Go to target
      ?at(Target, Tx, Ty);
      !go_to(Tx, Ty);
      // Do the task
      !perform_action(Type, Target);
      // Update the task as completed
      +completed(ID);
      .broadcast(tell, task_completed(ID));  // Broadcast to other agents
      .print("Task ", ID, " completed.").

// Failure handler for execution
-!execute_task(ID)
   <- .print("FAILED to execute ", ID, ". Re-evaluating...");
      // Add it to ignored list so we don't retry immediately
      ?ignored_tasks(L);
      .concat(L, [ID], NewL); // Ignore this specific Task ID for now
      -+ignored_tasks(NewL);
      !decide_next_action.

+doing(ID)[source(Ag)] 
   <- +completed(ID); // Treat 'doing' as effectively 'completed' for planning purposes
      .print("Learned ", Ag, " is doing ", ID).

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

// Safety check: Tool not found in environment
+!acquire_missing([Tool|Rest])
   : not at(Tool, _, _) 
   <- .print("CRITICAL: I need ", Tool, " but I don't see it! Aborting task.");
      .fail.

//? === HELPERS === */

+!perform_action(painting, Obj) <- paint(Obj).
+!perform_action(opening, Obj)  <- open_door.

// NEW MOVEMENT LOGIC WITH RESERVATIONS
+!go_to(X, Y) : pos(X, Y).
+!go_to(Tx, Ty) : pos(Sx, Sy) 
   <- actions.PathTo(Sx, Sy, Tx, Ty, PathList);
      .findall(T, holding(T), Tools);
      .length(Tools, NumTools);
      if (NumTools == 0) {
         Weight = 1;
      } else {
         Weight = 2 * NumTools;
      }
      actions.ReservePath(PathList, Weight, ResResult);
      !handle_move_attempt(ResResult, PathList, Tx, Ty).

// Case: Reservation Successful
+!handle_move_attempt(true, PathList, Tx, Ty)
   <- !move_path(PathList).

// Case: Reservation Failed (Blocked or Outweighed)
+!handle_move_attempt(false, _, Tx, Ty)
   <- .print("Path reservation FAILED (Blocked/Lower Priority). Waiting...");
      .wait(1000); 
      !go_to(Tx, Ty). // Retry calculation and reservation

// FAILURE HANDLER: If PathTo fails (returns false) or move_path fails unrecoverably
-!go_to(Tx, Ty)
   <- .print("Path calculation failed (target unreachable?). Waiting...");
      .wait(1000);
      !go_to(Tx, Ty).

+!move_path([]).
+!move_path([M|Rest])
   <- move(M);
      !move_path(Rest).

// FAILURE HANDLER: Collision detected during execution
-!move_path(Path)
   <- .print("Movement interrupted! (Reservation likely revoked). Re-planning...");
      .fail.

+!finish_episode
   <- ?episode_cost(C);
      actions.CalculateEpisodeScore(C, Score);
      .print("EPISODE FINISHED");
      .print("Episode Score: ", Score);
      .wait(200);
      finish_episode.

// Compare and select the better option
+!compare_and_select(ID1, C1, ID2, C2, ID1, C1) : C1 <= C2.
+!compare_and_select(ID1, C1, ID2, C2, ID2, C2) : C2 < C1.

// Listen for completion
+task_completed(ID)[source(Ag)]
   <- +completed(ID);
      .print("Learned from ", Ag, " that task ", ID, " is done.").
