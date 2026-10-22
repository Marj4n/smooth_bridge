# Automated test result — v0.1.0

- Environment: OpenJDK 21 (Java source compiled with `--release 17`)
- Manager compile: PASS
- Fabric runtime compile against interface-signature stubs (not real game loader): PASS
- Test harness synthetic Forge/Fabric ZIP metadata: 11 / 11 PASS
- Forge mods included in package: NONE
- Kilt binary included in package: NONE
- Minecraft launch/in-world compatibility: **NOT TESTED**
- Gradle online build: **NOT TESTED** (network/Gradle distribution unavailable in this environment)

The tests prove local pre-launch selection/transaction/restore and report-writing logic, **not** Forge/Kilt binary compatibility.
