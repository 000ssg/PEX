# PEX — Parse and Execute (root)

## Build Instructions

### Maven
To build the project using Maven:
```bash
mvn clean install
```

### Gradle
To build the project using Gradle:
```bash
./gradlew assemble
```

## Project Structure
- `pex-base`: Core parsing logic.
- `pex-arithmetics`: Arithmetic operations for SQL/NoSQL expressions.
- `pex-sql`: SQL dialect support and execution engine.
- `pex-converter`: Utilities for data format conversion.
- `pex-tools`: Build and testing utilities.
- `pex-all`: Aggregated module containing all features.
- `pex-nosql`: NoSQL database implementation and SPIs.
    - `pex-nosql-core`: Core engine for NoSQL operations.
    - `pex-nosql-dialects`: Specific dialect implementations for NoSQL.

## Dependency & Versioning Standards
The project must adhere to the following Java and library versions:
- **Java**: 25 (Target Release)
- **SLF4J**: 2.0.16
- **JUnit Jupiter**: 5.11.4
- **Mockito**: 5.14.2
- **AssertJ**: 3.27.3

All changes to dependencies must be reflected in both `pom.xml` and `build.gradle.kts`.

---

# Testing Guidelines for CI Reliability

## Anti-Patterns to Avoid

### 1. Fixed Thread.sleep() Without Latch Verification ❌
**Never use:** `Thread.sleep(N)` as the primary mechanism to wait for async operations on CI runners.

**Always use:** CountDownLatch with generous timeouts + latch verification AFTER the operation.

### 2. Insufficient Polling Timeouts ❌
**Never use:** Fixed timeouts < 5 seconds for operations involving network I/O or executor scheduling on CI runners.

**Always use:** Minimum 5 seconds for simple operations, 10 seconds for server-side processing. Poll every 50ms.

### 3. Warmup Requests for Subscription Readiness ❌
**Never use:** Extra "warmup" requests before real test operations.

**Always use:** Dedicated subscription callback or health check endpoint that signals actual readiness.

## Pre-Approved Build Commands

**Maven:**
```bash
mvn clean install
```

**Gradle (full test suite):**
```bash
./gradlew test
```

### Structural Rules

1. **Never remove parent references** from child modules.
2. **Root POM groupId is ssg**.
3. **Modules without JUnit/SLF4J test dependencies will fail Maven compile**.

### Final Verification Protocol

**Before any PR, run BOTH build systems:**

1. **Maven:** `mvn clean test`
2. **Gradle:** `./gradlew clean test --rerun-tasks`

**NEVER report "verified" without explicitly running BOTH Maven and Gradle clean test commands.**

---

## Documentation & Graphics Guidelines

### Mermaid Graphics (REQUIRED)
Always use Mermaid diagrams instead of ASCII graphics.

### Documentation Update Checklist
Before committing changes that affect code structure:
1. Update README.md: module descriptions, build instructions
2. Update module README.md — if the module's public API changed
3. Update AGENTS.md: add any new structural rules or build system changes
