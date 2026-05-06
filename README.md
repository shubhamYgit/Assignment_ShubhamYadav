# Backend Engineering Assignment

A high-performance Spring Boot microservice implementing a post/comment API with Redis-backed guardrails for bot interaction control, real-time virality scoring, and smart notification batching.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.3, Java 17 |
| Database | PostgreSQL 16 |
| Cache / State | Redis 7 |
| Build | Maven |
| Testing | JUnit 5, Testcontainers |

---

## Quick Start

### Prerequisites

- Java 17+
- Docker & Docker Compose

### Run

```bash
docker compose up -d          # starts Postgres + Redis
./mvnw spring-boot:run        # starts the API on http://localhost:8080
```

The app auto-seeds test data on startup:

| Type | ID | Name |
|------|----|------|
| User | 1 | alice |
| User | 2 | bob (premium) |
| Bot | 1–200 | Bot 1 through Bot 200 |

---

## API Endpoints

### `POST /api/posts` — Create a post

```json
{
  "authorId": 1,
  "authorType": "USER",
  "content": "Hello world"
}
```

**Response** `201 Created`

```json
{
  "id": 1,
  "authorId": 1,
  "authorType": "USER",
  "content": "Hello world",
  "createdAt": "2026-05-06T14:00:00Z"
}
```

---

### `POST /api/posts/{postId}/comments` — Add a comment

```json
{
  "authorId": 1,
  "authorType": "BOT",
  "parentCommentId": null,
  "content": "Bot reply"
}
```

**Response** `201 Created` — Returns the saved comment with its computed `depthLevel`.

**Error** `429 Too Many Requests` — Returned when any guardrail is violated (horizontal cap, vertical cap, or cooldown).

---

### `POST /api/posts/{postId}/like` — Like a post

```json
{
  "userId": 1
}
```

**Response** `200 OK`

```json
{
  "postId": 1,
  "userId": 1,
  "viralityScore": 20
}
```

---

## Architecture

### Database Schema (PostgreSQL)

```
┌──────────┐       ┌──────────────┐
│  users   │       │     bots     │
├──────────┤       ├──────────────┤
│ id (PK)  │       │ id (PK)      │
│ username │       │ name         │
│ is_premium│      │ persona_desc │
└──────────┘       └──────────────┘

┌────────────────────┐      ┌─────────────────────────┐
│       posts        │      │        comments         │
├────────────────────┤      ├─────────────────────────┤
│ id (PK)            │◄─────│ post_id (FK)            │
│ author_id          │      │ id (PK)                 │
│ author_type (ENUM) │      │ parent_comment_id (FK)  │
│ content            │      │ author_id               │
│ created_at         │      │ author_type (ENUM)      │
└────────────────────┘      │ content                 │
                            │ depth_level             │
                            │ created_at              │
                            └─────────────────────────┘
```

`author_id` is polymorphic — it references either `users.id` or `bots.id` depending on `author_type`. This avoids the complexity of two separate FK columns while keeping the schema clean.

### Redis Key Layout

| Key | Type | Purpose |
|-----|------|---------|
| `post:{id}:virality_score` | String (integer) | Real-time virality score |
| `post:{id}:bot_count` | String (integer) | Atomic bot reply counter per post |
| `cooldown:bot_{id}:human_{id}` | String | 10-minute bot↔human interaction cooldown |
| `user:{id}:notif_cooldown` | String | 15-minute notification throttle |
| `user:{id}:pending_notifs` | List | Queued notification messages for batching |

---

## Guardrails

### Virality Score

Every interaction atomically updates the post's virality score in Redis via `INCRBY`:

| Interaction | Points |
|------------|--------|
| Bot Reply | +1 |
| Human Like | +20 |
| Human Comment | +50 |

### Horizontal Cap

A single post cannot receive more than **100 bot replies**. Enforced atomically in Redis.

### Vertical Cap

Comment threads cannot exceed **20 levels** of depth. Checked before persistence; rejected with `429`.

### Cooldown Cap

A specific bot cannot interact with a specific human more than once per **10 minutes**. Enforced via a Redis key with TTL.

---

## Thread Safety — How the 200-Bot Spam Test is Guaranteed

This is the critical design decision. All bot comment requests — regardless of whether the target is a human or another bot — are gated through a **single atomic Lua script** executed on Redis (`src/main/resources/redis/reserve_bot_reply.lua`).

### The Lua Script

The script accepts:
- `KEYS[1]` — the bot count key (`post:{id}:bot_count`)
- `KEYS[2]` — the cooldown key (`cooldown:bot_{id}:human_{id}`)
- `ARGV[1]` — the horizontal cap limit (100)
- `ARGV[2]` — cooldown TTL in seconds (600)
- `ARGV[3]` — skip-cooldown flag (`"1"` or `"0"`)

Execution flow:

