# AI Usage and Development Workflow

## 1. Overview

AI was used as an engineering assistant during development of this project.

The goal was not to delegate the entire solution to an AI model. The architecture, requirements, correctness criteria, tradeoffs, and final engineering decisions were reviewed and validated by the developer.

AI was primarily used to accelerate:

- Project scaffolding
- Boilerplate implementation
- Test generation
- SQL/query drafting
- Docker configuration
- Documentation
- Code review and design discussion
- Identification of edge cases

The developer remained responsible for the final design and verification.

---

# 2. Development Setup

The project was developed using:

- IntelliJ IDEA
- Java 21
- Spring Boot 4.1.1
- PostgreSQL
- PostGIS
- Maven
- Docker Desktop
- Docker Compose
- Testcontainers
- MockWebServer

AI was given the assignment requirements and relevant project context when asking for implementation or design assistance.

The development process was iterative rather than generating the entire project in one step.

---

# 3. How Work Was Divided

## Developer Responsibilities

The developer made the major product and architecture decisions, including:

- Choosing PostgreSQL + PostGIS
- Deciding to store current availability rather than historical events
- Defining stale-data behavior
- Defining out-of-order update semantics
- Choosing timestamp-aware database UPSERTs
- Deciding which availability lot type should be used for nearby car searches
- Defining API validation rules
- Deciding against unnecessary Redis/Kafka infrastructure
- Reviewing scalability and failure scenarios
- Running the application through Docker
- Validating external API behavior
- Reviewing test results
- Making the final decision on what code was accepted

The developer also explicitly challenged proposed solutions when correctness or operational behavior was unclear.

## AI-Assisted Responsibilities

AI was used for:

- Generating initial Spring Boot project structure
- Drafting Java classes and DTOs
- Drafting repository implementations
- Drafting SQL queries and Flyway migrations
- Suggesting integration and unit tests
- Drafting Dockerfile and Docker Compose configuration
- Reviewing concurrency scenarios
- Suggesting error handling
- Drafting README and design documentation
- Identifying possible edge cases
- Explaining framework behavior and implementation alternatives

The generated code was treated as a proposal rather than as automatically trusted production code.

---

# 4. Prompting Approach

The development used progressively more specific prompts instead of asking the model to generate an entire application without constraints.

Important constraints were repeatedly made explicit, including:

- Java/Spring Boot implementation
- PostgreSQL as the default database
- Docker Compose as the required runtime
- High-traffic considerations
- Concurrency correctness
- Edge-case handling
- Automated testing
- Staff-level engineering expectations
- Avoiding unnecessary infrastructure
- Maintaining simple and explainable architecture

Prompts were also used to challenge designs rather than only generate code.

Examples of questions asked during development included:

- How should stale availability be handled if the external API stops updating?
- What happens if an older availability response arrives after a newer response?
- Could two concurrent updates overwrite each other?
- Should Redis be introduced?
- Should Kafka be introduced?
- How should PostGIS handle proximity queries?
- How should SVY21 coordinates be converted to WGS84?
- What happens if the live API fails during ingestion?
- How can the reviewer run the application without Java or Maven installed?

This approach made AI part of the design-review process rather than only a code-generation tool.

---

# 5. Verification Strategy

AI-generated output was not accepted based only on whether it looked reasonable.

Verification was performed at multiple levels.

## Automated Tests

The project includes unit and integration tests covering:

- Coordinate transformation
- PostGIS proximity search
- Radius filtering
- Distance ordering
- Pagination
- Zero-availability filtering
- Stale availability
- Out-of-order updates
- Concurrent database updates
- Invalid API requests
- Live API response parsing
- Retry behavior
- Ingestion success/failure handling

The test suite was run using:

```bash
./mvnw test
```

At the point of the final repository optimization, the suite reported:

```text
Tests run: 53
Failures: 0
Errors: 0
Skipped: 0
```

The integration tests use Testcontainers with PostgreSQL/PostGIS.

External HTTP behavior is tested using MockWebServer.

---

# 6. Manual Verification

Important behavior was also verified by running the actual application rather than relying exclusively on unit tests.

The application was run through:

```bash
docker compose up --build
```

The health endpoint was verified:

```bash
curl http://localhost:8080/actuator/health
```

The response reported:

```json
{
  "status": "UP"
}
```

Database state was inspected directly to verify:

- Static car park count
- Availability records
- Freshness
- Available car lots
- PostGIS coordinates

The nearby API was also tested manually with real coordinates.

---

# 7. What Was Not Trusted to AI

Some areas were considered too important to accept without independent verification.

## Coordinate Transformation

Coordinate systems are a common source of subtle geographic bugs.

The implementation converts SVY21 coordinates to WGS84 and stores them as EPSG:4326.

This was verified using integration tests and actual nearby API results.

## Stale Availability

The freshness behavior was explicitly tested.

A record made older than the configured freshness threshold was confirmed to disappear from nearby results.

## Out-of-Order Updates

