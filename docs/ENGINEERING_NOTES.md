# Implementation details — v0.5.0 embedded Lionfish dependency

## Real uploaded JAR metadata

| Target | Forge mod ID | Version | Note |
|---|---|---|---|
| Skarrier Mobs | `skarrier_mobs` | 1.1.1 | Forge JavaFML / GeckoLib references |
| Cult of Azazel | `netherman` | 1.1.4.12 | Malformed/atypical `loaderVersion="Forge"`, normalized for this target only |
| L_Ender's Cataclysm | `cataclysm` | 3.31 | `lionfishapi` hard-dependency version range `[3.0,)` |

## How the library is loaded

1. Build host fetches official pinned Modrinth `CPftDBcY` metadata and selects `lionfishapi-3.0.jar` for Forge 1.20.1, checks SHA-512, ID/version, bundles raw bytes + SHA-256 + license in `META-INF/smoothbridge/` resources.
2. Kilt-derived fork entrypoint is still ID `kilt` for ABI compatibility, provides alias `smooth_bridge`. User installs only this fork, not original Kilt.
3. Knit calls `KiltLoader.getBuiltinModDefinitions()`. Fork retains Kilt's builtin definitions and calls `SmoothBridgeEmbeddedLionfish.extract()` to verify and stage library outside `mods/`.
4. `KiltLoader.loadModDefinitions(extractedPath)` returns a **normal Forge ModDefinition** (`lionfishapi:3.0`), included in the Forge processing list before mod construction. This deliberately bypasses the three-gameplay-mod allowlist for the fixed internal library ONLY.
5. Cataclysm's Forge dependency resolver should now see `lionfishapi:3.0`, subject to Kilt loader behavior and real launch verification. The original Lionfish mixin config comes from its own embedded original Forge JAR.
6. No network requests occur at Minecraft startup. Existing cache files are verified by SHA-256 and replaced safely if altered.

**Important:** source architecture is tested with Kotlin and Python fixtures, *not* the actual Minecraft/Kilt runtime. Unverified registry, classloader injection order, GeckoLib and custom mixin behavior can still cause crash or missing content.

## Next live acceptance gates

- Native Gradle remapJar with pinned Kilt source/submodules; `verify_built_jar` passes on produced 20+ MB-ish runtime JAR (size is not an acceptance gate).
- Launch Fabric 1.20.1 with exact three target Forge JARs and original Fabric mods, no external Lionfish, inspect `latest.log` for library creation, remapping and FML loading; ensure no duplicate mod ID.
- Test game registry IDs, creative items, Skarrier mob AI/animation, Azazel structures and bosses, Cataclysm entities/render/model animation, save/reload, dedicated server and Forge network sync.
- Regression run with Smooth Classes, Smooth Progression, Smooth Fix and original modpack; FPS/TPS bench and extended tests.
