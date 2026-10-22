# Roadmap Smooth Bridge (MC 1.20.1)

## Milestone 0 — selesai: selection + diagnostics v0.1.0

- Runtime Fabric mod dengan startup diagnostic yang tidak bergantung pada Minecraft-named classes
- Java pre-launch manager dengan tiga profil, allowlist strict, stage-only copy, SHA-256 journal
- Perubahan profil transactional, snapshot & one-step rollback
- Guard unmanaged Forge JAR & duplicate GeckoLib Fabric/Forge
- Unit/integration tests dengan dummy JAR; dokumentasi lisensi dan matriks pengujian

**Bukan** rekreasi Forge API; memakai Kilt yang disediakan user secara eksternal.

## Milestone 1 — belum: Skarrier Mobs real compatibility

- Validasi versi Forge 1.20.1 resmi terbaru + GeckoLib yang tepat; ambil log dari instance bersih
- Investigasi apakah Forge GeckoLib bisa berdampingan dengan Fabric GeckoLib atau butuh bridge API
- Patch minimal yang reproducible hanya untuk jalur Skarrier: registries, entity renderer, AI, weapons, coating workshop, spawn rules, networking
- Tes dedicated server dan client, 10x restart, save/load, spawn akurat, serta lokalisasi collision

## Milestone 2 — belum: Cult of Azazel

- Validasi GeckoLib dan dependency (kompatibel dengan mod yang sudah dipilih)
- Test boss/cutscene, generation Nether mansion, trigger zone, drops, dimensi, combat effects
- Patch per-mod setelah reproduksi log. Codebase Cult MIT bisa dievaluasi secara legal sambil tetap memperhatikan attribution

## Milestone 3 — belum: L_Ender's Cataclysm

- Periksa Lionfish API + Curios Forge di atas Kilt
- Evaluasi compatibility aksesoris dengan Fabric Trinkets atau cross-API jika ada
- Test boss AI, server tick, animation state, particles, hitbox, entity registry, worldgen, dungeon loot
- Patuhi hak cipta/lisensi Cataclysm; gunakan JAR asli dan minta izin author sebelum port atau redistribusi derivatif

## Milestone 4 — belum: Smooth Odyssey integration

- Fresh copy instance serta world baru; whitelist dan semua dependencies dicek sebelum launch
- Benchmark Smooth Fix: FPS (client), TPS/MSPT (server), RAM, RAM server, generation spikes
- Clash audit dengan Smooth Classes, Smooth Origins, Smooth Progression dan mods Fabric yang ada
- Jika lolos, rilis profil/test instructions untuk PC normal dan low-end tanpa memaksa setiap platform menjalankan mod berat

### Definition of done untuk 1 mod

1. Fabric + Kilt boot sampai menu tanpa crash
2. Mod terdaftar lengkap, tanpa missing registry atau nameless items
3. AI/spawn/renderer/animation/combat bekerja dengan aslinya
4. Save & reopen + client/server sync aman
5. Stress test FPS/TPS selesai, tidak ada regresi besar vs baseline
6. Tidak ada mod Forge di luar allowlist, dan lisensi patuh
