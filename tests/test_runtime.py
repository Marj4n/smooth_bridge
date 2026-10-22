"""Offline bytecode smoke test with tiny Fabric interface contract stubs.

This does NOT claim Minecraft/Kilt integration testing: real Fabric API and game
JARs cannot be resolved in this offline environment.
"""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MOD_SRC = ROOT / 'src/main/java/org/marj4n/smooth_bridge/fabric/SmoothBridgeMod.java'


class BridgeFabricRuntimeSmoke(unittest.TestCase):
    def test_writes_diagnostic_and_reports_untracked_forge_jar(self):
        with tempfile.TemporaryDirectory() as scratch:
            td = Path(scratch)
            game = td / 'instance'
            (game / 'mods').mkdir(parents=True)
            classes = td / 'classes'
            stubs = td / 'stub'
            a = stubs / 'net/fabricmc/api/ModInitializer.java'
            b = stubs / 'net/fabricmc/loader/api/FabricLoader.java'
            c = stubs / 'test/Main.java'
            for path in (a, b, c):
                path.parent.mkdir(parents=True, exist_ok=True)
            a.write_text('package net.fabricmc.api; public interface ModInitializer { void onInitialize(); }')
            b.write_text('''package net.fabricmc.loader.api;
import java.nio.file.*;
public interface FabricLoader {
  static FabricLoader getInstance() { return new FabricLoader() {
    public Path getGameDir() { return Path.of(System.getProperty("bridge.game.dir")); }
    public boolean isModLoaded(String id) { return id.equals("kilt"); }
  }; }
  Path getGameDir(); boolean isModLoaded(String id);
}''')
            c.write_text('''package test;
public final class Main { public static void main(String[] args) {
  new org.marj4n.smooth_bridge.fabric.SmoothBridgeMod().onInitialize();
} }''')
            forge = game / 'mods' / 'dummy-forge-1.20.1.jar'
            with zipfile.ZipFile(forge, 'w') as z:
                z.writestr('META-INF/mods.toml', '[[mods]]\nmodId="dummy"\n')
            subprocess.run(['javac', '--release', '17', '-d', str(classes), str(a), str(b), str(c), str(MOD_SRC)], check=True)
            result = subprocess.run(['java', '-Dbridge.game.dir=' + str(game), '-cp', str(classes), 'test.Main'],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            report = (game / 'logs' / 'smooth-bridge-runtime.txt').read_text()
            self.assertIn('Kilt listed by Fabric Loader: true', report)
            self.assertIn('UNMANAGED Forge JAR', report)
            self.assertIn('Gameplay verification: NOT PERFORMED', report)


if __name__ == '__main__':
    unittest.main()
