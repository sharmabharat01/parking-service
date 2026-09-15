# Live Parking Service

A Spring Boot service that helps users find nearby Singapore car parks with currently available parking lots.

The service combines:

- Static car park information from Singapore's public data API
- Live parking availability data
- PostgreSQL with PostGIS for geospatial queries
- Scheduled availability ingestion
- Stale-data protection
- Out-of-order update protection
- Docker Compose for reproducible execution
- Automated unit, integration, and API tests

## 1. Technology Stack

- Java 21
- Spring Boot 4.1.1
- PostgreSQL 17
- PostGIS 3.5
- Flyway
- Spring JDBC
- Spring Actuator
- Maven
- Docker / Docker Compose
- Testcontainers
- MockWebServer

## 2. Prerequisites

Only Docker is required to run the application.

Java, Maven, and Gradle are **not required** on the reviewer's machine.

Verify Docker is available:

```bash
docker --version
docker compose version
```

## 3. Running the Application

Clone the repository and navigate to the project directory.

Start the complete application:

```bash
docker compose up --build
```

This starts:

- PostgreSQL with PostGIS
- The parking service

The application is available at:

```text
http://localhost:8080
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

### Initial Data Loading

On the first startup, the service loads static car park information into PostgreSQL.

Live parking availability is then populated by the scheduled ingestion job.

After starting the application, wait for the first successful live availability sync before calling the nearby parking API.

Until fresh availability data is available, the nearby endpoint may return an empty result.

If a live availability sync fails, the application remains available and the scheduler retries during the next scheduled cycle.

## 4. API

### Find Nearby Car Parks

```http
GET /api/v1/carparks/nearby
```

Returns car parks within the requested radius that:

- support car parking availability data
- currently have available car lots
- have fresh availability data
- are within the requested geographic radius

Results are ordered by distance from the requested coordinates.

### Request Parameters

| Parameter | Required | Description | Example |
|---|---|---|---|
| `latitude` | Yes | Latitude of the user's location | `1.358351` |
| `longitude` | Yes | Longitude of the user's location | `103.831086` |
| `radiusMeters` | No | Search radius in meters | `2000` |
| `page` | No | Zero-based page number | `0` |
| `pageSize` | No | Number of results per page | `10` |

Defaults:

- `radiusMeters`: `1000`
- `page`: `0`
- `pageSize`: `20`

The maximum supported search radius is configurable and defaults to `5000` meters.

`pageSize` is limited to a maximum of `100`.

### Example Request

```bash
curl "http://localhost:8080/api/v1/carparks/nearby?latitude=1.358351&longitude=103.831086&radiusMeters=2000&page=0&pageSize=10"
```

### Example Response

```json
[
  {
    "carParkNumber": "BE18",
    "address": "BLK 441-455 SIN MING AVENUE/BRIGHT HILL DRIVE",
    "latitude": 1.3583510906519656,
    "longitude": 103.83108616866849,
    "availableLots": 148,
    "distanceMeters": 0.02
  },
  {
    "carParkNumber": "BE44",
    "address": "BLK 448A BRIGHT HILL DRIVE",
    "latitude": 1.3578308220309556,
    "longitude": 103.8316301438792,
    "availableLots": 263,
    "distanceMeters": 83.52
  }
]
```

The actual values will change as the live availability source changes.

## 5. Validation and Errors

Invalid requests return HTTP `400`.

Example:

```bash
curl "http://localhost:8080/api/v1/carparks/nearby?latitude=100&longitude=103.8"
```

Response:

```json
{
  "code": "INVALID_REQUEST",
  "message": "..."
}
```

Examples of invalid input include:

- Latitude outside `[-90, 90]`
- Longitude outside `[-180, 180]`
- Non-positive search radius
- Search radius above the configured maximum
- Negative page number
- Page size outside the supported range

Unexpected server-side failures return HTTP `500` with:

```json
{
  "code": "INTERNAL_ERROR",
  "message": "Internal server error"
}
```

## 6. Architecture

The service consists of four main areas:

```text
                   ┌─────────────────────┐
                   │   Client / User     │
                   └──────────┬──────────┘
                              │
                              ▼
                   ┌─────────────────────┐
                   │   Nearby REST API   │
                   └──────────┬──────────┘
                              │
                              ▼
                   ┌─────────────────────┐
                   │  Nearby Search      │
                   │     Service         │
                   └──────────┬──────────┘
                              │
                              ▼
                   ┌─────────────────────┐
                   │ PostgreSQL +        │
                   │      PostGIS        │
                   └─────────────────────┘


 ┌───────────────────┐       ┌─────────────────────┐
 │ Static Singapore  │──────▶│ Static Ingestion    │
 │ Car Park API      │       │                     │
 └───────────────────┘       └──────────┬──────────┘
                                        │
                                        ▼
                              ┌─────────────────────┐
                              │ PostgreSQL +         │
                              │ PostGIS              │
                              └─────────────────────┘


 ┌───────────────────┐       ┌─────────────────────┐
 │ Live Availability │──────▶│ Scheduled Ingestion  │
 │ API               │       │                     │
 └───────────────────┘       └──────────┬──────────┘
                                        │
                                        ▼
                              ┌─────────────────────┐
                              │ Availability State  │
                              │ in PostgreSQL       │
                              └─────────────────────┘
