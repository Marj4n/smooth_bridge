package org.marj4n.smoothbridge.build;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.marj4n.smoothbridge.kilt_runtime.EmbeddedLionfish;
import org.marj4n.smoothbridge.kilt_runtime.EmbeddedCurios;
import org.marj4n.smoothbridge.kilt_runtime.EmbeddedCitadel;
import org.marj4n.smoothbridge.kilt_runtime.TargetJars;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds the selective Kilt runtime, rejecting unexpected upstream bytecode. */
public final class KiltBinaryPatcher {
    private static final String LOADER = "xyz/bluspring/kilt/loader/KiltLoader";
    private static final String TARGET_HELPER = "org/marj4n/smoothbridge/kilt_runtime/TargetJars";
    private static final String CITADEL_HELPER =
            "org/marj4n/smoothbridge/kilt_runtime/EmbeddedCitadel";
    private static final String CURIOS_HELPER =
            "org/marj4n/smoothbridge/kilt_runtime/EmbeddedCurios";
    private static final String LIONFISH_HELPER =
            "org/marj4n/smoothbridge/kilt_runtime/EmbeddedLionfish";
    private static final String LOADER_CLASS = LOADER + ".class";
    private static final Set<String> RUNTIME_HELPERS =
            Set.of(
                    TARGET_HELPER + ".class",
                    TARGET_HELPER + "$ModInfo.class",
                    LIONFISH_HELPER + ".class",
                    CURIOS_HELPER + ".class",
                    CITADEL_HELPER + ".class",
                    TARGET_HELPER + "$FabricPortCollision.class");