1. **Cooldown check** — If `skip_cooldown` is `"0"`, check `EXISTS KEYS[2]`. If the cooldown key exists, return `-1` (rejected).
2. **Pre-check** — `GET KEYS[1]`. If current count ≥ limit, return `-2` (cap exceeded) without modifying anything.
3. **Atomic reserve** — `INCR KEYS[1]`. If the incremented value exceeds the limit (race between step 2 and 3), `DECR` and return `-2`.
4. **Set cooldown** — If cooldown is not skipped, `SET KEYS[2] 1 EX ttl NX`. If the `NX` fails (another thread set it first), `DECR KEYS[1]` to release the reserved slot and return `-1`.
5. **If cooldown is skipped** — return immediately after the INCR succeeds (step 3).
6. **Success** — return the new bot count.

### Why this is safe

- **Redis executes Lua scripts atomically** — no other command can interleave between the `GET`, `INCR`, and `SET`. This means 200 concurrent threads hitting the same post will each see a consistent, serialized view of the counter.
- **Every bot comment path uses the script** — when a bot replies to another bot's content (no human target), the Java code passes `skip_cooldown = "1"` so the cooldown logic is bypassed, but the atomic `INCR` check still runs.
- **Compensating rollback** — if the PostgreSQL write fails after the Redis reservation succeeds, the service decrements the bot count and deletes the cooldown key, keeping Redis and Postgres in sync.
- **Virality rollback** — if any step after the virality score increment throws, the score is decremented before re-throwing.

### Data Integrity Guarantee

```
Redis reservation (Lua) ──► PostgreSQL write ──► Virality increment ──► Notification
        │                         │                      │
        │                    fails?                  fails?
        │                      │                      │
        └── release() ◄────────┘    decrementScore() ◄┘
```

PostgreSQL is only written to after the Redis guardrails approve the action. If the DB write fails, the Redis reservation is released. The application is fully **stateless** — all mutable state lives in Redis.

---

## Notification Engine

### Throttled Push Notifications

When a bot interacts with a human's post or comment:

1. **Check cooldown** — `SET user:{id}:notif_cooldown 1 NX EX 900` (15 minutes)
2. **If key was created** (first notification in window) → log immediate push notification
3. **If key already existed** → append message to `user:{id}:pending_notifs` Redis list

### CRON Sweeper

A `@Scheduled` task runs every **5 minutes** (configurable via `assignment.notifications.sweep-rate-ms`):

1. `SCAN` for all keys matching `user:*:pending_notifs`
2. For each key, `LPOP` all messages
3. Log a summarized notification:
   - 1 message: `"Summarized Push Notification: Bot X interacted with your posts."`
   - N messages: `"Summarized Push Notification: Bot X and [N-1] others interacted with your posts."`
4. `DEL` the pending list

---

## Testing

### Concurrency Test

`HorizontalCapConcurrencyTest` uses **Testcontainers** to spin up a real Redis instance and fires **200 concurrent bot comment requests** against a single post using a `CountDownLatch` barrier:

```bash
./mvnw test -Dtest=HorizontalCapConcurrencyTest
```

Assertions:
- Exactly **100** comments accepted
- Exactly **100** requests rejected with `429`
- PostgreSQL contains exactly **100** rows
- Redis `post:{id}:bot_count` equals `"100"`

> Requires Docker to be running for Testcontainers.

---

## Project Structure

```
src/main/java/org/shubh/assignment/
├── AssignmentApplication.java          # Entry point, @EnableScheduling
├── api/                                # Request/Response DTOs (Java records)
│   ├── CreatePostRequest.java
│   ├── AddCommentRequest.java
│   ├── LikePostRequest.java
│   ├── PostResponse.java
│   ├── CommentResponse.java
│   └── LikePostResponse.java
├── config/
│   └── RedisConfig.java                # Lua script bean
├── domain/                             # JPA entities
│   ├── User.java
│   ├── Bot.java
│   ├── Post.java
│   ├── Comment.java
│   └── AuthorType.java
├── repository/                         # Spring Data JPA repositories
├── service/
│   ├── AssignmentService.java          # Core business logic
│   ├── BotGuardrailService.java        # Redis atomic locks via Lua
│   ├── ViralityService.java            # Virality score management
│   ├── NotificationService.java        # Throttled notifications + sweeper
│   ├── InteractionType.java            # Score deltas enum
│   └── SeedData.java                   # Startup data seeder
└── web/
    ├── PostController.java             # REST controller
    ├── GlobalExceptionHandler.java     # Exception → HTTP status mapping
    └── ApiError.java                   # Error response DTO

src/main/resources/
├── application.properties
└── redis/
    └── reserve_bot_reply.lua           # Atomic guardrail script
```

---

## Postman

Import `postman_collection.json` into Postman. The collection uses a `{{baseUrl}}` variable defaulting to `http://localhost:8080`.
