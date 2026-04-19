# Grid Agent - Jason MAS Project

## Overview

This project implements a Multi-Agent System (MAS) using the **Jason** framework, a mature platform for developing and executing AgentSpeak-based systems. The agents are designed to autonomously operate, collaborate, and interact within a simulated grid environment. 

### Key Features

- **BDI Architecture**: Agents are built using the Belief-Desire-Intention model, allowing for sophisticated reasoning and autonomous decision-making.
- **Dynamic Grid Environment**: A simulated spatial environment where agents can perceive their surroundings, navigate securely, and interact with environmental elements.
- **Multi-Agent Collaboration**: Agents can communicate and coordinate with one another to achieve common or individual goals.

For an in-depth explanation of the system's core logic, agent behaviors, architecture, and design decisions, please refer to the accompanying formal Report and Presentation.

## Prerequisites

To build and run the project locally, please ensure the following dependencies are met:

- **Java JDK 17** (Required)
  - *Note:* The project is strictly configured for Java 17 via `build.gradle`.
  - Older versions (e.g., Java 8, 11) are **incompatible** and will result in build failures.
  - Newer LTS versions (e.g., Java 21) might work, but Java 17 is officially supported.
- **Jason Framework** (Integrated logic programming for rational agents)
- **Git Bash** (Recommended for terminal operations on Windows)
- **Gradle** (Optional, as the repository includes a Gradle wrapper)

## Getting Started

Follow these instructions to build and execute the system.

### Building the Project

To compile the project and fetch all required dependencies, execute:

```bash
gradle build
```

Alternatively, if you do not have Gradle globally installed on your system, use the provided wrapper:

```bash
./gradlew build
```

### Running the Agents

Once the build is complete, you can start the Multi-Agent System using the following command:

```bash
gradle run
```
*(Or `./gradlew run` if using the wrapper)*