The timestamp-aware UPSERT was specifically tested because a simple application-level read/compare/write approach could introduce a race condition.

## Docker Runtime

The application was actually started through Docker Compose.

This was important because the assignment explicitly requires reviewers to run the system without installing Java, Maven, or Gradle.

## External API Behavior

The real Singapore data APIs were exercised during development.

This exposed behavior that would not have been discovered from generated code alone.

---

# 8. Example of an AI Mistake: Static API Pagination

One of the useful mistakes caught during development occurred while implementing the static car park ingestion.

The initial implementation followed the API's `_links.next` pagination links.

The static dataset contained approximately 2,272 records, but following the pagination links resulted in many HTTP requests.

The upstream API eventually returned HTTP `429 Too Many Requests`.

This was identified by running the actual ingestion against the real API.

The implementation was changed to request a sufficiently large page size:

```text
limit = 5000
```

The dataset could then be retrieved in a single request under the expected dataset size.

### Lesson

Generated code can follow an API pattern that is technically valid but operationally inappropriate.

Testing against the real dependency exposed the issue.

---

# 9. Example of an AI/Implementation Issue: Duplicate Scheduler Bean

Another issue was discovered when running the complete application through Docker.

The application initially failed during startup because the live availability scheduler was registered twice.

One registration came from a scheduling configuration class while another came from the scheduler component itself.

The Spring application context therefore attempted to create duplicate scheduler beans.

The issue was immediately visible in the Docker startup logs.

The design was simplified by:

- Removing the redundant scheduling configuration
- Enabling scheduling at the application level
- Keeping the scheduler as a single Spring component
- Applying the scheduler-enabled condition directly to that component

The application was then restarted through Docker and the health endpoint was verified successfully.

### Lesson

A design can look correct when reviewing individual classes but still fail when all framework components are assembled together.

Application-level runtime validation is therefore necessary.

---

# 10. External API Failure During Development

During development, one live availability synchronization failed while Jackson was reading the external HTTP response.

The underlying exception was:

```text
java.io.IOException: closed
```

The failure occurred while processing the response stream.

The client treated this as a retryable upstream/network failure.

The next scheduled synchronization succeeded and populated the availability table.

The successful sync processed approximately 2,400 availability records.

This reinforced the importance of treating external APIs as unreliable dependencies and preserving the existing database state when a synchronization attempt fails.

---

# 11. Why the Developer Did Not Blindly Accept the First Design

Several implementation choices were intentionally revisited during development.

For example, the initial availability repository performed an existence check for every incoming record.

For thousands of records, this resulted in a large number of database round trips.

The implementation was changed to:

1. Validate records in memory.
2. Fetch existing car parks in one query.
3. Batch the availability UPSERTs.

The complete test suite was then rerun.

This is an example of using AI iteratively:

```text
Generate
   ↓
Review
   ↓
Run
   ↓
Measure / Observe
   ↓
Identify weakness
   ↓
Refine
   ↓
Test again
```

rather than:

```text
Generate → Accept
```

---

# 12. What Was Intentionally Not Added

AI suggested or discussed several possible technologies and architectural patterns during design.

The following were intentionally not introduced:

- Redis
- Kafka
- Kubernetes
- Elasticsearch
- Event sourcing
- Distributed caching
- Complex reservation locking
- Historical event storage

These technologies can be useful in larger systems, but they were not added simply to make the architecture appear more sophisticated.

The final design focuses on the requirements actually demonstrated by the assignment.

---

# 13. Why AI Was Useful

AI was particularly useful for:

### Exploring alternatives

For example:

```text
PostGIS vs application-side distance calculation
```

and:

```text
SELECT + UPDATE vs conditional UPSERT
```

### Generating repetitive code

Examples include:

- DTOs
- repository boilerplate
- test setup
- configuration classes

### Finding edge cases

Examples include:

- stale data
- out-of-order updates
- invalid availability values
- upstream API failures
- pagination
- concurrent updates

### Documentation

AI helped structure the README, design document, and explanation of engineering decisions.

---

# 14. Human Review and Ownership

The final implementation was accepted only after:

1. Reviewing the architecture.
2. Running automated tests.
3. Running the application in Docker.
4. Testing the real API endpoints.
5. Inspecting database state.
6. Investigating runtime failures.
7. Reviewing external API behavior.
8. Reconsidering scalability and concurrency decisions.

The developer therefore retained ownership of the final solution.

AI was used as an engineering accelerator and design-review partner, not as an autonomous implementation authority.

---

# 15. Final Workflow

The overall workflow used for this assignment was:

```text
Assignment Requirements
        ↓
Architecture Discussion
        ↓
Prompt AI with Constraints
        ↓
Generate / Modify Small Increment
        ↓
Review Design and Code
        ↓
Run Tests
        ↓
Run Application
        ↓
Observe Real Behavior
        ↓
Fix / Refine
        ↓
Document Decision
        ↓
Commit
```

This workflow was chosen to balance development speed with engineering correctness.