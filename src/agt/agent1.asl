//? INITIAL BELIEFS AND GOALS

// Tasks: task(ID, Type, Target, [RequiredTools])
task(paint_table, painting, table, [brush, color]).
task(paint_chair, painting, chair, [brush, color]).
task(open_door, opening,  door,  [key, code]).

completed(none).
episode_cost(0).
current_ep(0).
ignored_tasks([]).
busy(false).
step_count(0).
escaping_corridor(false).
yield_attempt_count(0).
max_yield_attempts(3).
safe_zone_center(2, 2).
safe_zone_tiles([loc(1,2), loc(2,1), loc(2,2), loc(2,3), loc(3,2)]).
tools_available(Tools) :- not (.member(T, Tools) & not holding(T) & not at(T, _, _)).

corridor_zone(X, Y) :- X >= 4 & Y <= 2.

narrow_corridor_position(X, Y) :-
    not obstacle(X, Y) &
    .count(D, (
        (D = up & NY = Y - 1 & NX = X) |
        (D = down & NY = Y + 1 & NX = X) |
        (D = left & NX = X - 1 & NY = Y) |
        (D = right & NX = X + 1 & NY = Y)
    ) & NX >= 0 & NX < 5 & NY >= 0 & NY < 5 & not obstacle(NX, NY), FreeNeighbors) &
    FreeNeighbors <= 2.

corridor_corner(4, 0). // This is a dead end, only exit is (4,1)


!start.

+episode(Ep) : Ep > 100
   <- .print(">>> MAX RUNS REACHED (", Ep-1, "). Stopping agents. <<<");
      .drop_all_intentions;
      .abolish(holding(_));
      .abolish(doing(_));
      .print("Final stats are visible in the console.").

+episode(Ep) :current_ep(OldEp) & Ep > OldEp & Ep <= 100
   <- -+current_ep(Ep);
      .print("--- NEW EPISODE ", Ep, " ---");
      .drop_all_intentions;
      .abolish(holding(_));
      .abolish(completed(_));
      .abolish(ignored_tasks(_));
      .abolish(propose(_, _));
      .abolish(doing(_));
      .abolish(task_completed(_));
      .abolish(task_dropped(_));
      .abolish(busy(_));
      .abolish(step_count(_));
      .abolish(cost_report(_));
      .abolish(yield_attempt_count(_));
      .abolish(escaping_corridor(_));
      -+episode_cost(0);
      +ignored_tasks([]);
      +busy(false);
      +step_count(0);
      +yield_attempt_count(0);
      +escaping_corridor(false);
      !start.

+episode(Ep) : current_ep(OldEp) & Ep == OldEp
   <- .print("DEBUG: Ignoring redundant episode signal for ", Ep).

//? MAIN CONTROL LOOP
+!start : true
   <- .print("Waiting for environment restart...");
      .wait(home(Hx, Hy)); // Wait for home percept from environment
      .wait(pos(Hx, Hy));
      .print("Environment restarted. Beginning new episode.");
      !decide_next_action.

// If I have no tasks left, wait
+!decide_next_action : open_door & painted_chair & painted_table
   <- .print("All tasks physically finished. Calculating final score...");
      !finish_episode.

+!decide_next_action : .count(task(ID,_,_,_) & not completed(ID), 0)
   <- -+busy(false);
      .print("I have no uncompleted tasks. Checking environment goals...");
      ?pos(Mx, My);
      if (Mx == 2 & My == 2) {
          !escape_to_safe_zone; 
      }
      if (open_door & painted_chair & painted_table) {
          .print("All goals are complete in environment. Finishing...");
          !finish_episode;
      } else {
          .print("Waiting for remaining goals to complete...");
          .wait(1000);
          !decide_next_action;
      }.

// Main decision loop
+!decide_next_action : true
   <- ?ignored_tasks(Blacklist); // Find candidates
      .findall(id(ID, Type, Target, Tools), (task(ID, Type, Target, Tools) & not completed(ID) & not .member(Tools, Blacklist) & tools_available(Tools)), Options);
      !select_best_option(Options).

