# Smooth Bridge standalone architecture — implementation status

> User requirement: ONE Fabric 1.20.1 mod in `mods/`, original Forge JARs remain in `mods/`, NO Kilt, and only three allowlisted Forge mods are handled. Never alter unrelated Fabric mods or active world saves.

## 0. User-facing model

```
Fabric Loader 1.20.1
      |
      v
smooth_bridge.jar (preLaunch)
      |-- Skarrier Mobs: 1.1.1 / modId skarrier_mobs
      |-- Cult of Azazel: 1.1.4.12 / modId netherman
      |-- L_Ender's Cataclysm: 3.31 / modId cataclysm
      +-- every other mod: unchanged / ignored

Required **future**, currently missing:
  (A) Forge Mojmap -> Fabric intermediary bytecode remapper
  (B) Target-specific Forge API implementation (NOT a general Forge loader)
  (C) Registry and mod lifecycle bridge -> Fabric events/registries
  (D) Capability and network translation, GeckoLib/Curios/Lionfish
  (E) Forge worldgen/renderer adaptations + mixin validation
  (F) Automated launch/in-game regression tests
```

## Milestones

| Milestone | Concrete definition of done | Status |
| --- | --- | --- |
| M0 | No Kilt dependency and single Fabric JAR works as an entrypoint | Source compiled, actual Fabric launch not tested |
| M1 | Scan only the three exact original mod IDs and report ABI needs | **PASS (JVM/CLI)** |
| M2 | Remap Forge Mojang names -> Fabric intermediary at preLaunch without modifying original JAR files | **NOT IMPLEMENTED** |
| M3 | Attach processed Forge classes/resources to Fabric's launch classpath before they load | **NOT IMPLEMENTED** |
| M4 | Implement required RegistryObject, DeferredRegister, IForgeRegistry, mod event bus and lifecycle actions | **NOT IMPLEMENTED** |
| M5 | Implement Forge TickEntity/Player/World/Living, ForgeHooks, Capability and SimpleChannel mappings | **NOT IMPLEMENTED** |
| M6 | GeckoLib renderer, Curios and Lionfish API adaptation + worldgen/mixins on Fabric | **NOT IMPLEMENTED** |
| M7 | Original three JARs genuinely initialize, entities spawn, animations and bosses work | **NOT VERIFIED** |
| M8 | Dedicated server, multiplayer, modpack FPS/TPS, reload, world safety | **NOT VERIFIED** |

### Design rules for future runtime implementation

1. **Never run unremapped Forge bytecode.** Original JARs expect Mojang names and Forge-patched Minecraft classes; running them on raw Fabric is unsafe and likely to fail.
2. Load targets by `mods.toml` `modId`, validated exact original JAR version and expected root package; do not trust user filenames or indiscriminately load any Forge JAR.
3. Create transformed **cache copies only**, keyed by original SHA-256, Fabric Loader version and transformation revision. Leave `mods/` and saves intact. Rebuild caches after upgrades.
4. Classpath admission must happen before mod classes, mixins and registries are initialized. `preLaunch` is a possible entrypoint, but loader classpath management may require access to Fabric internals. Treat this as an integration risk; don't assert that a regular initializer can retroactively load a mod.
5. Event bus and deferred registries must preserve registration order and freeze barriers. Fake registry entries without real MC registration are forbidden.
6. Networking must have symmetrical client/server registries, thread-safe scheduling and protocol checks.
7. Forge-injected Vanilla hooks (e.g., capabilities and certain entity events) may require targeted Fabric mixins; simply stubbing class names is insufficient.
8. Cataclysm injects its own mixins. Mapping/refmaps must be validated against target Minecraft classes and other Smooth Odyssey mixins.
9. Third-party dependencies require separate interop review. A GeckoLib Fabric JAR may expose API names used by Forge mods, but binary compatibility must be checked; Lionfish and Curios are separate implementation concerns.
10. Incompatible targets must fail CLOSED at the compatibility boundary without crashing/rewriting unrelated Fabric mods or risking saves. The present audit code is passive and safe; future integration must preserve this.

### Highest-leverage Forge APIs to implement first (derived from real uploaded bytecode)

- `net.minecraftforge.registries.RegistryObject#get()` and `DeferredRegister#register()`, used throughout all three.
- `net.minecraftforge.eventbus.api.IEventBus`, `MinecraftForge.EVENT_BUS`, and `@Mod.EventBusSubscriber` subscriptions.
- `net.minecraftforge.network.NetworkHooks#getEntitySpawningPacket` and `SimpleChannel.registerMessage/send` for Skarrier.
- `net.minecraftforge.common.ForgeConfigSpec` and `ForgeEventFactory` for Cataclysm/Azazel.
- GeckoLib versioned cross-loader compatibility (Skarrier/Azazel), Curios + Lionfish API (Cataclysm).

These are implementation candidates, **not classes currently provided**. Per-API methods/fields are enumerated in `FORGE_ABI_BACKLOG.csv`.

## M2 progress in 0.3.0 (2026-10-23)

New code: `remap/TinyMappings.java`, `remap/ClassFileRemapper.java`, `remap/JarTransformer.java`.
The program now supports an **offline, opt-in preview** rewrite when supplied a proper Tiny v2 mapping with Mojmap source namespace. No runtime injection; no Forge ABI. The included mapping file contains only one example Minecraft class for smoke testing, **not real production mappings**. A 2-class synthetic Java example was rewritten and executed by the ordinary JVM; 3 user-uploaded mod JARs' class files were processed and archives validated without touching their originals. This is partial M2 only; it should not be marked DONE because method inheritance, mapping completeness, mixins and full bytecode semantics are unresolved. All M3-M8 are incomplete.
