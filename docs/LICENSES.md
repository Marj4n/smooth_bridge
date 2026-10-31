# Licensing and bundled dependency provenance

The Smooth Bridge selective fork is derived from **Kilt v20.1.21**, authors BluSpring, AlphaMode and contributors, under `LGPL-2.1-only` (https://github.com/KiltMC/Kilt). Its additions have the same source license. Preserve upstream credits and provide complete corresponding source in redistribution.

**Lionfish API 3.0** by mcl_ender / lender544 is a **separately licensed LGPL-3.0-only** library (https://modrinth.com/mod/lionfish-api/version/CPftDBcY; https://github.com/lender544/Lionfish-API). It is bundled **as an unchanged nested JAR**, not pasted or merged into Kilt classes. The root source kit has LGPL-3.0 and GPL-3.0 license texts under `third_party_licenses/`. `tools/bundle_lionfish.py` embeds both texts and a notice in the actual compiled fork's resource folder alongside the nested JAR. Build scripts record original Modrinth SHA-512/SHA-256. Respect both components' redistribution/source-code requirements and the ability to replace/modify the LGPL library when distributing packages.

The original Skarrier Mobs, Cult of Azazel and L_Ender's Cataclysm binaries are **not included** in this ZIP. They have their own upstream licenses/rights. Do not publish redistributed third-party gameplay binaries without checking the applicable permissions.