// No options available
+!select_best_option([])
   <- if (open_door & painted_chair & painted_table) {
          .print("All goals complete. No more tasks needed.");
          !finish_episode;
      } else {
          .print("No valid tasks available right now. Waiting...");
          -+busy(false);
          ?pos(Px, Py);
          if (Px == 4 & Py == 1 & at(color, 4, 0)) {
              .print("I'm idle at (4,1) and color is still at (4,0). Escaping to (3,2) to unblock corridor...");
              -+escaping_corridor(true);
              
              .count(holding(_), TC1);
              if (TC1 == 0) { W1 = 1; } elif (TC1 == 1) { W1 = 2; } else { W1 = 4; }
              
              // Step 1: (4,1) -> (4,2)
              actions.IsBlocked(4, 2, B42);
              if (B42) {
                  .print("(4,2) blocked. Waiting...");
                  .wait(1000);
              }
              move(down);
              ?step_count(SC1);
              -+step_count(SC1 + W1);
              .print("Escape step 1: (4,1)->(4,2) cost: ", W1);
              
              // Step 2: (4,2) -> (3,2)
              .wait(200);
              actions.IsBlocked(3, 2, B32);
              if (B32) {
                  .print("(3,2) blocked. Waiting...");
                  .wait(1000);
              }
              move(left);
              ?step_count(SC2);
              -+step_count(SC2 + W1);
              .print("Escape complete! Now at (3,2)");
              
              -+escaping_corridor(false);
              .broadcast(tell, tile_cleared(4, 1));
          }
          
          .findall(Item, holding(Item), HeldItems);
          for (.member(I, HeldItems)) {
              .print("Dropping ", I);
              drop(I);
          }
          .wait(500);
          -+ignored_tasks([]); // Clear list and try again
          !decide_next_action;
      }.

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
   <- .wait(propose(OT, OC), 2000, _);
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
      };
      .abolish(propose(_, _)).

//? OUTCOME HANDLERS
// I WON: Add Cost & Execute
+!handle_outcome(win, TaskID, Cost)
   <- // Check for problematic door scenario
      if (TaskID == open_door & at(door, 4, 1)) {
          .print("Door at (4,1) detected. Adding coordination delay...");
          .wait(1500);
      }
      .print("Negotiation WON for ", TaskID);
      ?task(TaskID, _, _, RequiredTools);
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
   : completed(ID)
   <- .print("Task ", ID, " is already handled by the other agent. Skipping.").

+!execute_task(ID)
   : task(ID, Type, Target, Tools)
   <- -+busy(true);
      .print(">>> EXECUTING TASK: ", ID);
      .broadcast(tell, doing(ID));
      ?at(Target, Tx, Ty);
      // Manage inventory
      !prepare_inventory(Tools, Target);
      // Store current target for priority calculations
      -+current_target(Tx, Ty);
      // Go to target
      !go_to(Tx, Ty);
      -current_target(_,_);
      // Verification: Ensure I am still at target
      ?pos(CX, CY);
      if (CX \== Tx | CY \== Ty) {
          .print("CRITICAL: Moved away from target (at ", CX, ",", CY, ")! Aborting action.");
          .fail;
      }
      // Do the task
      !perform_action(Type, Target);
      // Update the task as completed
      +completed(ID);
      // Notify other agents that this tile is now free
      .broadcast(tell, tile_cleared(Tx, Ty));
      .broadcast(tell, task_completed(ID));  // Broadcast to other agents
      .print("Task ", ID, " completed.").

// Failure handler for execution
-!execute_task(ID)
   <- .print("FAILED to execute ", ID, ". Re-evaluating...");
      .broadcast(tell, task_dropped(ID)); // Tell others I gave up
      // Add it to ignored list so we don't retry immediately
      ?ignored_tasks(L);
      .concat(L, [ID], NewL); // Ignore this specific Task ID for now
      -+ignored_tasks(NewL);
      !decide_next_action.

+doing(ID)[source(Ag)] 
   <- +completed(ID); // Treat 'doing' as effectively 'completed' for planning purposes
      .print("Learned ", Ag, " is doing ", ID).

+task_dropped(ID)[source(Ag)]
   <- -completed(ID);
      .print("Learned ", Ag, " dropped task ", ID).

//? === INVENTORY & TOOL MANAGER === */

+!prepare_inventory(RequiredTools, Target)
   <- .findall(Item, holding(Item), CurrentItems);
      !drop_unneeded(CurrentItems, RequiredTools);
      .findall(T, (.member(T, RequiredTools) & not holding(T)), MissingTools);
      .length(MissingTools, MCount);
      !acquire_tools_strategy(MCount, MissingTools, Target).

