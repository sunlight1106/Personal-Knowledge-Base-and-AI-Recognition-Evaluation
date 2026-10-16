import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import zipfile
import online_snapshot as online
import binlog_archive as logs

class OnlineSnapshotTest(unittest.TestCase):
    def test_coordinate_is_read_from_dump_header(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'dump.sql'
            path.write_text("-- CHANGE REPLICATION SOURCE TO SOURCE_LOG_FILE='binlog.000023', SOURCE_LOG_POS=453;\nCREATE TABLE x (id INT);",encoding='utf-8')
            self.assertEqual(online.dump_coordinate(path),{'file':'binlog.000023','position':453})

    def test_restore_waits_for_authenticated_initialization(self):
        with patch.object(online,'sql',side_effect=[RuntimeError('initializing'),b'1\n']) as query,patch.object(online.time,'sleep'):
            online.wait_for_database(['docker','compose'])
            self.assertEqual(query.call_count,2)

    def test_safe_object_keys_and_log_names(self):
        self.assertEqual(online.key_path('uploads/my file.png'),'uploads/my%20file.png')
        for value in ('../secret','/secret','a/../b','a\\b','\x00'):
            with self.assertRaises(ValueError):online.key_path(value)
        for value in ('../binlog.000001','binlog','binlog.000001; rm','/binlog.000001'):
            with self.assertRaises(ValueError):logs.valid_log(value)

    def test_encrypted_online_snapshot_requires_exact_members_and_hashes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);(root/'.runtime').mkdir()
            payload=root/'data.zip';archive=root/'data.pkb';secret=b'fixture-only-backup-password'
            values={'database.sql':b'CREATE TABLE sample(id INT);','installation.env':b'PRIVATE=fixture'}
            manifest={'format':'pkb-online-1','files':{k:{'bytes':len(v),'sha256':online.hashlib.sha256(v).hexdigest()} for k,v in values.items()},'objects':{}}
            with zipfile.ZipFile(payload,'w') as z:
                z.writestr('manifest.json',json.dumps(manifest))
                for k,v in values.items():z.writestr(k,v)
            online.crypt(payload,archive,secret)
            with patch.object(online,'ROOT',root),patch.object(online,'run',side_effect=AssertionError('Verification must not call Docker')):
                online.restore(archive,secret,None,0,verify=True)
                with self.assertRaises(Exception):online.restore(archive,b'wrong-password',None,0,verify=True)
            with zipfile.ZipFile(payload,'a') as z:z.writestr('../unsafe',b'unsafe')
            archive.unlink()
            online.crypt(payload,archive,secret)
            with patch.object(online,'ROOT',root),self.assertRaises(ValueError):online.restore(archive,secret,None,0,verify=True)

    def test_restore_and_replay_reject_live_project_before_docker(self):
        with patch.object(online,'run',side_effect=AssertionError('No Docker allowed')):
            with self.assertRaises(ValueError):online.restore(Path('unused'),b'fixture','robust-vision',4273)
        with patch.object(logs,'run',side_effect=AssertionError('No Docker allowed')):
            with self.assertRaises(ValueError):logs.replay(Path('unused'),b'fixture','robust-vision','binlog.000001',100,True)
            with self.assertRaises(ValueError):logs.replay(Path('unused'),b'fixture','pkb-online-restore-fixture01','binlog.000001',100,False)

if __name__=='__main__':unittest.main()
