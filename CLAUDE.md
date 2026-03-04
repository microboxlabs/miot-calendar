# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MIOT Calendar is a Quarkus 3.31 microservice (Java 21) for calendar booking and resource scheduling, part of the MicroboxLabs Modular IoT platform. It uses PostgreSQL with Flyway migrations, Hibernate ORM Panache, and JAX-RS REST endpoints.

## Build & Development Commands

```bash
# Dev mode (preferred on macOS — includes Netty workarounds)
./run-dev.sh

# Standard dev mode
./mvnw quarkus:dev

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=CalendarResourceTest

# Run a single test method
./mvnw test -Dtest=CalendarResourceTest#testCreateCalendar

# Full build with tests
./mvnw verify

# Package (JVM)
./mvnw package

# Package (native)
./mvnw package -Pnative
```

Dev mode runs on port **8083** (test profile: 8084). Swagger UI at `/swagger-ui`, OpenAPI spec at `/openapi`.

**Note:** Dev services for PostgreSQL are disabled by default. Dev mode expects a local PostgreSQL at `localhost:5432/miot_calendar` (user: postgres/postgres). CI provides PostgreSQL via service container.

## Architecture

Layered architecture: **Resource → Service → Entity → Database**

```
src/main/java/com/microboxlabs/miot/calendar/
├── entity/       # JPA entities extending PanacheEntityBase
├── model/        # Request/Response DTOs (Java records)
├── resource/     # JAX-RS REST endpoints (@Path)
├── service/      # Business logic (@ApplicationScoped, @Transactional)
├── validation/   # BookingValidationService
├── health/       # DatabaseHealthCheck
└── Main.java     # CLI entry point for run-slots mode
```

### Key Conventions

- **Entities** use static finder methods (active record pattern): `Calendar.findByCode(code)`, `Slot.findByCalendarAndDateTime(...)`
- **DTOs** are Java records in `model/` — separate Request and Response types per entity
- **Tables** use `cld_` prefix, all in the `miot_calendar` schema
- **Soft deletes** via `active` boolean on Calendar, CalendarGroup, SlotManager — never hard delete these
- **Booking.resourceData** is stored as PostgreSQL JSONB for flexible metadata
- **Slots are materialized** — pre-generated from TimeWindow configs for O(1) availability queries
- Slot occupancy/status auto-updates on booking create/delete

### Domain Model

- **Calendar** → has many **TimeWindows** (availability rules) and **Slots** (materialized bookable units)
- **CalendarGroup** ↔ **Calendar** (many-to-many via cld_calendar_group_members)
- **Slot** → has many **Bookings** (resource reservations with capacity tracking)
- **SlotManager** → one per calendar, configures automatic slot generation (daysInAdvance, batchDays)

### REST API

Base path: `/api/v1/miot-calendar`

Resources: `/calendars`, `/groups`, `/slots`, `/bookings`, `/slot-managers`

### Database Migrations

Flyway migrations in `src/main/resources/db/migration/` (V1–V6). Schema changes must go through new versioned migration files — Hibernate generation is set to `none`.

### Slot Generation

SlotManager runs on a configurable cron (`miot-calendar.slot-manager.cron`). Can also run via CLI (`java -jar miot-calendar.jar run-slots`) for K8s CronJobs. Set cron to `off` to disable the internal scheduler.

## Git Workflow

- Main branch: **trunk**
- Feature branches: `based/<issue-number>-<short-name>`
- PRs target trunk

## Testing

Tests use `@QuarkusTest` with REST Assured. Tests are ordered (`@TestMethodOrder`, `@Order`) and share state per class (`@TestInstance(PER_CLASS)`). Test profile cleans and re-migrates the database on each run (`flyway.clean-at-start=true`).
