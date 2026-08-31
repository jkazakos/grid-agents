# Autonomous Grid World - Multi-Agent System (MAS)

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Jason MAS](https://img.shields.io/badge/Jason%20MAS-v3.2.0-2563EB?style=for-the-badge&logo=prolog&logoColor=white)](https://jason-lang.github.io/)
[![Gradle](https://img.shields.io/badge/Gradle-9.1.0-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-10B981?style=for-the-badge)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Linux%20%7C%20macOS%20%7C%20Windows-64748B?style=for-the-badge)](https://github.com/)

> A decentralized, cooperative **Multi-Agent System (MAS)** built on the **Jason (AgentSpeak)** framework and the **BDI (Belief-Desire-Intention)** rational agency model. Autonomous agents communicate, negotiate task allocations under tool dependencies and weight constraints, resolve path deadlocks in narrow corridors, and optimize global episode utility within a dynamic 2D grid world.

---

## Key Capabilities & Highlights

- **BDI Cognitive Reasoning**: Agents operate on dynamic beliefs, intentions, and internal plans programmed in **AgentSpeak(L)**.
- **Distributed Contract-Net Negotiation**: Autonomous bidding protocol where agents calculate travel & tool-retrieval costs and cooperatively assign tasks to minimize total movement cost.
- **Dynamic A\* Pathfinding & Deadlock Resolution**: Real-time pathing that accounts for static obstacles, dynamic agent occupancy, corridor bottlenecks, and cooperative yield/step-aside maneuvers.
- **Weighted Movement Costs**: Realistic physics where carrying tools increases step cost ($1\times$ for 0 items, $2\times$ for 1 item, $4\times$ for 2 items), requiring optimal route ordering (e.g. sequence sorting for multi-tool tasks).
- **Modernized Swing Grid Visualizer**: High-DPI antialiased 2D interface with dark-mode slate theme, coordinate tracking, distinct color-coded agent tokens, and status feedback.

---

## System Architecture

```mermaid
graph TD
    subgraph Environment ["Grid World Environment (Java)"]
        GridState["Grid World State (5x5)"]
        Obs["Obstacles & Safe Zones"]
        Items["Tools: Brush, Key, Code, Color"]
        Goals["Goals: Door, Table, Chair"]
    end

    subgraph Agent1 ["Agent 1 (Cyan / A1)"]
        Percepts1["Percepts & Perceptions"]
        Beliefs1[("Belief Base")]
        Planning1["Task Cost Evaluation"]
        Negotiation1["Contract-Net Protocol"]
        AStar1["Dynamic A* & Deadlock Engine"]
    end

    subgraph Agent2 ["Agent 2 (Coral / A2)"]
        Percepts2["Percepts & Perceptions"]
        Beliefs2[("Belief Base")]
        Planning2["Task Cost Evaluation"]
        Negotiation2["Contract-Net Protocol"]
        AStar2["Dynamic A* & Deadlock Engine"]
    end

    %% Environment percept flow
    GridState -->|Perceptions: pos, holding, at, obstacle| Percepts1
    GridState -->|Perceptions: pos, holding, at, obstacle| Percepts2

    Percepts1 --> Beliefs1
    Percepts2 --> Beliefs2

    Beliefs1 --> Planning1 --> Negotiation1
    Beliefs2 --> Planning2 --> Negotiation2

    %% Inter-agent communication
    Negotiation1 <-->|Propose, Win/Lose, Yield, IntendMove| Negotiation2

    Negotiation1 --> AStar1 -->|move, pickup, drop, paint, open_door| GridState
    Negotiation2 --> AStar2 -->|move, pickup, drop, paint, open_door| GridState
```

---

## Domain Specifications

### 1. Agents

| Agent                  | Theme Color                | Starting Home | Role                           |
| :--------------------- | :------------------------- | :------------ | :----------------------------- |
| **Agent 1 (`agent1`)** | `Cyan / Teal` (`#06B6D4`)  | `(0, 4)`      | Autonomous Rational Cooperator |
| **Agent 2 (`agent2`)** | `Sunset Coral` (`#F97316`) | `(2, 2)`      | Autonomous Rational Cooperator |

### 2. Tools (Pickups)

| Tool      | Symbol | Theme Color             | Target Tasks           |
| :-------- | :----: | :---------------------- | :--------------------- |
| **Brush** | `[B]`  | Amber (`#F59E0B`)       | Painting Chair & Table |
| **Key**   | `[K]`  | Gold (`#EAB308`)        | Unlocking Door         |
| **Code**  | `[Cd]` | Indigo (`#6366F1`)      | Unlocking Door         |
| **Color** | `[Cl]` | Rose / Pink (`#EC4899`) | Painting Chair & Table |

### 3. Goals & Prerequisites

| Objective       | Symbol | Theme Color          | Required Tools    | Action Functor |
| :-------------- | :----: | :------------------- | :---------------- | :------------- |
| **Unlock Door** | `[D]`  | Emerald (`#10B981`)  | `Key` + `Code`    | `open_door`    |
| **Paint Table** | `[T]`  | Violet (`#A855F7`)   | `Brush` + `Color` | `paint(table)` |
| **Paint Chair** | `[Ch]` | Sky Blue (`#0EA5E9`) | `Brush` + `Color` | `paint(chair)` |

### 4. Movement Cost Formula

Carrying payload increases physical movement strain:
$$\text{Step Cost}(n) = \begin{cases} 1 & \text{if } n = 0 \text{ tools held} \\ 2 & \text{if } n = 1 \text{ tool held} \\ 4 & \text{if } n \ge 2 \text{ tools held} \end{cases}$$

$$\text{Final Episode Utility} = R_{\text{objectives}} - \frac{\text{Total Agent Path Costs}}{100}$$

---

## Negotiation & Deadlock Protocol

```mermaid
sequenceDiagram
    autonumber
    participant A1 as Agent 1 (A1)
    participant A2 as Agent 2 (A2)
    participant Env as Grid World

    Note over A1,A2: Phase 1: Task Selection & Bidding
    A1->>A1: Calculate lowest trip cost for available tasks
    A2->>A2: Calculate lowest trip cost for available tasks
    A1->>A2: .broadcast(tell, propose(TaskA, CostA))
    A2->>A1: .broadcast(tell, propose(TaskB, CostB))

    Note over A1,A2: Phase 2: Conflict Evaluation
    alt Non-conflicting Tasks or Tools
        A1->>A1: Win & Execute TaskA
        A2->>A2: Win & Execute TaskB
    else Conflicting Task / Shared Tool
        A1->>A2: Compare Utilities (Lower Cost Wins)
        alt A1 Cost < A2 Cost
            A1->>A1: Win Task
            A2->>A2: Concede, blacklist task, pick next best
        else A2 Cost < A1 Cost
            A2->>A2: Win Task
            A1->>A1: Concede, blacklist task, pick next best
        end
    end

    Note over A1,A2: Phase 3: Dynamic Pathing & Corridor Yielding
    A1->>Env: actions.PathTo(start, target, steps)
    opt Head-on Collision / Narrow Corridor Encounter
        A1->>A2: yield_request(Priority) / blocking_my_path
        A2->>Env: move(aside) to nearest Safe Zone tile
    end
    A1->>Env: perform_action (paint / open_door)
```

---

## Getting Started

### Prerequisites

- **Java JDK 21** (or Java 17 LTS)
- **Gradle** (or use the included `./gradlew` wrapper)

### Build

Compile Java internal actions and assemble resources:

```bash
./gradlew build
```

### Run Multi-Agent System

Launch the simulation with graphical visualization and real-time console streaming:

```bash
./gradlew run
```

---

## Repository Structure

```text
grid-agents/
├── build.gradle              # Gradle build script and dependencies
├── grid_agent.mas2j          # Jason Multi-Agent System declaration
├── src/
│   ├── agt/
│   │   └── agent1.asl        # AgentSpeak(L) BDI reasoning and coordination plans
│   └── java/
│       ├── Main.java         # Application launcher and GUI stream multiplexer
│       ├── actions/          # Custom Jason internal actions
│       │   ├── CalculateEpisodeScore.java  # Multi-agent scoring and utility
│       │   ├── DirsToLocs.java             # Direction-to-coordinate mapper
│       │   ├── GetSafeTile.java            # BFS safe-tile escape finder
│       │   ├── IsBlocked.java              # Collision and obstacle inspector
│       │   ├── PathCost.java               # A* heuristic cost evaluator
│       │   └── PathTo.java                 # Dynamic A* pathfinder
│       └── env/
│           └── GridEnvironment.java       # Grid simulation model & Swing visualizer
└── README.md
```

---

## License

This project is licensed under the [MIT License](LICENSE).
