package org.marj4n.smoothbridge.kilt_runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Predicate;
import java.nio.file.DirectoryStream;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes INSIDE the compiled Kilt discovery method before its normal Forge pipeline.
 * Unknown Forge mods become an empty, valid JAR, so Kilt handles ONLY our five targets.
 * Original third-party JARs are never modified.
 */
public final class TargetJars {
    private static final Map<String, String> TARGETS = Map.of(
            "skarrier_mobs", "1.1.1",
            "netherman", "1.1.4.12",
            "cataclysm", "3.31",
            "alexscaves", "2.0.2",
            "alexsmobs", "1.22.9"
    );
    private static final Pattern MOD_SECTION = Pattern.compile("(?m)^\\s*\\[\\[mods]]\\s*(?:#.*)?$");
    private static final Pattern FIELD = Pattern.compile("(?m)^\\s*([A-Za-z_]+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern AZAZEL_LOADER = Pattern.compile("(?m)^(\\s*loaderVersion\\s*=\\s*)\"Forge\"");

    private TargetJars() { }

    public static Path permitAndPrepare(Path input) {
        Objects.requireNonNull(input, "input");
        if (!input.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
            return input; // Kilt handles non-JAR input using its own original logic.
        }
        try (JarFile jar = new JarFile(input.toFile())) {
            if (jar.getJarEntry("fabric.mod.json") != null) {
                return ignoredJar();
            }
            JarEntry entry = jar.getJarEntry("META-INF/mods.toml");
            if (entry == null) {
                return ignoredJar();
            }
            String toml;
            try (InputStream stream = jar.getInputStream(entry)) {
                toml = new String(stream.readNBytes(262_145), StandardCharsets.UTF_8);
            }
            if (toml.length() > 262_144) {
                return ignoredJar();
            }
            ModInfo mod = parseSingleMod(toml);
            if (mod == null || !mod.version.equals(TARGETS.get(mod.id))) {
                if (mod != null) {
                    System.out.println("[Smooth Bridge] Ignoring unrelated Forge mod " + mod.id + " " + mod.version);
                }
                return ignoredJar();
            }
            assertNoFabricPortCollision(mod.id, TargetJars::isFabricModLoaded);
            System.out.println("[Smooth Bridge] Allowlisted Forge " + mod.id + " " + mod.version);
            if (mod.id.equals("netherman")) {
                return normalizedAzazelJar(input, toml);
            }
            return input;
        } catch (FabricPortCollision conflict) {
            // This must reach the startup error dialog. Suppressing it would leave the
            // user with mixed Forge/Fabric registry and unpredictable world corruption.
            throw conflict;
        } catch (Exception error) {
            System.err.println("[Smooth Bridge] Skipping unreadable mod " + input.getFileName() + ": " + error);
            return ignoredJar();
        }
    }

    static ModInfo parseSingleMod(String metadata) {
        Matcher sections = MOD_SECTION.matcher(metadata);
        if (!sections.find()) {
            return null;
        }
        int start = sections.end();
        if (sections.find()) {
            return null; // No multi-mod Forge archives in the allowlist.
        }
        String block = metadata.substring(start);
        Matcher nextTable = Pattern.compile("(?m)^\\s*\\[\\[").matcher(block);
        if (nextTable.find()) {
            block = block.substring(0, nextTable.start());
        }
        Matcher fields = FIELD.matcher(block);
        Map<String, String> result = new HashMap<>();
        while (fields.find()) {
            result.put(fields.group(1), fields.group(2));
        }
        String id = result.get("modId");
        String version = result.get("version");
        return id == null || version == null ? null : new ModInfo(id, version);
    }

    record ModInfo(String id, String version) { }