```

The application is stateless from an API-serving perspective. Availability state is persisted in PostgreSQL.

## 7. Data Model

### `car_parks`

Stores relatively static information about each car park.

Important fields include:

- `carpark_number`
- `address`
- `location`
- parking configuration fields

The `location` column uses:

```text
GEOGRAPHY(POINT, 4326)
```

A GiST spatial index is used for proximity queries.

### `carpark_availability`

Stores the latest known availability for each car park and lot type.

The primary key is:

```text
(carpark_number, lot_type)
```

Supported lot types are:

- `C` - Cars
- `H` - Heavy vehicles
- `S` - Motorcycles with side car
- `Y` - Motorcycles

The nearby API currently uses the `C` lot type.

Important timestamps:

- `source_updated_at` - timestamp supplied by the external live API
- `ingested_at` - timestamp when the service stored the data

### `sync_runs`

Stores information about availability ingestion attempts, including:

- sync type
- status
- start/completion timestamps
- records received
- records updated
- records rejected
- error information

This provides basic operational visibility into ingestion.

## 8. Availability Freshness

Live availability is not treated as permanently valid.

A configurable freshness threshold is used. The default is:

```text
5 minutes
```

The nearby query only returns availability where:

```text
source_updated_at >= current_time - freshness_threshold
```

When a synchronization fails, previously stored data is retained rather than deleted.

However, once that data becomes stale, it is excluded from user-facing search results.

This avoids presenting potentially incorrect parking availability.

## 9. Handling Out-of-Order Updates

The external availability API can theoretically produce updates that arrive out of order.

For example:

```text
Update A: source timestamp 10:05
Update B: source timestamp 10:04
```

If update B arrives after update A, it must not overwrite the newer state.

The service therefore performs an atomic PostgreSQL UPSERT with a timestamp condition:

```sql
ON CONFLICT (carpark_number, lot_type)
DO UPDATE SET
    total_lots = EXCLUDED.total_lots,
    available_lots = EXCLUDED.available_lots,
    source_updated_at = EXCLUDED.source_updated_at
WHERE carpark_availability.source_updated_at
      <= EXCLUDED.source_updated_at;
