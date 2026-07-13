---
id: CHG-0002-run-the-existing-jdk-17-and-21-ci-matrix-when-trust-and-specsync-governance-file
state: implementing
type: operations
base_commit: ccac4ab302937e85e57a3ef2fade003fa936d3ba
---

# Run the existing JDK 17 and 21 CI matrix when Trust and SpecSync governance files change

## Intent

Run the existing JDK 17 and 21 CI matrix when Trust and SpecSync governance files change

## Affected Canonical Specs

- None

## Acceptance Criteria

- Pull requests changing Trust or SpecSync governance files trigger the unchanged JDK 17 and JDK 21 matrix; existing Gradle setup build test and artifact steps remain intact; strict SpecSync and full Trust pass

## No-spec Rationale

This changes CI triggering for governance files without changing Kotlin AlgoChat runtime or protocol behavior.
