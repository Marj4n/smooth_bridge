# Build & verifikasi — Smooth Bridge

## Normal (IntelliJ IDEA / Windows)

- JDK 21 boleh dipakai; source/bytecode ditarget Java 17.
- Pastikan tersedia **Gradle 8.14.x** atau versi yang memenuhi kebutuhan Loom 1.10.5; tidak ada wrapper binary dalam ZIP karena runtime build offline tidak dapat mengunduhnya.
- Buka folder `smooth-bridge/` di IntelliJ sebagai Gradle project. Tunggu import dependency Fabric Loom, Minecraft, Yarn, dan Fabric Loader (perlu akses internet di komputer user).
- Jalankan task **`build`** untuk Fabric JAR; task **`:manager:jar`** untuk CLI manager.
- Output normal: `build/libs/smooth-bridge-0.1.0+1.20.1.jar` dan `manager/build/libs/smooth-bridge-manager-0.1.0.jar` (nama final bisa bervariasi jika Gradle mengubah versi/name).
- `./gradlew` tidak disediakan; jika Gradle terpasang jalankan `gradle build :manager:jar`. Untuk membuat wrapper resmi di PC: `gradle wrapper --gradle-version 8.14.3`, commit empat file wrapper hasil resmi.

## Rilis JAR yang termasuk ZIP

Dibuat offline menggunakan JDK 21, `javac --release 17`, dan API-signature stubs **hanya untuk kompilasi**. Stub Fabric Loader tidak dimasukkan ke dalam JAR. Runtime entrypoint memakai API `ModInitializer`, `FabricLoader.getInstance()`, `getGameDir()`, `isModLoaded()` yang stabil. JAR berisi `fabric.mod.json` versi konkret dan kode asli.

**Belum diverifikasi**: Gradle build dengan artifact Maven sesungguhnya, Fabric game launch, Kilt remapping/injects, GeckoLib linkage, AI mobs, boss fights, worldgen, serta performa. Jangan tulis changelog release sebagai 'fully compatible' sebelum seluruh tes in-game lolos.

## Test lokal yang dijalankan

```bash
python -m unittest discover -s tests -v
```

Suite membuat JAR dummy dengan `META-INF/mods.toml` dan `fabric.mod.json`, bukan mendownload mod pihak ketiga. Fitur yang diuji: pemeriksaan Kilt, dependency terpilih, staging selektif, blocker unmanaged Forge jar, blocker GeckoLib duplikat, blocker versi bukan 1.20.1, penolakan duplicate target, SHA-256 tamper protection, pergantian profil, rollback, dan penulisan laporan runtime memakai test doubles minimal.

## Catatan produksi

Jika dari game muncul `NoSuchMethodError`, `ClassNotFoundException`, `Mixin apply failed`, atau `Forge registry error`, catat log lengkap beserta JAR dan versinya. Smooth Bridge 0.1.0 **tidak** mengklaim memperbaiki ketiganya. Jangan upload/distribusikan ulang JAR mod Forge yang lisensinya melarang.
