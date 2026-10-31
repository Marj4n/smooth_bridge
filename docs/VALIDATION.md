# Gameplay qualification gates (do not mark release as ready prematurely)

## Gate 1 — bytecode patch and build

- IntelliJ Gradle `build` passes, JUnit `test` passes.
- `build/libs/smooth_bridge-0.6.2+1.20.1-EXPERIMENTAL.jar` is not a thin 50 KB audit mod: it must retain all original compiled Kilt classes plus nested Lionfish and Curios JARs.
- Archive contains `xyz/bluspring/kilt/loader/KiltLoader.class`, `net/minecraftforge/**`, `org/marj4n/smoothbridge/kilt_runtime/*.class`, `META-INF/smoothbridge/lionfishapi-3.0.jar`, `META-INF/smoothbridge/curios-forge-5.14.1+1.20.1.jar`, `fabric.mod.json`.
- No separate `Kilt-*.jar` or `lionfishapi-*.jar` or `curios-*.jar` in the `mods/` directory.

## Gate 2 — minimal instance first

Copy the instance (do NOT test on your real recorded world), with matching Fabric Loader, Fabric API, Fabric Language Kotlin, Forge Config API Port, other documented Kilt requirements, the three original JARs and Smooth Bridge candidate. Do not test the whole ~223-mod pack initially.

Expected logs: lines containing `[Smooth Bridge] Allowlisted Forge skarrier_mobs`, `netherman`, `cataclysm` and `Embedded lionfishapi 3.0 registered` and `Embedded Curios 5.14.1 registered`. Beware: Fabric Loader may still initially call the three JARs `non-fabric` before the compatibility loader runs; that line ALONE does not determine final Forge loading. Check actual Kilt FML registrations and `Mod Menu`.

## Gate 3 — full registry

After loading a *new* creative world, use Creative Inventory search and item ID autocomplete for each mod. Required: nonzero registered items/blocks/entities in all three mod namespaces (`skarrier_mobs`, `netherman`, `cataclysm`). A creative-tab entry alone is insufficient proof.

## Gate 4 — gameplay

Test mob eggs and natural spawning, GeckoLib animation, combat AI, boss phases, particles/rendering, loot, structures/worldgen, saves/reload, and integrated + dedicated server networking/synchronization. Run a second login and verify all content survives save/load.

## Gate 5 — Smooth Odyssey full pack

Only after isolated tests pass, gradually add mods from the real pack and collect conflicts (mixins, GeckoLib, Trinkets/Accessories, Porting Lib, worldgen, renderer). Record FPS/TPS/stress test comparisons. Kilt upstream remains experimental.

## Notes from user's earlier log

The log showing Fabric Loader 0.19.5 listed `smooth_bridge 0.3.0+1.20.1` and three `non-fabric mods`. That means the OLD audit JAR was active. This Java project is a DIFFERENT experimental build, and does not claim to resolve those logs until a fresh Minecraft launch is run.

## Gate 0.6.2 — regression for actual 05:09 crash

The previous v0.6.0 candidate failed with `MixinTargetAlreadyLoadedException`
for `org.marj4n.smoothbridge.runtime.TargetJars` during preLaunch.
In the new candidate, both runtime helper classes must live under
`org.marj4n.smoothbridge.kilt_runtime/` (note `kilt` in the name).
Kilt 1.20.1's EarlyRiser excludes names containing `kilt` from
Fabric-ASM transformation. New startup logs must not mention
`mixins.mm_kilt.json:org.marj4n.smoothbridge.runtime.TargetJars`.
This bypasses the known helper-transformation bug; any subsequent
Forge API conflicts must be debugged separately.
