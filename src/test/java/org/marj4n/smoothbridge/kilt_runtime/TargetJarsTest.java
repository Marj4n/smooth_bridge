package org.marj4n.smoothbridge.kilt_runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TargetJarsTest {
    @TempDir Path temporary;
    private String originalUserDir;

    @BeforeEach void setup() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", temporary.toString());
    }

    @AfterEach void cleanup() {
        System.setProperty("user.dir", originalUserDir);
    }

    @Test void allowsTheFivePinnedModsByForgeIdAndVersion() throws IOException {
        for (String[] target : new String[][] {
                {"skarrier_mobs", "1.1.1"},
                {"netherman", "1.1.4.12"},
                {"cataclysm", "3.31"},
                {"alexscaves", "2.0.2"},
                {"alexsmobs", "1.22.9"}
        }) {
            Path jar = fakeJar(target[0] + ".jar", toml(target[0], target[1], "[47,)"), false);
            Path selected = TargetJars.permitAndPrepare(jar);
            assertTrue(Files.isRegularFile(selected));
            try (JarFile selectedJar = new JarFile(selected.toFile())) {
                assertNotNull(selectedJar.getJarEntry("META-INF/mods.toml"));
            }
        }
    }

    @Test void refusesFabricAndUnknownForgeJars() throws IOException {
        Path fabric = fakeJar("fabric.jar", toml("cataclysm", "3.31", "[47,)"), true);
        Path foreign = fakeJar("foreign.jar", toml("unrelated_mod", "1.0", "[47,)"), false);
        Path wrongVersion = fakeJar("wrong.jar", toml("cataclysm", "999", "[47,)"), false);
        for (Path input : new Path[] { fabric, foreign, wrongVersion }) {
            Path selected = TargetJars.permitAndPrepare(input);
            try (JarFile selectedJar = new JarFile(selected.toFile())) {
                assertNull(selectedJar.getJarEntry("META-INF/mods.toml"));
            }
        }
    }

    @Test void normalizesAzazelOnlyInCacheAndPreservesOriginalBytes() throws Exception {
        Path azazel = fakeJar("original-azazel.jar", toml("netherman", "1.1.4.12", "Forge"), false);
        String hash = TargetJars.sha256(Files.readAllBytes(azazel));
        Path normalized = TargetJars.permitAndPrepare(azazel);
        assertNotEquals(azazel, normalized);
        assertEquals(hash, TargetJars.sha256(Files.readAllBytes(azazel)));
        assertTrue(normalized.startsWith(temporary));
        assertTrue(readToml(normalized).contains("loaderVersion=\"[47,)\""));
        assertTrue(readToml(azazel).contains("loaderVersion=\"Forge\""));
        assertEquals(normalized, TargetJars.permitAndPrepare(azazel));
    }

    @Test void helperClassesAreInKiltIgnoredNamespace() {
        assertTrue(TargetJars.class.getName().contains("kilt"));
        assertTrue(EmbeddedLionfish.class.getName().contains("kilt"));
        assertTrue(TargetJars.class.getName().contains(".kilt_runtime."));
        assertTrue(EmbeddedLionfish.class.getName().contains(".kilt_runtime."));
        assertTrue(EmbeddedCurios.class.getName().contains(".kilt_runtime."));
        assertTrue(EmbeddedCitadel.class.getName().contains(".kilt_runtime."));
    }

    @Test void rejectsFakeCuriosJarAndRequiresActualApiBytecode() throws IOException {
        Path fake = fakeJar("fake-curios.jar", toml("curios", "5.14.1+1.20.1", "[47,)"), false);
        assertThrows(IOException.class, () -> EmbeddedCurios.validateArchive(fake));
        Path properShape = temporary.resolve("curios-shape.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(properShape))) {
            write(jar, "META-INF/mods.toml", toml("curios", "${file.jarVersion}", "[47,)"));
            write(jar, "top/theillusivec4/curios/api/type/capability/ICurioItem.class", "fixture");
        }
        assertDoesNotThrow(() -> EmbeddedCurios.validateArchive(properShape));
    }

    @Test void bundledCitadelIsOnlyNeededForExactOriginalAlexForgeTargets() throws IOException {
        Path mods = Files.createDirectories(temporary.resolve("mods"));
        assertFalse(TargetJars.needsEmbeddedCitadel());
        fakeJar("mods/alexscaves-port.jar", toml("alexscaves", "1.1.1", "[47,)"), true);
        assertFalse(TargetJars.needsEmbeddedCitadel());
        fakeJar("mods/other-forge.jar", toml("alexsmobs", "1.22.8", "[47,)"), false);
        assertFalse(TargetJars.needsEmbeddedCitadel());
        fakeJar("mods/alexsmobs-forge.jar", toml("alexsmobs", "1.22.9", "[47,)"), false);
        assertTrue(TargetJars.needsEmbeddedCitadel());
    }

    @Test void warnsAboutFabricPortCollisionsRatherThanPretendTheyCanCoexist() {
        assertThrows(TargetJars.FabricPortCollision.class, () ->
                TargetJars.assertNoFabricPortCollision("alexscaves", id -> "citadel".equals(id)));
        assertThrows(TargetJars.FabricPortCollision.class, () ->
                TargetJars.assertNoFabricPortCollision("alexsmobs", id -> "alexsmobs".equals(id)));
        assertDoesNotThrow(() -> TargetJars.assertNoFabricPortCollision("cataclysm", id -> true));
        assertDoesNotThrow(() -> TargetJars.assertNoFabricPortCollision("alexscaves", id -> false));
    }

    @Test void fakeCitadelRejectedAndVerifiedByModIdVersionAndCoreClass() throws IOException {
        Path missing = fakeJar("citadel-stub.jar", toml("citadel", "2.6.3", "[47,)"), false);
        assertThrows(IOException.class, () -> EmbeddedCitadel.validateArchive(missing));
        Path wrong = temporary.resolve("citadel-wrong.jar");
        try (JarOutputStream zip = new JarOutputStream(Files.newOutputStream(wrong))) {
            write(zip, "META-INF/mods.toml", toml("citadel", "2.5.9", "[47,)"));
            write(zip, "com/github/alexthe666/citadel/Citadel.class", "fixture");
        }
        assertThrows(IOException.class, () -> EmbeddedCitadel.validateArchive(wrong));
        Path valid = temporary.resolve("citadel-metadata.jar");
        try (JarOutputStream zip = new JarOutputStream(Files.newOutputStream(valid))) {
            write(zip, "META-INF/mods.toml", toml("citadel", "2.6.3", "[47,)"));
            write(zip, "com/github/alexthe666/citadel/Citadel.class", "fixture");
        }
        assertDoesNotThrow(() -> EmbeddedCitadel.validateArchive(valid));
    }

    @Test void refusesMultiModForgeJar() throws IOException {
        Path malformed = fakeJar("multi.jar", toml("cataclysm", "3.31", "[47,)") +
                "\n[[mods]]\nmodId=\"evil\"\nversion=\"1\"", false);
        Path selected = TargetJars.permitAndPrepare(malformed);
        try (JarFile zip = new JarFile(selected.toFile())) {
            assertNull(zip.getJarEntry("META-INF/mods.toml"));
        }
    }

    private String toml(String modId, String version, String loaderVersion) {
        return "modLoader=\"javafml\"\nloaderVersion=\"" + loaderVersion + "\"\n" +
                "[[mods]]\nmodId=\"" + modId + "\"\nversion=\"" + version + "\"\n";
    }

    private Path fakeJar(String name, String modsToml, boolean fabricMetadata) throws IOException {
        Path path = temporary.resolve(name);
        Files.createDirectories(path.getParent());
        try (JarOutputStream zip = new JarOutputStream(Files.newOutputStream(path))) {
            write(zip, "META-INF/mods.toml", modsToml);
            if (fabricMetadata) {
                write(zip, "fabric.mod.json", "{\"id\":\"fabric_example\"}");
            }
            write(zip, "assets/fake/mod.png", "fixture");
        }
        return path;
    }

    private String readToml(Path input) throws IOException {
        try (JarFile jar = new JarFile(input.toFile())) {
            return new String(jar.getInputStream(jar.getJarEntry("META-INF/mods.toml"))
                    .readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void write(JarOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new JarEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
