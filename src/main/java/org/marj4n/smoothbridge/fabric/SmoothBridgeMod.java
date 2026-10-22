package org.marj4n.smoothbridge.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Passive, dependency-light Fabric runtime diagnostics.
 * Does not remap Forge mods or patch their execution: Kilt handles that.
 * Pre-launch allowlisting lives in Smooth Bridge Manager, not this initializer.
 */
public final class SmoothBridgeMod implements ModInitializer {
    private static final String VERSION = "0.1.0";
    private static final Pattern MOD_ID = Pattern.compile("^\\s*modId\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']");

    @Override
    public void onInitialize() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path mods = gameDir.resolve("mods");
        List<String> output = new ArrayList<>();
        output.add("Smooth Bridge " + VERSION + " | Runtime diagnostic | " + Instant.now());
        output.add("Note: discovered Forge JAR != successfully loaded Forge mod.");
        boolean kilt = FabricLoader.getInstance().isModLoaded("kilt");
        output.add("Kilt listed by Fabric Loader: " + kilt);
        if (!kilt) output.add("WARNING: Kilt is not loaded. Smooth Bridge cannot provide Forge loading.");
        try {
            Map<String, String> managed = readManaged(gameDir.resolve("smoothbridge/state/managed.properties"));
            int discovered = 0;
            int untracked = 0;
            if (Files.isDirectory(mods)) {
                try (DirectoryStream<Path> paths = Files.newDirectoryStream(mods, "*.jar")) {
                    for (Path jar : paths) {
                        if (!Files.isRegularFile(jar) || Files.isSymbolicLink(jar)) continue;
                        List<String> forgeIds;
                        try { forgeIds = forgeIds(jar); }
                        catch (IOException ex) {
                            output.add("WARNING unreadable JAR: " + jar.getFileName() + " - " + ex.getMessage());
                            continue;
                        }
                        if (forgeIds.isEmpty()) continue;
                        discovered++;
                        String file = jar.getFileName().toString();
                        if (!managed.containsKey(file)) {
                            output.add("UNMANAGED Forge JAR (Kilt may still load it): " + file + " ids=" + forgeIds);
                            untracked++;
                        } else {
                            String actual = sha256(jar);
                            String expected = managed.get(file);
                            output.add((actual.equals(expected) ? "TRACKED" : "MODIFIED")
                                + " Forge JAR: " + file + " ids=" + forgeIds);
                        }
                    }
                }
            }
            for (Map.Entry<String, String> e : managed.entrySet()) {
                if (!Files.isRegularFile(mods.resolve(e.getKey())))
                    output.add("MISSING managed Forge JAR: " + e.getKey());
            }
            output.add("Forge JARs discovered: " + discovered + "; unmanaged: " + untracked);
            output.add("Gameplay verification: NOT PERFORMED (boss AI, animations, spawning, saving require game tests)");
        } catch (Exception ex) {
            output.add("DIAGNOSTIC ERROR: " + ex.getClass().getSimpleName() + " " + ex.getMessage());
        }
        try {
            Path report = gameDir.resolve("logs/smooth-bridge-runtime.txt");
            Files.createDirectories(report.getParent());
            Files.write(report, output, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("[Smooth Bridge] Cannot write diagnostic report: " + ex.getMessage());
        }
        for (String line : output) {
            if (line.contains("WARNING") || line.contains("UNMANAGED") || line.contains("MODIFIED") || line.contains("ERROR")) {
                System.err.println("[Smooth Bridge] " + line);
            } else if (line.startsWith("Kilt") || line.startsWith("Forge JARs")) {
                System.out.println("[Smooth Bridge] " + line);
            }
        }
    }

    private static Map<String, String> readManaged(Path journal) throws IOException {
        Map<String, String> entries = new HashMap<>();
        if (!Files.isRegularFile(journal)) return entries;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(journal)) { p.load(in); }
        int count;
        try { count = Integer.parseInt(p.getProperty("count", "0")); }
        catch (NumberFormatException ex) { throw new IOException("Corrupt managed count", ex); }
        if (count < 0 || count > 500) throw new IOException("Unsafe managed count");
        for (int i = 0; i < count; i++) {
            String file = p.getProperty("item." + i + ".name", "");
            String sha = p.getProperty("item." + i + ".sha256", "");
            if (!file.endsWith(".jar") || file.contains("/") || file.contains("\\") || file.contains("..")
                || !sha.matches("[a-f0-9]{64}")) throw new IOException("Corrupt managed entry " + i);
            entries.put(file, sha);
        }
        return entries;
    }

    private static List<String> forgeIds(Path jar) throws IOException {
        List<String> ids = new ArrayList<>();
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            ZipEntry toml = zf.getEntry("META-INF/mods.toml");
            if (toml == null) return ids;
            String content;
            try (InputStream in = zf.getInputStream(toml)) {
                content = new String(in.readNBytes(1024 * 1024), StandardCharsets.UTF_8);
            }
            boolean insideMods = false;
            for (String line : content.split("\\R")) {
                String text = line.strip();
                if (text.startsWith("[[")) insideMods = text.matches("\\[\\[\\s*mods\\s*\\]\\]");
                if (insideMods) {
                    Matcher m = MOD_ID.matcher(text);
                    if (m.find()) ids.add(m.group(1));
                }
            }
        }
        return ids;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] block = new byte[65536];
                for (int n; (n = in.read(block)) >= 0;) if (n > 0) md.update(block, 0, n);
            }
            return java.util.HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }
}
