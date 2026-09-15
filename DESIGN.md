# Design Document

## 1. Problem Statement

The service provides an API that helps users find nearby car parks in Singapore with currently available parking lots.

The system needs to:

- Find car parks within a requested geographic radius.
- Return only car parks with available parking.
- Prefer the closest car parks.
- Consume live availability from an external API.
- Handle stale and failed availability updates safely.
- Support concurrent availability updates.
- Remain responsive under high read traffic.
- Run reproducibly through Docker Compose.

The primary design goal is **correctness of parking availability combined with efficient proximity search**, while keeping the system simple enough for the assignment.

---

# 2. High-Level Architecture

```text
                         ┌─────────────────────┐
                         │       Client        │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │  Spring Boot API    │
                         │                     │
                         │ Nearby Controller   │
                         │ Nearby Service     │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │ PostgreSQL +        │
                         │      PostGIS        │
                         │                     │
                         │ car_parks           │
                         │ carpark_availability│
                         │ sync_runs            │
                         └──────────▲──────────┘
                                    │
                     ┌──────────────┴──────────────┐
                     │                             │
                     │                             │
          ┌──────────┴─────────┐        ┌──────────┴─────────┐
          │ Static Ingestion   │        │ Live Ingestion     │
          │                    │        │                    │
          │ Car park metadata  │        │ Scheduled sync     │
          └──────────┬─────────┘        └──────────┬─────────┘
                     │                             │
                     ▼                             ▼
             Static Data API              Live Availability API
```

The API-serving path and ingestion path share PostgreSQL as the source of truth for the application's current state.

---

# 3. Major Design Decisions

## 3.1 PostgreSQL + PostGIS

PostgreSQL was selected as the primary database because the application needs both:

- relational data integrity
- geospatial queries

PostGIS provides native geographic operations such as:

```text
ST_DWithin
ST_Distance
```

This allows the database to perform proximity filtering instead of loading large numbers of car parks into the application and calculating distances there.

A GiST index is created on the car park location:

```sql
CREATE INDEX idx_car_parks_location
    ON car_parks
    USING GIST (location);
```

The stored location uses:

```text
GEOGRAPHY(POINT, 4326)
```

This means distance calculations are performed in meters, which directly matches the API's `radiusMeters` parameter.

### Alternative considered

A simpler approach would be:

1. Fetch all car parks.
2. Calculate distance in Java.
3. Filter by radius.
4. Sort the results.

This is acceptable for a very small dataset but does not scale well because every request would require application-side processing.

PostGIS allows the database to perform the filtering and use a spatial index.

---

# 4. Coordinate System

The static Singapore car park dataset provides coordinates using the SVY21 coordinate system.

The source coordinates are transformed from:

```text
EPSG:3414
```

to:

```text
EPSG:4326
```

during ingestion.

The database therefore stores WGS84 latitude/longitude as a PostGIS geography point.

The transformation is performed once during ingestion rather than repeatedly during every search request.

This has two advantages:

1. Search requests remain simple and fast.
2. The stored representation matches the API's latitude/longitude contract.

Coordinate transformation is covered by automated integration tests.

---

# 5. Availability Data Model

Availability is modeled separately from relatively static car park metadata.

### `car_parks`

Contains information that changes infrequently:

- car park number
- address
- geographic location
- parking configuration

### `carpark_availability`

Contains the latest known availability for each:

```text
(carpark_number, lot_type)
```

The composite primary key prevents duplicate availability rows.

For example:

```text
BE18 + C
BE18 + H
BE18 + Y
```

are separate availability records.

The nearby search currently uses:

```text
lot_type = 'C'
```

because the user is searching for available car parking spaces.

---

# 6. Why Current State Instead of Availability History?

The primary product question is:

> "Where can I park now?"

The assignment does not require:

- historical availability analytics
- demand forecasting
- occupancy trends
- historical reporting

Therefore the database stores only the latest valid availability state.

This keeps:

- storage requirements low
- queries simple
- ingestion efficient

If historical analytics were required later, a separate append-only availability history model could be introduced without changing the user-facing API significantly.

---

# 7. Stale Availability Handling

Live availability is inherently time-sensitive.

A successful API response does not guarantee that the values remain correct indefinitely.

The service therefore uses:

```text
source_updated_at
```

to determine freshness.

The default freshness threshold is:

```text
5 minutes
```

The nearby query excludes availability older than this threshold.

Conceptually:

```sql
source_updated_at >=
    CURRENT_TIMESTAMP - freshness_threshold
```

## Why use `source_updated_at`?

The external API provides the timestamp representing when the source generated the data.

This is more meaningful for freshness than the time at which our application received the response.

For example:

```text
Source generated data: 10:00
Application received it: 10:04
```

