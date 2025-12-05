is_needed_for(brush, painting).
is_needed_for(color, painting).
is_needed_for(key, door).
is_needed_for(code, door).

!start.

+episode(Ep) : Ep > 10
   <- .print(">>> MAX RUNS REACHED (", Ep-1, "). Stopping agent. <<<").

+episode(Ep) : true 
   <- .print("--- NEW EPISODE ", Ep, " ---");
      .drop_all_intentions;
      .abolish(holding(_));
      .abolish(strategy(_));
      .abolish(paint_order(_));
      .abolish(tool_order(_, _));
      !start.

+!start : true
   <- .print("Waiting for synchronization...");
      .wait(pos(0, 4));
      .print("Synchronized. Planning strategy...");
      !calculate_best_strategy;
      !execute_plan.

//? ================================================================== */
//? STRATEGY PLANNING (Full Path Calculation)                          */
//? ================================================================== */

+!calculate_best_strategy
    : pos(Ax, Ay) 
    & at(brush, Bx, By) & at(color, Clx, Cly)
    & at(key, Kx, Ky)   & at(code, Cdx, Cdy)
    & at(table, Tx, Ty) & at(chair, Chx, Chy) & at(door, Dx, Dy)
   <- 
    .print("=== CALCULATING PATH COSTS ===");
    
    //? SCENARIO 1: DOOR -> TABLE -> CHAIR
    //? Leg 1: Agent -> Get Door Tools -> Go to Door
    !calc_tool_trip(Ax, Ay, Kx, Ky, key, Cdx, Cdy, code, Dx, Dy, Cost_Ag_Door, Order_Door_1);
    //? Leg 2: Door -> Get Paint Tools -> Go to Table
    !calc_tool_trip(Dx, Dy, Bx, By, brush, Clx, Cly, color, Tx, Ty, Cost_Door_Table, Order_Paint_1);
    //? Leg 3: Table -> Chair
    actions.PathCost(Tx, Ty, Chx, Chy, Steps_Tab_Ch);
    Cost_Tab_Ch = Steps_Tab_Ch * 4;
    
    Total_D_T_C = Cost_Ag_Door + Cost_Door_Table + Cost_Tab_Ch;
    .print("Path 1 (Door -> Table -> Chair): ", Total_D_T_C);

    //? SCENARIO 2: DOOR -> CHAIR -> TABLE
    //? Leg 1 is same as above (Cost_Ag_Door)
    // TODO: Separate all of the cost (no duplicates) for clarity
    //? Leg 2: Door -> Get Paint Tools -> Go to Chair
    !calc_tool_trip(Dx, Dy, Bx, By, brush, Clx, Cly, color, Chx, Chy, Cost_Door_Chair, Order_Paint_2);
    //? Leg 3: Chair -> Table
    actions.PathCost(Chx, Chy, Tx, Ty, Steps_Ch_Tab);
    Cost_Ch_Tab = Steps_Ch_Tab * 4;

    Total_D_C_T = Cost_Ag_Door + Cost_Door_Chair + Cost_Ch_Tab;
    .print("Path 2 (Door -> Chair -> Table): ", Total_D_C_T);

    //? SCENARIO 3: TABLE -> CHAIR -> DOOR
    //? Leg 1: Agent -> Get Paint Tools -> Go to Table
    !calc_tool_trip(Ax, Ay, Bx, By, brush, Clx, Cly, color, Tx, Ty, Cost_Ag_Table, Order_Paint_3);
    //? Leg 2: Table -> Chair (Same as Cost_Tab_Ch)
    //? Leg 3: Chair -> Get Door Tools -> Go to Door
    !calc_tool_trip(Chx, Chy, Kx, Ky, key, Cdx, Cdy, code, Dx, Dy, Cost_Chair_Door, Order_Door_3);

    Total_T_C_D = Cost_Ag_Table + Cost_Tab_Ch + Cost_Chair_Door;
    .print("Path 3 (Table -> Chair -> Door): ", Total_T_C_D);


    //? SCENARIO 4: CHAIR -> TABLE -> DOOR
    //? Leg 1: Agent -> Get Paint Tools -> Go to Chair
    !calc_tool_trip(Ax, Ay, Bx, By, brush, Clx, Cly, color, Chx, Chy, Cost_Ag_Chair, Order_Paint_4);
    //? Leg 2: Chair -> Table (Same as Cost_Ch_Tab)
    //? Leg 3: Table -> Get Door Tools -> Go to Door
    !calc_tool_trip(Tx, Ty, Kx, Ky, key, Cdx, Cdy, code, Dx, Dy, Cost_Table_Door, Order_Door_4);

    Total_C_T_D = Cost_Ag_Chair + Cost_Ch_Tab + Cost_Table_Door;
    .print("Path 4 (Chair -> Table -> Door): ", Total_C_T_D);

    //? SELECT BEST STRATEGY
    .min([Total_D_T_C, Total_D_C_T, Total_T_C_D, Total_C_T_D], BestCost);
    //? APPLY THE BEST STRATEGY
    !apply_strategy(BestCost, 
                    Total_D_T_C, Order_Door_1, Order_Paint_1, 
                    Total_D_C_T, Order_Door_1, Order_Paint_2, 
                    Total_T_C_D, Order_Door_3, Order_Paint_3, 
                    Total_C_T_D, Order_Door_4, Order_Paint_4).