// Strategy A: 2 tools
+!acquire_tools_strategy(2, MissingTools, Target)
   : pos(Ax, Ay) & at(Target, Tx, Ty)
   <- !sort_tools_by_cost(MissingTools, Ax, Ay, Tx, Ty, OrderedTools);
      !acquire_missing(OrderedTools).

// Strategy: Default
+!acquire_tools_strategy(_, MissingTools, Target)
   <- !acquire_missing(MissingTools).

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
   <- .print("Acquiring ", Tool);
      ?at(Tool, Tx, Ty);
      !go_to(Tx, Ty);
      pickup(Tool);
      !acquire_missing(Rest).

// Safety check: Tool not found in environment
+!acquire_missing([Tool|Rest])
   : not at(Tool, _, _) 
   <- .print("CRITICAL: I need ", Tool, " but I don't see it! Aborting task.");
      .fail.

// Safety Fallback for sorting: If calculation fails, just return original order
+!sort_tools_by_cost(Tools, _, _, _, _, Tools)
   <- .print("DEBUG: Cost sort failed or equal. Using default order.").

+!sort_tools_by_cost([T1, T2], Ax, Ay, Tx, Ty, [T1, T2])
   <- !calc_trip_sequence(T1, T2, Ax, Ay, Tx, Ty, CostA);
      !calc_trip_sequence(T2, T1, Ax, Ay, Tx, Ty, CostB);
      CostA <= CostB.

+!sort_tools_by_cost([T1, T2], Ax, Ay, Tx, Ty, [T2, T1]).

//? === HELPERS === */

+!perform_action(painting, Obj) <- paint(Obj).
+!perform_action(opening, Obj)  <- open_door.

//? === NEW STEP-BY-STEP MOVEMENT LOGIC === */

+!go_to(X, Y) : pos(X, Y) <- .print("Arrived at ", X, ",", Y).

+!go_to(Tx, Ty) 
   <- !go_to_step(Tx, Ty, 0).

+!go_to_step(Tx, Ty, WaitCount) : pos(Tx, Ty) 
   <- .print("Arrived at target: ", Tx, ",", Ty);
      // Reset yield counter on successful arrival
      -+yield_attempt_count(0).

+!go_to_step(Tx, Ty, WaitCount) : pos(Cx, Cy)
   <- .print("Thinking about path from ", Cx, ",", Cy, " to ", Tx, ",", Ty);
      // Use PathTo as a statement to handle failure via failure handler
      !do_path_step(Tx, Ty, WaitCount).

+!do_path_step(Tx, Ty, WaitCount) : pos(Cx, Cy)
   <- actions.PathTo(Cx, Cy, Tx, Ty, Path, false);
      .print("Path list: ", Path);
      if (.length(Path, L) & L > 0) {
          .nth(0, Path, Dir);
          .print("Next direction: ", Dir);
          !calculate_next_pos(Cx, Cy, Dir, Nx, Ny);
          .print("Next coordinates: ", Nx, ",", Ny);
          
          actions.IsBlocked(Nx, Ny, Blocked);
          .print("IsBlocked result for ", Nx, ",", Ny, ": ", Blocked);
          
          if (not Blocked) {
              .print("Attempting move ", Dir);
              .broadcast(tell, intend_move(Nx, Ny));
              move(Dir);
              .abolish(intend_move(_,_));
              // Track actual cost: weight based on tools held
              .count(holding(_), ToolCount);
              if (ToolCount == 0) { W = 1; }
              elif (ToolCount == 1) { W = 2; }
              else { W = 4; }
              ?step_count(S);
              -+step_count(S + W);
              .print("Step cost: ", W, " Total: ", S + W);
              !go_to_step(Tx, Ty, 0); // Reset wait count on successful move
          } else {
              if (other_agent_at(Nx, Ny)) {
                  !handle_collision(Nx, Ny, Tx, Ty, WaitCount);
              } else {
                  .wait(200);
                  !go_to_step(Tx, Ty, WaitCount);
              }
          }
      } else {
          .print("Already at target or empty path.");
          !go_to_step(Tx, Ty, 0);
      }.

