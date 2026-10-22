"""Dependency-free integration tests using synthetic metadata JARs, never third-party mod code."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAVA_SRC = ROOT / 'manager/src/main/java/org/marj4n/smooth_bridge/manager/BridgeManager.java'
CLI_MAIN = 'org.marj4n.smooth_bridge.manager.BridgeManager'


def forge_jar(path: Path, mod_id: str, payload: bytes = b'original'):
    path.parent.mkdir(parents=True, exist_ok=True)
    toml = (f'modLoader="javafml"\nloaderVersion="[47,)"\n'
            f'[[mods]]\nmodId="{mod_id}"\nversion="1.0"\n'
            f'[[dependencies.{mod_id}]]\nmodId="forge"\nmandatory=true\n')
    with zipfile.ZipFile(path, 'w') as z:
        z.writestr('META-INF/mods.toml', toml)
        z.writestr('assets/data.bin', payload)


def fabric_jar(path: Path, mod_id: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w') as z:
        z.writestr('fabric.mod.json', '{"schemaVersion":1,"id":"' + mod_id + '","version":"1.0"}')


class BridgeManagerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls._build = tempfile.TemporaryDirectory()
        cls.classes = Path(cls._build.name)
        subprocess.run(['javac', '--release', '17', '-d', str(cls.classes), str(JAVA_SRC)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls._build.cleanup()

    def setUp(self):
        self._temp = tempfile.TemporaryDirectory()
        self.game = Path(self._temp.name)
        self.mods = self.game / 'mods'
        self.staging = self.game / 'smoothbridge/staging'
        self.mods.mkdir()
        self.staging.mkdir(parents=True)
        fabric_jar(self.mods / 'Kilt-20.1.21.jar', 'kilt')
        fabric_jar(self.mods / 'fabric-api-0.92.jar', 'fabric-api')
        fabric_jar(self.mods / 'fabric-language-kotlin.jar', 'fabric-language-kotlin')
        fabric_jar(self.mods / 'smooth-bridge.jar', 'smooth_bridge')

    def tearDown(self):
        self._temp.cleanup()

    def run_cmd(self, action='plan', profile='skarrier'):
        return subprocess.run(['java', '-cp', str(self.classes), CLI_MAIN, action,
                               '--game-dir', str(self.game), '--profile', profile],
                              capture_output=True, text=True)

    def stage_skarrier(self):
        forge_jar(self.staging / 'skarrier_mobs-1.1.1(hotfix)-forge-1.20.1.jar', 'skarrier_mobs')
        forge_jar(self.staging / 'geckolib-forge-1.20.1-4.8.2.jar', 'geckolib')

    def stage_azazel(self):
        forge_jar(self.staging / 'cultofazazelforge-1.1.4.12.jar', 'cultofazazel')
        forge_jar(self.staging / 'geckolib-forge-1.20.1-4.8.2.jar', 'geckolib')

    def test_plan_blocks_missing_required_deps_and_never_writes(self):
        forge_jar(self.staging / 'skarrier_mobs-1.1.1(hotfix)-forge-1.20.1.jar', 'skarrier_mobs')
        result = self.run_cmd('apply')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Missing staged Forge JAR for geckolib', result.stdout)
        self.assertFalse((self.game / 'smoothbridge/state/managed.properties').exists())
        self.assertFalse((self.mods / 'skarrier_mobs-1.1.1(hotfix)-forge-1.20.1.jar').exists())

    def test_apply_and_rollback_does_not_touch_fabric_mods(self):
        self.stage_skarrier()
        forge_jar(self.staging / 'random-forge-1.20.1.jar', 'random_mod')
        before = {x.name: x.read_bytes() for x in self.mods.iterdir()}
        res = self.run_cmd('apply')
        self.assertEqual(res.returncode, 0, res.stdout + res.stderr)
        self.assertIn('Activated 2 allowed Forge JAR(s)', res.stdout)
        self.assertFalse((self.mods / 'random-forge-1.20.1.jar').exists())
        self.assertTrue((self.mods / 'skarrier_mobs-1.1.1(hotfix)-forge-1.20.1.jar').is_file())
        self.assertIn('[OK]', self.run_cmd('status').stdout)
        res = self.run_cmd('rollback')
        self.assertEqual(res.returncode, 0, res.stdout + res.stderr)
        self.assertFalse((self.mods / 'skarrier_mobs-1.1.1(hotfix)-forge-1.20.1.jar').exists())
        for name, data in before.items():
            self.assertEqual((self.mods / name).read_bytes(), data)

    def test_switch_profile_and_restore_previous_profile(self):
        self.stage_skarrier()
        self.stage_azazel()
        self.assertEqual(self.run_cmd('apply', 'skarrier').returncode, 0)
        self.assertEqual(self.run_cmd('apply', 'azazel').returncode, 0)
        self.assertFalse((self.mods / 'skarrier_mobs-1.1.1(hotfix)-forge-1.20.1.jar').exists())
        self.assertTrue((self.mods / 'cultofazazelforge-1.1.4.12.jar').exists())
        self.assertEqual(self.run_cmd('rollback').returncode, 0)
        self.assertTrue((self.mods / 'skarrier_mobs-1.1.1(hotfix)-forge-1.20.1.jar').exists())
        self.assertFalse((self.mods / 'cultofazazelforge-1.1.4.12.jar').exists())

    def test_modified_managed_file_is_protected(self):
        self.stage_skarrier()
        self.assertEqual(self.run_cmd('apply').returncode, 0)
        target = self.mods / 'skarrier_mobs-1.1.1(hotfix)-forge-1.20.1.jar'
        with target.open('ab') as f:
            f.write(b'custom changes')
        res = self.run_cmd('rollback')
        self.assertNotEqual(res.returncode, 0)
        self.assertIn('changed outside Bridge', res.stderr)
        self.assertTrue(target.read_bytes().endswith(b'custom changes'))

    def test_unmanaged_forge_mod_blocks_apply(self):
        self.stage_skarrier()
        forge_jar(self.mods / 'unknown_mod.jar', 'unknown_mod')
        res = self.run_cmd('apply')
        self.assertNotEqual(res.returncode, 0)
        self.assertIn('UNMANAGED Forge JAR', res.stdout)
        self.assertTrue((self.mods / 'unknown_mod.jar').exists())

    def test_rejects_fabric_only_and_wrong_version(self):
        fabric_jar(self.staging / 'geckolib-fabric-1.20.1.jar', 'geckolib')
        forge_jar(self.staging / 'skarrier_mobs-1.0.6-forge-1.21.1.jar', 'skarrier_mobs')
        res = self.run_cmd('apply')
        self.assertNotEqual(res.returncode, 0)
        self.assertIn('non-1.20.1', res.stdout)
        self.assertIn('Missing staged Forge JAR for geckolib', res.stdout)

    def test_cataclysm_dependencies_and_all_profile(self):
        self.stage_skarrier()
        self.stage_azazel()
        forge_jar(self.staging / 'L_Enders_Cataclysm-3.31.jar', 'cataclysm')
        forge_jar(self.staging / 'lionfishapi-3.0.jar', 'lionfishapi')
        forge_jar(self.staging / 'curios-forge-5.14.1+1.20.1.jar', 'curios')
        res = self.run_cmd('apply', 'all')
        self.assertEqual(res.returncode, 0, res.stdout + res.stderr)
        self.assertIn('Activated 6 allowed Forge JAR(s)', res.stdout)

    def test_existing_fabric_geckolib_blocks_double_install(self):
        self.stage_skarrier()
        fabric_jar(self.mods / 'geckolib-fabric-1.20.1.jar', 'geckolib')
        res = self.run_cmd('apply')
        self.assertNotEqual(res.returncode, 0)
        self.assertIn('duplicate Forge GeckoLib', res.stdout)

    def test_duplicate_staged_versions_block_apply(self):
        self.stage_skarrier()
        forge_jar(self.staging / 'skarrier_mobs-1.1.0-forge-1.20.1.jar', 'skarrier_mobs')
        res = self.run_cmd('apply')
        self.assertNotEqual(res.returncode, 0)
        self.assertIn('Multiple staged JARs match skarrier', res.stdout)

    def test_no_kilt_refuses_apply(self):
        self.stage_skarrier()
        (self.mods / 'Kilt-20.1.21.jar').unlink()
        res = self.run_cmd('apply')
        self.assertNotEqual(res.returncode, 0)
        self.assertIn('Kilt', res.stdout)


if __name__ == '__main__':
    unittest.main(verbosity=2)
