import importlib.util,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('installer',Path(__file__).with_name('install.py'))
installer=importlib.util.module_from_spec(spec);spec.loader.exec_module(installer)
class InstallTests(unittest.TestCase):
 def test_manifest_requires_exact_four_unique_safe_names(self):
  text='\n'.join('a'*64+'  '+n+'.tar.gz' for n in installer.COMPONENTS)
  self.assertEqual(len(installer.manifest(text)),4)
  for invalid in (text+'\n'+text.splitlines()[0],text.replace('backend.tar.gz','../backend.tar.gz'),text.splitlines()[0],'bad'):
   with self.assertRaises(ValueError):installer.manifest(invalid)
 def test_existing_credentials_are_not_overwritten(self):
  with tempfile.TemporaryDirectory() as temp,patch.object(installer,'ROOT',Path(temp)):
   file=Path(temp)/'.env';file.write_text('private-existing')
   installer.configure(4273);self.assertEqual(file.read_text(),'private-existing')
 def test_new_configuration_has_independent_secrets(self):
  with tempfile.TemporaryDirectory() as temp,patch.object(installer,'ROOT',Path(temp)):
   (Path(temp)/'.env.example').write_text('\n'.join(k+'=' for k in installer.SECRET_KEYS)+'\nWEB_PORT=4173\n')
   installer.configure(4273);values=dict(line.split('=',1) for line in (Path(temp)/'.env').read_text().splitlines())
   self.assertEqual(values['WEB_PORT'],'4273');self.assertEqual(len({values[k] for k in installer.SECRET_KEYS}),7)
   self.assertTrue(all(len(values[k])==64 for k in installer.SECRET_KEYS))
 def test_failed_checksum_never_replaces_archive(self):
  import io
  with tempfile.TemporaryDirectory() as temp,patch('urllib.request.urlopen',return_value=io.BytesIO(b'corrupt')):
   target=Path(temp)/'backend.tar.gz'
   with self.assertRaises(ValueError):installer.download('https://example.test/image',target,'a'*64)
   self.assertFalse(target.exists());self.assertFalse(target.with_suffix('.partial').exists())
if __name__=='__main__':unittest.main()
