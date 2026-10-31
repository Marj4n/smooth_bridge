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
import java.util.jar.JarFile;

/**
 * Loads the COMPLETE official Curios Forge 5.14.1 mod through Kilt's normal
 * Forge mod-discovery pipeline BEFORE Cataclysm's @Mod constructor executes.
 * The nested Curios JAR is shipped inside Smooth Bridge, not in /mods.
 * This is NOT a fake/stub ICurioItem: real Curios code, events and inventory
 * logic must be initialized by Forge compatibility runtime.
 */
public final class EmbeddedCurios {
    public static final String RESOURCE = "META-INF/smoothbridge/curios-forge-5.14.1+1.20.1.jar";
    public static final String DIGEST = "META-INF/smoothbridge/curios-forge-5.14.1+1.20.1.sha256";
    private static final String REQUIRED_INTERFACE = "top/theillusivec4/curios/api/type/capability/ICurioItem.class";

    private EmbeddedCurios() { }

    public static List<?> extendBuiltIns(List<?> originalDefinitions, Object loader) {
        Objects.requireNonNull(originalDefinitions, "definitions");
        Objects.requireNonNull(loader, "loader");
        Path nestedJar = extract();
        try {
            Method scanner = loader.getClass().getDeclaredMethod("loadModDefinitions", Path.class, List.class);
            scanner.setAccessible(true);
            Object scanned = scanner.invoke(loader, nestedJar, null);
            if (!(scanned instanceof List<?> nestedDefinitions) || nestedDefinitions.size() != 1) {
                throw new IllegalStateException("Embedded Curios must resolve to exactly one Forge mod");
            }
            List<Object> merged = new ArrayList<>(originalDefinitions.size() + 1);
            merged.addAll(originalDefinitions);
            merged.addAll(nestedDefinitions);
            System.out.println("[Smooth Bridge] Embedded Curios 5.14.1 registered through Forge discovery");
            return merged;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Kilt ABI changed; unable to register embedded Curios", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Kilt rejected embedded Curios during discovery", e.getCause());
        }
    }

    public static synchronized Path extract() {
        try {
            byte[] jarBytes = resourceBytes(RESOURCE);
            String expected = new String(resourceBytes(DIGEST), StandardCharsets.UTF_8)
                    .trim().toLowerCase(Locale.ROOT);
            if (!expected.matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("Invalid embedded Curios checksum metadata");
            }
            if (!TargetJars.sha256(jarBytes).equals(expected)) {
                throw new IllegalStateException("Embedded Curios checksum mismatch");
            }
            Path directory = TargetJars.gameDir().resolve(".smoothbridge-cache").resolve("embedded");
            Files.createDirectories(directory);
            Path output = directory.resolve("curios-5.14.1+1.20.1-" + expected.substring(0,24) + ".jar");
            if (Files.isRegularFile(output) && expected.equals(TargetJars.sha256(Files.readAllBytes(output)))) {
                validateArchive(output);
                return output;
            }
            Path temporary = Files.createTempFile(directory, "curios-", ".tmp");
            try {
                Files.write(temporary, jarBytes);
                validateArchive(temporary);
                try {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
                }
                return output;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not extract embedded Curios", e);
        }
    }

    /** Verifies the REAL runtime mod, rather than an API-only artifact. */
    static void validateArchive(Path file) throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            if (jar.getJarEntry(REQUIRED_INTERFACE) == null) {
                throw new IOException("Curios JAR missing ICurioItem.class (wrong classifier or version)");
            }
            var metadata = jar.getJarEntry("META-INF/mods.toml");
            if (metadata == null) {
                throw new IOException("Curios JAR missing Forge mods.toml");
            }
            String toml;
            try (InputStream in = jar.getInputStream(metadata)) {
                toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            TargetJars.ModInfo mod = TargetJars.parseSingleMod(toml);
            // Official Curios uses ${file.jarVersion}; Kilt resolves it via JAR manifest.
            if (mod == null || !"curios".equals(mod.id())) {
                throw new IOException("Not Curios Forge metadata: " + mod);
            }
            String version = mod.version();
            if (!(version.equals("${file.jarVersion}") || version.startsWith("5.14.1"))) {
                throw new IOException("Unexpected Curios version in Forge metadata: " + version);
            }
        }
    }

    private static byte[] resourceBytes(String name) throws IOException {
        try (InputStream stream = EmbeddedCurios.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("Missing embedded Curios resource: " + name);
            }
            return stream.readAllBytes();
        }
    }
}