// FAILURE HANDLER for do_path_step (triggers when PathTo returns false)
-!do_path_step(Tx, Ty, WaitCount)
   <- .print("No path found (PathTo failed). Checking if other agent is the cause...");
      if (other_agent_at(_, _)) {
         !handle_total_block(Tx, Ty, WaitCount);
      } else {
         .print("Unreachable target. Failing execution.");
         .fail;
      }.

+!handle_collision(Nx, Ny, Tx, Ty, WaitCount)
   <- ?pos(Cx, Cy);
      // HARDCODED CORRIDOR DEADLOCK FIX:
      // If I'm at (4,2), trying to reach (4,0), and blocked at (4,1) by another agent,
      // Back up to (4,3) to give the agent room to escape to (3,2).
      if (Cx == 4 & Cy == 2 & Tx == 4 & Ty == 0 & Nx == 4 & Ny == 1 & other_agent_at(4, 1)) {
          .print("CORRIDOR DEADLOCK: I'm at (4,2) blocking agent at (4,1) from escaping.");
          .print("Backing up to (4,3) to let them escape to (3,2)...");
          
          // Move down to (4,3)
          move(down);
          .count(holding(_), ToolCount);
          if (ToolCount == 0) { W = 1; }
          elif (ToolCount == 1) { W = 2; }
          else { W = 4; }
          ?step_count(S);
          -+step_count(S + W);
          .print("Backed up to (4,3). Cost: ", W, " Total: ", S + W);
          // Tell the blocked agent the path is clear
          .broadcast(tell, corridor_clear_you_can_escape);
          // Wait for the other agent to escape through (4,2) to (3,2)
          .print("Waiting for blocked agent to escape via (4,2) -> (3,2)...");
          .wait(3000);
          // Now continue to target
          .print("Resuming path to (4,0)...");
          !go_to_step(Tx, Ty, 0);
      } else {
          // Normal collision handling
          !calculate_priority(Tx, Ty, MyP);
          .print("Collision at ", Nx, ",", Ny, "! My Priority: ", MyP);
          .broadcast(tell, yield_request(MyP));
          // Send blocking message to stationary agent
          .broadcast(tell, blocking_my_path(Nx, Ny, Tx, Ty));
          if (WaitCount > 5) {
              .print("Persistent conflict! Trying to path AROUND the other agent...");
              // Use a goal for alternate path calculation to handle failure
              !do_alt_path(Cx, Cy, Tx, Ty);
          } else {
              .random(R);
              WaitTime = 500 + (WaitCount * 200) + (R * 500);
              .print("Collision wait: ", WaitTime);
              .wait(WaitTime);
              !go_to_step(Tx, Ty, WaitCount + 1);
          }
      }.

+!do_alt_path(Cx, Cy, Tx, Ty)
   <- actions.PathTo(Cx, Cy, Tx, Ty, AltPath, true);
      .print("Found alternate path: ", AltPath);
      !go_to_step(Tx, Ty, 0).

-!do_alt_path(Cx, Cy, Tx, Ty)
   <- .print("No alternate path found. Deadlock detected. Moving to a safe tile.");
      !step_aside;
      .wait(1000);
      !go_to_step(Tx, Ty, 0).

+!handle_total_block(Tx, Ty, WaitCount)
   <- !calculate_priority(Tx, Ty, MyP);
      .broadcast(tell, yield_request(MyP));
      .print("Path totally blocked by other agent. Priority: ", MyP, " Wait: ", WaitCount);
      if (WaitCount > 3) {
          .print("Backing off to allow room...");
          !step_aside;
          .wait(1000);
          !go_to_step(Tx, Ty, 0);
      } else {
          .wait(1000);
          !go_to_step(Tx, Ty, WaitCount + 1);
      }.

+!calculate_priority(Tx, Ty, P)
   <- .count(holding(_), Tools);
      ?pos(Cx, Cy);
      Dist = math.abs(Tx - Cx) + math.abs(Ty - Cy);
      P = (Tools * 10) + (10 - Dist).

+!calculate_next_pos(X, Y, up, X, NY) <- NY = Y - 1; +temp_next(X, Y, up, X, NY).
+!calculate_next_pos(X, Y, down, X, NY) <- NY = Y + 1; +temp_next(X, Y, down, X, NY).
+!calculate_next_pos(X, Y, left, NX, Y) <- NX = X - 1; +temp_next(X, Y, left, NX, Y).
+!calculate_next_pos(X, Y, right, NX, Y) <- NX = X + 1; +temp_next(X, Y, right, NX, Y).

