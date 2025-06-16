# Cinema Ticket System - Smoke Tests

This repository contains some smoke integration tests for the Cinema Ticket System microservices architecture.

## Running Tests

- In order to run smoke tests, you need to have all services running. Which are:
    - All from [Cinema Ticket System - Microservices](https://github.com/enriquemolinari/book-microservices).
    - And then the [Cinema Ticket System - API Composition](https://github.com/enriquemolinari/book-apicomposition)
      service.
- After all are up and running:
    - cd `smoke-tests`
    - To run tests: `./mvnw test`