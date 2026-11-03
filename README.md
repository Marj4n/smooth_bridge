# Smooth Bridge

Selective Kilt-based compatibility runtime for Minecraft 1.20.1 on Fabric. It targets the original Forge builds of Skarrier Mobs 1.1.1, Cult of Azazel 1.1.4.12, Cataclysm 3.31, Alex's Caves 2.0.2 and Alex's Mobs 1.22.9.

## Build and install

Use JDK 21; output targets Java 17.

```sh
./gradlew build
```

The playable candidate is `build/libs/smooth_bridge-<version>-EXPERIMENTAL.jar`, produced by `smoothBridgeJar`. A plain thin JAR is deliberately disabled. The build fetches pinned dependencies; the first build needs network access.

Install one Bridge JAR with Fabric API, the required Fabric libraries and your original supported Forge mod JARs. Citadel, Curios and Lionfish are bundled. Do not also install standalone copies of those embedded libraries or conflicting Fabric ports of Alex's Caves, Alex's Mobs or Citadel. Compatibility is version-specific; test changes in a separate instance before using a valued save.

## Source layout

`src/main/java` contains the build-time bytecode patcher and runtime helpers. `src/test/java` contains patcher tests. Existing package and injected-class names are retained because the bytecode transformations depend on them.

## Credits and licenses

Java adaptations by Marj4n. The generated runtime modifies [Kilt 20.1.21](https://github.com/KiltMC/Kilt/tree/v20.1.21) by BluSpring, AlphaMode and contributors under LGPL-2.1-only. Preserve notices and provide corresponding source/build instructions when redistributing.

Bundled libraries remain separate, original nested JARs: [Citadel 2.6.3](https://github.com/AlexModGuy/Citadel) by Alexthe666/AlexModGuy (LGPL-3.0-only), [Lionfish API 3.0](https://github.com/lender544/Lionfish-API) by mcl_ender and contributors (LGPL-3.0-only), and [Curios 5.14.1](https://github.com/TheIllusiveC4/Curios/tree/1.20.x) by TheIllusiveC4 and contributors (LGPL-3.0-or-later). Retain each library's replacement/modification and source-distribution rights.

Third-party gameplay mod JARs are not redistributed. Root license texts and `licenses/` are preserved; each component keeps its own terms.

## Permission enquiries

For support or permission enquiries concerning separately owned material, contact Marj4n through [nivekts.vercel.app](https://nivekts.vercel.app/). This contact policy does not add an approval requirement to rights granted by the applicable LGPL licenses. The bridge and bundled components retain the licenses and source/replacement rights described above; earlier valid grants and GitHub platform rights remain unaffected.
