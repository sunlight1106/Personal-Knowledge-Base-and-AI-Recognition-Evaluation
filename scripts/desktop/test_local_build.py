import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('local_build', Path(__file__).with_name('build-local.py'))
local = importlib.util.module_from_spec(spec)
spec.loader.exec_module(local)


class SourceBoundaryTest(unittest.TestCase):
    def test_includes_untracked_source_but_not_deleted_files(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            (root/'new.py').write_text('new')
            with patch.object(local, 'ROOT', root), patch.object(local.release, 'command', return_value='new.py\0deleted.py\0new.py\0'):
                self.assertEqual(['new.py'], local.source_names())

    def test_private_and_escape_paths_abort_packaging(self):
        for name in ('.env', '.ENV.LOCAL', '.env.local', 'cle/.env.production', '.runtime/key.dpapi', 'output/report.json',
                     'backups/data.pkb', 'secret.pem', 'private.pfx', '../outside.py', 'src/server.log'):
            with self.subTest(name=name), patch.object(local.release, 'command', return_value=name+'\0'):
                with self.assertRaises(ValueError):
                    local.source_names()

    def test_env_example_is_allowed(self):
        with patch.object(local.release, 'command', return_value='.env.example\0'):
            self.assertEqual(['.env.example'], local.source_names())


if __name__ == '__main__':
    unittest.main()
