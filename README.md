# Factory Collaboration Dashboard

A real-time collaboration dashboard for the **Warehouse**, **PMC** and **Purchase** departments. It replaces the
daily "who changed what" meeting with shared, permission-controlled online spreadsheets that live in MySQL, push
every edit to everyone instantly, and raise acknowledgeable alerts when key data (deadlines, quantities, statuses)
changes.

| Layer | Tech |
|---|---|
| Front-end | Vue 3 · Vite · Pinia · Vue Router · Element Plus · STOMP over WebSocket |
| Back-end | Java 17+ · Spring Boot 3.5 (Web, Security/JWT, Data JPA, WebSocket, AMQP, Validation) · Maven |
| Database | MySQL 8+ (schema managed by Flyway) |
| Messaging | RabbitMQ |
| Files | Apache POI (xlsx/xls import & export), built-in CSV parser |

## How it maps to the requirements

| Requirement | Implementation |
|---|---|
| **1.1 Instant change notifications** | Every committed edit publishes an event to RabbitMQ. A shared consumer turns it into persisted notifications; a per-instance consumer pushes it over WebSocket to every open browser (live cell updates + toast + bell badge). |
| **1.2 Cross-department sharing** | Each sheet has an owner department and per-department **VIEW / EDIT** grants. The dashboard groups every sheet you can see by department, with 24 h activity, a "key changes" feed and a recent-activity feed. |
| **2 Transparency / outdated data** (the "deadline 15th → 20th" case) | Mark a column as **watched** (e.g. *Material Deadline*). Any change to it sends an **IMPORTANT** notification to every user of every department that can see the sheet. It shows *row · column: old → new*, who changed it, and when. Users **acknowledge** it, and the change history shows who has and hasn't acknowledged. Every edit goes into an immutable audit table (`cell_change`) with its old and new value. |
| **2.1 Real-time editable form + permission control** | Sheet-level access (Owner / Edit / View / None), **column-level rules** ("only PMC may edit *Required By*", even with view-only sheet access), and header rows that only the owner can edit. Rules are enforced on the server for every cell; the UI mirrors them (read-only cells are shaded). |
| **2.2 Excel basics** | Virtualised grid, formula bar, ~60 functions (`SUM`, `IF`, `VLOOKUP`, `SUMIF(S)`, `COUNTIF(S)`, `INDEX/MATCH`, `ROUND`, date math…), ranges, `$` absolute refs, error values, cycle detection, copy/paste with relative-reference shifting (also from/to Excel), undo/redo, bold/italic/align/fill/text colour, number formats, insert/delete rows and columns with formula rewriting, column resize, keyboard navigation, IME input. |
| **2.3 Stored in the database** | Each non-empty cell is a row in `sheet_cell` (raw input + style). Formulas are stored as typed and evaluated in the browser. |
| **2.4 Templates** | Build a template in the designer (columns, widths, watched flags, department-only columns), or save any sheet as a template, with or without its data. New sheets can start from a template. |
| **2.5 Import** | Upload `.xlsx` / `.xls` / `.csv`. Each worksheet becomes a sheet; formulas, bold, fills and column widths are kept. You can also export any sheet to `.xlsx` with its formulas. |

## Architecture

```
 Browser (Vue)  ──REST──▶  Spring Boot  ──JPA──▶  MySQL
     ▲   │                    │  (after commit)
     │   └──STOMP/WebSocket───┤
     │                        ▼
     └─── /topic/sheets/{id}  RabbitMQ exchange "collab.events" (topic)
          /user/queue/...       ├─ sheet.#  ─▶ collab.notifications (durable, shared) ─▶ persist notifications ─▶ user.push
                                └─ #        ─▶ collab.broadcast.<instance> (one per app instance) ─▶ WebSocket clients
```

* Events are only published **after the DB transaction commits** (`@TransactionalEventListener`), so nobody ever sees a rolled-back change.
* Each app instance has its own broadcast queue, so you can run several instances behind a load balancer and every client still gets every update. Notifications are created **once** (shared queue). Retries are idempotent, and a dead-letter queue catches poison messages.
* Writes to one sheet are serialised with a row lock. Each change bumps the sheet's `version`. If a client sees a gap in versions, or its WebSocket reconnects, it reloads.

## Running with Docker

```bash
docker compose up -d --build
```

Open http://localhost:5174. The backend is also available at http://localhost:8081.
Compose runs the frontend with the Vite development server; there is no nginx service in this setup.

Browser requests use `/api` and `/ws` on the frontend origin. Vite forwards both to
`http://backend:8080` inside the Compose network. `8081` is the host's published port,
not the backend container's listening port; `localhost` inside the frontend container
refers to that frontend container.

The Docker backend allows origins `http://localhost:5174` and `http://127.0.0.1:5174`.
When accessing from another machine, set `CORS_ORIGINS` to the actual frontend origin
(including scheme and port) before starting Compose. This also controls WebSocket origin checks.
After editing frontend source or the Dockerfiles, rerun the build command above; source is copied
into the images rather than mounted live. Existing MySQL data is retained in `mysql-data`.

## Running locally

Prerequisites: JDK 17+, Node 18+, plus MySQL 8+ and RabbitMQ (use the command below or local installs).

```bash
# 1. infrastructure (or use your own MySQL/RabbitMQ)
docker compose up -d mysql rabbitmq

# 2. backend  →  http://localhost:8080
cd backend
DB_PASSWORD=collab ./mvnw spring-boot:run

# 3. frontend →  http://localhost:5173  (proxies /api and /ws to :8080)
cd frontend
npm install
npm run dev
```

**Without Docker (Homebrew MySQL):** you can run a private MySQL just for this app. It uses its own data folder and port, so your main MySQL and its root password are left alone:

```bash
# one-time: create the data folder (root without password, localhost only)
$(brew --prefix mysql@9.7)/bin/mysqld --no-defaults --initialize-insecure --datadir="$PWD/.mysql-data"
# each time: start it on port 3307
$(brew --prefix mysql@9.7)/bin/mysqld --no-defaults --datadir="$PWD/.mysql-data" --port=3307 --bind-address=127.0.0.1 --socket=/tmp/collab-mysql.sock --mysqlx=OFF
# then start the backend against it
cd backend && DB_PORT=3307 ./mvnw spring-boot:run
```

On first start, the empty database is seeded with the departments, demo users (password `password123`), three templates, and three linked sample sheets:

| User | Department | Role |
|---|---|---|
| `admin` | IT | Admin (everything) |
| `wh.manager` / `wh.clerk` | Warehouse | Manager / Member |
| `pmc.manager` / `pmc.planner` | PMC | Manager / Member |
| `pur.manager` / `pur.buyer` | Purchase | Manager / Member |

**Try the core scenario.** Open two browsers. Sign in as `pur.buyer` in one and `pmc.planner` in the other. In *Purchase Order Tracking*, the buyer changes a **Material Deadline** date. The PMC planner sees the cell flash right away and gets an *important* alert showing the old and new dates. Once they acknowledge it, the buyer can see that in **History**. The planner can edit only the *Required By (PMC)* column of that sheet. Warehouse can edit only *Received Qty*.

### Configuration (environment variables)

| Variable | Default | |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `localhost` / `3306` / `collab_dashboard` / `root` / *(empty)* | DB is created if missing |
| `RABBIT_HOST` / `RABBIT_PORT` / `RABBIT_USER` / `RABBIT_PASSWORD` | `localhost` / `5672` / `guest` / `guest` | |
| `JWT_SECRET` | dev value | **Set a random base64 256-bit secret in production** |
| `JWT_TTL_HOURS` | `12` | |
| `CORS_ORIGINS` | `http://localhost:5173` | comma-separated |
| `SERVER_PORT` | `8080` | Backend listening port; Docker publishes it as host port `8081` |
| `VITE_API_PROXY_TARGET` | `http://localhost:8080` | Vite proxy for both `/api` and `/ws`; Compose sets `http://backend:8080`. Local overrides can use `frontend/.env.local`; restart Vite after changes |
| `SEED_DEMO_DATA` | `true` | set `false` in production |
| `EXCEL_MAX_FILE_COUNT` | `10000` | Maximum internal ZIP entries when opening Excel files, independent of worksheet row/column limits; restart the backend after changing |

An Excel import error mentioning `MAX_FILE_COUNT` refers to files inside the Excel ZIP package,
not rows. Configure `collab.excel.max-file-count` in `application.yml` or set
`EXCEL_MAX_FILE_COUNT` for local backend startup. Other POI protections and the 20 MB upload
limit remain active. Compose also forwards `EXCEL_MAX_FILE_COUNT` to the backend;
rebuild/recreate the backend after changes. The frontend create-sheet/template inputs and
backend template validation still have separate fixed row/column caps; changing `collab.sheet`
does not update those controls.

### Production build

```bash
cd frontend && npm run build            # static files in frontend/dist → serve with nginx
cd backend && ./mvnw package            # target/collab-dashboard-1.0.0.jar
```

Serve `frontend/dist` with nginx and proxy `/api` and `/ws` to the backend. For `/ws`, forward the WebSocket upgrade headers.

## Permission model

| Who | Access |
|---|---|
| Admin | Owner of everything |
| Manager of the owning department | **Owner**: edit everything, share, set column rules, header rows, insert/delete columns, delete the sheet |
| Member of the owning department | **Edit**: data rows and rows insert/delete |
| Other departments | Whatever the owner granted: **Edit**, **View**, or nothing (the sheet is hidden) |
| Column rule `editDepartments` | If set, only those departments (and owners) may edit that column, even when they only have View on the sheet |

Only managers and admins create sheets, templates, and imports.

## Tests

```bash
cd backend && ./mvnw test      # formula reference shifting, CSV parsing
cd frontend && npm test        # formula engine (precedence, ranges, lookups, dates, errors, cycles, paste shifting)
```

## API overview

`POST /api/auth/login`, `GET /api/dashboard`, `GET|POST /api/sheets`, `POST /api/sheets/import`,
`GET|PUT|DELETE /api/sheets/{id}`, `PATCH /api/sheets/{id}/cells`, `POST /api/sheets/{id}/structure`,
`PUT /api/sheets/{id}/columns/{col}`, `PUT /api/sheets/{id}/permissions`, `PUT|DELETE /api/sheets/{id}/subscription`,
`GET /api/sheets/{id}/history`, `GET /api/sheets/{id}/cells/history`, `GET /api/sheets/{id}/export`,
`POST /api/sheets/{id}/save-as-template`, `GET|POST|PUT|DELETE /api/templates`, `GET /api/notifications`,
`POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`, `/api/admin/users`, `/api/admin/departments`.
WebSocket: STOMP at `/ws` (send `Authorization: Bearer <jwt>` in the CONNECT headers). Topics are
`/topic/sheets/{id}`, `/topic/activity`, and `/user/queue/notifications`.

## Known limits / next steps

* Formulas reference cells **within one sheet**. Cross-sheet references, such as PMC formulas reading the Warehouse inventory directly, would be the next step.
* Concurrent edits to the **same cell** are last-write-wins. Each one is still recorded in history and pushed to everyone.
* No merged cells, charts, or conditional formatting yet.
# cross-department-forms
