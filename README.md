# ATM System: A DDD & Functional Programming Reference

This project is a high-integrity, multi-module ATM simulation built with Scala 3, Cats Effect, and Domain-Driven Design (DDD). It demonstrates robust handling of distributed state, transactional atomicity without distributed locks, and resilient local persistence.

## Project Structure

The project is organized into a Hexagonal (Ports & Adapters) architecture across multiple SBT modules.

### Modules

* `cash-domain`: The core "Money" logic. Contains value objects (`Money`, `Denomination`) and the `NoteOptimizer` interface for note-dispensing strategies.
* `atm-domain`: ATM-specific business rules. Defines refined types (`AccountId`), domain services (`CashDispenserService`), and ports for repositories and hardware.
* `atm-application`: The "Saga Orchestrator." Coordinates withdrawal flows, compensation logic (refunds), and transaction auditing.
* `atm-infrastructure`: The technical implementation.
    * `persistence.doobie`: H2 database implementation for the `TransactionAuditor`.
    * `persistence.file`: JSON-based state storage with an atomic "Corruption Guard."
    * `ui`: Console-based interface.
* `test-support`: Shared testing utilities, including the `ScalaCheckShuffler` for reproducible property-based tests.

## Prerequisites

* JDK 25+
* SBT 1.9+
* Native Binaries: The project uses Google OR-Tools. These are bundled for major OSs, but ensure your environment allows native library loading.

## Setup & Installation

1. Clone and Compile

```shell
git clone https://github.com/etorres/atm-machine.git
cd atm-machine
sbt compile
```

2. Prepare the System Status

The ATM boots from a `status.json` file. If it doesn't exist, create it in the project root:

```json
{
  "accounts": {
    "ACC-123": 500.00,
    "ACC-456": 1200.50
  },
  "cashInventory": {
    "50": 10,
    "20": 20,
    "10": 50
  }
}
```

3. Run the Application

```shell
sbt "project app-launcher" run
```

## Key Features

### Transactional Integrity

We avoid the "Dual-Write Problem" by using a __Saga Pattern__ with `cats-effect`. If the ATM inventory update fails after the bank has been debited, the system automatically triggers a `credit` (refund) operation.

### The Corruption Guard

State is persisted locally using an __Atomic Write + Rotation__ strategy.

* Writes are performed to a `.tmp` file.
* The current file is moved to `.bak`.
* The `.tmp` is atomically renamed to `.json`. If a crash occurs mid-write, the system self-heals from the `.bak` file on next boot.

### Refined Type Safety

User inputs are validated at the "edge" of the system using __Refined Types__. An `AccountId` is guaranteed to be a non-empty string before it ever touches a service or repository.

### Reproducible Testing

By using the `test-support` module, you can run effectful property-based tests. If a specific note combination causes a failure, ScalaCheck provides the `Seed`, which the `ScalaCheckShuffler` uses to recreate the exact failure state.

## Running Tests

```shell
sbt test
```

* __Unit Tests__: Found in `src/test` of individual modules.
* __Property Tests__: Uses `scalacheck-effect` to verify business invariants across thousands of randomized scenarios.

## License

This project is licensed under the MIT License.
