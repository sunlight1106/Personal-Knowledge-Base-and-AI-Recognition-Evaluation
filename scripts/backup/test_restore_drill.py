import json
from pathlib import Path
import re
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch
import restore_drill


class RestoreDrillSafetyTest(unittest.TestCase):
    def test_checks_stay_inside_the_container_and_credentials_are_not_arguments(self):
        with patch.object(restore_drill.backup,'run',return_value=SimpleNamespace(stdout=b'{}')) as run:
            restore_drill.container_request(['docker','compose','-p','fixture'],'http://127.0.0.1:8080/api/v1/auth/login','POST',{'Authorization':'Bearer synthetic-token'},{'password':'synthetic-secret'})
            args,kwargs=run.call_args
            self.assertIn('backend',args[0]);self.assertNotIn('synthetic-secret',' '.join(args[0]));self.assertNotIn('synthetic-token',' '.join(args[0]))
            self.assertIn(b'synthetic-secret',kwargs['input'])
            with self.assertRaises(ValueError):restore_drill.container_request([], 'https://outside.invalid/')

    def test_partial_restore_cleans_only_its_labeled_volumes(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'.runtime').mkdir();calls=[];name=None
            def restore(archive,secret,project,restore_name):
                nonlocal name
                name=restore_name
                self.assertRegex(name,r'^pkb-restore-[a-z0-9-]{6,40}$')
                stage=root/'.runtime'/name;stage.mkdir();(stage/'.env').write_text('synthetic')
                raise RuntimeError('Partial restore')
            def run(args,**kwargs):
                calls.append(args)
                if args[:3]==['docker','volume','ls']:
                    self.assertIn('label=pkb.restore='+name,args)
                    return SimpleNamespace(stdout=(name+'_mysql_data\n').encode())
                return SimpleNamespace(stdout=b'')
            with patch.object(restore_drill.backup,'inspect_archive',side_effect=restore),patch.object(restore_drill.backup,'run',side_effect=run):
                with self.assertRaisesRegex(RuntimeError,'Partial restore'):restore_drill.drill(root,root/'fixture.pkb',b'synthetic')
            self.assertIn(['docker','volume','rm',name+'_mysql_data'],calls)
            self.assertFalse((root/'.runtime'/name).exists())
            self.assertFalse(json.loads((root/'.runtime/backups/drill.json').read_text())['success'])

    def test_unexpected_volume_name_is_never_removed(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'.runtime').mkdir();calls=[]
            def run(args,**kwargs):
                calls.append(args)
                return SimpleNamespace(stdout=b'production_mysql_data\n')
            with patch.object(restore_drill.backup,'inspect_archive',side_effect=RuntimeError('Interrupted')),patch.object(restore_drill.backup,'run',side_effect=run):
                with self.assertRaisesRegex(ValueError,'Unexpected drill volume'):restore_drill.drill(root,root/'fixture.pkb',b'synthetic')
            self.assertFalse(any(call[:3]==['docker','volume','rm'] for call in calls))


if __name__=='__main__':unittest.main()
