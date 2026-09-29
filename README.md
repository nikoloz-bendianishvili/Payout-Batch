# Velmora Payout Batch Optimizer

A Spring Boot REST service that selects the combination of payout requests that maximizes total agent commission without exceeding the available payout float. Every optimization run (input and result) is persisted to PostgreSQL as an audit trail.

## Tech Stack

- Java 21, Spring Boot 4, Maven (wrapper included)
- Spring Web, Spring Data JPA, Bean Validation
- PostgreSQL 17, Flyway
- JUnit 5, AssertJ, Mockito, Testcontainers

## Prerequisites

- JDK 21
- Docker (for the database and for the Testcontainers tests)

## Database Setup

The database runs in Docker Compose (`compose.yaml`):

```bash
docker compose up -d
```

| Setting  | Value                                          |
|----------|------------------------------------------------|
| Image    | `postgres:17`                                  |
| URL      | `jdbc:postgresql://localhost:5432/payout_batch` |
| Username | `payout`                                       |
| Password | `payout`                                       |

The schema is created by Flyway on application startup from `src/main/resources/db/migration`. No manual SQL is required.

To stop the database: `docker compose down` (add `-v` to also delete the data volume).

## Build and Run

Build the JAR and run all unit and integration tests (Docker must be running):

```bash
./mvnw verify          # Windows: mvnw.cmd verify
```

To build without running the tests:

```bash
./mvnw package -DskipTests
```

Run the application (with the database already started):

```bash
java -jar target/payout-batch-optimizer-0.0.1-SNAPSHOT.jar
```

The service listens on `http://localhost:8080`.

## API

| Method | Path                                  | Description                                  | Status        |
|--------|---------------------------------------|----------------------------------------------|---------------|
| POST   | `/api/v1/payout-batches/optimize`     | Run the optimization and persist the result  | 201 / 200 / 400 |
| GET    | `/api/v1/payout-batches/{batchId}`    | Get a persisted batch by id                  | 200 / 404 / 400 |
| GET    | `/api/v1/payout-batches?page=&size=`  | Paginated audit trail, newest first          | 200 / 400     |

### 1. Optimize a payout batch

```bash
curl -i -X POST http://localhost:8080/api/v1/payout-batches/optimize \
  -H "Content-Type: application/json" \
  -d '{
    "availablePayoutFloat": 12000,
    "payoutRequests": [
      { "requestReference": "PO-3001", "payoutAmount": 4000, "agentCommission": 90 },
      { "requestReference": "PO-3002", "payoutAmount": 6000, "agentCommission": 150 },
      { "requestReference": "PO-3003", "payoutAmount": 2500, "agentCommission": 55 },
      { "requestReference": "PO-3004", "payoutAmount": 5000, "agentCommission": 115 }
    ]
  }'
```

Response `201 Created`:

```json
{
  "batchId": "3f2b8c1e-7d4a-4e6b-9a1f-2c5d8e0b4a71",
  "selectedPayouts": [
    { "requestReference": "PO-3002", "payoutAmount": 6000.00, "agentCommission": 150.00 },
    { "requestReference": "PO-3004", "payoutAmount": 5000.00, "agentCommission": 115.00 }
  ],
  "totalFloatConsumed": 11000.00,
  "totalAgentCommission": 265.00,
  "createdAt": "2026-09-07T09:00:00.123456Z"
}
```

If no combination fits within `availablePayoutFloat`, the run is still persisted and the response is `200 OK` with an empty list:

```bash
curl -i -X POST http://localhost:8080/api/v1/payout-batches/optimize \
  -H "Content-Type: application/json" \
  -d '{
    "availablePayoutFloat": 1000,
    "payoutRequests": [
      { "requestReference": "PO-3001", "payoutAmount": 4000, "agentCommission": 90 }
    ]
  }'
```

```json
{
  "batchId": "a9d04e52-1b3c-4f7e-8d26-5e0f9c7b1a34",
  "selectedPayouts": [],
  "totalFloatConsumed": 0.00,
  "totalAgentCommission": 0.00,
  "createdAt": "2026-09-07T09:05:00.654321Z"
}
```

Invalid input returns `400 Bad Request` with a descriptive message:

```bash
curl -i -X POST http://localhost:8080/api/v1/payout-batches/optimize \
  -H "Content-Type: application/json" \
  -d '{
    "availablePayoutFloat": -1,
    "payoutRequests": [
      { "requestReference": "PO-3001", "payoutAmount": 4000, "agentCommission": 90 }
    ]
  }'
```

```json
{
  "status": 400,
  "message": "Validation failed",
  "details": [
    "availablePayoutFloat: must be greater than or equal to 0"
  ],
  "timestamp": "2026-09-07T09:10:00.000000Z"
}
```

Input rules:

- `availablePayoutFloat`: required, `>= 0`, at most 12 integer digits and 2 decimal places.
- `payoutRequests`: required, not empty.
- `requestReference`: required, not blank, at most 255 characters.
- `payoutAmount`: required, `> 0`, at most 12 integer digits and 2 decimal places.
- `agentCommission`: required, `>= 0`, at most 12 integer digits and 2 decimal places.
- Malformed JSON or wrong value types also return `400`.
- A batch too large to optimize (number of requests × float in cents above the configured limit) returns `400`.

### 2. Get a batch by id

