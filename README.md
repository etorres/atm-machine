# ATM machine

[The ATM machine kata](https://www.codurance.com/katas/atm-machine).

An ATM programming kata is a practice exercise designed to help developers improve their skills by building a simulated Automated Teller Machine (ATM). These katas typically involve implementing core ATM functions like checking balances, withdrawing and depositing cash, and validating PINs, often with variations like handling different currencies or limited cash supply. They are used to hone skills through practice and repetition, especially using techniques like Test-Driven Development (TDD).

## TO-DO List

* ~~Record the timestamp with the audit entry~~.
* Fix the `audit` database:
  * ~~The key of the `audit_log` table should include the state.~~
  * Migrate from memory to local sink.
* ~~Test the transaction auditor.~~
* ~~Create the UI and move the decisions to the UI.~~
* Test edge cases and concurrency:
  * Property-based test 100 concurrent withdrawals.
  * Catch exceptions.