+!step_aside
   <- // Increment attempt counter
      ?yield_attempt_count(Attempts);
      NewAttempts = Attempts + 1;
      -+yield_attempt_count(NewAttempts);
      ?max_yield_attempts(Max);
      if (NewAttempts > Max) {
          // Too many failed attempts
          .print("Step-aside failed ", Max, " times. Escalating to safe zone escape...");
          -+yield_attempt_count(0);  // Reset counter
          !escape_to_safe_zone;
      } else {
          // Single-step aside
          .print("Step-aside attempt ", NewAttempts, "/", Max);
          !try_single_step_aside;
      }.

+!try_single_step_aside
   <- ?pos(Cx, Cy);
      .abolish(temp_next(_, _, _, _, _));
      !calculate_next_pos(Cx, Cy, up, _, _);
      !calculate_next_pos(Cx, Cy, down, _, _);
      !calculate_next_pos(Cx, Cy, left, _, _);
      !calculate_next_pos(Cx, Cy, right, _, _);
      // Corridor-aware escape: prioritize moving DOWN/LEFT to get out
      if (corridor_zone(Cx, Cy)) {
          .findall(loc(Nx, Ny, D), (temp_next(Cx, Cy, D, Nx, Ny) & not actions.IsBlocked(Nx, Ny, true) & not corridor_corner(Nx, Ny)), GoodSafes);
          .findall(loc(Nx, Ny, D), (.member(loc(Nx,Ny,D), GoodSafes) & D == down), DownMoves);
          .findall(loc(Nx, Ny, D), (.member(loc(Nx,Ny,D), GoodSafes) & D == left), LeftMoves);
          .findall(loc(Nx, Ny, D), (.member(loc(Nx,Ny,D), GoodSafes) & D \== down & D \== left), OtherGoodMoves);
          .concat(DownMoves, LeftMoves, Temp1);
          .concat(Temp1, OtherGoodMoves, Safes);
      } else {
          // Simple escape: move to a neighbor that isn't where the other agent is and isn't a wall
          .findall(loc(Nx, Ny, D), (temp_next(Cx, Cy, D, Nx, Ny) & not actions.IsBlocked(Nx, Ny, true)), Safes);
      }

      if (.length(Safes, L) & L > 0) {
          .nth(0, Safes, loc(Sx, Sy, Dir));
          .print("Stepping aside to ", Sx, ",", Sy);
          move(Dir);
          // Track actual cost: weight based on tools held
          .count(holding(_), ToolCount);
          if (ToolCount == 0) { W = 1; }
          elif (ToolCount == 1) { W = 2; }
          else { W = 4; }
          ?step_count(S);
          -+step_count(S + W);
          .print("Yield step cost: ", W, " Total: ", S + W);
          // Success! Reset the yield counter
          -+yield_attempt_count(0);
      } else {
          .print("No room to step aside! Will escalate if this continues...");
      }.

+!escape_to_safe_zone
   <- ?pos(Cx, Cy);
      .print("Finding best escape position from ", Cx, ",", Cy);
      // Find nearest tile with good escape routes
      // First, check if we're in the corridor zone - if so, prioritize moving left/out
      if (corridor_zone(Cx, Cy)) {
          .print("In corridor zone. Prioritizing escape to open area...");
          !escape_from_corridor(Cx, Cy);
      } else {
          // Use the safe zone center as fallback
          ?safe_zone_center(Tx, Ty);
          if ((math.abs(Cx - Tx) <= 1) & (math.abs(Cy - Ty) <= 1)) {
              .print("Already near safe zone. Waiting for path to clear...");
              .wait(1000);
          } else {
              !try_escape_path(Tx, Ty);
          }
      }.

+!escape_from_corridor(Cx, Cy)
   <- // Try to move to column 2-3 which has more space
      .findall(loc(X, Y, Dist), (
          X >= 0 & X <= 3 & Y >= 0 & Y < 5 &
          not obstacle(X, Y) &
          Dist = math.abs(X - Cx) + math.abs(Y - Cy) &
          Dist > 0
      ), OpenTiles);
      // Sort by distance and find reachable tile
      !find_reachable_escape(OpenTiles, Cx, Cy).

+!find_reachable_escape([], Cx, Cy)
   <- .print("No open escape tiles found. Waiting...");
      .wait(1500).

