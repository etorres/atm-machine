# ATM System: Functional DDD Reference

This project is a high-integrity, multi-module ATM simulation built with **Scala 3**, **Cats Effect**, and **Domain-Driven Design (DDD)**. It demonstrates robust handling of distributed state, transactional atomicity, and resilient local persistence.

---

## 🏗 Project Structure

The project is organized into three distinct namespaces within the `modules/` directory to ensure strict isolation of business logic and technical implementation.

### 1. ATM Namespace (`modules/atm/`)
* **`atm-domain`**: Pure business models (e.g., `AccountId` via Refined types) and domain service definitions.
* **`atm-application`**: The "Saga Orchestrator." Coordinates the withdrawal workflow, handles compensation logic (refunds), and defines repository interfaces.
* **`atm-infrastructure`**: Concrete implementations of ports.
  * `persistence.doobie`: H2 database implementation for transactional auditing.
  * `persistence.file`: JSON-based state storage with an **Atomic Corruption Guard**.
  * `ui`: The console-based user interface.
* **`app-launcher`**: The **Composition Root**. Wires all modules together and handles the system startup sequence.

### 2. Cash Namespace (`modules/cash/`)
* **`cash-domain`**: The "Shared Kernel" of money logic.
  * **Logic**: Handles `Money` value objects and `Denomination` logic.
  * **Infrastructure**: Houses the **Google OR-Tools** solver implementation for calculating optimal bill distributions.

### 3. Test Namespace (`modules/test/`)
* **`test-support`**: A dedicated module for shared testing utilities.
  * **`gen`**: ScalaCheck generators for domain types.
  * **`stubs`**: In-memory repository implementations for fast, stateful unit testing.
  * **`utils`**: Contains the `ScalaCheckShuffler` for reproducible randomized testing.

---

## 🛠 Prerequisites

To build and run this system, ensure your development environment meets the following requirements:

* **Java Development Kit (JDK) 17+**: Required for Scala 3 compatibility and modern `java.nio` features used in the Corruption Guard.
* **SBT 1.9.x+**: The project uses recent SBT features for module aggregation and scope mapping.
* **Native Library Support**:
  * The system relies on **Google OR-Tools** for optimization.
  * While the JARs bundle binaries for Linux, macOS, and Windows, your OS must allow the loading of native shared libraries (`.so`, `.dylib`, or `.dll`).
* **Hardware Architecture**: If using Docker, ensure your base image architecture (e.g., `amd64`) matches your host to avoid native binary execution errors.

---

## 🚀 Getting Started

Follow these steps to initialize the environment and launch the ATM interface.

### 1. Build the Project
Compile all modules and download dependencies (this will also pull the Google OR-Tools native binaries).
```bash
sbt compile
```

### 2. Prepare the Initial State
The system is "state-first"—it requires a `status.json` file in the project root to initialize the virtual cash drawers and bank accounts. Create the file with the following content:
```json
{
  "accounts": {
    "ACC-123": 1000.00,
    "ACC-456": 500.00
  },
  "cashInventory": {
    "50": 20,
    "20": 50,
    "10": 100
  }
}
```

### 3. Launch the ATM
Run the application launcher. The system will perform a "warm-up" (loading native binaries and initializing the H2 audit log) before presenting the CLI.
```shell
sbt "project appLauncher" run
```

## 🛡 Architectural Highlights

The project implements several advanced patterns to ensure the system is "Production-Ready" even in a local simulation environment.

### 1. The Corruption Guard
To ensure the ATM never loses its state during a power failure or crash, we use an **Atomic Write + Rotation** strategy in the infrastructure layer.
* **Reliability:** Before writing new data, the current `status.json` is cloned to a `.bak` file.
* **Atomicity:** The system writes to a `.tmp` file and performs an OS-level atomic rename to replace the actual state file. This prevents "half-written" JSON files.

### 2. Circular Dependency Management
To solve the "Chicken and Egg" problem where tests need the domain and the domain needs test utilities, we use **SBT Scope Mapping** (`compile->test`).
* **Benefit:** Production JARs remain lightweight and free of testing libraries like ScalaCheck.
* **Constraint:** The `test-support` module only depends on the abstract interfaces in the domain, not the concrete implementations.

### 3. Deterministic Randomization
Randomly shuffling bills is a side effect that usually makes tests non-deterministic. We treat randomization as a dependency.
* By injecting a `ScalaCheckShuffler` during tests, any failing edge case (e.g., a specific bill combination) can be reproduced exactly using the **ScalaCheck Seed** provided in the failure logs.

## 🧪 Testing

The project uses a "Property-First" testing philosophy to ensure the ATM handles edge cases like empty dispensers, bank timeouts, and account overflows.

### Running the Suite
To execute tests across all modules simultaneously:
```shell
sbt -v -Dfile.encoding=UTF-8 +check +test
```

### The Stack
* **MUnit & Cats Effect:** Provides the `CatsEffectSuite` for testing purely functional `IO` code without manually calling `unsafeRun`.
* **ScalaCheck:** Drives Property-Based Testing (PBT). Instead of testing one withdrawal amount, we test thousands of generated scenarios.
* **ScalaMock:** Used in the application layer to simulate hardware failures and network timeouts.

### Reproducing Failures
If a property test fails, look for the `Seed` in the console output. You can re-run that specific failing scenario by passing the seed to the shuffler in your test configuration:
```scala 3
// Example of fixing a seed for debugging
override def scalaCheckInitialSeed = "6L9z..."
```

---

## 📝 License

This project is licensed under the **MIT License**. You are free to use, modify, and distribute this code for educational or commercial purposes. See the `LICENSE` file in the root directory for the full text.

---

*Created as a reference for Domain-Driven Design and Functional Programming in Scala 3.*