    private KiltBinaryPatcher() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 8) {
            throw new IllegalArgumentException(
                    "Usage: KiltBinaryPatcher <Kilt.jar> <Lionfish.jar> <Curios.jar> <Citadel.jar> <project_root> <compiled_classes_dir> <output.jar> <version>");
        }
        Path kilt = Path.of(args[0]);
        Path lionfish = Path.of(args[1]);
        Path curios = Path.of(args[2]);
        Path citadel = Path.of(args[3]);
        Path projectRoot = Path.of(args[4]);
        Path compiledClasses = Path.of(args[5]);
        Path output = Path.of(args[6]);
        String version = args[7];
        enforceKiltAsmSafePackages();
        validateSource(kilt, "Kilt", "xyz/bluspring/kilt/loader/KiltLoader.class");
        validateLionfish(lionfish);
        validateCurios(curios);
        validateCitadel(citadel);
        byte[] originalCitadel = Files.readAllBytes(citadel);
        String citadelDigest = digest(originalCitadel);
        byte[] originalCurios = Files.readAllBytes(curios);
        String curiosDigest = digest(originalCurios);
        byte[] originalLionfish = Files.readAllBytes(lionfish);
        String digest = digest(originalLionfish);

        Files.createDirectories(output.getParent());
        Path temp = Files.createTempFile(output.getParent(), "smooth_bridge-build-", ".jar.tmp");
        int patchedLoaders = 0;
        int patchedMetadata = 0;
        try {
            try (JarFile upstream = new JarFile(kilt.toFile());
                    JarOutputStream destination =
                            new JarOutputStream(Files.newOutputStream(temp))) {
                Set<String> written = new HashSet<>();
                var entries = upstream.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (isJarSignature(name)) {
                        // Modifying classes invalidates any existing signature; retain all
                        // licenses.
                        continue;
                    }
                    if (name.equals(LOADER_CLASS)) {
                        byte[] original;
                        try (InputStream in = upstream.getInputStream(entry)) {
                            original = in.readAllBytes();
                        }
                        write(destination, written, name, patchLoader(original));
                        patchedLoaders++;
                    } else if (name.equals("net/minecraftforge/common/ForgeMod.class")) {
                        try (InputStream in = upstream.getInputStream(entry)) {
                            write(
                                    destination,
                                    written,
                                    name,
                                    ModifierFallbackPatch.patch(in.readAllBytes()));
                        }
                    } else if (name.equals(
                            "xyz/bluspring/kilt/mixin/workarounds/early_screen_delay/MinecraftMixin.class")) {
                        try (InputStream in = upstream.getInputStream(entry)) {
                            write(
                                    destination,
                                    written,
                                    name,
                                    ScreenDelayPatch.patch(in.readAllBytes()));
                        }
                    } else if (name.equals("fabric.mod.json")) {
                        byte[] original;
                        try (InputStream in = upstream.getInputStream(entry)) {
                            original = in.readAllBytes();
                        }
                        String metadata =
                                customizeMetadata(
                                        new String(original, StandardCharsets.UTF_8), version);
                        write(
                                destination,
                                written,
                                name,
                                metadata.getBytes(StandardCharsets.UTF_8));
                        patchedMetadata++;
                    } else if (!entry.isDirectory()) {
                        try (InputStream in = upstream.getInputStream(entry)) {
                            write(destination, written, name, in.readAllBytes());
                        }
                    }
                }
                if (patchedLoaders != 1 || patchedMetadata != 1) {
                    throw new IllegalStateException(
                            "Refusing to release: Kilt loader class or metadata absent");
                }
                for (String name : RUNTIME_HELPERS) {
                    Path compiled = compiledClasses.resolve(name);
                    if (!Files.isRegularFile(compiled)) {
                        throw new IllegalStateException(
                                "Missing compiled runtime class: " + compiled);
                    }
                    write(destination, written, name, Files.readAllBytes(compiled));
                }
                write(
                        destination,
                        written,
                        "META-INF/smoothbridge/LICENSE-LGPL-3.0.txt",
                        Files.readAllBytes(projectRoot.resolve("LICENSE-LGPL-3.0.txt")));
                write(
                        destination,
                        written,
                        "META-INF/smoothbridge/LICENSE-GPL-3.0.txt",
                        Files.readAllBytes(projectRoot.resolve("LICENSE-GPL-3.0.txt")));
                write(destination, written, EmbeddedCitadel.RESOURCE, originalCitadel);
                write(
                        destination,
                        written,
                        EmbeddedCitadel.DIGEST,
                        (citadelDigest + "\n").getBytes(StandardCharsets.UTF_8));
                write(destination, written, EmbeddedCurios.RESOURCE, originalCurios);
                write(
                        destination,
                        written,
                        EmbeddedCurios.DIGEST,
                        (curiosDigest + "\n").getBytes(StandardCharsets.UTF_8));
                write(destination, written, EmbeddedLionfish.RESOURCE, originalLionfish);
                write(
                        destination,
                        written,
                        EmbeddedLionfish.DIGEST,
                        (digest + "\n").getBytes(StandardCharsets.UTF_8));
                write(
                        destination,
                        written,
                        "META-INF/smoothbridge/CREDITS.txt",
                        ("Smooth Bridge v"
                                        + version
                                        + "; adaptation by Marj4n.\n"
                                        + "Kilt v20.1.21 by BluSpring and contributors: LGPL-2.1-only.\n"
                                        + "Upstream: https://github.com/KiltMC/Kilt\n"
                                        + "Lionfish API v3.0 by mcl_ender and contributors: LGPL-3.0-only.\n"
                                        + "Lionfish upstream: https://modrinth.com/mod/lionfish-api\n"
                                        + "Curios API 5.14.1 by TheIllusiveC4 and contributors: LGPL-3.0-or-later.\n"
                                        + "Curios upstream: https://github.com/TheIllusiveC4/Curios\n"
                                        + "Citadel v2.6.3 by Alexthe666 and contributors: LGPL-3.0-only.\n"
                                        + "Citadel upstream: https://github.com/AlexModGuy/Citadel\n"
                                        + "Alex's Caves and Alex's Mobs gameplay mod JARs are NOT bundled.\n"
                                        + "Original gameplay mod JARs are not redistributed.\n")
                                .getBytes(StandardCharsets.UTF_8));
                write(
                        destination,
                        written,
                        "META-INF/smoothbridge/BUILD_STATUS.txt",
                        ("EXPERIMENTAL UNVERIFIED GAMEPLAY\n"
                                        + "Java bytecode patch passed structural checks only.\n"
                                        + "Minecraft 1.20.1 real client/server smoke testing is REQUIRED.\n"
                                        + "Kilt SHA256="
                                        + digest(Files.readAllBytes(kilt))
                                        + "\n"
                                        + "Lionfish SHA256="
                                        + digest
                                        + "\n"
                                        + "Curios SHA256="
                                        + curiosDigest
                                        + "\n"
                                        + "Citadel SHA256="
                                        + citadelDigest
                                        + "\n")
                                .getBytes(StandardCharsets.UTF_8));
            }
            validateOutput(temp);
            Files.move(temp, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Smooth Bridge experimental candidate: " + output);
            System.out.println("IMPORTANT: not verified to load all three mods in Minecraft.");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Kilt 1.20.1's KiltEarlyRiser.processForgeClasses() automatically transforms
     * every class bundled in the Kilt JAR unless its internal name contains an
     * ignored keyword. "kilt" is on its upstream ignored-keyword list.
     *
     * If our early-loaded helpers live outside this namespace, Fabric-ASM
     * mistakenly adds them to mixins.mm_kilt.json and crashes at preLaunch
     * with MixinTargetAlreadyLoadedException.
     */
    private static void enforceKiltAsmSafePackages() {
        for (String helper : RUNTIME_HELPERS) {
            if (!helper.contains("/kilt_runtime/") || !helper.endsWith(".class")) {
                throw new IllegalStateException(
                        "Smooth Bridge helper is not excluded from Kilt ASM scanning: " + helper);
            }
        }
        if (!TARGET_HELPER.contains("/kilt_runtime/")
                || !LIONFISH_HELPER.contains("/kilt_runtime/")
                || !CURIOS_HELPER.contains("/kilt_runtime/")
                || !CITADEL_HELPER.contains("/kilt_runtime/")) {
            throw new IllegalStateException(
                    "Smooth Bridge runtime helpers must remain in kilt_runtime");
        }
    }

    private static void validateSource(Path file, String label, String requiredEntry)
            throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            if (jar.getJarEntry(requiredEntry) == null) {
                throw new IllegalStateException(label + " artifact is missing " + requiredEntry);
            }
        }
    }

    private static void validateLionfish(Path file) throws IOException {
        validateSource(file, "Lionfish API", "META-INF/mods.toml");
        try (JarFile jar = new JarFile(file.toFile());
                InputStream stream = jar.getInputStream(jar.getJarEntry("META-INF/mods.toml"))) {
            String toml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (!Pattern.compile("(?m)^\\s*modId\\s*=\\s*\"lionfishapi\"").matcher(toml).find()
                    || !Pattern.compile("(?m)^\\s*version\\s*=\\s*\"3\\.0\"")
                            .matcher(toml)
                            .find()) {
                throw new IllegalStateException(
                        "Downloaded artifact is not Lionfish API 3.0 for Forge 1.20.1");
            }
        }
    }

    private static void validateCurios(Path file) throws IOException {
        validateSource(
                file,
                "Curios API",
                "top/theillusivec4/curios/api/type/capability/ICurioItem.class");
        validateSource(file, "Curios API", "META-INF/mods.toml");
        try (JarFile jar = new JarFile(file.toFile());
                InputStream stream = jar.getInputStream(jar.getJarEntry("META-INF/mods.toml"))) {
            String toml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (!Pattern.compile("(?m)^\\s*modId\\s*=\\s*\"curios\"").matcher(toml).find()) {
                throw new IllegalStateException(
                        "Downloaded Curios artifact isn't the full Forge mod");
            }
            if (!toml.contains("${file.jarVersion}") && !toml.contains("5.14.1")) {
                throw new IllegalStateException("Unexpected Curios metadata version");
            }
        }
    }

    private static void validateCitadel(Path file) throws IOException {
        validateSource(file, "Citadel 2.6.3", "META-INF/mods.toml");
        validateSource(file, "Citadel 2.6.3", "com/github/alexthe666/citadel/Citadel.class");
        try (JarFile jar = new JarFile(file.toFile());
                InputStream in = jar.getInputStream(jar.getJarEntry("META-INF/mods.toml"))) {
            String toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (!Pattern.compile("(?m)^\\s*modId\\s*=\\s*\"citadel\"").matcher(toml).find()) {
                throw new IllegalStateException("Downloaded Citadel JAR has unexpected modId");
            }
            if (!toml.contains("2.6.3") && !toml.contains("${file.jarVersion}")) {
                throw new IllegalStateException("Expected Citadel 2.6.3 metadata");
            }
            if (toml.contains("${file.jarVersion}")
                    && (jar.getManifest() == null
                            || jar.getManifest()
                                            .getMainAttributes()
                                            .getValue("Implementation-Version")
                                    == null)) {
                throw new IllegalStateException(
                        "Citadel metadata version placeholder requires manifest Implementation-Version");
            }
        }
    }

    static byte[] patchLoader(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        if (!LOADER.equals(node.name)) {
            throw new IllegalStateException("Unexpected Kilt loader class name " + node.name);
        }
        MethodNode discovery =
                method(node, "getModDefinitions", "(Ljava/nio/file/Path;)Ljava/util/List;");
        MethodNode builtin = method(node, "getBuiltinModDefinitions", "()Ljava/util/List;");
        method(
                node,
                "loadModDefinitions",
                "(Ljava/nio/file/Path;Ljava/util/List;)Ljava/util/List;");

        // No new branch or stackmap frame: one original Path local gets replaced
        // with the allowlisted Path (or a clean no-mod JAR) before Kotlin executes.
        InsnList before = new InsnList();
        before.add(new VarInsnNode(Opcodes.ALOAD, 1));
        before.add(
                new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        TARGET_HELPER,
                        "permitAndPrepare",
                        "(Ljava/nio/file/Path;)Ljava/nio/file/Path;",
                        false));
        before.add(new VarInsnNode(Opcodes.ASTORE, 1));
        discovery.instructions.insert(before);

        // The list already on stack at each ARETURN is extended using the
        // ORIGINAL KiltLoader::loadModDefinitions via Java reflection.
        int patchedReturns = 0;
        for (AbstractInsnNode instruction = builtin.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() == Opcodes.ARETURN) {
                InsnList beforeReturn = new InsnList();
                // Citadel is conditionally loaded for original Alex mods.
                beforeReturn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                beforeReturn.add(
                        new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                CITADEL_HELPER,
                                "extendBuiltIns",
                                "(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;",
                                false));
                // Curios is loaded before Lionfish and Cataclysm. Both go through
                // Kilt's real Forge discovery and metadata/mixin processing.
                beforeReturn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                beforeReturn.add(
                        new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                CURIOS_HELPER,
                                "extendBuiltIns",
                                "(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;",
                                false));
                beforeReturn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                beforeReturn.add(
                        new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                LIONFISH_HELPER,
                                "extendBuiltIns",
                                "(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;",
                                false));
                builtin.instructions.insertBefore(instruction, beforeReturn);
                patchedReturns++;
            }
        }
        if (patchedReturns < 1 || patchedReturns > 3) {
            throw new IllegalStateException(
                    "Unexpected Kilt getBuiltinModDefinitions return structure: " + patchedReturns);
        }
        // Existing stackmap control flow is retained. Recalculate only max stack.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        throw new IllegalStateException("Kilt ABI changed, could not find " + name + descriptor);
    }

    private static String customizeMetadata(String json, String version) {
        if (!Pattern.compile("\"id\"\\s*:\\s*\"kilt\"").matcher(json).find()) {
            throw new IllegalStateException(
                    "Upstream is not the expected technical kilt Fabric mod");
        }
        // Technical ID "kilt" MUST stay to preserve internal upstream bootstrap,
        // but Mod Menu displays Smooth Bridge and other mods can query our alias.
        String result = replaceJsonString(json, "name", "Smooth Bridge");
        result = replaceJsonString(result, "version", version);
        result =
                replaceJsonString(
                        result,
                        "description",
                        "Selective Forge-to-Fabric compatibility using Kilt runtime (experimental). Adaptation: Marj4n.");
        Pattern provides = Pattern.compile("(\"provides\"\\s*:\\s*\\[)([^]]*)(])", Pattern.DOTALL);
        Matcher matcher = provides.matcher(result);
        if (!matcher.find()) {
            throw new IllegalStateException("Kilt metadata missing 'provides' list");
        }
        String list = matcher.group(2);
        if (!list.contains("\"forge\"")) {
            throw new IllegalStateException("Kilt Forge capability missing");
        }
        if (!list.contains("\"smooth_bridge\"")) {
            result =
                    matcher.replaceFirst(
                            Matcher.quoteReplacement(
                                    matcher.group(1)
                                            + list.trim()
                                            + ", \"smooth_bridge\""
                                            + matcher.group(3)));
        }
        return result;
    }

    private static String replaceJsonString(String json, String key, String value) {
        Pattern pattern = Pattern.compile("(\"" + Pattern.quote(key) + "\"\\s*:\\s*)\"[^\"]*\"");
        Matcher match = pattern.matcher(json);
        if (!match.find()) {
            throw new IllegalStateException("Kilt metadata missing " + key);
        }
        return match.replaceFirst(Matcher.quoteReplacement(match.group(1) + "\"" + value + "\""));
    }

    private static void validateOutput(Path output) throws IOException {
        try (JarFile candidate = new JarFile(output.toFile())) {
            String[] entries = {
                "fabric.mod.json",
                LOADER_CLASS,
                TARGET_HELPER + ".class",
                LIONFISH_HELPER + ".class",
                CURIOS_HELPER + ".class",
                CITADEL_HELPER + ".class",
                EmbeddedLionfish.RESOURCE,
                EmbeddedLionfish.DIGEST,
                EmbeddedCurios.RESOURCE,
                EmbeddedCurios.DIGEST,
                EmbeddedCitadel.RESOURCE,
                EmbeddedCitadel.DIGEST,
                "META-INF/smoothbridge/LICENSE-LGPL-3.0.txt",
                "META-INF/smoothbridge/LICENSE-GPL-3.0.txt",
                "META-INF/smoothbridge/BUILD_STATUS.txt",
                "META-INF/forge.mods.toml"
            };
            for (String entry : entries) {
                if (candidate.getJarEntry(entry) == null) {
                    throw new IllegalStateException("Output incomplete: " + entry);
                }
            }
            // Prevent regression of the reported preLaunch crash: early-loaded Java
            // bridge helpers must not be scheduled for Kilt's ASM transformations.
            for (String helper : RUNTIME_HELPERS) {
                if (!helper.startsWith("org/marj4n/smoothbridge/kilt_runtime/")) {
                    throw new IllegalStateException(
                            "Unsafe helper name in runtime output: " + helper);
                }
            }
            String metadata =
                    new String(
                            candidate
                                    .getInputStream(candidate.getJarEntry("fabric.mod.json"))
                                    .readAllBytes(),
                            StandardCharsets.UTF_8);
            if (!metadata.contains("\"name\": \"Smooth Bridge\"")
                    || !metadata.contains("smooth_bridge")
                    || !metadata.contains("\"id\": \"kilt\"")) {
                throw new IllegalStateException("Output contains the wrong mod metadata");
            }
        }
    }

    private static boolean isJarSignature(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA"));
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void write(
            JarOutputStream output, Set<String> recorded, String name, byte[] bytes)
            throws IOException {
        if (!recorded.add(name)) {
            throw new IllegalStateException("Duplicate output JAR entry " + name);
        }
        output.putNextEntry(new JarEntry(name));
        output.write(bytes);
        output.closeEntry();
    }
}
