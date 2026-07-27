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
    /`pex-nosql-dialects`: Specific dialect implementations for NoSQL.

## Dependency & Versioning Standards
The project must adhere to the following Java and library versions:
- **Java**: 25 (Target Release)
- **SLF4J**: 2.0.16
- **JUnit Jupiter**: 5.11.4
- **Mockito**: 5.14.2
- **AssertJ**: 3.27.3

All changes to dependencies must be reflected in both `pom.xml` and `build.gradle.kts`.
