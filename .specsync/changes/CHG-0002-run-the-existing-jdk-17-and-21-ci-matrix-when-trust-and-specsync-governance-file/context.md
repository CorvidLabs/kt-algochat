---
change: CHG-0002-run-the-existing-jdk-17-and-21-ci-matrix-when-trust-and-specsync-governance-file
artifact: context
---

# Context

The existing CI workflow preserves a JDK 17 and JDK 21 test matrix but limits
pull-request and push triggers to Kotlin source and build files. The rollout
changes only governance files, so the native matrix does not run on its head.
Governance paths must trigger the existing matrix without changing its jobs.
