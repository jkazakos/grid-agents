//? INITIAL BELIEFS AND GOALS

// Tasks: task(ID, Type, Target, [RequiredTools])
task(paint_table, painting, table, [brush, color]).
task(paint_chair, painting, chair, [brush, color]).
task(open_door, opening,  door,  [key, code]).

completed(none).
episode_cost(0).
ignored_tasks([]).
busy(false).
step_count(0).

// Graduated step-aside escalation
yield_attempt_count(0).
max_yield_attempts(3).
safe_zone_center(2, 2).
safe_zone_tiles([loc(1,2), loc(2,1), loc(2,2), loc(2,3), loc(3,2)]).
tools_available(Tools) :- not (.member(T, Tools) & not holding(T) & not at(T, _, _)).


!start.

+episode(Ep) : Ep > 10
   <- .print(">>> MAX RUNS REACHED (", Ep-1, "). Stopping agent. <<<");
      .stopMAS.

+episode(Ep) : Ep <= 10
   <- .print("--- NEW EPISODE ", Ep, " ---");
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
      -+episode_cost(0);
      +ignored_tasks([]);
      +busy(false);
      +step_count(0);
      +yield_attempt_count(0);
      !start.

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
   <- .print("I have no more tasks to do. Waiting for others to finish...");
      .wait(1000);
      !decide_next_action.

// Main decision loop
+!decide_next_action : true
   <- ?ignored_tasks(Blacklist); // Find candidates
      .findall(id(ID, Type, Target, Tools), (task(ID, Type, Target, Tools) & not completed(ID) & not .member(Tools, Blacklist) & tools_available(Tools)), Options);
      !select_best_option(Options).

// No options available
+!select_best_option([])
   <- .print("No valid tasks available right now. Waiting...");
      -+busy(false);
      // Drop all tools so we don't block with phantom priority
      .findall(Item, holding(Item), HeldItems);
      for (.member(I, HeldItems)) {
          .print("Dropping ", I);
          drop(I);
      }
      .wait(500);
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
   <- .print("Negotiation WON for ", TaskID);
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

      // DEBUG: Dump Beliefs about Target/Tools
      ?at(Target, Tx, Ty);
      .print("DEBUG: Belief Target ", Target, " is at ", Tx, ",", Ty);
      for ( .member(T, Tools) ) {
          if (holding(T)) {
              .print("DEBUG: Belief Tool ", T, " is held.");
          } else {
              if (at(T, ToolX, ToolY)) {
                  .print("DEBUG: Belief Tool ", T, " is at ", ToolX, ",", ToolY);
              } else {
                  .print("DEBUG: Belief Tool ", T, " location is UNKNOWN.");
              }
          }
      }
      // Manage inventory
      .print("DEBUG: Preparing inventory for ", ID);
      !prepare_inventory(Tools, Target);
      .print("DEBUG: Inventory prepared. Going to target ", Target);
      // Store current target for priority calculations
      -+current_target(Tx, Ty);
      // Go to target
      !go_to(Tx, Ty);
      -current_target(_,_);
      
      // Verification: Ensure I am still at target (might have yielded)
      ?pos(CX, CY);
      if (CX \== Tx | CY \== Ty) {
          .print("CRITICAL: Moved away from target (at ", CX, ",", CY, ")! Aborting action.");
          .fail;
      }

      // Do the task
      .print("DEBUG: At target. Performing action ", Type);
      !perform_action(Type, Target);
      // Update the task as completed
      +completed(ID);
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
      .print("DEBUG: Missing tools count: ", MCount, " Tools: ", MissingTools);
      !acquire_tools_strategy(MCount, MissingTools, Target).

// Strategy A: 2 tools
+!acquire_tools_strategy(2, MissingTools, Target)
   : pos(Ax, Ay) & at(Target, Tx, Ty)
   <- .print("DEBUG: Sorting 2 tools for optimal path...");
      !sort_tools_by_cost(MissingTools, Ax, Ay, Tx, Ty, OrderedTools);
      .print("DEBUG: Ordered tools: ", OrderedTools);
      !acquire_missing(OrderedTools).

