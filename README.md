# Smooth Bridge v1.0.0 — Alex's Caves + Alex's Mobs (experimental)

**Maintainer:** Marj4n — pure Java source / IntelliJ IDEA Gradle project.
**Minecraft:** 1.20.1, Fabric Loader. This project bundles the upstream Kilt runtime
in its generated JAR. Kilt's original Kotlin-compiled bytecode is kept internally,
but none of the *Smooth Bridge source code* is Kotlin or Python.

## What changed from the uploaded v0.6.2

The original selective Forge loader is preserved and extended with two targets:

| Forge mod | exact version | internal mod ID |
|---|---|---|
| Skarrier Mobs | 1.1.1 | `skarrier_mobs` |
| Cult of Azazel | 1.1.4.12 | `netherman` |
| L_Ender's Cataclysm | 3.31 | `cataclysm` |
| Alex's Caves | 2.0.2 | `alexscaves` |
| Alex's Mobs | 1.22.9 | `alexsmobs` |

* New embedded full **Citadel 2.6.3 Forge JAR**, used by *both* original
  Alex's Caves and Alex's Mobs. It gets extracted to the existing
  `.smoothbridge-cache/embedded` and enters the genuine Kilt Forge-discovery
  pipeline. It is extracted only when one of the two exact Forge JARs is found.
* Retains **Curios 5.14.1** and **Lionfish 3.0**, both embedded from v0.6.2.
* Verifies `Citadel.class`, Forge `mods.toml`, mod ID and version, and SHA-256
  before loading. All exact dependency Maven versions are pinned.
* Rejects duplicate *native Fabric* versions of `alexscaves`, `alexsmobs`,
  and `citadel` with a helpful conflict message, **not** an unsafe attempt to
  load the same gameplay mod twice.
* All unrecognized Forge JARs are still ignored; existing unrelated Fabric
  mods are untouched. Neither original Forge gameplay JAR is redistributed.

## IMPORTANT: prevent duplicate mod IDs and Java packages

The user's previously shown Fabric instance included:

- `alexscaves-1.1.1-fabric+1.20.1.jar`
- `alexsmobsfabric-1.20.1-1.5.0.jar`
- `citadelfabric-1.20.1-1.1.0.jar`

For the Forge versions, **disable these THREE Fabric-port JARs in a COPY of
that instance**, not in your recorded/survival world, then place the two new
original Forge JARs beside the existing three. Do **not** install Citadel Forge
as a separate `mods/` JAR: Smooth Bridge has it embedded.

Other Fabric mods stay. However, addons that require the *Fabric port's*
classes or that declare a Fabric dependency on one of these mods may stop
working; test those specifically (for example, Alex's Cuisine). A Forge
bridge does not guarantee binary compatibility with unrelated Fabric addons.

### Expected instance layout

```text
mods/
  smooth_bridge-1.0.0+1.20.1-EXPERIMENTAL.jar
  skarrier_mobs-1.1.1-forge-1.20.1.jar
  cultofazazelforge-1.1.4.12.jar
  L_Enders_Cataclysm-3.31.jar
  alexscaves-2.0.2.jar
  alexsmobs-1.22.9.jar
  fabric-api-...jar
  geckolib-fabric-...jar
  ...all other compatible Fabric mods...
```

No separate Kilt, Curios, Lionfish, or Citadel JAR in `mods/`.

## Build in IntelliJ IDEA

1. Extract ZIP, **File > Open**, select the directory containing `build.gradle`.
2. Select a local Gradle distribution in IntelliJ, **Gradle JVM = JDK 21**.
   (The user-provided source did not include a functioning Gradle Wrapper
   `gradle-wrapper.jar`; broken shell launchers were removed.)
3. Reload Gradle (online for first dependency resolution).
4. Run **Gradle > Tasks > build > build** (or task `smoothBridgeJar`).
5. Candidate output: `build/libs/smooth_bridge-1.0.0+1.20.1-EXPERIMENTAL.jar`.
   There is no thin `jar` output to accidentally install.
6. Back up the instance/world. Remove older Smooth Bridge versions, then
   install the new candidate in a separate test instance and inspect `latest.log`.

## Test checklist (do not skip gameplay)

- [ ] Fabric and Forge dependency discovery recognizes **5** allowed Forge mods
- [ ] Citadel 2.6.3 detected as **one** internal Forge dependency
- [ ] All 5 Forge main mod constructors initialize without an exception
- [ ] Creative Inventory contains genuine items / spawn eggs from both Alex mods
- [ ] Alex's Mobs creatures spawn; AI, drops, animation and networking work
- [ ] Alex's Caves biome/worldgen, structures, mobs and custom shaders work
- [ ] Re-enter the world; check registries, item serialization and world saves
- [ ] Dedicated server loads and connects with the same mod list
- [ ] Stress-test client/server with Smooth Fix; investigate incompatibility

**STATUS:** This release is a Java source + Gradle build candidate. The helper
logic was compiled and audited against both real uploaded Forge JAR metadata.
**It has not passed a complete Minecraft client/server launch test here.**
A successful build or remap is *not* proof of game compatibility. Kilt's
experimental Forge API, Citadel mixins, worldgen, networking, and other Fabric
addon interoperability can still require targeted fixes. Do not use a valued
world until this test matrix succeeds.

## Credits and license

Kilt 20.1.21 by BluSpring and contributors — LGPL-2.1-only.
[Source](https://github.com/KiltMC/Kilt).
Citadel 2.6.3 by Alexthe666 / AlexModGuy — LGPL-3.0-only.
[Source](https://github.com/AlexModGuy/Citadel).
Lionfish 3.0 — LGPL-3.0; Curios 5.14.1 — LGPL-3.0-or-later.
Alex's Caves and Alex's Mobs copyright remains with their developers; their
original JARs are not distributed in this source ZIP or the runtime Bridge JAR.
See LICENSE-LGPL-2.1.txt, LICENSE-LGPL-3.0.txt and LICENSE-GPL-3.0.txt. The final generated Bridge
should retain upstream Kilt's notices and embedded original dependency JARs.
