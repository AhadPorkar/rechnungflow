"""Actual process and encrypted snapshot recovery exercises; not a performance benchmark."""
from pathlib import Path
import base64,hashlib,json,os,shutil,subprocess,sys,tempfile,unittest
from support import ROOT,RunningApp,run_suite,PASSWORD
class Operations(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.app=RunningApp();cls.app.login('admin');_,listing,_=cls.app.request('GET','/api/invoices','admin');cls.original_ids=sorted(x['id'] for x in listing['items']);cls.app.stop();cls.root=cls.app.temp
 @classmethod
 def tearDownClass(cls):cls.app.close()
 def env(self):
  e=os.environ.copy()
  for k in list(e):
   if k.startswith('RF_'):e.pop(k)
  return e
 def cli(self,*args,env=None):return subprocess.run(['java','-cp',str(ROOT/'build/rechnungflow-0.1.0.jar'),'io.ahad.rechnungflow.server.Maintenance',*map(str,args)],capture_output=True,text=True,timeout=15,env=env or self.env(),cwd=ROOT)
 def copy(self,label):
  p=self.root/label
  if p.exists():shutil.rmtree(p)
  shutil.copytree(self.app.data,p);return p
 def test_verify_all_originals_and_audit(self):
  r=self.cli('verify',self.app.data);self.assertEqual(0,r.returncode,r.stderr);d=json.loads(r.stdout.strip().splitlines()[-1]);self.assertTrue(d['verified']);self.assertEqual(8,d['invoices'])
 def test_snapshot_excludes_master_key(self):
  target=self.root/'snapshot';r=self.cli('snapshot',self.app.data,target);self.assertEqual(0,r.returncode,r.stderr);self.assertFalse((target/'master.key').exists());m=json.loads((target/'snapshot-manifest.json').read_text());self.assertFalse(m['masterKeyIncluded'])
  for name,sha in m['files'].items():self.assertEqual(sha,hashlib.sha256((target/name).read_bytes()).hexdigest())
 def test_snapshot_restores_with_separate_key(self):
  target=self.root/'restore';r=self.cli('snapshot',self.app.data,target);self.assertEqual(0,r.returncode,r.stderr);shutil.copy2(self.app.data/'master.key',target/'master.key');r=self.cli('verify',target);self.assertEqual(0,r.returncode,r.stderr)
 def test_snapshot_refuses_existing_target(self):
  target=self.root/'existing';target.mkdir();r=self.cli('snapshot',self.app.data,target);self.assertNotEqual(0,r.returncode)
 def test_missing_data_not_initialized(self):
  missing=self.root/'missing';r=self.cli('verify',missing);self.assertNotEqual(0,r.returncode);self.assertFalse(missing.exists())
 def test_wrong_external_key_detected(self):
  e=self.env();e['RF_MASTER_KEY']=base64.b64encode(bytes(range(32))).decode();r=self.cli('verify',self.app.data,env=e);self.assertNotEqual(0,r.returncode)
 def test_missing_original_detected(self):
  d=self.copy('missing-original');next((d/'originals').rglob('*.enc')).unlink();r=self.cli('verify',d);self.assertNotEqual(0,r.returncode)
 def test_changed_original_detected(self):
  d=self.copy('changed-original');p=next((d/'originals').rglob('*.enc'));b=bytearray(p.read_bytes());b[-1]^=1;p.write_bytes(b);r=self.cli('verify',d);self.assertNotEqual(0,r.returncode)
 def test_changed_state_detected(self):
  d=self.copy('changed-state');p=d/'state.enc';b=bytearray(p.read_bytes());b[-1]^=1;p.write_bytes(b);r=self.cli('verify',d);self.assertNotEqual(0,r.returncode)
 def test_persisted_bytes_are_not_plaintext(self):
  self.assertNotIn(b'NW-2026-1001',(self.app.data/'state.enc').read_bytes())
  for p in (self.app.data/'originals').rglob('*.enc'):self.assertNotIn(b'<Invoice',p.read_bytes())
 def test_restart_preserves_cases(self):
  self.app.start()
  try:self.app.login('admin');s,l,_=self.app.request('GET','/api/invoices','admin');self.assertEqual(200,s);self.assertEqual(self.original_ids,sorted(x['id'] for x in l['items']))
  finally:self.app.stop()
 def test_live_maintenance_refused(self):
  self.app.start()
  try:r=self.cli('verify',self.app.data);self.assertNotEqual(0,r.returncode)
  finally:self.app.stop()
 def test_jar_help(self):
  r=subprocess.run(['java','-jar',str(ROOT/'build/rechnungflow-0.1.0.jar'),'--help'],capture_output=True,text=True,timeout=10,env=self.env());self.assertEqual(0,r.returncode);self.assertIn('RechnungFlow',r.stdout)
 def test_public_demo_refused(self):
  r=subprocess.run(['java','-jar',str(ROOT/'build/rechnungflow-0.1.0.jar'),'--demo','--host','0.0.0.0','--data',str(self.root/'unsafe-demo')],capture_output=True,text=True,timeout=10,env=self.env());self.assertNotEqual(0,r.returncode);self.assertFalse((self.root/'unsafe-demo').exists())
 def test_public_strict_requires_security_ack(self):
  r=subprocess.run(['java','-jar',str(ROOT/'build/rechnungflow-0.1.0.jar'),'--host','0.0.0.0','--data',str(self.root/'unsafe-strict')],capture_output=True,text=True,timeout=10,env=self.env());self.assertNotEqual(0,r.returncode)
if __name__=='__main__':sys.exit(0 if run_suite(unittest.defaultTestLoader.loadTestsFromTestCase(Operations),ROOT/'build/test-results/operations.xml') else 1)
