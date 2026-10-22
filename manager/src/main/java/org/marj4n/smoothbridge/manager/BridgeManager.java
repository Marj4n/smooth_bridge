package org.marj4n.smoothbridge.manager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Self-contained pre-launch Forge JAR manager. It does NOT implement the Forge APIs:
 * Kilt remains responsible for Forge loading and execution.
 *
 * Security/safety: never downloads, modifies Forge JAR bytes, overrides unmanaged mods,
 * or deletes a managed JAR whose checksum no longer matches the journal.
 */
public final class BridgeManager {
    private static final String VERSION = "0.1.0";
    private static final Pattern FABRIC_ID = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern MOD_ID = Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']");
    private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> PROFILE_ORDER = List.of("skarrier", "azazel", "cataclysm");
    private static final Map<String, List<String>> PROFILES = Map.of(
        "skarrier", List.of("skarrier", "geckolib"),
        "azazel", List.of("azazel", "geckolib"),
        "cataclysm", List.of("cataclysm", "lionfish", "curios"),
        "none", List.of()
    );
    private static final Map<String, List<String>> ALIASES = Map.of(
        "skarrier", List.of("skarrier"),
        "azazel", List.of("cultofazazel", "cult_of_azazel"),
        "cataclysm", List.of("cataclysm"),
        "geckolib", List.of("geckolib", "gecko_lib"),
        "lionfish", List.of("lionfish"),
        "curios", List.of("curios")
    );
    private static final String JOURNAL_HEADER = "Managed by Smooth Bridge; do not edit while Minecraft is running.";

    private BridgeManager() {}

    record Options(String command, Path gameDir, String profile) {}
    record JarInfo(Path path, Set<String> forgeIds, String fabricId, String sha256, boolean suspectedWrongGameVersion) {
        boolean forge() { return !forgeIds.isEmpty(); }
        String filename() { return path.getFileName().toString(); }
    }
    record Managed(String filename, String sha256, String role) {}
    record Scan(Map<String, JarInfo> staged, Map<String, JarInfo> active,
                List<String> warnings, List<String> errors, Set<String> desired) {}

    public static void main(String[] args) {
        try {
            Options opt = parse(args);
            if ("help".equals(opt.command())) {
                usage();
                return;
            }
            if (!Files.isDirectory(opt.gameDir())) {
                throw new IllegalArgumentException("Instance directory does not exist: " + opt.gameDir());
            }
            switch (opt.command()) {
                case "plan", "doctor" -> showPlan(opt);
                case "apply" -> apply(opt);
                case "status" -> status(opt);
                case "rollback" -> rollback(opt);
                default -> throw new IllegalArgumentException("Unknown action: " + opt.command());
            }
        } catch (IllegalArgumentException ex) {
            System.err.println("[Smooth Bridge] ERROR: " + ex.getMessage());
            usage();
            System.exit(2);
        } catch (Exception ex) {
            System.err.println("[Smooth Bridge] FAILED: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(3);
        }
    }

    private static Options parse(String[] args) {
        if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) {
            return new Options("help", Path.of("."), "skarrier");
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        Path path = null;
        String profile = "skarrier";
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--game-dir" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("--game-dir needs a path");
                    path = Path.of(args[++i]).toAbsolutePath().normalize();
                }
                case "--profile" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("--profile needs a name");
                    profile = args[++i].toLowerCase(Locale.ROOT);
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (path == null) throw new IllegalArgumentException("Pass --game-dir <path to COPY of your Minecraft instance>");
        if (!(PROFILES.containsKey(profile) || "all".equals(profile))) {
            throw new IllegalArgumentException("Profile must be skarrier, azazel, cataclysm, all, or none");
        }
        return new Options(action, path, profile);
    }

    private static void usage() {
        System.out.println("Smooth Bridge Manager " + VERSION + " — MC 1.20.1 / Fabric / Kilt");
        System.out.println("  java -jar smooth-bridge-manager-0.1.0.jar plan     --game-dir <instance> --profile skarrier");
        System.out.println("  java -jar smooth-bridge-manager-0.1.0.jar apply    --game-dir <instance> --profile skarrier");
        System.out.println("  java -jar smooth-bridge-manager-0.1.0.jar status   --game-dir <instance>");
        System.out.println("  java -jar smooth-bridge-manager-0.1.0.jar rollback --game-dir <instance>");
        System.out.println("Profiles: skarrier | azazel | cataclysm | all | none");
        System.out.println("Stage original Forge 1.20.1 JARs in <instance>/smoothbridge/staging/ first.");
        System.out.println("PLAN only inspects. APPLY stages allowlisted JARs and snapshots previously managed JARs.");
        System.out.println("ROLLBACK restores the immediately previous managed state. NEVER use on a live instance.");
    }

