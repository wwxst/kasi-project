# Production Configuration Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove repository-owned production secrets and unsafe defaults while preserving local development through an ignored configuration file.

**Architecture:** Keep common required configuration in `application.properties`, place production-only Redis and logging overrides in `application-prod.properties`, and publish only a non-secret local example. Rely on Spring placeholder resolution for fail-fast startup instead of adding Java validation code.

**Tech Stack:** Spring Boot properties, Git ignore rules, Maven Wrapper, PowerShell

---

### Task 1: Harden common and production configuration

**Files:**
- Modify: `src/main/resources/application.properties`
- Create: `src/main/resources/application-prod.properties`

- [ ] **Step 1: Record the unsafe configuration precondition**

Run:

```powershell
rg -n "PROVIDER_CREDENTIAL_MASTER_KEY:|logging.level.com.kasi.backend=DEBUG|REDIS_HOST:localhost|REDIS_PASSWORD:" src/main/resources/application.properties
```

Expected: the command finds the current provider-key fallback, Redis defaults, and DEBUG logging.

- [ ] **Step 2: Make the provider master key mandatory**

Change the common property to:

```properties
app.provider-credentials.master-key=${PROVIDER_CREDENTIAL_MASTER_KEY}
```

Keep existing local-friendly Redis defaults in the common file; production will override them in the dedicated profile.

- [ ] **Step 3: Add production-only Redis and logging properties**

Create `application-prod.properties` with exactly:

```properties
# Production-only overrides. All values are injected by the deployment environment.
spring.data.redis.host=${REDIS_HOST}
spring.data.redis.port=${REDIS_PORT}
spring.data.redis.password=${REDIS_PASSWORD}

logging.level.com.kasi.backend=INFO
logging.level.com.kasi.backend.provider.mapper.ShortDramaConnectionMapper=INFO
```

- [ ] **Step 4: Verify production placeholders and logging**

Run:

```powershell
rg -n "PROVIDER_CREDENTIAL_MASTER_KEY:|REDIS_HOST:localhost|REDIS_PASSWORD:|logging.level.com.kasi.backend=DEBUG" src/main/resources/application-prod.properties src/main/resources/application.properties
```

Expected: DEBUG and Redis defaults remain only in the common development-compatible file; the provider master key has no fallback, and the production file has no defaults.

### Task 2: Stop tracking local credentials and document deployment

**Files:**
- Modify: `../.gitignore`
- Untrack but preserve locally: `src/main/resources/application-local.properties`
- Create: `src/main/resources/application-local.example.properties`
- Modify: `README.md`

- [ ] **Step 1: Ignore the real local configuration**

Add this root ignore rule:

```gitignore
kasi-backend/src/main/resources/application-local.properties
```

Then run:

```powershell
git rm --cached -- kasi-backend/src/main/resources/application-local.properties
```

Expected: Git records the tracked file as deleted while the local file remains on disk.

- [ ] **Step 2: Add the non-secret local example**

Create `application-local.example.properties` with placeholders for the local datasource, JWT, provider master key, and Redis connection. Do not include a usable password, JWT secret, or Base64 master key.

- [ ] **Step 3: Update backend production instructions**

Document:

```text
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
JWT_SECRET
PROVIDER_CREDENTIAL_MASTER_KEY
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD
```

State that `application-local.example.properties` must be copied to the ignored local filename for development, and that production credentials must be rotated outside Git.

- [ ] **Step 4: Verify no known local credentials remain tracked**

Run:

```powershell
git grep -n -E "PROVIDER_CREDENTIAL_MASTER_KEY:[^}]|spring.datasource.password=[^$]|local-dev-jwt-secret-key"
```

Expected: no matches in tracked files.

### Task 3: Verify the release-hardening change

**Files:**
- Verify only the files listed in Tasks 1 and 2 plus this plan.

- [ ] **Step 1: Verify missing production configuration fails fast**

Run the application with `SPRING_PROFILES_ACTIVE=prod` and the required variables removed from the command environment.

Expected: non-zero exit caused by an unresolved required placeholder before serving requests.

- [ ] **Step 2: Run backend category and static-analysis gates with Java 25**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Punit-tests verify
.\mvnw.cmd -Pintegration-tests verify
.\mvnw.cmd -Pstatic-analysis -DskipTests verify
```

Expected: all three commands exit 0 with no failures or SpotBugs High findings.

- [ ] **Step 3: Run the complete backend gate**

Run:

```powershell
.\mvnw.cmd verify
```

Expected: `BUILD SUCCESS`, zero failures, and zero errors. Environment-dependent real tests may only be reported using the repository's PASS/FAIL/SKIP semantics.

- [ ] **Step 4: Check diff quality and scope**

Run from the Git root:

```powershell
git diff --check
git status --short
git diff -- kasi-backend/src/main/resources/application.properties kasi-backend/src/main/resources/application-prod.properties kasi-backend/src/main/resources/application-local.example.properties kasi-backend/README.md .gitignore
```

Expected: `git diff --check` exits 0; the scoped diff contains only approved production configuration hardening plus pre-existing changes that are explicitly identified.