//? Helper functions
+!calc_tool_trip(Sx, Sy, T1x, T1y, I1, T2x, T2y, I2, Ex, Ey, ResultCost, BestOrder)
   <- 
    //? Option A: Start -> Item1 -> Item2 -> End
    actions.PathCost(Sx, Sy, T1x, T1y, D_S_T1);
    actions.PathCost(T1x, T1y, T2x, T2y, D_T1_T2);
    actions.PathCost(T2x, T2y, Ex, Ey, D_T2_E);
    CostA = (D_S_T1 * 1) + (D_T1_T2 * 2) + (D_T2_E * 4);

    //? Option B: Start -> Item2 -> Item1 -> End
    actions.PathCost(Sx, Sy, T2x, T2y, D_S_T2);
    actions.PathCost(T1x, T1y, Ex, Ey, D_T1_E); //? Dist T2->T1 is same as T1->T2
    CostB = (D_S_T2 * 1) + (D_T1_T2 * 2) + (D_T1_E * 4);
    .min([CostA, CostB], ResultCost);
    !set_order(CostA, CostB, I1, I2, BestOrder).

+!set_order(CA, CB, I1, I2, I1) : CA <= CB. //? Option A is better/equal -> Pick Item 1 first
+!set_order(CA, CB, I1, I2, I2) : CB < CA.  //? Option B is better -> Pick Item 2 first

//? ================================================================== */
//? STRATEGY APPLICATION LOGIC                                         */
//? ================================================================== */

//? Case 1: Door -> Table -> Chair
+!apply_strategy(Best, D1, D_Ord, P_Ord, _, _, _, _, _, _, _, _, _) : Best == D1
   <- +strategy(door_first); +final_cost(Best); +paint_order(table_first); 
      +tool_order(door, D_Ord); +tool_order(painting, P_Ord);
      .print("Strategy: Door -> Table -> Chair. Paint Tools: ", P_Ord, " first.").

//? Case 2: Door -> Chair -> Table
+!apply_strategy(Best, _, _, _, D2, D_Ord, P_Ord, _, _, _, _, _, _) : Best == D2
   <- +strategy(door_first); +final_cost(Best); +paint_order(chair_first); 
      +tool_order(door, D_Ord); +tool_order(painting, P_Ord);
      .print("Strategy: Door -> Chair -> Table. Paint Tools: ", P_Ord, " first.").

//? Case 3: Table -> Chair -> Door
+!apply_strategy(Best, _, _, _, _, _, _, P1, D_Ord, P_Ord, _, _, _) : Best == P1
   <- +strategy(paint_first); +final_cost(Best); +paint_order(table_first);
      +tool_order(door, D_Ord); +tool_order(painting, P_Ord);
      .print("Strategy: Table -> Chair -> Door. Paint Tools: ", P_Ord, " first.").

//? Case 4: Chair -> Table -> Door
+!apply_strategy(Best, _, _, _, _, _, _, _, _, _, P2, D_Ord, P_Ord) : Best == P2
   <- +strategy(paint_first); +final_cost(Best); +paint_order(chair_first);
      +tool_order(door, D_Ord); +tool_order(painting, P_Ord);
      .print("Strategy: Chair -> Table -> Door. Paint Tools: ", P_Ord, " first.").