```

This keeps the concurrency decision inside the database rather than relying on a non-atomic application-level read/compare/write sequence.

## 10. Nearby Search

The service uses PostGIS for geospatial filtering.

The query uses:

```text
ST_DWithin
```

to efficiently identify car parks within the requested radius.

Distance is calculated using:

```text
ST_Distance
```

against the geography representation, so the result is expressed in meters.

Results are ordered by:

1. Distance ascending
2. Car park number ascending

The second ordering criterion provides deterministic ordering when two car parks have the same distance.

Pagination is implemented using SQL `LIMIT` and `OFFSET`.

## 11. Coordinate Transformation

The static Singapore car park dataset provides coordinates in the SVY21 coordinate system.

These coordinates are transformed to WGS84 during ingestion.

The database stores the resulting coordinates as:

```text
EPSG:4326
```

This is important because the API accepts latitude/longitude and PostGIS proximity calculations operate on the stored geographic coordinates.

Coordinate transformation is covered by automated integration tests.

## 12. External API Failure Handling

The live availability API is an external dependency and can fail independently of the parking service.

The ingestion client retries transient failures such as:

- HTTP `429`
- HTTP `5xx`
- connection/read timeout failures

Non-retryable client errors are not repeatedly retried.

If all retry attempts fail:

1. The sync is marked as failed.
2. Existing availability data is preserved.
3. The application remains healthy.
4. The scheduler attempts another synchronization later.

This prevents an external API outage from taking down the user-facing API.

## 13. Static Data Ingestion

Static car park data is loaded from Singapore's public car park dataset.

The ingestion process:

1. Fetches the static dataset.
2. Validates required fields.
3. Converts source coordinates to WGS84.
4. Stores car park metadata.
5. Skips invalid records.

The service avoids repeatedly loading the static dataset on every application startup once car park data already exists.

## 14. Configuration

Important configuration can be overridden through environment variables.

Examples:

```text
PARKING_AVAILABILITY_SCHEDULER_ENABLED
PARKING_AVAILABILITY_SYNC_INTERVAL_MS
PARKING_AVAILABILITY_STALE_AFTER_SECONDS
PARKING_MAX_SEARCH_RADIUS_METERS
PARKING_LIVE_API_CONNECT_TIMEOUT_MS
PARKING_LIVE_API_READ_TIMEOUT_MS
PARKING_LIVE_API_MAX_ATTEMPTS
```

Database configuration is supplied through:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

Docker Compose supplies these values automatically.

## 15. Testing

The project contains automated tests covering:

- Coordinate transformation
- PostGIS proximity queries
- Distance ordering
- Radius filtering
- Pagination
- Available-lot filtering
- Stale availability filtering
- Out-of-order availability updates
- Concurrent database update behavior
- Invalid API requests
- External API parsing
- External API retry behavior
- Ingestion success/failure handling

The integration tests use Testcontainers with PostgreSQL/PostGIS.

The live external API client is tested independently using MockWebServer.

Run the complete test suite:

```bash
./mvnw test
```

## 16. Docker Validation

The application can be built and executed without installing Java or Maven locally:

```bash
docker compose up --build
```

To stop the application:

```bash
docker compose down
```

To stop the application and remove the PostgreSQL volume:

```bash
docker compose down -v
```

Removing the volume deletes the locally persisted database data.

## 17. Scalability

The API layer is designed to remain stateless, allowing multiple application instances to run behind a load balancer.

For higher traffic, the system can be scaled by:

- Running multiple application instances
- Increasing database connection capacity appropriately
- Maintaining the spatial GiST index
- Keeping proximity filtering inside PostGIS
- Using bounded pagination
- Separating ingestion workloads from API-serving workloads

The current implementation intentionally avoids introducing Redis, Kafka, Kubernetes, or other distributed infrastructure because the assignment does not require their complexity.

For a significantly larger production deployment, ingestion could be separated into an independent worker service and coordinated using a distributed scheduling mechanism.

## 18. Design Tradeoffs

### PostgreSQL + PostGIS

PostGIS was selected instead of calculating distances in application code because the database can efficiently perform spatial filtering and leverage a spatial index.

### Current State Instead of Availability History

The product requirement is to find currently available parking.

Therefore, only the latest valid availability state is stored.

Historical availability analytics would require a different data model and is outside the assignment scope.

### Stale Data Is Retained

Stale records are not immediately deleted.

This provides useful operational visibility and allows the system to recover naturally when the external source becomes available again.

They are simply excluded from user-facing search results once they exceed the freshness threshold.

### Atomic Database Update

The timestamp-aware UPSERT was preferred over:

```text
SELECT → compare → UPDATE
```

because the latter introduces a race window between the read and write.

Keeping the comparison inside the SQL statement makes the update atomic at the database level.

## 19. Known Limitation

The application uses a scheduled availability synchronization job within the application process.

For a larger production deployment with multiple application instances, this scheduler would need distributed coordination or would be separated into a dedicated ingestion worker to avoid duplicate ingestion work.

This is intentionally kept simple for the assignment while preserving correctness of the stored availability state.

## 20. Future Improvement

With additional development time, the ingestion path could be separated from the API-serving application.

For example:

```text
                    ┌──────────────────┐
                    │ API Instances    │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ PostgreSQL /     │
                    │ PostGIS          │
                    └────────▲─────────┘
                             │
                    ┌────────┴─────────┐
                    │ Ingestion Worker │
                    └────────▲─────────┘
                             │
                    ┌────────┴─────────┐
                    │ Live External API│
                    └──────────────────┘
```

This would allow API traffic and ingestion workload to scale independently and would make distributed scheduling, retry management, and backpressure easier to evolve.