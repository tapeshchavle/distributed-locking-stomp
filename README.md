# Scalable Seat Booking System (BookMyShow Clone)

A high-performance, industry-standard ticket booking system built with **Spring Boot**, **React**, **Redis**, **MySQL**, and **RabbitMQ**. This project demonstrates how to handle massive concurrency (e.g., thousands of people trying to book the same seat) using a two-tier locking strategy and real-time updates.

## 🚀 Key Features
- **Two-Tier Locking**: Prevents double-booking at both the cache (Redis) and database (MySQL) levels.
- **Real-Time Sync**: Instant UI updates across all connected clients via **WebSockets (STOMP)**.
- **Auto-Release Logic**: Abandoned seat holds are automatically released after 2 minutes using **RabbitMQ Dead Letter Exchanges**.
- **Aesthetic UI**: A premium, dark-mode seat map built with **Framer Motion** and **TailwindCSS**.

---

## 🏗️ Technical Architecture

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

### 2. The RabbitMQ "Auto-Release" Pattern
We avoid using traditional "Schedulers" (like `@Scheduled`) because they don't scale well in clustered environments. Instead, we use the **Dead Letter Exchange (DLX)** pattern:

1.  **Wait Queue**: When a seat is locked, a message with a **2-minute TTL** is sent to a "Wait" queue.
2.  **Expiration**: After 2 minutes, the message expires (dies) and is automatically routed to a **Dead Letter Queue (DLQ)**.
3.  **Listener**: Our `SeatExpirationListener` consumes from the DLQ, checks if the seat is still in `LOCKED` status, and if so, reverts it to `AVAILABLE` in both Redis and MySQL.

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

---

## 🎨 System Design & Aesthetics
The UI is designed to feel alive and responsive:
- **Interactive Map**: Smooth hover animations and tap feedback.
- **Live Legend**: Clear indicators for Available, Held, Your Choice, and Sold seats.
- **Glassmorphism**: A modern, transparent layout using TailwindCSS blur effects.

---
Developed as a demonstration of high-scale transaction management.