Using ingestion time would incorrectly make the data appear newer than it actually is.

---

# 8. What Happens When the Live API Fails?

The service does **not** delete existing availability when the upstream API fails.

Instead:

1. The ingestion attempt is recorded as failed.
2. Existing availability remains in the database.
3. The application remains available.
4. The scheduler retries later.
5. Existing data eventually becomes stale.
6. Stale data is excluded from nearby search results.

This provides a useful separation between:

- operational recovery
- user-facing correctness

The system prefers returning no parking result over returning potentially incorrect stale availability.

---

# 9. Out-of-Order Updates

One of the most important correctness problems is an out-of-order update.

Consider:

```text
Update A
source_updated_at = 10:05
available_lots = 100

Update B
source_updated_at = 10:04
available_lots = 20
```

If update B arrives after update A, blindly updating the database would regress the stored state.

The service therefore compares timestamps inside the database.

The UPSERT effectively behaves as:

```sql
INSERT ...

ON CONFLICT (carpark_number, lot_type)
DO UPDATE
SET ...
WHERE existing.source_updated_at
      <= incoming.source_updated_at;
```

This means:

```text
incoming timestamp > existing timestamp
    → update

incoming timestamp = existing timestamp
    → update safely

incoming timestamp < existing timestamp
    → ignore
```

The important part is that the comparison happens inside the database operation.

---

# 10. Why Not SELECT Then UPDATE?

A tempting implementation would be:

```text
SELECT current row
        ↓
compare timestamps in Java
        ↓
UPDATE if newer
```

This introduces a race condition.

Two concurrent requests could execute:

```text
Request A              Request B

SELECT 10:05           SELECT 10:05
compare 10:06          compare 10:07
UPDATE 10:06           UPDATE 10:07
```

The result may still be correct in this example, but the read/compare/write sequence creates an unnecessary race window.

More importantly, the application becomes responsible for coordinating the correctness of concurrent updates.

The conditional UPSERT moves the critical decision into one atomic database statement.

This is simpler and safer.

---

# 11. Concurrent Availability Updates

The database primary key:

```text
(carpark_number, lot_type)
```

ensures that concurrent writes for the same availability record cannot create duplicate rows.

The conditional UPSERT then determines which version is allowed to win based on `source_updated_at`.

This gives two layers of protection:

1. Database uniqueness
2. Timestamp-aware conditional update

This is preferable to implementing concurrency control entirely in Java memory because the application may eventually run multiple instances.

---

# 12. Ingestion Optimization

The live API can return thousands of availability records.

An initial implementation performed a database existence check for each individual record.

That resulted in approximately:

```text
N existence queries
+
N upserts
```

for N availability records.

For approximately 2,400 records, this creates thousands of database operations.

The implementation was optimized to:

1. Validate records in memory.
2. Collect unique car park numbers.
3. Perform one query to identify valid car parks.
4. Perform a JDBC batch UPSERT for the valid records.

The resulting database interaction is significantly smaller:

```text
1 existence query
+
1 batch write
```

instead of performing individual existence checks.

This reduces database round trips and makes ingestion more predictable.

---

# 13. Nearby Query

The nearby query performs several operations in the database:

1. Join car park metadata with availability.
2. Select car lots.
3. Exclude zero-availability records.
4. Exclude stale availability.
5. Apply geographic radius filtering.
6. Calculate distance.
7. Order by distance.
8. Apply pagination.

The important spatial operation is:

```text
ST_DWithin
```

which performs the radius filtering.

Distance is then calculated with:

```text
ST_Distance
```

The results are ordered by:

```text
distance ASC,
carpark_number ASC
```

The second ordering field ensures deterministic pagination.

---

# 14. Pagination

The API supports:

```text
page
pageSize
```

with bounded page size.

The service defaults to:

```text
page = 0
pageSize = 20
```

and limits page size to 100.

This prevents an unbounded query from returning thousands of records in a single response.

Pagination uses:

```sql
LIMIT ?
OFFSET ?
```

For the assignment's expected result sizes this is sufficient.

### Future evolution

For extremely large result sets, keyset pagination could be considered.

A stable cursor could contain:

```text
distance
carpark_number
```

This would avoid large offsets.

---

# 15. External API Reliability

The live API is an external dependency and therefore cannot be assumed to be reliable.

The client retries transient failures including:

- HTTP 429
- HTTP 5xx
- connection failures
- read timeouts

The retry count and timeout values are configurable.

Non-retryable client errors are not repeatedly retried.

This prevents the application from aggressively retrying invalid requests while still providing resilience against temporary upstream failures.

---

# 16. Scheduler Design

Availability synchronization is triggered using a scheduled job.

