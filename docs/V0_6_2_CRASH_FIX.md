# Crash fix v0.6.2

Runtime evidence from `latest(2).log`:
- 05:24:14: Cataclysm, Skarrier, Azazel and Lionfish completed Kilt remapping.
- 05:25:06: `Initialized new instance of mod lionfishapi` and `... skarrier_mobs`.
- 05:25:06: Cataclysm constructor `Cataclysm.java:77` referenced
  `ModBlocks.java:535` and failed on `ICurioItem` missing.
- 05:25:06: Cult of Azazel `netherman` constructed.

Action: package official Curios API Forge 5.14.1 runtime (not `-api.jar`),
from Modrinth project `vvuO3ImH`, pinned version ID `IPQlZkz1`,
alongside nested Lionfish. Extend original Kilt Forge discovery with
`EmbeddedCurios.extendBuiltIns()` before `EmbeddedLionfish.extendBuiltIns()`.

Startup expectations:
`[Smooth Bridge] Embedded Curios 5.14.1 registered through Forge discovery`
`Found Forge mod Curios API (curios)`
`Initialized new instance of mod curios`

If registry initialization progresses but new errors appear, debug separately
instead of creating dummy stubs or deleting unrelated Fabric mods.

`necronomicon-common.mixins.json` is a warning during this run; not the
exception terminating Forge loading in the log.
