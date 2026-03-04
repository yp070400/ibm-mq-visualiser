# IBM MQ Monitor

A production-grade internal tool for monitoring multiple IBM MQ Queue Managers.
Displays queue depth, open handles, and health state in real time with minimal
load on the MQ environment.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  React UI (Vite)                                                │
│  Sidebar: QM list │ Main: Queue table (sortable, filterable)   │
│  Auto-refreshes every 30 s via fetch + useAutoRefresh hook      │
└───────────────────────┬─────────────────────────────────────────┘
                        │ HTTP GET /api/**
┌───────────────────────▼─────────────────────────────────────────┐
│  Spring Boot REST API (served on :8080)                         │
│                                                                 │
│  QueueManagerController  /api/queue-managers                   │
│  QueueStatsController    /api/queues/{qm}/{queue}              │
│  HealthController        /api/health                           │
│           │ reads from                                          │
│  ┌────────▼────────────────────────────────────────────────┐   │
│  │  MQMonitoringService  (in-memory ConcurrentHashMap cache)│   │
│  └────────▲────────────────────────────────────────────────┘   │
│           │ writes to cache                                     │
│  ┌────────┴──────────────────────────────────────────────────┐  │
│  │  MQPollingScheduler  (@Scheduled + startup collection)    │  │
│  │  → calls MQMonitoringService.collectAll()                 │  │
│  └────────┬──────────────────────────────────────────────────┘  │
│           │ submits CompletableFuture per QM                    │
│  ┌────────▼──────────────────────────────────────────────────┐  │
│  │  ExecutorService (fixed thread pool, mq-collector-N)      │  │
│  └────────┬──────────────────────────────────────────────────┘  │
│           │ one task per QM                                     │
│  ┌────────▼──────────────────────────────────────────────────┐  │
│  │  MQMetricsCollector                                       │  │
│  │  INQUIRE_Q (definitions) + INQUIRE_Q_STATUS (runtime)     │  │
│  │  = exactly 2 PCF round-trips per collection per QM        │  │
│  └────────┬──────────────────────────────────────────────────┘  │
│           │ uses PCFMessageAgent                                │
│  ┌────────▼──────────────────────────────────────────────────┐  │
│  │  MQConnectionManager                                      │  │
│  │  One PCFMessageAgent per QM, per-QM ReentrantLock,        │  │
│  │  reactive reconnect on connection errors                  │  │
└──┴────────────────────────────────────────────────────────────┘──┘
              │ IBM MQ client connection (TCP/TLS)
     ┌────────▼─────────┐  ┌───────────────────┐
     │  QM_DEV :1414    │  │  QM_PROD :1414    │  ...
     └──────────────────┘  └───────────────────┘
```

### Key design decisions

| Decision | Rationale |
|---|---|
| **2 PCF round-trips per QM per cycle** | `INQUIRE_Q` (wildcard) + `INQUIRE_Q_STATUS` (wildcard) = minimum possible MQ load regardless of queue count |
| **One PCFMessageAgent per QM** | Reusing the connection avoids expensive TCP+auth per poll |
| **Per-QM ReentrantLock for reconnect** | Prevents thundering herd: only one thread reconnects while others wait, then all reuse the new agent |
| **CompletableFuture per QM** | One slow/hanging QM does not block collection from others |
| **In-memory ConcurrentHashMap cache** | API reads never touch MQ; cache is replaced atomically per QM after each cycle |
| **fixedDelay scheduling** | Waits for the previous cycle to finish before starting the next — no overlapping collections |
| **Startup collection** | Cache is warm before the first API request (ApplicationReadyEvent) |
| **React UI via Vite proxy** | Development: separate dev servers with proxy. Production: `vite build` writes to Spring Boot static folder |

---

## Project Structure

```
ibm-mq/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/mqmonitor/
│       │   ├── MQMonitorApplication.java
│       │   ├── config/
│       │   │   ├── MonitorProperties.java   # YAML binding
│       │   │   ├── AppConfig.java           # ExecutorService, CORS
│       │   │   └── SSLConfig.java           # TLS socket factory builder
│       │   ├── model/
│       │   │   ├── QueueManagerConfig.java
│       │   │   ├── QueueStats.java
│       │   │   ├── QueueManagerStats.java
│       │   │   ├── QueueHealth.java
│       │   │   └── ConnectionStatus.java
│       │   ├── mq/
│       │   │   ├── MQConnectionManager.java # Agent lifecycle + reconnect
│       │   │   ├── MQPCFClient.java         # PCF command wrappers
│       │   │   └── MQMetricsCollector.java  # Per-QM collection
│       │   ├── service/
│       │   │   └── MQMonitoringService.java # Concurrent collect + cache
│       │   ├── scheduler/
│       │   │   └── MQPollingScheduler.java  # @Scheduled + startup
│       │   └── api/
│       │       ├── QueueManagerController.java
│       │       ├── QueueStatsController.java
│       │       └── HealthController.java
│       └── resources/
│           └── application.yml
└── frontend/
    ├── package.json
    ├── vite.config.js
    ├── index.html
    └── src/
        ├── App.jsx / App.css
        ├── api/mqApi.js
        ├── components/
        │   ├── Sidebar.jsx
        │   ├── QueueTable.jsx
        │   ├── HealthBadge.jsx
        │   └── DepthBar.jsx
        └── hooks/useAutoRefresh.js
```

---

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Node 18+** (frontend only)
- **IBM MQ allclient JAR** — available on Maven Central:
  ```
  com.ibm.mq:com.ibm.mq.allclient:9.3.5.0
  ```
  If your artifact repository does not proxy Maven Central, install manually:
  ```bash
  mvn install:install-file \
    -Dfile=com.ibm.mq.allclient-9.3.5.0.jar \
    -DgroupId=com.ibm.mq \
    -DartifactId=com.ibm.mq.allclient \
    -Dversion=9.3.5.0 \
    -Dpackaging=jar
  ```

---

## Configuration

Edit `backend/src/main/resources/application.yml`:

```yaml
mq:
  monitor:
    polling-interval-seconds: 30     # How often to poll MQ
    thread-pool-size: 10             # Parallel QM collection threads
    collection-timeout-seconds: 20   # Max wait per collection cycle
    warning-threshold-percent: 70    # Queue depth % → WARNING
    critical-threshold-percent: 90   # Queue depth % → CRITICAL

    queue-managers:
      - name: QM_DEV
        host: mq-dev.internal.example.com
        port: 1414
        channel: DEV.ADMIN.SVRCONN
        username: mqadmin
        password: "${MQ_DEV_PASSWORD}"
        queue-pattern: "*"
        exclude-system-queues: true
        enabled: true

      - name: QM_PROD
        host: mq-prod.internal.example.com
        port: 1414
        channel: PROD.ADMIN.SVRCONN
        username: mqadmin
        password: "${MQ_PROD_PASSWORD}"
        ssl-cipher-suite: TLS_RSA_WITH_AES_256_CBC_SHA256
        ssl-key-store: /opt/mq/certs/client.jks
        ssl-key-store-password: "${MQ_SSL_KS_PASS}"
        ssl-trust-store: /opt/mq/certs/trust.jks
        ssl-trust-store-password: "${MQ_SSL_TS_PASS}"
        queue-pattern: "APP.*"
        enabled: true
```

**MQ channel permissions required** (apply on each QM):

```
SET AUTHREC OBJTYPE(QMGR) PRINCIPAL('mqadmin') AUTHADD(CONNECT,INQ)
SET AUTHREC PROFILE('MQADMIN') OBJTYPE(NAMELIST) PRINCIPAL('mqadmin') AUTHADD(DSP)
SET AUTHREC PROFILE('**') OBJTYPE(Q) PRINCIPAL('mqadmin') AUTHADD(DSP,INQ)
```

The admin user only needs `INQ` (inquiry) — no GET/PUT rights required.

---

## Running

### Backend

```bash
cd backend
mvn spring-boot:run
# or
mvn package -q && java -jar target/mq-monitor-1.0.0.jar
```

### Frontend (development)

```bash
cd frontend
npm install
npm run dev
# Opens http://localhost:5173 — proxies /api to :8080
```

### Production build (single deployable)

```bash
cd frontend
npm run build
# Outputs to backend/src/main/resources/static/

cd backend
mvn package
java -jar target/mq-monitor-1.0.0.jar
# UI and API served from http://host:8080
```

---

## REST API

| Method | Path | Description |
|---|---|---|
| GET | `/api/queue-managers` | All QMs with summary stats |
| GET | `/api/queue-managers/{name}` | One QM with full queue list |
| GET | `/api/queue-managers/{name}/queues` | Queue list sorted by health |
| GET | `/api/queues/{qm}/{queue}` | Single queue stats |
| GET | `/api/health` | MQ-level health (200/207/503) |
| GET | `/actuator/health` | Spring Boot app health |

### Queue health states

| State | Condition |
|---|---|
| `NORMAL` | depth < 70% of max |
| `WARNING` | depth ≥ 70% and < 90% of max |
| `CRITICAL` | depth ≥ 90% of max |
| `UNKNOWN` | max depth = 0 or unavailable |

Thresholds are configurable per deployment in `application.yml`.

---

## Extending

**Add a new Queue Manager**: add an entry to `mq.monitor.queue-managers` in `application.yml` and restart.

**Change polling interval**: update `mq.monitor.polling-interval-seconds`. No code change needed.

**Add new metrics**: extend `MQPCFClient.inquireQueueStatus()` to request additional PCF attributes, add fields to `QueueStats`, and expose them in the controller/UI.

**Persistent history**: wire `MQMonitoringService.collectAll()` to write to a time-series store (InfluxDB, Prometheus) in addition to the in-memory cache.

**Alerting**: add a post-collection hook in `MQMonitoringService.collectOne()` that publishes `ApplicationEvent`s for health transitions; listen with `@EventListener` to send Slack/PagerDuty notifications.