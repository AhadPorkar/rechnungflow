"""Isolated real Java server fixture. All passwords and data are synthetic test values."""
from pathlib import Path
import http.client,json,os,re,shutil,subprocess,tempfile,time,urllib.parse,xml.etree.ElementTree as ET,unittest
ROOT=Path(__file__).resolve().parents[1]
PASSWORD='Synthetic-test-passphrase-2026!'
class RunningApp:
    def __init__(self,demo=True,worker=False):
        self.temp=Path(tempfile.mkdtemp(prefix='rf-http-'));self.data=self.temp/'data';self.log_path=self.temp/'server.log';self.log=None;self.process=None;self.demo=demo;self.worker=worker;self.sessions={};self.start()
    def start(self):
        self.log=self.log_path.open('w',encoding='utf-8')
        env=os.environ.copy()
        for k in list(env):
            if k.startswith('RF_'):env.pop(k)
        env.update(RF_BOOTSTRAP_PASSWORD=PASSWORD,RF_DISABLE_WORKER='false' if self.worker else 'true')
        command=['java','-jar',str(ROOT/'build/rechnungflow-0.1.0.jar'),'--data',str(self.data),'--port','0']+(['--demo'] if self.demo else [])
        self.process=subprocess.Popen(command,stdout=self.log,stderr=subprocess.STDOUT,env=env,cwd=ROOT)
        for _ in range(400):
            text=self.log_path.read_text(encoding='utf-8',errors='replace');match=re.search(r'Open: http://127\.0\.0\.1:(\d+)',text)
            if match:
                self.port=int(match.group(1));self.base=f'http://127.0.0.1:{self.port}';return
            if self.process.poll() is not None:raise RuntimeError('Java server exited:\n'+text)
            time.sleep(.05)
        raise RuntimeError('Server startup timeout')
    def stop(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:self.process.wait(timeout=15)
            except subprocess.TimeoutExpired:self.process.kill();self.process.wait(timeout=5)
        if self.log:self.log.close()
    def close(self):
        self.stop()
        if os.environ.get('RF_KEEP_TEST_DATA')!='true':shutil.rmtree(self.temp,ignore_errors=True)
    def request(self,method,path,role=None,data=None,raw=None,headers=None,csrf=True):
        h=dict(headers or {});body=raw
        if data is not None:body=json.dumps(data,ensure_ascii=False).encode();h.setdefault('Content-Type','application/json')
        if role:
            session=self.sessions[role];h.setdefault('Cookie',session['cookie'])
            if csrf and method not in ('GET','HEAD'):h.setdefault('X-CSRF-Token',session['csrf'])
        conn=http.client.HTTPConnection('127.0.0.1',self.port,timeout=25)
        try:
            conn.request(method,path,body=body,headers=h);res=conn.getresponse();b=res.read();rh={k.lower():v for k,v in res.getheaders()}
            try:result=json.loads(b)
            except (ValueError,UnicodeDecodeError):result=b
            return res.status,result,rh
        finally:conn.close()
    def login(self,role,password=PASSWORD):
        status,body,h=self.request('POST','/api/auth/login',data={'tenant':'demo' if self.demo else 'workspace','login':role,'password':password})
        if status!=200:raise AssertionError((status,body))
        self.sessions[role]={'cookie':h['set-cookie'].split(';')[0],'csrf':body['csrf']};return body
    def upload(self,name,role='intake',file='01-ubl-office.xml',key=None,extra_headers=None):
        import uuid
        b=(ROOT/'examples/invoices'/file).read_bytes().replace(b'NW-2026-1001',name.encode())
        h={'Content-Type':'application/xml','Idempotency-Key':key or str(uuid.uuid4()),'X-Filename':'invoice.xml'};h.update(extra_headers or {})
        return self.request('POST','/api/invoices',role,raw=b,headers=h)

def run_suite(suite,path:Path):
    class XMLResult(unittest.TextTestResult):
        def startTest(self,test):super().startTest(test);self.started=time.monotonic()
        def stopTest(self,test):
            failures=[err for case,err in self.failures+self.errors if case is test]
            node=ET.SubElement(root,'testcase',{'classname':test.__class__.__name__,'name':test._testMethodName,'time':str(time.monotonic()-self.started)})
            if failures:ET.SubElement(node,'failure').text='\n'.join(failures)
            skipped=[reason for case,reason in self.skipped if case is test]
            if skipped:ET.SubElement(node,'skipped').text=skipped[0]
            super().stopTest(test)
    root=ET.Element('testsuite',{'name':path.stem});result=unittest.TextTestRunner(verbosity=2,resultclass=XMLResult).run(suite)
    root.set('tests',str(result.testsRun));root.set('failures',str(len(result.failures)));root.set('errors',str(len(result.errors)));root.set('skipped',str(len(result.skipped)))
    path.parent.mkdir(parents=True,exist_ok=True);ET.ElementTree(root).write(path,encoding='utf-8',xml_declaration=True)
    return result.wasSuccessful()