    /** Only the two added gameplay targets depend on bundled Citadel. */
    public static boolean needsEmbeddedCitadel() {
        Path mods = gameDir().resolve("mods");
        if (!Files.isDirectory(mods)) {
            return false;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(mods, "*.jar")) {
            for (Path file : files) {
                try (JarFile zip = new JarFile(file.toFile())) {
                    if (zip.getJarEntry("fabric.mod.json") != null) {
                        continue;
                    }
                    JarEntry entry = zip.getJarEntry("META-INF/mods.toml");
                    if (entry == null) {
                        continue;
                    }
                    String toml;
                    try (InputStream in = zip.getInputStream(entry)) {
                        toml = new String(in.readNBytes(262_145), StandardCharsets.UTF_8);
                    }
                    if (toml.length() > 262_144) {
                        continue;
                    }
                    ModInfo mod = parseSingleMod(toml);
                    if (mod != null && ("alexscaves".equals(mod.id) || "alexsmobs".equals(mod.id))
                            && mod.version.equals(TARGETS.get(mod.id))) {
                        return true;
                    }
                } catch (IOException unreadable) {
                    // A non-target corrupted/locked archive must not cause this scan to fail.
                    System.err.println("[Smooth Bridge] Could not inspect potential Citadel consumer: " + file.getFileName());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Smooth Bridge cannot scan mods/ for Citadel consumers", e);
        }
        return false;
    }

    /**
     * Fabric recognizes its own mods before Kilt begins Forge discovery.
     * Native Fabric ports of Alex's mods or Citadel must NOT be active together
     * with original Forge targets due to identical mod IDs and Java packages.
     */
    static void assertNoFabricPortCollision(String forgeId, Predicate<String> fabricLoaded) {
        if (!"alexscaves".equals(forgeId) && !"alexsmobs".equals(forgeId)) {
            return;
        }
        for (String id : new String[] {forgeId, "citadel"}) {
            if (fabricLoaded.test(id)) {
                throw new FabricPortCollision("Smooth Bridge cannot run Forge " + forgeId
                        + " alongside the Fabric mod '" + id + "'. In a COPY of the instance,"
                        + " disable the Fabric port of " + id + " and keep the Forge target JAR."
                        + " Citadel Forge 2.6.3 is already embedded in Smooth Bridge."
                        + " Existing saves should be backed up before switching loaders.");
            }
        }
    }

    private static boolean isFabricModLoaded(String id) {
        try {
            Class<?> api = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isModLoaded", String.class).invoke(loader, id);
        } catch (ClassNotFoundException notMinecraft) {
            // Compile-time and fixture tests run without Fabric Loader.
            return false;
        } catch (ReflectiveOperationException brokenRuntime) {
            throw new IllegalStateException("Could not check Fabric mod collision for " + id, brokenRuntime);
        }
    }

    static final class FabricPortCollision extends IllegalStateException {
        FabricPortCollision(String explanation) { super(explanation); }
    }

    private static synchronized Path ignoredJar() {
        Path destination = gameDir().resolve(".smoothbridge-cache").resolve("ignored.jar");
        try {
            if (Files.isRegularFile(destination)) {
                try (JarFile ignored = new JarFile(destination.toFile())) {
                    if (ignored.getJarEntry("META-INF/mods.toml") == null) {
                        return destination;
                    }
                } catch (IOException damaged) {
                    // Rebuild a clean, empty JAR below.
                }
            }
            Files.createDirectories(destination.getParent());
            Path temp = Files.createTempFile(destination.getParent(), "smoothbridge-empty-", ".jar");
            try {
                try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(temp))) {
                    // Valid empty JAR: Kilt's original discovery immediately returns no mods.
                }
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            return destination;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot make Smooth Bridge Forge-ignore cache", e);
        }
    }

    private static synchronized Path normalizedAzazelJar(Path original, String toml) throws IOException {
        Matcher matcher = AZAZEL_LOADER.matcher(toml);
        if (!matcher.find()) {
            // Upstream Kilt already understands valid loaderVersion. Never overwrite originals.
            return original;
        }
        String normalized = matcher.replaceFirst("$1\"[47,)\"");
        Path dir = gameDir().resolve(".smoothbridge-cache").resolve("normalized");
        Files.createDirectories(dir);
        String sourceHash = sha256(Files.readAllBytes(original));
        Path output = dir.resolve("netherman-" + sourceHash.substring(0, 24) + ".jar");
        if (Files.isRegularFile(output)) {
            try (JarFile cached = new JarFile(output.toFile())) {
                JarEntry metadata = cached.getJarEntry("META-INF/mods.toml");
                if (metadata != null) {
                    try (InputStream in = cached.getInputStream(metadata)) {
                        String value = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        if (value.contains("loaderVersion=\"[47,)\"")) {
                            return output;
                        }
                    }
                }
            } catch (IOException badCache) {
                // Rebuild below.
            }
        }
        Path temp = dir.resolve(".netherman-" + UUID.randomUUID() + ".tmp");
        try {
            try (JarFile inJar = new JarFile(original.toFile());
                 JarOutputStream out = new JarOutputStream(Files.newOutputStream(temp))) {
                var entries = inJar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (isSignature(entry.getName())) {
                        throw new IOException("Refusing to rewrite signed Cult of Azazel JAR");
                    }
                    JarEntry next = new JarEntry(entry.getName());
                    if (entry.getTime() > 0) {
                        next.setTime(entry.getTime());
                    }
                    out.putNextEntry(next);
                    if (!entry.isDirectory()) {
                        if (entry.getName().equals("META-INF/mods.toml")) {
                            out.write(normalized.getBytes(StandardCharsets.UTF_8));
                        } else {
                            try (InputStream src = inJar.getInputStream(entry)) {
                                src.transferTo(out);
                            }
                        }
                    }
                    out.closeEntry();
                }
            }
            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        System.out.println("[Smooth Bridge] Normalized Cult of Azazel Forge metadata in cache; original untouched");
        return output;
    }

    private static boolean isSignature(String path) {
        String name = path.toUpperCase(java.util.Locale.ROOT);
        return name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"));
    }

    static Path gameDir() {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            return (Path) loaderClass.getMethod("getGameDir").invoke(loader);
        } catch (ReflectiveOperationException e) {
            // Allows isolated Java smoke tests without Fabric on the classpath.
            return Path.of(System.getProperty("user.dir"));
        }
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
