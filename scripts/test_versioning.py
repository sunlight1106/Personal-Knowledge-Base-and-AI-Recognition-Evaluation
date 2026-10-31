import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import versioning as versions

spec = importlib.util.spec_from_file_location('desktop_build', versions.ROOT/'scripts/desktop/build-release.py')
desktop = importlib.util.module_from_spec(spec)
spec.loader.exec_module(desktop)


class VersioningTest(unittest.TestCase):
    def test_repository_is_consistent(self):
        versions.sync(check=True)

    def test_release_ledger_is_not_git_ignored(self):
        result = subprocess.run(['git', 'check-ignore', '--no-index', '--quiet', str(versions.LEDGER)], cwd=versions.ROOT)
        self.assertEqual(result.returncode, 1, 'Release metadata must be included in fresh source checkouts')

    def test_unique_ordered_versions_and_safe_names(self):
        for bad in ('../0.11', '0.01', '0.11/bad', '0.11\n', 'v0.11', '0.11.0'):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                versions.version_parts(bad)
        self.assertGreater(versions.version_parts('0.20'), versions.version_parts('0.19'))
        data = copy.deepcopy(versions.load())
        data['releases'].append(copy.deepcopy(data['releases'][-1]))
        with self.assertRaises(ValueError):
            versions.validate(data)

    def test_missing_notes_date_translation_are_rejected(self):
        for kind in ('notes', 'date', 'translation'):
            with self.subTest(kind=kind):
                data = copy.deepcopy(versions.load()); current = data['releases'][0]
                if kind == 'notes':
                    for key in ('added', 'fixed', 'improved'): current[key] = []
                elif kind == 'date': current.update(status='ready', date=None)
                else: current['title']['en'] = ''
                with self.assertRaises(ValueError): versions.validate(data)

    def test_sync_updates_all_targets_and_detects_drift(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for path in (versions.LEDGER, Path('cle/package.json'), Path('cle/package-lock.json'), Path('pom.xml')):
                (root/path).parent.mkdir(parents=True, exist_ok=True)
                (root/path).write_bytes((versions.ROOT/path).read_bytes())
            data = json.loads((root/versions.LEDGER).read_text(encoding='utf-8'))
            major, minor = versions.version_parts(data['current'])
            next_version = f'{major}.{minor + 1}'
            data['current'] = data['releases'][0]['version'] = next_version
            (root/versions.LEDGER).write_text(json.dumps(data), encoding='utf-8')
            versions.sync(root)
            self.assertEqual((root/'VERSION').read_text().strip(), next_version)
            self.assertEqual(json.loads((root/'cle/package-lock.json').read_text())['packages']['']['version'], next_version+'.0')
            self.assertIn('<version>'+next_version+'.0</version>', (root/'pom.xml').read_text(encoding='utf-8'))
            self.assertIn('## v'+next_version, (root/'CHANGELOG.md').read_text(encoding='utf-8'))
            versions.sync(root, check=True)
            (root/'VERSION').write_text('0.1')
            with self.assertRaises(ValueError): versions.sync(root, check=True)

    def test_packaging_refuses_draft_published_and_old_revisions(self):
        for status in ('draft', 'published'):
            data = copy.deepcopy(versions.load()); data['releases'][0]['status'] = status
            with patch.object(desktop, 'sync', return_value=data), patch.object(desktop, 'command', side_effect=AssertionError('Must not touch Git or Docker')):
                with self.assertRaises(ValueError): desktop.build()
        with self.assertRaises(ValueError): desktop.build(revision=2)

    def test_launcher_uses_versioned_assets_but_stable_data_root(self):
        source = desktop.launcher_source('0.12', 'a'*64, 'runtime-0.12.zip')
        for expected in ('releases/download/v0.12/', 'runtime-0.12.zip', 'AssemblyFileVersion("0.12.0.1")', '"PKB-AI", "0.1"', 'installed>new System.Version(Version)'):
            self.assertIn(expected, source)
        self.assertNotIn('__VERSION__', source)
        with self.assertRaises(ValueError): desktop.launcher_source('0.12', 'a'*64, '../runtime.zip')

    @unittest.skipUnless(os.name == 'nt', 'Windows launcher compiler')
    def test_generated_launcher_compiles_and_checks_runtime_hash(self):
        compiler = Path(os.environ['WINDIR'])/'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
        with tempfile.TemporaryDirectory(prefix='pkb-version-test-') as directory:
            root = Path(directory); runtime = root/'runtime-0.11.zip'
            runtime.write_bytes(b'isolated launcher hash fixture; not an installation')
            source = root/'Launcher.cs'; exe = root/'0.11.exe'
            mutex_name = 'Local\\PKB-Test-' + root.name
            generated = desktop.launcher_source('0.11', desktop.sha(runtime), runtime.name)
            generated = generated.replace('Local\\\\PKB-AI-Desktop-Launcher', mutex_name.replace('\\', '\\\\'))
            source.write_text(generated, encoding='utf-8-sig')
            subprocess.run([str(compiler), '/nologo', '/target:winexe', '/platform:x64', '/reference:System.Windows.Forms.dll', '/reference:System.Drawing.dll', '/reference:System.IO.Compression.dll', '/reference:System.IO.Compression.FileSystem.dll', '/out:'+str(exe), str(source)], check=True, capture_output=True)
            self.assertEqual(subprocess.run([str(exe), '--self-test', str(runtime)]).returncode, 0)
            runtime.write_bytes(b'tampered fixture')
            self.assertEqual(subprocess.run([str(exe), '--self-test', str(runtime)]).returncode, 3)
            # Holding an existing launcher mutex must fail fast without a hidden
            # dialog. Use an isolated mutex so this test never affects a real app.
            probe = root/'mutex.ps1'
            probe.write_text('''$ErrorActionPreference='Stop'
$created=$false
$guard=[Threading.Mutex]::new($true,$env:PKB_TEST_MUTEX,[ref]$created)
try {
    $child=Start-Process -FilePath $env:PKB_TEST_LAUNCHER -ArgumentList '--smoke-test' -WindowStyle Hidden -PassThru
    if(-not $child.WaitForExit(5000)){$child.Kill();throw 'Blocked smoke test opened a hidden dialog.'}
    if($child.ExitCode -ne 4){throw 'Expected explicit already-running failure.'}
} finally {$guard.ReleaseMutex();$guard.Dispose()}
''', encoding='utf-8-sig')
            subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(probe)],
                           env={**os.environ, 'PKB_TEST_MUTEX': mutex_name, 'PKB_TEST_LAUNCHER': str(exe)},
                           check=True, capture_output=True, timeout=15)


if __name__ == '__main__': unittest.main()