    private static List<Path> jarFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        List<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path file : files) {
                if (Files.isRegularFile(file) && !Files.isSymbolicLink(file)) paths.add(file);
            }
        }
        paths.sort(Comparator.comparing(x -> x.getFileName().toString().toLowerCase(Locale.ROOT)));
        return paths;
    }

    private static JarInfo inspect(Path jar, boolean hash) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        String fabricId = "";
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            ZipEntry forgeEntry = zf.getEntry("META-INF/mods.toml");
            if (forgeEntry != null) {
                String toml;
                try (InputStream input = zf.getInputStream(forgeEntry)) {
                    toml = new String(input.readNBytes(1024 * 1024), StandardCharsets.UTF_8);
                }
                // Ignore dependency modId entries. Only IDs declared in [[mods]] sections belong to this JAR.
                boolean inMods = false;
                for (String line : toml.split("\\R")) {
                    String clean = line.strip();
                    if (clean.startsWith("[[")) {
                        inMods = clean.matches("\\[\\[\\s*mods\\s*\\]\\]");
                    }
                    if (inMods) {
                        Matcher m = MOD_ID.matcher(clean);
                        if (m.find()) ids.add(m.group(1).toLowerCase(Locale.ROOT));
                    }
                }
            }
            ZipEntry fabricEntry = zf.getEntry("fabric.mod.json");
            if (fabricEntry != null) {
                String json;
                try (InputStream input = zf.getInputStream(fabricEntry)) {
                    json = new String(input.readNBytes(256 * 1024), StandardCharsets.UTF_8);
                }
                Matcher m = FABRIC_ID.matcher(json);
                if (m.find()) fabricId = m.group(1).toLowerCase(Locale.ROOT);
            }
        }
        boolean wrong = jar.getFileName().toString().matches("(?i).*1\\.(?:21|19|18|16)(?:\\.\\d+)?[-_.].*")
                     || jar.getFileName().toString().matches("(?i).*[-_.]1\\.(?:21|19|18|16)(?:\\.\\d+)?\\.jar");
        return new JarInfo(jar, Set.copyOf(ids), fabricId, hash ? sha256(jar) : "", wrong);
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = in.read(buffer)) >= 0;) if (n > 0) digest.update(buffer, 0, n);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError("Java SHA-256 is unavailable", ex);
        }
    }

    private static String normalized(String id) { return id.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT); }
    private static boolean matchesRole(JarInfo j, String role) {
        String filename = normalized(j.filename());
        List<String> aliases = ALIASES.get(role);
        boolean filenameMatch = aliases.stream().anyMatch(a -> filename.contains(normalized(a)));
        boolean modIdMatch = j.forgeIds.stream().map(BridgeManager::normalized)
            .anyMatch(id -> aliases.stream().map(BridgeManager::normalized).anyMatch(id::contains));
        // Both filename and Forge metadata must agree; this is NOT a signature verification.
        return j.forge() && filenameMatch && modIdMatch;
    }

    private static Set<String> desired(String profile) {
        Set<String> result = new LinkedHashSet<>();
        if ("all".equals(profile)) {
            for (String p : PROFILE_ORDER) result.addAll(PROFILES.get(p));
        } else {
            result.addAll(PROFILES.get(profile));
        }
        return result;
    }

    private static Map<String, JarInfo> listAsMap(Path dir, boolean hash) throws IOException {
        Map<String, JarInfo> jars = new LinkedHashMap<>();
        for (Path p : jarFiles(dir)) {
            try {
                jars.put(p.getFileName().toString(), inspect(p, hash));
            } catch (IOException ex) {
                throw new IOException("Could not read ZIP/JAR " + p.getFileName() + ": " + ex.getMessage(), ex);
            }
        }
        return jars;
    }

    private static Scan scan(Options opt, boolean hash) throws IOException {
        Path stage = opt.gameDir().resolve("smoothbridge/staging");
        Path mods = opt.gameDir().resolve("mods");
        Map<String, JarInfo> staged = listAsMap(stage, hash);
        Map<String, JarInfo> active = listAsMap(mods, hash);
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        boolean kilt = active.values().stream().anyMatch(j -> j.fabricId().equals("kilt"));
        if (!kilt) errors.add("Kilt fabric.mod.json ID not found in mods/. Install official Kilt for Minecraft 1.20.1.");
        if (active.values().stream().noneMatch(j -> j.fabricId().equals("fabric-api")))
            warnings.add("Fabric API not detected; Kilt requires Fabric API.");
        if (active.values().stream().noneMatch(j -> j.fabricId().equals("fabric-language-kotlin")))
            warnings.add("Fabric Language Kotlin not detected; Kilt requires it.");
        if (active.values().stream().noneMatch(j -> j.fabricId().equals("smooth_bridge")))
            warnings.add("Smooth Bridge Fabric runtime not detected. The manager still works, but runtime report will be absent.");
        if (active.values().stream().anyMatch(j -> j.fabricId().equals("quilt_loader")))
            warnings.add("Quilt Loader detected; Kilt does not support it.");
        if (active.values().stream().anyMatch(j -> j.fabricId().equals("embeddium")))
            warnings.add("Embeddium detected; current Kilt releases report incompatibility.");
        Map<String, Managed> managed = loadManaged(opt.gameDir());
        for (JarInfo j : active.values()) if (j.forge() && !j.fabricId().equals("kilt") && !managed.containsKey(j.filename())) {
            errors.add("UNMANAGED Forge JAR already in mods/: " + j.filename()
                + " (Kilt would load it outside the allowlist; relocate it manually to staging or outside mods/)");
        }
        for (JarInfo j : staged.values()) {
            if (!j.forge()) warnings.add("Ignored non-Forge staged JAR: " + j.filename());
        }
        Set<String> wanted = desired(opt.profile());
        if (wanted.contains("geckolib") && active.values().stream()
            .anyMatch(j -> j.fabricId().equals("geckolib"))) {
            errors.add("Fabric GeckoLib already exists in mods/. Do NOT install duplicate Forge GeckoLib on top; "
                + "test this profile in a clean isolated instance, then investigate a proper GeckoLib bridge.");
        }
        for (String role : wanted) {
            List<JarInfo> found = staged.values().stream().filter(j -> matchesRole(j, role)).toList();
            if (found.isEmpty()) errors.add("Missing staged Forge JAR for " + role);
            if (found.size() > 1) errors.add("Multiple staged JARs match " + role + ": " + found.stream().map(JarInfo::filename).toList());
            for (JarInfo jar : found) {
                if (jar.suspectedWrongGameVersion()) errors.add("Suspicious non-1.20.1 version: " + jar.filename());
            }
        }
        return new Scan(staged, active, warnings, errors, wanted);
    }

    private static void printScan(Options opt, Scan scan) {
        System.out.println("\nSmooth Bridge " + VERSION + " | Instance: " + opt.gameDir());
        System.out.println("Profile: " + opt.profile() + " | Allowed roles: " + scan.desired());
        System.out.println("Staging JARs: " + scan.staged().size() + " | Active JARs: " + scan.active().size());
        for (String role : scan.desired()) {
            List<JarInfo> found = scan.staged().values().stream().filter(j -> matchesRole(j, role)).toList();
            for (JarInfo jar : found) {
                System.out.println("  [READY] " + role + " <- " + jar.filename() + " | Forge mod IDs " + jar.forgeIds());
                if (!jar.sha256().isEmpty()) System.out.println("          SHA-256 " + jar.sha256());
            }
        }
        for (String warning : scan.warnings()) System.out.println("  [WARN] " + warning);
        for (String error : scan.errors()) System.out.println("  [BLOCK] " + error);
        System.out.println("Result: " + (scan.errors().isEmpty() ? "PREFLIGHT OK (NOT game compatibility proof)" : "BLOCKED") + "\n");
    }

    private static void showPlan(Options opt) throws IOException {
        Scan s = scan(opt, true);
        printScan(opt, s);
        // Deliberately diagnostic; failures appear in output even when PLAN is a read-only inspection.
    }

    private static Path stateFile(Path root) { return root.resolve("smoothbridge/state/managed.properties"); }
    private static Path lastBackupFile(Path root) { return root.resolve("smoothbridge/state/last-backup.txt"); }
    private static boolean isSafeFile(String file) {
        return file != null && file.endsWith(".jar") && file.equals(Path.of(file).getFileName().toString())
            && !file.equals(".") && !file.contains("..") && !file.contains("/") && !file.contains("\\");
    }

    private static Map<String, Managed> loadManaged(Path root) throws IOException {
        Path state = stateFile(root);
        if (!Files.exists(state)) return new LinkedHashMap<>();
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(state)) { props.load(in); }
        int count;
        try { count = Integer.parseInt(props.getProperty("count", "0")); }
        catch (NumberFormatException ex) { throw new IOException("Corrupt managed state count", ex); }
        if (count < 0 || count > 500) throw new IOException("Unsafe managed count: " + count);
        Map<String, Managed> result = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String file = props.getProperty("item." + i + ".name");
            String sha = props.getProperty("item." + i + ".sha256");
            String role = props.getProperty("item." + i + ".role", "unknown");
            if (!isSafeFile(file) || sha == null || !sha.matches("[a-f0-9]{64}")) {
                throw new IOException("Corrupt/unsafe managed state entry #" + i);
            }
            if (result.put(file, new Managed(file, sha, role)) != null) throw new IOException("Duplicate journal file " + file);
        }
        return result;
    }

    private static void writeManaged(Path root, Map<String, Managed> entries, String profile) throws IOException {
        Properties p = new Properties();
        p.setProperty("version", "1");
        p.setProperty("profile", profile);
        p.setProperty("count", String.valueOf(entries.size()));
        int index = 0;
        for (Managed m : entries.values()) {
            p.setProperty("item." + index + ".name", m.filename());
            p.setProperty("item." + index + ".sha256", m.sha256());
            p.setProperty("item." + index + ".role", m.role());
            index++;
        }
        Path state = stateFile(root);
        Files.createDirectories(state.getParent());
        Path tmp = Files.createTempFile(state.getParent(), "bridge-state-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) { p.store(out, JOURNAL_HEADER); }
            move(tmp, state);
        } finally { Files.deleteIfExists(tmp); }
    }

    private static void move(Path from, Path dest) throws IOException {
        try { Files.move(from, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) {
            Files.move(from, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void verifyManaged(Path root, Map<String, Managed> managed) throws IOException {
        for (Managed m : managed.values()) {
            Path target = root.resolve("mods").resolve(m.filename());
            if (!Files.isRegularFile(target) || Files.isSymbolicLink(target))
                throw new IOException("Previously managed JAR missing/invalid: " + m.filename() + ". Resolve manually.");
            if (!sha256(target).equals(m.sha256()))
                throw new IOException("Previously managed JAR changed outside Bridge: " + m.filename() + ". Refusing to modify it.");
        }
    }

    private static String saveSnapshot(Path root, Map<String, Managed> old) throws IOException {
        Path backup = root.resolve("smoothbridge/backups")
            .resolve(BACKUP_TS.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 6));
        Files.createDirectories(backup);
        Path state = stateFile(root);
        if (Files.exists(state)) Files.copy(state, backup.resolve("managed.properties"));
        else Files.writeString(backup.resolve("empty.marker"), "No managed JARs before this apply.");
        for (Managed m : old.values()) {
            Files.copy(root.resolve("mods").resolve(m.filename()), backup.resolve(m.filename()));
        }
        return backup.getFileName().toString();
    }

    private static void saveLastBackup(Path root, String backupId) throws IOException {
        Path marker = lastBackupFile(root);
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, backupId + "\n", StandardCharsets.UTF_8);
    }

    private static void apply(Options opt) throws IOException {
        Path root = opt.gameDir();
        if (!Files.isDirectory(root.resolve("mods")))
            throw new IOException("Instance mods/ directory missing. Refusing to create one in an unverified instance.");
        Scan s = scan(opt, true);
        printScan(opt, s);
        if (!s.errors().isEmpty()) throw new IOException("Preflight blocked. See errors above; nothing changed.");
        Map<String, Managed> old = loadManaged(root);
        verifyManaged(root, old);
        Map<String, Managed> desired = new LinkedHashMap<>();
        Map<String, Path> inputs = new LinkedHashMap<>();
        for (String role : s.desired()) {
            JarInfo jar = s.staged().values().stream().filter(j -> matchesRole(j, role)).findFirst().orElseThrow();
            desired.put(jar.filename(), new Managed(jar.filename(), jar.sha256(), role));
            inputs.put(jar.filename(), jar.path());
        }
        // Do not replace files we didn't install, even if a matching filename exists.
        for (String filename : desired.keySet()) {
            if (Files.exists(root.resolve("mods").resolve(filename)) && !old.containsKey(filename)) {
                throw new IOException("Destination already exists but is unmanaged: " + filename + ". Nothing changed.");
            }
        }
        String backupId = saveSnapshot(root, old);
        List<Path> written = new ArrayList<>();
        try {
            for (Managed m : old.values()) Files.delete(root.resolve("mods").resolve(m.filename()));
            for (Managed m : desired.values()) {
                Path dest = root.resolve("mods").resolve(m.filename());
                Path temp = Files.createTempFile(root.resolve("mods"), "bridge-stage-", ".tmp");
                try {
                    Files.copy(inputs.get(m.filename()), temp, StandardCopyOption.REPLACE_EXISTING);
                    if (!sha256(temp).equals(m.sha256())) throw new IOException("Hash mismatch during copy: " + m.filename());
                    move(temp, dest);
                    written.add(dest);
                } finally { Files.deleteIfExists(temp); }
            }
            writeManaged(root, desired, opt.profile());
            saveLastBackup(root, backupId);
        } catch (Exception ex) {
            for (Path path : written) Files.deleteIfExists(path);
            Path backup = root.resolve("smoothbridge/backups").resolve(backupId);
            for (Managed m : old.values()) Files.copy(backup.resolve(m.filename()), root.resolve("mods").resolve(m.filename()), StandardCopyOption.REPLACE_EXISTING);
            if (Files.exists(backup.resolve("managed.properties"))) {
                Files.copy(backup.resolve("managed.properties"), stateFile(root), StandardCopyOption.REPLACE_EXISTING);
            } else Files.deleteIfExists(stateFile(root));
            throw new IOException("Apply failed and old managed JARs were restored: " + ex.getMessage(), ex);
        }
        System.out.println("[SUCCESS] Activated " + desired.size() + " allowed Forge JAR(s).");
        System.out.println("[BACKUP]  smoothbridge/backups/" + backupId);
        System.out.println("[WARNING] Minecraft/Kilt compatibility NOT verified. Use a COPIED test world first.");
    }

    private static void status(Options opt) throws IOException {
        Map<String, Managed> old = loadManaged(opt.gameDir());
        System.out.println("Smooth Bridge managed JARs in " + opt.gameDir() + ": " + old.size());
        for (Managed m : old.values()) {
            Path path = opt.gameDir().resolve("mods").resolve(m.filename());
            String status = !Files.isRegularFile(path) ? "MISSING" : sha256(path).equals(m.sha256()) ? "OK" : "MODIFIED";
            System.out.println("  [" + status + "] " + m.role() + " -> " + m.filename());
        }
        if (old.isEmpty()) System.out.println("No managed Forge mods. Unmanaged mods are never touched.");
    }

    private static void rollback(Options opt) throws IOException {
        Path root = opt.gameDir();
        Path marker = lastBackupFile(root);
        if (!Files.isRegularFile(marker)) throw new IOException("No prior Bridge apply snapshot to restore.");
        String id = Files.readString(marker).trim();
        if (!id.matches("\\d{8}-\\d{6}-[0-9a-f]{6}")) throw new IOException("Unsafe backup identifier");
        Path backup = root.resolve("smoothbridge/backups").resolve(id);
        if (!Files.isDirectory(backup)) throw new IOException("Backup folder missing: " + id);
        Map<String, Managed> current = loadManaged(root);
        verifyManaged(root, current);
        Map<String, Managed> previous = new LinkedHashMap<>();
        if (Files.exists(backup.resolve("managed.properties"))) {
            // Parse snapshot through the same parser in a private temporary root-like directory.
            Path tempRoot = Files.createTempDirectory(root.resolve("smoothbridge/state"), "rollback-parse-");
            try {
                Path stateDir = tempRoot.resolve("smoothbridge/state");
                Files.createDirectories(stateDir);
                Files.copy(backup.resolve("managed.properties"), stateDir.resolve("managed.properties"));
                previous = loadManaged(tempRoot);
            } finally {
                Files.deleteIfExists(tempRoot.resolve("smoothbridge/state/managed.properties"));
                Files.deleteIfExists(tempRoot.resolve("smoothbridge/state"));
                Files.deleteIfExists(tempRoot.resolve("smoothbridge"));
                Files.deleteIfExists(tempRoot);
            }
        }
        for (Managed m : previous.values()) {
            Path src = backup.resolve(m.filename());
            if (!Files.isRegularFile(src) || !sha256(src).equals(m.sha256()))
                throw new IOException("Backup JAR missing/modified: " + m.filename());
            Path destination = root.resolve("mods").resolve(m.filename());
            if (Files.exists(destination) && !current.containsKey(m.filename()))
                throw new IOException("Unmanaged JAR conflicts with rollback: " + m.filename());
        }
        for (Managed m : current.values()) Files.delete(root.resolve("mods").resolve(m.filename()));
        for (Managed m : previous.values()) {
            Files.copy(backup.resolve(m.filename()), root.resolve("mods").resolve(m.filename()));
        }
        if (Files.exists(backup.resolve("managed.properties")))
            Files.copy(backup.resolve("managed.properties"), stateFile(root), StandardCopyOption.REPLACE_EXISTING);
        else Files.deleteIfExists(stateFile(root));
        Files.delete(marker);
        System.out.println("[RESTORED] Previous managed profile restored from " + id);
        System.out.println("[NOTE] World saves were not changed or rolled back.");
    }
}
