# Smooth Bridge

**Selected Forge mods, inside your Fabric 1.20.1 setup.**

Smooth Bridge is an experimental, selective Forge-to-Fabric compatibility layer maintained by **Marj4n**, built on the **Kilt** runtime. It loads a fixed set of original Forge mod versions through Kilt's discovery pipeline. It is not a universal Forge loader and does not guarantee compatibility with arbitrary Forge mods or Fabric addons.

## Targeted Forge versions

| Mod | Exact version |
| --- | --- |
| Skarrier Mobs | 1.1.1 |
| Cult of Azazel | 1.1.4.12 |
| L_Ender's Cataclysm | 3.31 |
| Alex's Caves | 2.0.2 |
| Alex's Mobs | 1.22.9 |

Other Forge mods and unsupported versions are ignored by the selective loader. Download the original gameplay mods separately from their authors; they are not bundled with Smooth Bridge.

## Installation

1. Use **Minecraft Java 1.20.1**, **Java 17 or newer**, and **Fabric Loader 0.18.4 or newer**.
2. Install **Fabric API 0.92.11+1.20.1 or newer**, **Fabric Language Kotlin 1.13.7+kotlin.2.2.21 or newer**.
3. Add Smooth Bridge and the exact supported Forge mod JARs you want to use to your `mods` folder. Skarrier Mobs also needs the appropriate Fabric GeckoLib dependency.
4. Do not install standalone Kilt alongside Smooth Bridge. The bridge already contains its derived runtime. Manningham Mills - Kilt Edition 2.5.3, Citadel 2.6.3, Curios 5.14.1 and Lionfish API 3.0 are bundled; do not add duplicate copies.
5. Remove conflicting Fabric ports of the same mods, including native Fabric Citadel when using the original Alex mods. Keep a backup and test in a separate instance first.

The bundled dependency cache is verified with SHA-256. Original gameplay mod JARs are not edited in place. Duplicate Fabric-port checks help prevent loading the same mod twice.

## Current status

**1.0.0 is published as a Beta.** Packaging and nine automated metadata/selection checks pass. Full gameplay, world generation, rendering, addon compatibility, and dedicated-server acceptance across all five targets have not been completely verified. A successful build is not a promise of complete mod compatibility.

For multiplayer, use matching versions on both sides and test a separate world before deploying to a valued server. Include your exact mod versions, loader version and relevant crash log when reporting a problem in the comments.

## Credits and source

- **Kilt 20.1.21** — BluSpring, AlphaMode and contributors; LGPL-2.1-only. [Upstream source](https://github.com/KiltMC/Kilt).
- **Citadel 2.6.3** — Alexthe666 / AlexModGuy; LGPL-3.0-only. [Source](https://github.com/AlexModGuy/Citadel).
- **Curios 5.14.1** — TheIllusiveC4 and contributors; LGPL-3.0-or-later. [Source](https://github.com/TheIllusiveC4/Curios).
- **Lionfish API 3.0** — mcl_ender / lender544; LGPL-3.0-only. [Source](https://github.com/lender544/Lionfish-API).

Smooth Bridge's corresponding Java adaptation, build scripts and license notices are provided in the additional source archive. Original gameplay mods remain the work of their respective authors. Please report Smooth Bridge-specific issues here rather than assuming the upstream projects support this adaptation.
