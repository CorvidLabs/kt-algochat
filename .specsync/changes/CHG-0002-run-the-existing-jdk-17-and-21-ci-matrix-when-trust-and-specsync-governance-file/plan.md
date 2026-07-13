---
change: CHG-0002-run-the-existing-jdk-17-and-21-ci-matrix-when-trust-and-specsync-governance-file
artifact: plan
---

# Plan

1. Add Trust, SpecSync, Fledge, and Trust-workflow paths to both CI path filters.
2. Preserve the JDK matrix, Gradle setup, build, test, and artifact steps.
3. Validate locally with isolated JDK 17 and require both hosted matrix jobs.
