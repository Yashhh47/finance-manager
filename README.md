# Personal Finance Manager

REST API for tracking user incomes, expenses, categories, savings goals, and generating financial reports.

## Tech Stack

* Java 17
* Spring Boot 3.2
* Spring Security
* Spring Data JPA
* H2 Database

## How to run locally

```bash
./mvnw spring-boot:run
```

## API base URL

`https://finance-manager-i7us.onrender.com/api`

## How to run the test script

```bash
bash financial_manager_tests.sh https://finance-manager-i7us.onrender.com/api
```

## Design decisions

* Session-based authentication is used instead of JWT to leverage Spring Security's native HTTP session storage and simplify logout invalidation.
* An H2 in-memory database is used for fast data persistence without requiring local external database installations.
* Default categories are automatically seeded on startup using an application listener matching on empty database states.
* Goal progress calculation determines current progress by querying the sum of income minus expenses since the goal start date.
* A layered architecture separates the codebase into controllers, services, repositories, and entities to decouple logic.

## Running tests and coverage

Run tests using:
```bash
./mvnw clean test
```
The test suite achieves over 82% code coverage.
