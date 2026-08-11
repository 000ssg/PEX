# PEX — Release Procedure

This document describes the step-by-step process for releasing a new version of the PEX toolkit to GitHub Packages and creating a GitHub Release.

---

## Pre-Release Checklist

### 1. Verify Code State
- [ ] All tests pass: `./gradlew clean test --rerun-tasks`
- [ ] Maven build succeeds: `mvn clean install`
- [ ] Root `pom.xml` version reflects the target release version (e.g., `0.1.0`)
- [ ] `build.gradle.kts` version matches the target release version
- [ ] Child module `pom.xml` files inherit version from parent correctly
- [ ] No `--enable-preview` compilation errors (all modules use Java preview features)

### 2. Prepare Version
- [ ] Update root `pom.xml` `<version>` to the release version (e.g., `0.1.0`)
- [ ] Update `build.gradle.kts` root `version` to the release version
- [ ] Verify all child `pom.xml` files use `${project.version}` (not hardcoded)
- [ ] Update `README.md` version badge to match
- [ ] Update `README.md` test count if changed

### 3. Verify Release Workflow
- [ ] `.github/workflows/release.yml` has `permissions: contents: write` and `packages: write`
- [ ] Workflow uses `PACKAGE_PAT` secret for publishing
- [ ] No YAML multi-line command folding bugs

### 4. Pre-Release Build
```bash
./gradlew clean build --parallel --no-daemon

# Verify publish works locally
./gradlew publishToMavenLocal --parallel --no-daemon
```

---

## Release Steps

### Step 1: Tag and Push
```bash
git tag -a v0.1.0 -m "Release v0.1.0"
git push origin v0.1.0
```

### Step 2: Monitor GitHub Actions
- Watch the "Build & Publish" workflow at `https://github.com/000ssg/PEX/actions`
- Verify: Checkout → JDK 25 setup → Build & test → Publish → Create Release

### Step 3: Verify Published Artifacts
```bash
mvn dependency:get -Dartifact=ssg:pex-base:0.1.0 \
  -DremoteRepositories=https://maven.pkg.github.com/000ssg/PEX
```

### Step 4: Bump to Next Development Version
```bash
# Update pom.xml: 0.1.0 → 0.2.0-SNAPSHOT
# Update build.gradle.kts: version = "0.1.0" → "0.2.0-SNAPSHOT"
# Update README.md badge
git add pom.xml build.gradle.kts README.md
git commit -m "Bump version to 0.2.0-SNAPSHOT post-release"
git push origin master
```

---

## Known Issues & Findings

### Preview Features Requirement
PEX uses Java 25 preview features (`--enable-preview`). The Maven surefire plugin
is configured with `<argLine>--enable-preview</argLine>`. Ensure this flag is present
in both the compiler and test configurations.

### YAML Multi-Line Command Folding Bug (CRITICAL)
Never use backslash line continuations in GitHub Actions `run:` blocks. Write all
commands on a single line. See `AGENTS.md` for details.

### Parent Aggregator Modules
Modules `pex-sql` and `pex-nosql` are parent aggregators (pom packaging). They publish
as POM-only artifacts. Ensure `build.gradle.kts` `parentAggregatorProjects` set matches
the Maven structure.

### Profile-Based Builds
PEX supports `-P core-only`, `-P sql-only`, and `-P nosql-only` Maven profiles for
targeted builds. These are not used in the release workflow (full build is preferred).

---

## Troubleshooting

### Preview feature compilation errors
→ Verify `<argLine>--enable-preview</argLine>` in surefire config and
`--enable-preview` in Gradle compiler args.

### 401/403 on publish
→ Check `PACKAGE_PAT` secret and `packages: write` permission.

### Child module versions don't match
→ Verify `settings.gradle.kts` module names match Gradle project names.

---

## Release Order (Multi-Project)

PEX has no external project dependencies. It can be released independently.

If MDB-SQL depends on PEX-SQL artifacts, ensure PEX is released **before**
MDB-SQL for stable Maven coordinates.

---

**Last Updated**: 2026-08-11

---

## Hotfix Workflow (On-Demand Patch Releases)

The `release-v0.1.0-fix` branch accumulates fixes for the 0.1.x line. Fixes are applied
**without** version bumps. When ready, publish as 0.1.1, 0.1.2, etc.

### Applying a Fix

```bash
git checkout release-v0.1.0-fix
git cherry-pick <commit-hash>
# NO version bump — version stays at 0.1.0
```

Multiple fixes accumulate freely. Version only changes at publish time.

### Publishing a Hotfix

1. **Bump the version** on `release-v0.1.0-fix`:
   - `pom.xml`: `0.1.0` → `0.1.1`
   - `build.gradle.kts`: `version = "0.1.0"` → `version = "0.1.1"`
   - Commit: `git commit -am "release: bump to 0.1.1"`

2. **Push the branch**: `git push origin release-v0.1.0-fix`

3. **Trigger the hotfix workflow**:
   ```bash
   gh workflow run publish-hotfix.yml \
     -f branch=release-v0.1.0-fix \
     -f version=0.1.1 \
     -f tag_name=v0.1.1
   ```

4. The workflow builds, tests, publishes to GitHub Packages, creates the tag, and pushes.

### Updating Dependent Projects

When PEX publishes a hotfix, MDB-SQL must update `pexVersion` in its `gradle.properties`
and `<pex.version>` in its `pom.xml`.
