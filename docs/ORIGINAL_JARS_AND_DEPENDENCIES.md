# Original JAR audit — Smooth Bridge 0.7.0

These are the **actual user-uploaded JARs** examined on 2026-10-23. No
original gameplay JAR is modified, redistributed, or replaced by the Bridge.
Hash digests here are for audit only, not a requirement on every user file.

| Mod | `modId` | Version | Classes | Forge mandatory deps |
|---|---|---|---:|---|
| Skarrier Mobs | `skarrier_mobs` | `1.1.1` | 1404 | minecraft [1.20.1] [true], geckolib [0,) [false] |
| Cult of Azazel | `netherman` | `1.1.4.12` | 255 |  |
| L_Ender's Cataclysm | `cataclysm` | `3.31` | 1344 | lionfishapi [3.0,) [true], forge [36.0.42,) [true] |
| Alex's Caves | `alexscaves` | `2.0.2` | 1303 | citadel [2.6.0,) [true], forge [47.1.3,) [true] |
| Alex's Mobs | `alexsmobs` | `1.22.9` | 1118 | citadel [2.6.0,) [true], forge [47.1.0,) [true] |

### SHA-256 of uploaded test archives

- **Skarrier Mobs:** `8f41751e2ee395f128e45b5361687f7f5df4ec808892817b6ae759b076a3aefc`
- **Cult of Azazel:** `92b7eb590241b4c6fbd478f53529807127a2a9bdc5c28f062342df62484b8ed7`
- **L_Ender's Cataclysm:** `c29dcf940168208517b72f2d9965828b106a6b94cbea55d5001558c69a27b362`
- **Alex's Caves:** `131e205ce3eed384d701b336d8a0d4a671108ed56b6eff6014f0e407db2997f0`
- **Alex's Mobs:** `16bc5bcc19db9029c24f951346ec71098e6f5b0afef96e61894a2e9407aad37c`

### Bundled dependency plan

* Forge API/FML: supplied by the embedded **Kilt 20.1.21** upstream runtime.
* Lionfish API 3.0 (Forge): **already embedded** in v0.6.2.
* Curios API 5.14.1 (Forge): **already embedded** in v0.6.2.
* Citadel 2.6.3 (Forge, new): **nested original JAR**, only registered when
  one of the two selected Alex Forge mods is detected. Maven version:
  `maven.modrinth:jJfV67b1:lTAAe4sZ`.
* Dependencies provided directly by Fabric and MC: user keeps their existing
  Fabric API, Fabric Language Kotlin, Forge Config API Port, etc.

Both new original Alex JARs require **Citadel 2.6.0+**; the selected bundle
version 2.6.3 satisfies that range. Do not bundle/activate older Citadel
Fabric port simultaneously. They do **not** declare another mandatory
Forge content/API dependency in `mods.toml` apart from the Forge loader.

### Verification and limitation

- Compiled all Java runtime helper classes with `javac --release 17`.
- Ran **26 direct Java smoke assertions** on all five exact uploaded gameplay
  archives, preserving their SHA-256 hashes and testing the conditional
  Citadel selection/duplicate checks.
- Syntax-compiled the build-time bytecode patcher with local API-signature
  stand-ins for ASM. **This is NOT a substitute** for compiling against real
  ASM 9.7.1 using Gradle.
- **Full Gradle binary resolution, Kilt patching against the published JAR,
  actual Citadel binary introspection, Minecraft launch, registry, worldgen,
  and client/server gameplay have NOT been validated in this environment.**

A successful detection, remapping, or Minecraft main-menu entry should
not be misreported as proof that the five mods are fully playable together.