+!find_reachable_escape([loc(X, Y, _)|Rest], Cx, Cy)
   <- if (actions.PathTo(Cx, Cy, X, Y, _, true)) {
          .print("Escaping corridor to open tile at ", X, ",", Y);
          !go_to_step(X, Y, 0);
      } else {
          !find_reachable_escape(Rest, Cx, Cy);
      }.

+!try_escape_path(Tx, Ty)
   <- ?pos(Cx, Cy);
      if (actions.PathTo(Cx, Cy, Tx, Ty, _, true)) {
          // Path exists, go there step by step
          .print("Path to safe zone found. Moving...");
          !go_to_step(Tx, Ty, 0);
      } else {
          // No path to center, try adjacent safe zone tiles
          .print("No direct path to center. Trying alternate safe tiles...");
          !try_alternate_safe_tiles;
      }.

// Failure handler for try_escape_path
-!try_escape_path(Tx, Ty)
   <- .print("Failed to path to safe zone. Trying alternate tiles...");
      !try_alternate_safe_tiles.

+!try_alternate_safe_tiles
   <- ?safe_zone_tiles(Tiles);
      ?pos(Cx, Cy);
      .findall(loc(X, Y), (
          .member(loc(X,Y), Tiles) & 
          actions.PathTo(Cx, Cy, X, Y, _, true)
      ), Reachable);
      if (.length(Reachable, L) & L > 0) {
          .nth(0, Reachable, loc(Sx, Sy));
          .print("Escaping to alternate safe tile at ", Sx, ",", Sy);
          !go_to_step(Sx, Sy, 0);
      } else {
          // Last resort: just wait for situation to resolve
          .print("No safe escape path available. Waiting...");
          .wait(2000);
      }.


// ? YIELD LOGIC

+yield_request(ReqP)[source(Ag)]
   <- ?pos(Cx, Cy);
      ?busy(IsBusy);
      // SPECIAL CASE: If I am at the dead-end corner (4,0), I can't yield!
      // The other agent must back up to let me out.
      if (Cx == 4 & Cy == 0 & other_agent_at(4, 1)) {
          .print("I am trapped at corner (4,0)! ", Ag, " at (4,1) must back up to (4,2).");
          .broadcast(tell, blocking_my_path(4, 1, 4, 2));
      } 
      // Corridor exit logic: If I am at (4,1) and Ag is at (4,2), I should NOT yield ONLY if I'm busy
      elif (Cx == 4 & Cy == 1 & other_agent_at(4, 2)) {
          if (IsBusy) {
              .print("I am at corridor bottleneck (4,1). ", Ag, " must back up from (4,2) to let me out.");
              // Don't yield. Instead, ask them to move.
              .broadcast(tell, blocking_my_path(4, 2, 3, 2));
          } else {
              .print("I am idle at corridor bottleneck (4,1). Yielding to ", Ag, ".");
              !step_aside;
          }
      } 
      elif (not IsBusy) {
          .print("I am idle. Yielding to ", Ag, " immediately.");
          !step_aside;
      } else {
          !calculate_priority_minimal(MyP);
          .my_name(Me);
          ?pos(Cx, Cy);
          // Head-on collision
          if (intend_move(Cx, Cy)[source(Ag)]) {
              .print("Detected HEAD-ON collision with ", Ag, ". Using strict tie-breaker.");
              if (Ag == agent1 & Me == agent2) {
                  .print("Yielding to ", Ag, " (Swap detected, Agent 2 yields)");
                  .wait(500);
                  !step_aside;
              } else {
                  .print("Not yielding to ", Ag, " (Swap detected, I am higher rank)");
              }
          } 
          // Regular conflict
          elif (ReqP > MyP | (math.abs(ReqP - MyP) <= 2 & Ag == agent1 & Me == agent2)) {
              .print("Yielding to ", Ag, " (", ReqP, " approx > ", MyP, ")");
              // Add a small random delay to prevent sync
              .random(R);
              .wait(R * 200 + 100);
              !step_aside;
          } else {
              .print("Not yielding to ", Ag, " (", ReqP, " <= ", MyP, ")");
          }
      };
      .abolish(yield_request(_)).

