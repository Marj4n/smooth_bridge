# Third-party components and source compliance

This project's Gradle build downloads but does not commit bundled Kilt and Lionfish binaries.

- **Kilt v20.1.21**, source: https://github.com/KiltMC/Kilt/tree/v20.1.21, license LGPL-2.1-only, authors BluSpring and contributors. The produced JAR is a **modified Kilt binary** (not an independent Forge implementation). Preserve original credits and notices, and make this Java patch source and build recipe available when distributing the binary.
- **Lionfish API 3.0 for Forge 1.20.1**, project: https://modrinth.com/mod/lionfish-api/version/CPftDBcY, license LGPL-3.0-only, author mcl_ender and contributors. Kept as an independent nested JAR, byte-for-byte original.
- **Skarrier Mobs**, **Cult of Azazel**, **L_Ender's Cataclysm** are **not included** in this Java source project or its patched engine output. Users provide their own original JARs and must follow author licensing/permissions.
- Java adaptation / compatibility logic ©2026 Marj4n, LGPL-2.1-only.

This source ZIP contains the adaptation's Java code. Build Gradle fetches pinned Modrinth version IDs, and the output contains embedded third-party code subject to its own notices and licenses. Distribution and automatic fetching must respect upstream licenses, project terms and credits.

## Curios API 5.14.1+1.20.1
- Official Curios by TheIllusiveC4 and contributors.
- Source: https://github.com/TheIllusiveC4/Curios/tree/1.20.x
- Artifact: Modrinth version IPQlZkz1, project vvuO3ImH
- License: LGPL-3.0-or-later; separate unmodified nested JAR.
- Its bytecode is not replaced or stubbed in Smooth Bridge.
