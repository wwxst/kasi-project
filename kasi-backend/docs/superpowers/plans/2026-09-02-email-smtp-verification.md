# Email SMTP Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add configurable SMTP email verification for registration, code login, and password reset while preserving SMS and password login.

**Architecture:** Extend the singleton verification configuration with encrypted SMTP fields, expose them to super admins, and route email targets to a production SMTP sender. Reuse the existing Redis verification service and typed sender contract.

**Tech Stack:** Spring Boot, MyBatis, JavaMail, Redis, AES-GCM, React, TypeScript.

### Task 1: Configuration and persistence
- [ ] Add SMTP columns to `system_sms_config` and test schema: host, port, username, encrypted password, from address, enabled.
- [ ] Extend DTO/VO/runtime config and service update/read logic; add tests for encryption and blank-password retention.

### Task 2: SMTP sender
- [ ] Add JavaMail starter and `EmailVerificationCodeSender` under production profile.
- [ ] Add gateway abstraction and tests for command routing and unavailable configuration.

### Task 3: User API targets
- [ ] Add email-or-mobile DTO validation for registration/send/verify requests while keeping code-login routes shared.
- [ ] Add tests for email registration, email code login, and email password reset.

### Task 4: Admin frontend
- [ ] Add SMTP fields to the SMS configuration API types and page, omitting blank passwords on update.
- [ ] Add focused API/page tests and run typecheck/build.

### Task 5: User frontend and docs
- [ ] Update auth API/form target validation to accept email or mobile and wire register, code login, forgot-password flows.
- [ ] Update project docs and run backend/frontend gates.