// Handle blocking_my_path message - respond when I'm blocking another agent's path
+blocking_my_path(BlockedX, BlockedY, OtherTargetX, OtherTargetY)[source(Ag)]
   <- ?pos(MyX, MyY);
      // If the trapped agent is at (4,0) and I am at (4,1), I need to back up TWO tiles (to 4,3) to let them escape, then wait
      if (other_agent_at(4, 0) & MyX == 4 & MyY == 1) {
          .print("Agent at (4,0) is trapped! I'm at (4,1). Backing up to (4,3)...");
          // First backup step to (4,2)
          move(down);
          .count(holding(_), ToolCount);
          if (ToolCount == 0) { W = 1; }
          elif (ToolCount == 1) { W = 2; }
          else { W = 4; }
          ?step_count(S);
          -+step_count(S + W);
          .print("Backup step 1 cost: ", W, " Total: ", S + W);
          // Second backup step to (4,3)
          move(down);
          ?step_count(S2);
          -+step_count(S2 + W);
          .print("Backup step 2 cost: ", W, " Total: ", S2 + W);
          // Tell the trapped agent to escape to safe zone
          .broadcast(tell, corridor_clear_escape_now);
          // Wait for the trapped agent to escape
          .print("Waiting for trapped agent to escape to safe zone...");
          .wait(2000);
      }
      // If I'm at (4,1) and need to back up for agent going to (4,0)
      elif (other_agent_at(4, 2) & MyX == 4 & MyY == 1) {
          if (escaping_corridor(true)) {
              .print("I'm escaping corridor - NOT backing up to (4,0)!");
              // Continue escaping, don't go back
          } else {
              .print("Agent pushing from (4,2). Backing up to let them through...");
              // I'm in the middle, back up toward (4,0) temporarily
              move(up);
              .count(holding(_), ToolCount);
              if (ToolCount == 0) { W = 1; }
              elif (ToolCount == 1) { W = 2; }
              else { W = 4; }
              ?step_count(S);
              -+step_count(S + W);
              .wait(1500);
          }
      }
      // Am I at the tile they can't get past?
      elif (MyX == BlockedX & MyY == BlockedY) {
          .print("I am at ", MyX, ",", MyY, " blocking ", Ag, "'s path to ", OtherTargetX, ",", OtherTargetY);
          
          // Check for HEAD-ON collision
          ?busy(IsBusy);
          
          // Check if I'm at my actual task target or just passing through
          .findall(Target, (task(ID, _, Target, _) & not completed(ID) & at(Target, MyX, MyY)), TasksHere);
          .length(TasksHere, TasksAtMyLocation);
          
          if (TasksAtMyLocation == 0 & IsBusy) {
              .print("I'm in transit (not at my task target). Checking for head-on collision...");
              // Check if the other agent is also blocking MY next step
              if (other_agent_at(OtherTargetX, OtherTargetY)) {
                  .print("HEAD-ON COLLISION detected with ", Ag, "!");
                  // Compare priorities - lower priority agent should yield
                  !calculate_priority_minimal(MyPriority);
                  .print("My priority: ", MyPriority, " Other agent requested with blocking_my_path");
                  // Agent2 always yields to Agent1 in transit conflicts
                  .my_name(Me);
                  if (Me == agent2) {
                      .print("I'm agent2. Yielding to agent1 to break head-on deadlock.");
                      !step_aside;
                  } else {
                      .print("I'm agent1. Waiting for agent2 to yield.");
                  }
              } else {
                  // I'm blocking them but they're not blocking me
                  .print("I'm in transit and blocking ", Ag, ". Yielding...");
                  !step_aside;
              }
          } 
          // Check if I've completed my current action here
          elif (not IsBusy) {
              .print("I'm idle and blocking ", Ag, ". Stepping aside immediately.");
              !step_aside;
          } else {
              // I'm busy AND at my task target - actually working here
              .findall(ID, (completed(ID) & task(ID, _, Target, _) & at(Target, MyX, MyY)), CompletedHere);
              if (.length(CompletedHere, CL) & CL > 0) {
                  .print("I completed my task here. Stepping aside for ", Ag);
                  !step_aside;
              } else {
                  .print("Still working at this location. ", Ag, " must wait.");
              }
          }
      };
      .abolish(blocking_my_path(_, _, _, _)).