The scheduler uses a fixed delay rather than a fixed rate.

Conceptually:

```text
sync
  ↓
wait configured delay
  ↓
sync
  ↓
wait configured delay
```

This avoids immediately starting another synchronization while the previous synchronization is still executing within the same application instance.

The scheduler is also configurable through:

```text
PARKING_AVAILABILITY_SCHEDULER_ENABLED
```

This makes it possible to disable scheduling during tests.

---

# 17. Why No Kafka or Redis?

Both Kafka and Redis could be useful in a much larger production system.

However, adding them here would introduce complexity without solving a demonstrated requirement.

### Redis

Redis could be used for:

- caching nearby search results
- distributed locks
- rate limiting

However, nearby results depend on frequently changing availability and geographic coordinates.

Caching would therefore introduce cache invalidation complexity.

The current PostgreSQL/PostGIS query is sufficient for the assignment.

### Kafka

Kafka could be used to:

- decouple ingestion
- buffer availability updates
- distribute ingestion work
- provide event-driven processing

However, the assignment only requires current parking state.

Adding Kafka would increase deployment and operational complexity without a current requirement for durable event streams.

The architecture leaves room to introduce it later if ingestion volume grows substantially.

---

# 18. Scaling the API

The API layer is stateless.

Therefore multiple instances can run behind a load balancer:

```text
                   Load Balancer
                  /      |      \
                 /       |       \
                ▼        ▼        ▼
             API-1    API-2    API-3
                \        |       /
                 \       |      /
                  ▼      ▼     ▼
                 PostgreSQL
```

Scaling considerations include:

- PostgreSQL connection pool sizing
- spatial indexes
- query performance
- bounded pagination
- read replicas if read traffic becomes dominant

The database remains the source of truth for current availability.

---

# 19. Scaling the Ingestion Layer

The current scheduler is intentionally embedded in the application for simplicity.

For a larger production deployment, ingestion could be separated:

```text
API instances
      │
      ▼
PostgreSQL/PostGIS
      ▲
      │
Ingestion Worker
      │
      ▼
External API
```

This provides independent scaling of:

- API traffic
- ingestion traffic

It also makes it easier to introduce:

- distributed scheduling
- backpressure
- dedicated retry handling
- ingestion-specific monitoring

A queue such as Kafka or another durable messaging system could be introduced if the external source and ingestion workload justified it.

---

# 20. Failure Scenarios

## External API unavailable

Expected behavior:

```text
External API failure
        ↓
Retry transient failure
        ↓
Retries exhausted
        ↓
Mark sync failed
        ↓
Keep previous availability
        ↓
Data eventually becomes stale
        ↓
Nearby search excludes stale data
```

The API remains operational.

---

## Database unavailable

The user-facing API cannot reliably answer availability queries without the database.

Expected behavior is to return an internal error rather than serving potentially incorrect data.

The ingestion scheduler similarly records failures through application logging and resumes on later cycles.

---

## Invalid external data

Examples include:

- missing car park number
- invalid lot type
- negative total lots
- negative available lots
- available lots greater than total lots

Such records are rejected rather than allowing invalid data to enter the database.

---

# 21. Challenging Technical Decision

The most challenging design decision was how to handle **stale and out-of-order live availability**.

A simple implementation could periodically overwrite the database with whatever the external API returned.

That creates two correctness problems:

### Problem 1: stale data

If the external API stops updating, the database continues to contain old availability.

Returning that data to users could incorrectly tell someone that parking is available.

### Problem 2: out-of-order updates

If older data arrives after newer data, the older response can overwrite the newer state.

The final design addresses both:

```text
Freshness:
source_updated_at + stale threshold

Ordering:
atomic timestamp-aware UPSERT
```

This provides a stronger correctness guarantee without introducing distributed infrastructure.

---

# 22. Investigation of Incorrect Nearby Results

One important requirement was to investigate cases where a nearby result appeared to be approximately 10 km away.

The investigation should proceed systematically rather than assuming the distance calculation is incorrect.

## Step 1: Verify request coordinates

Confirm that the API receives the expected:

```text
latitude
longitude
```

and that the values are within valid geographic ranges.

## Step 2: Verify source coordinate system

The static Singapore dataset uses SVY21 coordinates.

These must not be treated as latitude/longitude.

The source coordinates are therefore transformed from:

```text
EPSG:3414
```

to:

```text
EPSG:4326
```

during ingestion.

## Step 3: Verify database SRID

The stored column is:

```text
GEOGRAPHY(POINT, 4326)
```

The search point is also constructed using:

```text
SRID 4326
```

This ensures both geometries use the same coordinate system.

## Step 4: Verify distance units

Using PostGIS geography means:

```text
ST_Distance
```

