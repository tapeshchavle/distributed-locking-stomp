# Scalable Seat Booking System

A high-performance, industry-standard ticket booking system built with **Spring Boot**, **React**, **Redis**, **MySQL**, and **RabbitMQ**. This project demonstrates how to handle massive concurrency (e.g., thousands of people trying to book the same seat) using a two-tier locking strategy and real-time updates.

## 🚀 Key Features
- **Two-Tier Locking**: Prevents double-booking at both the cache (Redis) and database (MySQL) levels.
- **Real-Time Sync**: Instant UI updates across all connected clients via **WebSockets (STOMP)**.
- **Auto-Release Logic**: Abandoned seat holds are automatically released after 2 minutes using **RabbitMQ Dead Letter Exchanges**.
- **Aesthetic UI**: A premium, dark-mode seat map built with **Framer Motion** and **TailwindCSS**.

---

## 🏗️ Technical Architecture

### Complete System Architecture Diagram

```mermaid
graph TD
    %% Styles
    classDef frontend fill:#2d3748,stroke:#4a5568,stroke-width:2px,color:#fff,rx:5px,ry:5px;
    classDef backend fill:#276749,stroke:#2f855a,stroke-width:2px,color:#fff,rx:5px,ry:5px;
    classDef datastore fill:#2b6cb0,stroke:#2c5282,stroke-width:2px,color:#fff,rx:5px,ry:5px;
    classDef cache fill:#c53030,stroke:#9b2c2c,stroke-width:2px,color:#fff,rx:5px,ry:5px;
    classDef broker fill:#6b46c1,stroke:#553c9a,stroke-width:2px,color:#fff,rx:5px,ry:5px;

    subgraph Client Layer
        React["React Frontend<br>SeatMap.tsx (UI)"]:::frontend
    end

    subgraph Spring Boot Backend Layer
        REST["BookingController<br>Hold & Confirm API"]:::backend
        WS["WebSocketConfig<br>STOMP Broker"]:::backend
        LockSvc["SeatLockingService<br>Redis Operations"]:::backend
        ConfirmSvc["BookingConfirmationService<br>MySQL Commit"]:::backend
        Listener["SeatExpirationListener<br>DLQ Consumer"]:::backend
        
        React -->|"1. POST /hold"| REST
        React -->|"2. POST /confirm"| REST
        React <-->|"3. WS: /topic/shows/{id}/seats"| WS

        REST -->|"Triggers hold"| LockSvc
        REST -->|"Triggers commit"| ConfirmSvc
        
        Listener -->|"Triggers release"| LockSvc
        ConfirmSvc -.->|"Publish Seat SOLD"| WS
        LockSvc -.->|"Publish Seat LOCKED / AVAILABLE"| WS
    end

    subgraph Caching & Persistence Tier
        Redis[("Redis<br>Atomic SETNX Key")]:::cache
        MySQL[("MySQL 8.0<br>show_seats (@Version)")]:::datastore
    end

    subgraph Message Broker Tier (RabbitMQ)
        WaitQ(["seat_wait_queue<br>TTL 10m based Timer"]):::broker
        DLX{{"seat_expiration_dlx"}}:::broker
        DLQ(["seat_expiration_dlq<br>Active Queue"]):::broker
        
        WaitQ -- "4. Message TTL Expires" --> DLX
        DLX -- "5. Re-route Dead Message" --> DLQ
    end

    %% Data Flow
    LockSvc -- "Fast Tier-1 Lock" --> Redis
    LockSvc -- "Schedule Async Timer" --> WaitQ
    
    DLQ -- "6. Consumes Msg" --> Listener
    Listener -- "7. Validate & Release Lock" --> Redis
    Listener -- "8. Revert Status (if not booked)" --> MySQL
    
    ConfirmSvc -- "Tier-2 Optimistic Lock Commit" --> MySQL
    ConfirmSvc -- "Clear Lock if Success" --> Redis
```

### 1. Two-Tier Locking Strategy
To ensure that NO seat is ever double-booked while maintaining extreme speed, we use a hybrid approach:

*   **Layer 1: Redis (Distributed Hold)**: 
    *   When a user clicks a seat, the system check **Redis** using an atomic `SETNX` (Set if Not Exists) operation.
    *   This is lightning fast (~2ms) and prevents the "herd effect" from hitting the database for every single click.
    *   The status is immediately broadcasted as `LOCKED`.

*   **Layer 2: MySQL (Optimistic Locking)**:
    *   During the final "Confirm Selection" step, we use **JPA Optimistic Locking (`@Version`)**.
    *   The database verifies that the version number of the seat hasn't changed since the user first selected it.
    *   If a race condition somehow bypasses Redis, the Database transaction will safely ROLLBACK, ensuring data integrity.

