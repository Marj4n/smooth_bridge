package org.marj4n.smoothbridge.kilt_runtime;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Citadel 2.6.3 Forge is present as an ORIGINAL nested JAR, not a Java stub.
 * Only register it when either Alex's Caves or Alex's Mobs original Forge JAR
 * is present. Keeping the original Kilt FML discovery path ensures Citadel's
 * registries, events, access transformers, and mixins are not bypassed.
 */
public final class EmbeddedCitadel {
    public static final String RESOURCE = "META-INF/smoothbridge/citadel-2.6.3.jar";
    public static final String DIGEST = "META-INF/smoothbridge/citadel-2.6.3.sha256";
    private static final String MAIN_CLASS = "com/github/alexthe666/citadel/Citadel.class";

    private EmbeddedCitadel() { }

    public static List<?> extendBuiltIns(List<?> originals, Object loader) {
        Objects.requireNonNull(originals, "originals");
        Objects.requireNonNull(loader, "loader");
        if (!TargetJars.needsEmbeddedCitadel()) {
            return originals;
        }
        // Detect native Fabric Citadel before trying to load the Forge class.
        TargetJars.assertNoFabricPortCollision("alexscaves", EmbeddedCitadel::isFabricModLoadedCitadel);
        Path embedded = extract();
        try {
            Method scanner = loader.getClass().getDeclaredMethod("loadModDefinitions", Path.class, List.class);
            scanner.setAccessible(true);
            Object result = scanner.invoke(loader, embedded, null);
            if (!(result instanceof List<?> discovered) || discovered.size() != 1) {
                throw new IllegalStateException("Embedded Citadel must resolve to exactly one Forge mod");
            }
            List<Object> merged = new ArrayList<>(originals.size() + 1);
            merged.addAll(originals);
            merged.addAll(discovered);
            System.out.println("[Smooth Bridge] Embedded Citadel Forge 2.6.3 registered for Alex's Caves/Mobs");
            return merged;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Kilt loader ABI changed; cannot add Citadel to Forge discovery", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Kilt rejected embedded Citadel during discovery", e.getCause());
        }
    }

    private static boolean isFabricModLoadedCitadel(String id) {
        // assertNoFabricPortCollision tests "alexscaves" and "citadel".
        // For builtin Citadel, check only the actual citadel native mod.
        if (!"citadel".equals(id)) return false;
        try {
            Class<?> api = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object fabric = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isModLoaded", String.class).invoke(fabric, "citadel");
        } catch (ClassNotFoundException testRuntime) {
            return false;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot inspect Fabric Citadel conflict", e);
        }
    }

    public static synchronized Path extract() {
        try {
            byte[] bytes = resource(RESOURCE);
            String expected = new String(resource(DIGEST), StandardCharsets.UTF_8)
                    .trim().toLowerCase(Locale.ROOT);
            if (!expected.matches("[0-9a-f]{64}") || !expected.equals(TargetJars.sha256(bytes))) {
                throw new IllegalStateException("Embedded Citadel SHA-256 verification failed");
            }
            Path folder = TargetJars.gameDir().resolve(".smoothbridge-cache").resolve("embedded");
            Files.createDirectories(folder);
            Path output = folder.resolve("citadel-2.6.3-1.20.1-" + expected.substring(0, 24) + ".jar");
            if (Files.isRegularFile(output) && expected.equals(TargetJars.sha256(Files.readAllBytes(output)))) {
                validateArchive(output);
                return output;
            }
            Path temporary = Files.createTempFile(folder, "citadel-", ".tmp");
            try {
                Files.write(temporary, bytes);
                validateArchive(temporary);
                try {
                    Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException fallback) {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
                }
                return output;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not extract embedded Citadel", e);
        }
    }

    /** Validates identity, version and the real runtime API entry class. */
    static void validateArchive(Path file) throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            if (jar.getJarEntry(MAIN_CLASS) == null) {
                throw new IOException("Citadel archive lacks its actual runtime Citadel.class");
            }
            JarEntry entry = jar.getJarEntry("META-INF/mods.toml");
            if (entry == null) {
                throw new IOException("Citadel archive lacks Forge mods.toml");
            }
            String toml;
            try (InputStream in = jar.getInputStream(entry)) {
                toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            TargetJars.ModInfo mod = TargetJars.parseSingleMod(toml);
            if (mod == null || !"citadel".equals(mod.id())) {
                throw new IOException("Bundled archive is not the Forge Citadel mod: " + mod);
            }
            if (!("2.6.3".equals(mod.version()) || "${file.jarVersion}".equals(mod.version())
                    || mod.version().startsWith("2.6.3+"))) {
                throw new IOException("Wrong Citadel version, expected 2.6.3: " + mod.version());
            }
            if ("${file.jarVersion}".equals(mod.version()) &&
                    (jar.getManifest() == null || jar.getManifest().getMainAttributes()
                            .getValue("Implementation-Version") == null)) {
                throw new IOException("Citadel requires an Implementation-Version manifest value");
            }
        }
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = EmbeddedCitadel.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IOException("Missing nested Citadel resource " + name);
            return in.readAllBytes();
        }
    }
}
