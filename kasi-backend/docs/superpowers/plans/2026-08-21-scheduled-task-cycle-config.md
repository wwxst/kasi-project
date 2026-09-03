# Scheduled Task Cycle Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all scheduler cycle types shown in the admin page persistable and executable by the backend.

**Architecture:** Add a V10 migration with normalized cycle fields on `system_scheduled_task`. A dedicated schedule calculator validates and computes the next run for interval and calendar cycles. The API and admin form exchange the same structured schedule fields; the existing lease dispatcher uses the calculator after each run.

**Tech Stack:** Java 25, Spring Boot, MyBatis, Flyway, H2, React 19, Ant Design, TypeScript, Vitest.

### Task 1: Backend schedule model and migration

- [x] Add `V10__scheduled_task_cycle_config.sql` with `cycle_type`, `interval_value`, `time_of_day`, `day_of_week`, `day_of_month`, `month_of_year`; backfill the existing task as `INTERVAL_MINUTES/60`.
- [x] Extend entity, DTO, VO, mapper XML and test schema with the fields.
- [x] Add failing validation tests for interval and calendar payloads.

### Task 2: Backend schedule calculator

- [x] Add tests for seconds, minutes, hours, days, daily, weekly, monthly and yearly next-run calculations, including month-end clamping.
- [x] Implement a pure calculator with explicit validation and deterministic `Clock` input.
- [x] Use it in management updates and dispatcher completion; remove hard-coded `plusMinutes`.

### Task 3: Backend integration verification

- [x] Extend controller tests for structured update payloads and validation errors.
- [x] Extend dispatcher tests to assert calculated next-run timestamps.
- [x] Run focused and complete Maven tests under Java 25.

### Task 4: Frontend structured editor

- [x] Extend API types with schedule fields.
- [x] Render interval input for seconds/minutes/hours/days and time/day selectors for daily/weekly/monthly/yearly.
- [x] Submit all selected fields; render the table period from the structured response.
- [x] Add UI tests proving type switching changes controls and request bodies.
- [x] Run focused tests, typecheck, lint and build.

### Task 5: Documentation and commits

- [x] Update backend/frontend README and design docs with structured cycle behavior.
- [x] Run diff checks and commit backend and frontend changes separately.
