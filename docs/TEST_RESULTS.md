# Smooth Bridge v0.5.0 — Test scope and status (2026-10-23)

- Python patch/source/archive fixture suite: **47 passing checks** (`python tests/run_tests.py`).
- Kotlin standalone test that compiles `SmoothBridgeEmbeddedLionfish.kt` and actually extracts a synthetic nested Forge JAR: **PASS**, including cache reuse, SHA-256 verification, tampered-cache recovery and use of a cache folder outside `mods/` (`bash tests/kotlin-lionfish-smoke/run.sh`).
- Kotlin selective allowlist code compiled with TEST DOUBLE stubs and checked against original three uploaded Forge JARs: **PASS** (`bash tests/kotlin-smoke/run.sh`).
- Real original Skarrier, Azazel, Cataclysm JAR metadata hashes verified/read-only; source patch anchors checked against live Kilt 20.1.21 on GitHub.
- Modrinth Lionfish v3.0 Forge 1.20.1 release/license verified from public web source; **actual library binary and its remote SHA-512 NOT DOWNLOADED IN THIS ENVIRONMENT**, because sandbox has no network access. Build script will download only from official Modrinth when used on a computer with network.
- Full Kilt fork Gradle remapJar: **NOT RUN**.
- Minecraft 1.20.1 client, dedicated server, item registration, mob spawns, AI, Cataclysm boss fight, world generation: **NOT RUN / NOT VERIFIED**.

No production gameplay-compatible JAR is included in this source ZIP. Successful packaging checks alone cannot validate user-requested full Forge compatibility.