```bash
curl -i http://localhost:8080/api/v1/payout-batches/3f2b8c1e-7d4a-4e6b-9a1f-2c5d8e0b4a71
```

Response `200 OK`:

```json
{
  "batchId": "3f2b8c1e-7d4a-4e6b-9a1f-2c5d8e0b4a71",
  "selectedPayouts": [
    { "requestReference": "PO-3002", "payoutAmount": 6000.00, "agentCommission": 150.00 },
    { "requestReference": "PO-3004", "payoutAmount": 5000.00, "agentCommission": 115.00 }
  ],
  "totalFloatConsumed": 11000.00,
  "totalAgentCommission": 265.00,
  "createdAt": "2026-09-07T09:00:00.123456Z"
}
```

Unknown id, `404 Not Found`:

```bash
curl -i http://localhost:8080/api/v1/payout-batches/00000000-0000-0000-0000-000000000000
```

```json
{
  "status": 404,
  "message": "Payout batch not found: 00000000-0000-0000-0000-000000000000",
  "details": [],
  "timestamp": "2026-09-07T09:15:00.000000Z"
}
```

An id that is not a valid UUID returns `400`.

### 3. List all batches (audit trail)

Query parameters: `page` (default `0`, min `0`) and `size` (default `10`, min `1`). Results are ordered by `createdAt`, newest first.

```bash
curl -i "http://localhost:8080/api/v1/payout-batches?page=0&size=10"
```

Response `200 OK`:

```json
{
  "content": [
    {
      "batchId": "a9d04e52-1b3c-4f7e-8d26-5e0f9c7b1a34",
      "selectedPayouts": [],
      "totalFloatConsumed": 0.00,
      "totalAgentCommission": 0.00,
      "createdAt": "2026-09-07T09:05:00.654321Z"
    },
    {
      "batchId": "3f2b8c1e-7d4a-4e6b-9a1f-2c5d8e0b4a71",
      "selectedPayouts": [
        { "requestReference": "PO-3002", "payoutAmount": 6000.00, "agentCommission": 150.00 },
        { "requestReference": "PO-3004", "payoutAmount": 5000.00, "agentCommission": 115.00 }
      ],
      "totalFloatConsumed": 11000.00,
      "totalAgentCommission": 265.00,
      "createdAt": "2026-09-07T09:00:00.123456Z"
    }
  ],
  "page": {
    "size": 10,
    "number": 0,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

## Database Schema

Managed by Flyway: `src/main/resources/db/migration/V1__create_payout_batch_tables.sql`.

**`payout_batch`**: one row per optimization run.

| Column                   | Type            | Notes                                   |
|--------------------------|-----------------|-----------------------------------------|
| `id`                     | `UUID`          | Primary key, the `batchId`              |
| `available_payout_float` | `NUMERIC(19,2)` | Input float limit                       |
| `total_float_consumed`   | `NUMERIC(19,2)` | Sum of `payout_amount` of selected items |
| `total_agent_commission` | `NUMERIC(19,2)` | Sum of `agent_commission` of selected items |
| `created_at`             | `TIMESTAMPTZ`   | Time of the run                         |

**`payout_batch_item`**: one row per candidate payout request of a run, selected or not.

| Column              | Type            | Notes                                     |
|---------------------|-----------------|-------------------------------------------|
| `id`                | `BIGINT`        | Identity primary key                      |
| `payout_batch_id`   | `UUID`          | Foreign key to `payout_batch(id)`         |
| `request_reference` | `VARCHAR(255)`  |                                           |
| `payout_amount`     | `NUMERIC(19,2)` |                                           |
| `agent_commission`  | `NUMERIC(19,2)` |                                           |
| `selected`          | `BOOLEAN`       | Whether the request was chosen for release |

Each run stores its full input (`available_payout_float` and every candidate) together with the result (`selected` flag and totals), so any past decision can be audited. Money is stored as `NUMERIC(19,2)` for exact decimal values.

### Indexes

- `idx_payout_batch_created_at` on `payout_batch (created_at DESC)`: serves the list endpoint, which sorts by `created_at` newest first and paginates.
- `idx_payout_batch_item_batch_id` on `payout_batch_item (payout_batch_id)`: serves loading the items of a batch (by id and for each page of the list). PostgreSQL does not index foreign key columns automatically.

## Project Structure

```
src/main/java/com/example/payout_batch_optimizer
├── api            # PayoutBatchController, GlobalExceptionHandler
├── dto            # Request/response records with validation
├── service        # PayoutBatchService
├── optimizer      # Optimizer (0/1 knapsack algorithm), PayoutCandidate, PayoutDecision
├── entity         # PayoutBatch, PayoutBatchItem
├── persistence    # PayoutBatchRepository
└── exception      # PayoutBatchNotFoundException, BatchTooLargeException
```

## Tests

`./mvnw verify` runs:

- `OptimizerTest`: unit tests of the algorithm.
- `PayoutBatchServiceTest`: service unit tests with a mocked repository.
- `PayoutBatchControllerTest`: `@WebMvcTest` tests of the endpoints, status codes and validation.
- `PayoutBatchRepositoryTest`: repository tests against PostgreSQL (Testcontainers).
- `PayoutBatchIntegrationTest`: end-to-end POST → GET by id → list flow against PostgreSQL (Testcontainers).
