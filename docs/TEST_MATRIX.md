# QA Checklist — jalankan di COPY instance (bukan world utama)

| Test | Skarrier | Azazel | Cataclysm | Kriteria |
|---|---|---|---|---|
| Kilt+Fabric bootstrap | ☐ | ☐ | ☐ | Masuk title screen, tak ada missing dependencies |
| Client + dedicated server startup | ☐ | ☐ | ☐ | Lolos keduanya, loader versions sama |
| Forge event bus / registry | ☐ | ☐ | ☐ | Semua entity/item/block terdaftar |
| GeckoLib animation | ☐ | ☐ | N/A | Animasi gerak/serang/item normal |
| Lionfish & Curios integration | N/A | N/A | ☐ | Boss animations/slot equipment normal |
| Natural spawn & spawn egg | ☐ | ☐ | ☐ | Spawn rules & spawn eggs valid |
| Structures & loot | ☐ | ☐ | ☐ | Map baru, chests, loot table aman |
| Special AI, boss fight, particles | ☐ | ☐ | ☐ | Tidak freeze/desync/game crash |
| Save/reload + 10 restarts | ☐ | ☐ | ☐ | Entity/block NBT tetap persisten |
| PvE, 2 players, network sync | ☐ | ☐ | ☐ | Combat dan entity sinkron |
| Smooth Fix benchmark | ☐ | ☐ | ☐ | FPS/TPS/MSPT dibanding baseline |
| RAM limits + crash audit | ☐ | ☐ | ☐ | Sesuai target device/server |

Simpan output `logs/latest.log`, crash-reports jika ada, `logs/smooth-bridge-runtime.txt`, `smoothbridge/state/managed.properties` dan nama+SHA-256 JAR. Jangan sertakan informasi pribadi dari log ketika di-share publik.

**Perhatian**: Jika ada 'Fabric GeckoLib' sudah terpasang, GUI/CLI guard akan menolak profil Skarrier/Azazel yang memasang Forge GeckoLib kedua. Itu blocker valid yang harus diselesaikan dulu, bukan bug acak yang aman diabaikan.