// Strategy: Default
+!acquire_tools_strategy(_, MissingTools, Target)
   <- .print("DEBUG: Acquiring tools unsorted...");
      !acquire_missing(MissingTools).

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
   <- .print("DEBUG: Acquiring ", Tool);
      ?at(Tool, Tx, Ty);
      .print("DEBUG: Tool at ", Tx, ",", Ty);
      !go_to(Tx, Ty);
      pickup(Tool);
      .print("Picked up: ", Tool);
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
                  // Static obstacle in the way of next step? 
                  // This shouldn't happen with PathTo, but if it does, recompute
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
   <- !calculate_priority(Tx, Ty, MyP);
      .print("Collision at ", Nx, ",", Ny, "! My Priority: ", MyP);
      .broadcast(tell, yield_request(MyP));
      
      if (WaitCount > 5) {
          .print("Persistent conflict! Trying to path AROUND the other agent...");
          ?pos(Cx, Cy);
          // Use a goal for alternate path calculation to handle failure
          !do_alt_path(Cx, Cy, Tx, Ty);
      } else {
          .random(R);
          WaitTime = 500 + (WaitCount * 200) + (R * 500);
          .print("Collision wait: ", WaitTime);
          .wait(WaitTime);
          !go_to_step(Tx, Ty, WaitCount + 1);
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
          // ESCALATE: Too many failed attempts
          .print("Step-aside failed ", Max, " times. Escalating to safe zone escape...");
          -+yield_attempt_count(0);  // Reset counter
          !escape_to_safe_zone;
      } else {
          // TRY: Single-step aside (existing logic)
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
      // Simple escape: move to a neighbor that isn't where the other agent is and isn't a wall
      .findall(loc(Nx, Ny, D), (temp_next(Cx, Cy, D, Nx, Ny) & not actions.IsBlocked(Nx, Ny, true)), Safes);
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
      ?safe_zone_center(Tx, Ty);
      .print("Escaping to central safe zone at ", Tx, ",", Ty);
      
      // Check if we're already in the safe zone
      if ((math.abs(Cx - Tx) <= 1) & (math.abs(Cy - Ty) <= 1)) {
          .print("Already near safe zone. Waiting for path to clear...");
          .wait(1000);
      } else {
          // Try to path to center, avoiding the other agent
          !try_escape_path(Tx, Ty);
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
      if (not IsBusy) {
          .print("I am idle. Yielding to ", Ag, " immediately.");
          !step_aside;
      } else {
          !calculate_priority_minimal(MyP);
          .my_name(Me);
          ?pos(Cx, Cy);
          // DETECT SWAP (Head-on collision)
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
          // REGULAR CONFLICT
          elif (ReqP > MyP | (math.abs(ReqP - MyP) <= 2 & Ag == agent1 & Me == agent2)) {
              .print("Yielding to ", Ag, " (", ReqP, " approx > ", MyP, ")");
              // Add a small random wait so we don't sync up perfectly on the retry
              .random(R);
              .wait(R * 200 + 100);
              !step_aside;
          } else {
              .print("Not yielding to ", Ag, " (", ReqP, " <= ", MyP, ")");
          }
      };
      .abolish(yield_request(_)).

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

+!finish_episode : episode(Ep) & Ep <= 10
   <- ?step_count(C);
      .print("Reporting my weighted steps: ", C);
      actions.CalculateEpisodeScore(C, Score);
      // Wait a bit to ensure the other agent also has time to report before reset
      .wait(500);
      finish_episode;
      .print("Environment reset triggered.").

+!finish_episode : true
   <- .print("Episode finished at MAX limit. No more episodes to trigger.").

// Compare and select the better option
+!compare_and_select(ID1, C1, ID2, C2, ID1, C1) : C1 <= C2.
+!compare_and_select(ID1, C1, ID2, C2, ID2, C2) : C2 < C1.

// Listen for completion
+task_completed(ID)[source(Ag)]
   <- +completed(ID);
      .print("Learned from ", Ag, " that task ", ID, " is done.").