//? ================================================================== */
//? EXECUTION                                                          */
//? ================================================================== */

//? CASE A: PAINT FIRST
+!execute_plan : strategy(paint_first) 
   <- !run_phase(painting);
      !run_phase(door);
      !finish_episode.

//? CASE B: DOOR FIRST
+!execute_plan : strategy(door_first) 
   <- !run_phase(door);
      !run_phase(painting);
      !finish_episode.

+!finish_episode : final_cost(C)
   <- .print(">>> SUCCESS: All missions accomplished! <<<");
      actions.CalculateEpisodeScore(C, FinalScore);
      .print("Final score for this run: ", FinalScore);
      .wait(500);
      finish_episode.

+!run_phase(painting) : not painted_chair | not painted_table
   <- .print(">>> PHASE: PAINTING");
      !manage_inventory(painting);
      !get_painting_tools;
      !execute_painting_tasks.
+!run_phase(painting).

+!run_phase(door) : not open_door
   <- .print(">>> PHASE: DOOR");
      !manage_inventory(door);
      !get_door_tools;
      !open_the_door.
+!run_phase(door).

//? ================================================================== */
//? TASK MANAGEMENT                                                    */
//? ================================================================== */

//? If order is Table First
+!execute_painting_tasks : paint_order(table_first) & not painted_table
   <- !achieve_paint(table); !execute_painting_tasks.

//? If order is Chair First
+!execute_painting_tasks : paint_order(chair_first) & not painted_chair
   <- !achieve_paint(chair); !execute_painting_tasks.

//? Fallback: If specific order is done, do whatever is left
+!execute_painting_tasks : not painted_table
   <- !achieve_paint(table); !execute_painting_tasks.
+!execute_painting_tasks : not painted_chair
   <- !achieve_paint(chair); !execute_painting_tasks.

+!execute_painting_tasks. //? All done

//? Paitnting Action
+!achieve_paint(Obj) 
   <- ?at(Obj, X, Y); !go_to(X, Y); paint(Obj); .wait(200).

//? Open Door
+!open_the_door
   <- ?at(door, X, Y); !go_to(X, Y); open_door; .wait(200).

//? ================================================================== */
//? HELPERS                                                            */
//? ================================================================== */

+!go_to(X, Y) : pos(X, Y).
+!go_to(Tx, Ty) : pos(Sx, Sy) <- actions.PathTo(Sx, Sy, Tx, Ty, PathList); !execute_path(PathList).

+!execute_path([]).
+!execute_path([Move|Rest]) <- move(Move); !execute_path(Rest).

//? Acquire Painting tools
+!get_painting_tools : tool_order(painting, brush) 
   <- !ensure_have(brush); !ensure_have(color).

+!get_painting_tools : tool_order(painting, color) 
   <- !ensure_have(color); !ensure_have(brush).

//? Acquire Door tools
+!get_door_tools : tool_order(door, key) 
   <- !ensure_have(key); !ensure_have(code).

+!get_door_tools : tool_order(door, code) 
   <- !ensure_have(code); !ensure_have(key).

//? Fallback
+!get_painting_tools <- !ensure_have(brush); !ensure_have(color).
+!get_door_tools     <- !ensure_have(key); !ensure_have(code).

//? Tool Pickup
+!ensure_have(Item) : holding(Item).
+!ensure_have(Item) : not holding(Item)
   <- ?at(Item, X, Y); !go_to(X, Y); 
      pickup(Item); .wait(200); ?holding(Item); 
      .print("Picked up ", Item).

//? Inventory Cleanup
+!manage_inventory(Phase)
   <- .print("Perforing inventory cleanup for phase: ", Phase);
      .findall(I, holding(I), Inv); !clean_list(Inv, Phase).

+!clean_list([], _).

+!clean_list([Item|Rest], Phase) : not is_needed_for(Item, Phase)
   <- .print("Cleanup: Dropping ", Item, " (not needed for ", Phase, ")"); drop(Item); .wait(200); !clean_list(Rest, Phase).

+!clean_list([Item|Rest], Phase) <- !clean_list(Rest, Phase).