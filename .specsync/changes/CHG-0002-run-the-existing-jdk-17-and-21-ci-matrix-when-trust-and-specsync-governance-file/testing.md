---
change: CHG-0002-run-the-existing-jdk-17-and-21-ci-matrix-when-trust-and-specsync-governance-file
artifact: testing
---

# Testing

- Run strict SpecSync validation without the hash cache.
- Run the native test, build, and JAR lane with isolated Temurin 17.
- Run the full Trust gate.
- Require hosted `Test (JDK 17)` and `Test (JDK 21)` jobs to pass.
