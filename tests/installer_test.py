"""Offline installer adversarial cases; no download or official acceptance is simulated."""
from pathlib import Path
import sys,tempfile,zipfile,stat,hashlib,json,unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import setup_kosit as installer
from support import ROOT,run_suite
class Installer(unittest.TestCase):
 def setUp(self):self.temp=tempfile.TemporaryDirectory(prefix='rf-installer-test-');self.root=Path(self.temp.name)
 def tearDown(self):self.temp.cleanup()
 def archive(self,name,body='synthetic'):
  p=self.root/'input.zip'
  with zipfile.ZipFile(p,'w') as z:z.writestr(name,body)
  return p
 def bad(self,name):
  with self.assertRaises((ValueError,OSError)):installer.safe_extract(self.archive(name),self.root/'output')
 def test_normal_nested_file(self):
  installer.safe_extract(self.archive('config/scenarios.xml'),self.root/'out');self.assertEqual('synthetic',(self.root/'out/config/scenarios.xml').read_text())
 def test_duplicate_casefold_paths(self):
  p=self.root/'input.zip'
  with zipfile.ZipFile(p,'w') as z:z.writestr('Schema.xml','one');z.writestr('schema.xml','two')
  with self.assertRaises(ValueError):installer.safe_extract(p,self.root/'out')
 def test_symlink_rejected(self):
  p=self.root/'input.zip';info=zipfile.ZipInfo('link');info.create_system=3;info.external_attr=(stat.S_IFLNK|0o777)<<16
  with zipfile.ZipFile(p,'w') as z:z.writestr(info,'../../outside')
  with self.assertRaises(ValueError):installer.safe_extract(p,self.root/'out')
 def test_existing_file_not_overwritten(self):
  target=self.root/'out';target.mkdir();(target/'x.xml').write_text('protected')
  with self.assertRaises(FileExistsError):installer.safe_extract(self.archive('x.xml'),target)
  self.assertEqual('protected',(target/'x.xml').read_text())
 def test_explicit_direct_standalone_selected(self):
  a={'name':'validator-1.6.3-standalone.jar'};self.assertEqual(a,installer.select([a,{'name':'sources.zip'}],'validator'))
 def test_config_release_selected(self):
  a={'name':'validator-configuration-xrechnung_3.0.2_2026-08-31.zip'};self.assertEqual(a,installer.select([a,{'name':'notes.txt'}],'configuration'))
 def test_ambiguous_assets_fail_closed(self):
  with self.assertRaises(ValueError):installer.select([{'name':'one.zip'},{'name':'two.zip'}],'validator')
 def test_no_config_assets_fail_closed(self):
  with self.assertRaises(ValueError):installer.select([{'name':'readme.txt'}],'configuration')
 def test_source_archive_not_validator(self):
  with self.assertRaises(ValueError):installer.select([{'name':'validator-source.zip'}],'validator')
 def test_unknown_repository_download_rejected_before_network(self):
  with self.assertRaises(ValueError):installer.download({'browser_download_url':'https://evil.example/validator.jar'},'validator',self.root/'x',False)
 def test_digest_absent_fails_before_network(self):
  with self.assertRaises(ValueError):installer.download({'browser_download_url':'https://github.com/itplr-kosit/validator/releases/download/v1.6.3/file.jar'},'validator',self.root/'x',False)
 def test_sha_is_exact_bytes(self):
  p=self.root/'bytes';p.write_bytes(b'\x00\xffexample');self.assertEqual(hashlib.sha256(p.read_bytes()).hexdigest(),installer.sha(p))
for name,value in {'parent':'../escape','nested_parent':'a/../../escape','absolute':'/tmp/escape','backslash':'a\\escape','drive':'C:/escape','reserved_con':'CON.txt','reserved_nul':'NUL','reserved_com':'sub/COM1.xml','trailing_space':'file.xml ','trailing_dot':'name.'}.items():
 def make(v):
  def test(self):self.bad(v)
  return test
 setattr(Installer,'test_reject_'+name,make(value))
if __name__=='__main__':sys.exit(0 if run_suite(unittest.defaultTestLoader.loadTestsFromTestCase(Installer),ROOT/'build/test-results/installer.xml') else 1)
