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
 * Adds the original Forge Lionfish API JAR through Kilt's real mod discovery path.
 * This is called from KiltLoader.getBuiltinModDefinitions(), before Forge initialization.
 * Packaging occurs at Gradle build time; NO downloads happen when Minecraft starts.
 */
public final class EmbeddedLionfish {
    public static final String RESOURCE = "META-INF/smoothbridge/lionfishapi-3.0.jar";
    public static final String DIGEST = "META-INF/smoothbridge/lionfishapi-3.0.sha256";

    private EmbeddedLionfish() { }

    public static List<?> extendBuiltIns(List<?> originalDefinitions, Object loader) {
        Objects.requireNonNull(originalDefinitions, "definitions");
        Objects.requireNonNull(loader, "loader");
        Path nestedJar = extract();
        try {
            Method forgeScanner = loader.getClass().getDeclaredMethod("loadModDefinitions", Path.class, List.class);
            forgeScanner.setAccessible(true);
            Object scanned = forgeScanner.invoke(loader, nestedJar, null);
            if (!(scanned instanceof List<?> nestedDefinitions) || nestedDefinitions.size() != 1) {
                throw new IllegalStateException("Embedded Lionfish must resolve to exactly one Forge mod");
            }
            List<Object> merged = new ArrayList<>(originalDefinitions.size() + 1);
            merged.addAll(originalDefinitions);
            merged.addAll(nestedDefinitions);
            System.out.println("[Smooth Bridge] Embedded lionfishapi 3.0 registered through Forge discovery");
            return merged;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Upstream Kilt loader ABI changed; cannot register Lionfish", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Kilt rejected embedded Lionfish API during discovery", e.getCause());
        }
    }

    public static synchronized Path extract() {
        try {
            byte[] jarBytes = resourceBytes(RESOURCE);
            String expected = new String(resourceBytes(DIGEST), StandardCharsets.UTF_8)
                    .trim().toLowerCase(Locale.ROOT);
            if (!expected.matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("Invalid embedded Lionfish SHA-256 metadata");
            }
            String actual = TargetJars.sha256(jarBytes);
            if (!actual.equals(expected)) {
                throw new IllegalStateException("Embedded Lionfish checksum mismatch");
            }
            Path cache = TargetJars.gameDir().resolve(".smoothbridge-cache").resolve("embedded");
            Files.createDirectories(cache);
            Path output = cache.resolve("lionfishapi-3.0-" + expected.substring(0, 24) + ".jar");
            if (Files.exists(output) && expected.equals(TargetJars.sha256(Files.readAllBytes(output)))) {
                verifyLionfishJar(output);
                return output;
            }
            Path temporary = Files.createTempFile(cache, "lionfishapi-", ".tmp");
            try {
                Files.write(temporary, jarBytes);
                verifyLionfishJar(temporary);
                try {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
                }
                return output;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not extract the embedded Lionfish API", e);
        }
    }

    private static void verifyLionfishJar(Path jarPath) throws IOException {
        try (JarFile zip = new JarFile(jarPath.toFile())) {
            var modsToml = zip.getJarEntry("META-INF/mods.toml");
            if (modsToml == null) {
                throw new IOException("Lionfish API archive has no Forge metadata");
            }
            String metadata;
            try (InputStream in = zip.getInputStream(modsToml)) {
                metadata = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            TargetJars.ModInfo mod = TargetJars.parseSingleMod(metadata);
            if (mod == null || !"lionfishapi".equals(mod.id()) || !"3.0".equals(mod.version())) {
                throw new IOException("Bundled JAR is not Lionfish API 3.0: " + mod);
            }
        }
    }

    private static byte[] resourceBytes(String resource) throws IOException {
        try (InputStream in = EmbeddedLionfish.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource in Smooth Bridge runtime: " + resource);
            }
            return in.readAllBytes();
        }
    }
}
