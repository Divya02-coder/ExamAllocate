# ExamAllocate

Exam seating and allocation system: students apply, an admin runs the allocation once registration closes, and each
student sees their center, room, seat and the *reason* for it, then prints an admit card.
The engine enforces room capacity, a same-school-never-share-a-room rule, accommodations and city fallback.

```
examallocate/
├── backend/     Spring Boot 3 + JPA REST API (Java 17)
├── frontend/    Plain HTML/CSS/JS (index, apply, status, admitcard, admin)
├── database/    00_create_user.sql, ExamAllocate.sql (your schema + data), 01_schema_patch.sql, 02_reset_allocations_for_demo.sql
└── README.md
```

## Setup - DB laptop
1. Install MySQL 8. In `my.cnf`/`my.ini` set `bind-address = 0.0.0.0`, restart, open port 3306.
2. Run in this order: `00_create_user.sql` (as root) -> `ExamAllocate.sql` -> `01_schema_patch.sql`.
3. Optional demo: run `02_reset_allocations_for_demo.sql`. Your sample data is already fully allocated, so this clears it
   and lets you watch the engine seat everyone.

## Setup - Backend laptop
1. Install JDK 17 and Maven.
2. Edit `backend/src/main/resources/application.properties`: set `DB_LAPTOP_IP` (or `localhost`), and **change `examallocate.admin-key`**.
3. `cd backend && mvn spring-boot:run` (port 8080 open). Swagger: `http://<backend-ip>:8080/swagger-ui.html`.
   Admin calls in Swagger need the header `X-Admin-Key`.

## Setup - Frontend laptop
1. Edit `frontend/js/api.js` and set `BACKEND` to the backend laptop's address.
2. `cd frontend && python3 -m http.server 5500`, then open `http://localhost:5500`.
   Then set `examallocate.cors.allowed-origin` in the backend to that origin.

## Demo flow
1. Candidate opens `apply.html`.
2. Candidate selects the course and enters personal/contact details plus preferred exam cities.
3. The backend generates the **application number** automatically.
4. The backend assigns the earliest available exam session for the selected course; the candidate does not enter an application number or session ID.
5. Candidate receives a confirmation page containing the generated application number and assigned date/shift.
6. Candidate later uses application number + registered email on `status.html`.
7. Admin approves special accommodations and runs seat allocation.
8. Candidate sees the allocated centre/room/seat and can open the hall ticket.
9. Candidate can file and track a grievance.


## What was added to your schema (01_schema_patch.sql)
- `student.school_name` - the same-school rule needs it (demo values filled in).
- `room_occupancy(session_id, room_id, occupied)` - the atomic seat counter (`UPDATE ... WHERE occupied < capacity`).
- `allocation_log.center_id` made nullable - unplaced students are logged too.

## How the design-doc scenarios are handled
| Scenario | Handling |
|---|---|
| Last seats in a full city | Atomic counter; extras fall back to 2nd choice, then any city, each logged with a reason |
| Two runs at once | 2nd run rejected (409) on this instance; the counter still prevents overbooking across instances |
| Retry / double click | Only students without a seat are processed, so re-running is harmless |
| Crash mid-run | One transaction per student; just run again and it resumes |
| Accommodation not honored | Searched in every city; if impossible the student is logged UNPLACED for admin review, never silently ignored |
| Someone else's data | Student endpoints need Registration ID **and** application number; admin endpoints need `X-Admin-Key` |
| Grievance flow | OPEN -> UNDER_REVIEW -> RESOLVED/REJECTED; terminal states cannot reopen |

## Not included
- Real login (Spring Security + JWT): the admin key and application-number pairing are lightweight stand-ins.
- Invigilator assignment endpoints/UI (tables exist), JUnit tests, notification queue.
- "Accessible" accommodations are matched to floor <= 1 because your rooms only have floors 1 and 2.

## Important
Written without access to Maven Central, so it has **not been compiled or run**. Expect to fix a few small errors that
Maven points out on the first `mvn spring-boot:run`.

## Registration workflow changes

The frontend/backend were revised to make the candidate flow more realistic:

- `applicationNo` is generated server-side; it is never requested from a new candidate.
- `sessionId` is not exposed to the candidate; the server assigns the first available session for the selected course.
- Candidate identity/contact fields are captured before registration.
- The confirmation page displays the generated application number and assigned exam date/shift.
- Registration lookup uses application number + registered email.
- Existing allocation, admit-card and grievance modules continue to use the registration ID internally.
- The frontend was redesigned as an NPTEL-style academic demonstration portal with a structured header, registration sections, alerts, confirmation, status view and hall ticket.


## Admin Analytics dashboard add-on

The project now includes a separate analytics page at `frontend/admin-analytics.html`. It reads live data through the protected backend endpoint `GET /api/analytics/overview` and requires the configured `X-Admin-Key`. The page includes KPI cards, course registration-share pie chart, course comparison chart, city distribution, grievance status, course-wise table, and a browser-side K-means grouping of current course registration counts (descriptive grouping, not future prediction).

Added files:
- `backend/src/main/java/com/examallocate/controller/AnalyticsDashboardController.java`
- `backend/src/main/java/com/examallocate/service/AnalyticsDashboardService.java`
- `frontend/admin-analytics.html`
- `frontend/css/admin-analytics.css`
- `frontend/js/admin-analytics.js`

Open `admin-analytics.html` using the existing frontend server. Confirm `BACKEND_BASE` in `frontend/js/admin-analytics.js` matches the backend laptop IP and `examallocate.admin-key` in `backend/src/main/resources/application.properties`. The Chart.js graph library is loaded from a CDN, so the browser needs internet access for charts. No new database tables are required. The added Java code should be built and tested against the current database before demo use.
