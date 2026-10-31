# Local tests carried out in this session

Environment: OpenJDK 21, compiled with `javac --release 17` for Minecraft 1.20.1 targeting Java 17 bytecode. No Gradle distribution, no access to external Maven artifacts, and no Minecraft launcher installed in this execution environment.

## Confirmed

- `TargetJars.java`, `EmbeddedLionfish.java`: **javac compilation passed**.
- `KiltBinaryPatcher.java`: **Java syntax/API signature smoke passed against temporary ASM method signature stubs**. This is **not** actual ASM binary transformation verification and does not establish compatibility with Kilt 20.1.21's shipped JAR.
- Three original user JARs parsed by the Java `TargetJars` logic:
  - Skarrier Mobs 1.1.1 — `skarrier_mobs`, accepted.
  - Cult of Azazel 1.1.4.12 — `netherman`, accepted, Forge `loaderVersion="Forge"` normalized into cache copy.
  - L_Ender's Cataclysm 3.31 — `cataclysm`, accepted.
- Original JAR SHA-256 values (not redistributed):
  - Skarrier: `8f41751e2ee395f128e45b5361687f7f5df4ec808892817b6ae759b076a3aefc`
  - Azazel: `92b7eb590241b4c6fbd478f53529807127a2a9bdc5c28f062342df62484b8ed7`
  - Cataclysm: `c29dcf940168208517b72f2d9965828b106a6b94cbea55d5001558c69a27b362`
- `EmbeddedLionfish` extracted a fabricated, correctly formatted test JAR and appended it using a **fake loader** reflecting Kilt's private method signature; **passed**. This does not test the real Lionfish binary.
- The Java metadata updater correctly produced the name `Smooth Bridge`, alias `smooth_bridge` and runtime technical ID `kilt` for test metadata.

## NOT confirmed

- Modrinth Gradle dependencies resolving (`Kilt v20.1.21`, `Lionfish API 3.0`, ASM, JUnit) on Windows.
- Gradle build with the actual Kilt 20.1.21 binary.
- ASM bytecode patching against the actual compiled Kilt JAR; this must be tested first on your PC.
- Actual Forge initialization for the three mods on Fabric; mob, boss, inventory, rendering, worldgen or dedicated server functionality.

No finished/playable JAR is included in this source ZIP. A Gradle-produced candidate is **experimental** until Minecraft gameplay verification passes.

## v0.6.2 incremental verification (2026-10-23)

- Compiled all three runtime helpers — `TargetJars`, `EmbeddedLionfish`, and new
  `EmbeddedCurios` — with `javac --release 17`: **PASS**.
- Compiled build-time Java patcher with temporary ASM signature stubs:
  **Java syntax / method signature smoke PASS** (not a real ASM runtime test).
- Created a synthetic Curios Forge JAR containing `mods.toml` and the
  `ICurioItem.class` path. `EmbeddedCurios.validateArchive()` accepted it:
  **PASS**.
- Removed `ICurioItem.class` from a fake JAR and confirmed validation refuses
  it: **PASS**.
- Verified the Modrinth Maven version ID for the official 1.20.1 Curios release,
  `IPQlZkz1`, and Java-only Gradle coordinates:
  `maven.modrinth:vvuO3ImH:IPQlZkz1`.
- Reviewed fresh user log: the actual blocker is `NoClassDefFoundError:
  top/theillusivec4/curios/api/type/capability/ICurioItem` at Cataclysm
  `ModBlocks.registerBlockAndItem` / `Cataclysm.<init>`.

**NOT YET RUN:** Gradle dependency download, transforming the actual shipped
Kilt JAR with ASM, the actual official Curios JAR runtime, and Minecraft launch.
The source package is not a proven game fix.
