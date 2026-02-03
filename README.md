# MIOT Calendar

A Quarkus-based microservice for calendar booking and resource scheduling.

## Overview

MIOT Calendar provides a generic, database-first calendar solution for booking resources into time slots. It supports:

- **Multiple calendars** with independent configurations
- **Time windows** defining when slots are available
- **Materialized slots** for fast queries and availability checks
- **Generic resource booking** with JSONB for flexible data storage
- **Capacity management** per slot with automatic status updates

## Tech Stack

- **Runtime**: Quarkus 3.31.x (Java 21)
- **Database**: PostgreSQL with Flyway migrations
- **ORM**: Hibernate ORM with Panache
- **API**: JAX-RS (Quarkus REST) with OpenAPI/Swagger
- **Observability**: Micrometer (Prometheus), SmallRye Health

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 14+ (or use Dev Services)

### Development Mode

```bash
# Run with Quarkus Dev Services (auto-starts PostgreSQL)
./run-dev.sh

# Or manually
./mvnw quarkus:dev
```

The application will:
- Start on port **8083**
- Auto-provision a PostgreSQL container via Dev Services
- Run Flyway migrations automatically
- Enable hot reload for development

### API Documentation

- **Swagger UI**: http://localhost:8083/swagger-ui
- **OpenAPI Spec**: http://localhost:8083/openapi

### Health Checks

- **Liveness**: http://localhost:8083/q/health/live
- **Readiness**: http://localhost:8083/q/health/ready

## API Endpoints

### Calendars

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/calendars` | List all calendars |
| GET | `/api/calendars/{id}` | Get calendar by ID |
| POST | `/api/calendars` | Create calendar |
| PUT | `/api/calendars/{id}` | Update calendar |
| DELETE | `/api/calendars/{id}` | Deactivate calendar |

### Time Windows

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/calendars/{id}/time-windows` | List time windows |
| POST | `/api/calendars/{id}/time-windows` | Create time window |
| PUT | `/api/calendars/{id}/time-windows/{twId}` | Update time window |

### Slots

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/slots?calendarId=X&startDate=Y&endDate=Z` | Query slots |
| GET | `/api/slots/{id}` | Get slot by ID |
| POST | `/api/slots/generate` | Generate slots for date range |
| PATCH | `/api/slots/{id}/status` | Update slot status |

### Bookings

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/bookings?calendarId=X&startDate=Y&endDate=Z` | List bookings |
| GET | `/api/bookings/{id}` | Get booking by ID |
| POST | `/api/bookings` | Create booking |
| DELETE | `/api/bookings/{id}` | Cancel booking |
| GET | `/api/bookings/resource/{resourceId}` | Get bookings by resource |

### Planned Services (Backward Compatibility)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/planned-services?startDate=X&endDate=Y` | List planned services |
| POST | `/api/planned-services?calendarId=X` | Create planned service |

## Database Schema

All tables are prefixed with `cld_` (calendar domain):

- `cld_calendars` - Calendar configurations
- `cld_time_windows` - Time window definitions
- `cld_slots` - Materialized booking slots
- `cld_bookings` - Resource bookings with JSONB data

## Configuration

Key application properties:

```properties
# HTTP
quarkus.http.port=8083

# Database
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/miot_calendar
quarkus.datasource.username=postgres
quarkus.datasource.password=postgres

# Flyway
quarkus.flyway.migrate-at-start=true
```

## Building

```bash
# Package
./mvnw package

# Run tests
./mvnw test

# Build native executable
./mvnw package -Dnative

# Build Docker image (JVM)
docker build -f src/main/docker/Dockerfile.jvm -t miot-calendar:jvm .

# Build Docker image (Native)
docker build -f src/main/docker/Dockerfile.native -t miot-calendar:native .
```

## Example Usage

### 1. Create a Calendar

```bash
curl -X POST http://localhost:8083/api/calendars \
  -H "Content-Type: application/json" \
  -d '{
    "code": "despacho-santiago",
    "name": "Despacho Santiago",
    "timezone": "America/Santiago"
  }'
```

### 2. Create a Time Window

```bash
curl -X POST http://localhost:8083/api/calendars/{calendarId}/time-windows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Turno Mañana",
    "startHour": 6,
    "endHour": 14,
    "slotDurationMinutes": 30,
    "capacityPerSlot": 3,
    "daysOfWeek": "MON,TUE,WED,THU,FRI",
    "validFrom": "2025-01-01"
  }'
```

### 3. Generate Slots

```bash
curl -X POST http://localhost:8083/api/slots/generate \
  -H "Content-Type: application/json" \
  -d '{
    "calendarId": "{calendarId}",
    "startDate": "2025-01-01",
    "endDate": "2025-01-31"
  }'
```

### 4. Create a Booking

```bash
curl -X POST http://localhost:8083/api/bookings \
  -H "Content-Type: application/json" \
  -H "X-User-Id: operator1" \
  -d '{
    "calendarId": "{calendarId}",
    "resource": {
      "id": "SRV-001",
      "type": "SERVICE",
      "label": "Acme Corp - Santiago to Valparaiso",
      "data": {
        "cliente": "Acme Corp",
        "origen": "Santiago",
        "destino": "Valparaiso"
      }
    },
    "slot": {
      "date": "2025-01-15",
      "hour": 10,
      "minutes": 30
    }
  }'
```

## License

Proprietary - MicroboxLabs