## 🕊️ Distributed Auto-Release Logic (RabbitMQ DLX)

To avoid using a resource-heavy Java Scheduler (which requires constant database polling), we use the **Dead Letter Exchange (DLX)** pattern. This allows the system to scale to millions of concurrent seat holds with nearly zero CPU overhead.

### **The Mechanism (Step-by-Step)**
1.  **The "Sleeper" Queue**: When a seat is locked, the backend sends a message (e.g., `{"showId": 101, "seat": "A1"}`) to the `seat_wait_queue`. 
    *   This queue has **NO consumers**. Messages just sit there.
    *   Each message is tagged with an **EXPIRATION (TTL)** (e.g., 600,000ms for 10 minutes).
2.  **Self-Destruction**: RabbitMQ's ultra-fast Erlang engine handles the timer. It doesn't poll; it simply schedules an internal event for that specific message.
3.  **The Routing (DLX)**: Once the 10 minutes are up, the message "dies." Because the queue is configured with a `dead-letter-exchange`, RabbitMQ automatically re-routes the "dead" message to the `seat_expiration_dlq`.
4.  **Reaction**: Our `SeatExpirationListener` (Spring Boot) is the ONLY one listening to the `seat_expiration_dlq`. It "wakes up" only when a seat has actually expired.
5.  **Validation**: The listener checks the database: *"Is Seat A1 still 'LOCKED' by the same user?"* 
    *   If **YES**: It reverts the status to `AVAILABLE` and clears the Redis lock.
    *   If **NO** (User booked it already): It ignores the message.

### **A Concrete Example**
Imagine a high-traffic movie release at **12:00:00 PM**.

*   **12:00:00.000**: User A clicks Seat **C10**.
    *   Backend sets a **Redis Lock** on `seatlock:101:C10` for 10 minutes.
    *   Backend sends a message to RabbitMQ with a **600,000ms TTL**.
*   **12:05:00.000**: User A is entering their credit card details. The server is doing **ZERO** work for this timer. No threads are blocked. No DB is polled.
*   **12:09:59.999**: User A closes their browser without paying.
*   **12:10:00.000**: **The "Wake Up" Moment.**
    *   RabbitMQ detects the message TTL has hit zero.
    *   It instantly moves the message to the **Dead Letter Queue**.
*   **12:10:00.020**: Your `SeatExpirationListener` receives the message.
    *   It checks the DB, sees the seat is still "LOCKED," and **frees it**.
    *   It broadcasts a WebSocket event so **User B** instantly sees Seat C10 turn **White (Available)** again.

---
### 3. Real-Time WebSockets
We use **STOMP over SockJS** to maintain a persistent tunnel between the server and all users.
*   Any status change (`LOCKED`, `AVAILABLE`, `BOOKED`) triggers a broadcast to `/topic/shows/{id}/seats`.
*   All active UIs update instantly without the user needing to refresh the page.

---

## 🛠️ Getting Started

### Prerequisites
- Docker & Docker Compose
- Java 17+
- Node.js 18+

### 1. Start Infrastructure
```bash
docker-compose up -d
```
This starts **MySQL 8.0** (on port 3307), **Redis**, and **RabbitMQ**.

### 2. Run Backend
```bash
cd backend
mvn spring-boot:run
```
The backend initializes the database schema automatically. You can seed initial seats using the provided `src/main/resources/seeds.sql`.

### 3. Run Frontend
```bash
cd frontend
npm install
npm run dev
```
Navigate to `http://localhost:5173`.

---

## 📊 Database Schema
| Table | Column | Type | Description |
| :--- | :--- | :--- | :--- |
| **show_seats** | `id` | BIGINT (PK) | Unique Seat ID |
| | `show_id` | BIGINT | The Event/Show ID |
| | `seat_number` | VARCHAR | e.g., "A1", "B10" |
| | `status` | ENUM | AVAILABLE, LOCKED, BOOKED |
| | `locked_by_user_id`| BIGINT | ID of the holding user |
| | **`version`** | **INT** | **Used for Optimistic Locking** |


## 🎨 System Design & Aesthetics
The UI is designed to feel alive and responsive:
- **Interactive Map**: Smooth hover animations and tap feedback.
- **Live Legend**: Clear indicators for Available, Held, Your Choice, and Sold seats.
- **Glassmorphism**: A modern, transparent layout using TailwindCSS blur effects.

Developed as a demonstration of high-scale transaction management.