returns meters.

Therefore the API's `radiusMeters` parameter can be passed directly to:

```text
ST_DWithin
```

without manually converting degrees to meters.

## Step 5: Verify stored coordinates

For a suspicious result, compare:

```text
request latitude/longitude
```

against:

```text
stored latitude/longitude
```

and independently calculate the expected distance.

## Step 6: Verify radius handling

Confirm that the requested radius is passed directly to `ST_DWithin` and that no accidental conversion or multiplication occurs.

### Root-cause category

The most likely cause of a result that appears approximately 10 km away is a coordinate-system or coordinate-order problem.

Typical examples include:

- treating SVY21 coordinates as WGS84
- swapping latitude and longitude
- storing an incorrect SRID
- transforming coordinates incorrectly
- using degrees as if they were meters

The implementation explicitly normalizes coordinates to WGS84 during ingestion and uses PostGIS geography for the final proximity query.

Automated integration tests cover the coordinate transformation and proximity behavior.

---

# 23. Observability

The application records operational information for availability synchronization.

Each sync run tracks:

- start time
- completion time
- status
- records received
- records updated
- records rejected
- error information

Application logs also include the sync result.

Spring Boot Actuator exposes health information so the container can be checked independently from the business API.

---

# 24. Security Considerations

The current assignment exposes a read-only nearby parking API and does not require user authentication.

The application does not store:

- user accounts
- payment information
- vehicle information
- personal information

Database credentials are supplied through environment variables in Docker Compose rather than being embedded in Java source code.

For a production deployment, additional controls could include:

- API authentication/authorization if required
- TLS termination
- secrets management
- rate limiting
- network-level database isolation

---

# 25. Assumptions

The implementation makes the following assumptions:

1. The Singapore public APIs are the source of truth for car park metadata and live availability.
2. The live availability timestamp is authoritative for determining freshness.
3. A 5-minute freshness threshold is acceptable for the assignment.
4. The nearby parking API is interested in car lots (`C`).
5. Current availability is more important than historical availability.
6. PostgreSQL/PostGIS can support the expected traffic for the assignment.
7. A scheduled ingestion interval of one minute is sufficient for the current use case.
8. Users prefer no result over knowingly stale availability.

---

# 26. What I Would Improve With More Time

The first improvement I would make is to **separate ingestion from the API-serving application**.

The current implementation keeps the scheduler inside the Spring Boot application because it keeps the deployment simple.

In a larger system, this creates coupling between:

```text
API serving
```

and:

```text
external data ingestion
```

Separating the two would allow:

- independent scaling
- independent deployment
- better retry/backpressure handling
- distributed scheduling
- clearer operational ownership

The user-facing API could remain a stateless service while ingestion runs as a dedicated worker.

I would make this change before introducing caching or additional infrastructure because ingestion isolation provides a more direct scalability benefit for this particular workload.

---

# 27. Summary

The design intentionally favors **correctness, simplicity, and explainability**.

The key decisions are:

```text
PostgreSQL + PostGIS
        ↓
Efficient geographic search

source_updated_at
        ↓
Freshness + ordering protection

Atomic conditional UPSERT
        ↓
Concurrency-safe availability updates

Stale data retained but hidden
        ↓
Graceful upstream failure handling

Stateless API
        ↓
Horizontal scalability

Docker Compose
        ↓
Reproducible reviewer environment
```

The architecture avoids introducing distributed infrastructure unless the workload demonstrates a need for it.

## Future scalability: spatial partitioning and caching

The current implementation uses PostGIS with a GiST spatial index for nearby
searches. Given the current dataset size, this provides a simple and efficient
solution without introducing additional infrastructure.

If the number of car parks or geographic search traffic grows significantly,
the search space could be reduced using spatial partitioning. For example,
the geographic area could be divided into cells using a scheme such as
geohash or H3, with each car park associated with a cell.

For an incoming request, the service could first determine the cell containing
the requested coordinates and then identify the relevant neighboring cells.
The search would then be limited to those candidate cells before performing
the exact PostGIS distance calculation.

Searching neighboring cells is important because restricting a request to
only its own cell could miss a closer car park located just across a cell
boundary.

Frequently requested cells could also be cached, potentially in Redis, to
reduce repeated database queries for popular locations.

However, availability changes frequently, so caching introduces a freshness
and invalidation problem. A cached result could become stale shortly after it
is generated. Therefore, caching would need to incorporate the same freshness
considerations as the underlying availability data.

This optimization is intentionally out of scope for the current
implementation. The current PostGIS-based approach is simpler and sufficient
for the dataset size and requirements of this assignment. Spatial
partitioning and caching would be considered based on measured traffic,
database load, and query latency rather than introduced prematurely.