// Handle corridor_clear_escape_now message - trapped agent should escape to safe zone
+corridor_clear_escape_now[source(Ag)]
   <- ?pos(Cx, Cy);
      if (corridor_corner(Cx, Cy)) {
          .print("Corridor is clear! Escaping from corner ", Cx, ",", Cy, " to safe zone...");
          // Move out through the now-clear corridor
          move(down);  // (4,0) -> (4,1)
          .count(holding(_), ToolCount);
          if (ToolCount == 0) { W = 1; }
          elif (ToolCount == 1) { W = 2; }
          else { W = 4; }
          ?step_count(S);
          -+step_count(S + W);
          // Keep moving to safe zone (toward 3,2)
          move(down);  // (4,1) -> (4,2)
          ?step_count(S2);
          -+step_count(S2 + W);
          move(left);  // (4,2) -> (3,2)
          ?step_count(S3);
          -+step_count(S3 + W);
          .print("Escaped to safe zone!");
          .broadcast(tell, tile_cleared(4, 0));
      } else {
          .print("Received escape signal but not at corridor corner.");
      };
      .abolish(corridor_clear_escape_now).

// Handle corridor_clear_you_can_escape message - agent at (4,1) should escape via (4,2) to (3,2)
+corridor_clear_you_can_escape[source(Ag)]
   <- ?pos(Cx, Cy);
      if (Cx == 4 & Cy == 1) {
          .print("Received escape signal from ", Ag, ". Escaping (4,1) -> (4,2) -> (3,2)...");
          -+escaping_corridor(true);
          // Move down to (4,2)
          move(down);
          .count(holding(_), ToolCount);
          if (ToolCount == 0) { W = 1; }
          elif (ToolCount == 1) { W = 2; }
          else { W = 4; }
          ?step_count(S);
          -+step_count(S + W);
          .print("Escape step 1: (4,1) -> (4,2). Cost: ", W);
          // Move left to (3,2)
          move(left);
          ?step_count(S2);
          -+step_count(S2 + W);
          .print("Escape step 2: (4,2) -> (3,2). Cost: ", W);
          
          -+escaping_corridor(false);
          .print("Successfully escaped corridor to (3,2)!");
          .broadcast(tell, tile_cleared(4, 1));
          .broadcast(tell, tile_cleared(4, 2));
      } else {
          .print("Received escape signal but not at (4,1). Current pos: ", Cx, ",", Cy);
      };
      .abolish(corridor_clear_you_can_escape).

// Handle tile_cleared message - wake up if we were waiting for this tile
+tile_cleared(X, Y)[source(Ag)]
   <- .print("Tile ", X, ",", Y, " cleared by ", Ag, ".");
      .abolish(tile_cleared(_, _)).

+!calculate_priority_minimal(P)
   <- if (busy(true)) {
          .count(holding(_), Tools);
          ?pos(X, Y);
          if (current_target(Tx, Ty)) {
              Dist = math.abs(X - Tx) + math.abs(Y - Ty);
          } else {
              // Fallback if target unknown
              Dist = (math.abs(X-2) + math.abs(Y-2));
          }
          P = (Tools * 10) + (10 - Dist);
      } else {
          // Idle agents have priority 0, so they always yield
          P = 0;
      }.

// FAILURE HANDLERS
-!go_to_step(Tx, Ty, _)
   <- .print("Critical Movement Error. Aborting step.");
      .fail.

+!finish_episode : episode(Ep) & Ep <= 100
   <- ?step_count(C);
      .print("Reporting my weighted steps: ", C);
      actions.CalculateEpisodeScore(C, Score);
      // Wait a bit to ensure the other agent also has time to report before reset
      .wait(500);
      .my_name(Me);
      if (Me == agent1) {
          .print("I am Agent 1. Waiting for partner to report...");
          // Wait 1-2 seconds to let Agent 2 finish reporting before we kill the episode.
          .wait(1500); 
          .print("Triggering Environment Reset now.");
          finish_episode;
      } else {
          .print("I am Agent 2. Waiting for Agent 1 to trigger reset.");
      }.

+!finish_episode : true
   <- .print("Episode finished at MAX limit. No more episodes to trigger.").

// Compare and select the better option
+!compare_and_select(ID1, C1, ID2, C2, ID1, C1) : C1 <= C2.
+!compare_and_select(ID1, C1, ID2, C2, ID2, C2) : C2 < C1.

// Listen for completion
+task_completed(ID)[source(Ag)]
   <- +completed(ID);
      .print("Learned from ", Ag, " that task ", ID, " is done.").
