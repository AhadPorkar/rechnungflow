#!/usr/bin/env python3
from pathlib import Path
import sys,json,uuid,unittest,urllib.parse,zipfile,io,time
sys.path.insert(0,str(Path(__file__).parent))
from support import RunningApp,PASSWORD,ROOT,run_suite
class HTTP(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app=RunningApp();cls.addClassCleanup(cls.app.close)
        for role in ('admin','intake','reviewer','approver','approver2','finance','auditor'):cls.app.login(role)
        cls.seed=cls.app.request('GET','/api/invoices','admin')[1]['items'][0]['id']
    def api(self,method,path,role='admin',**kwargs):return self.app.request(method,path,role,**kwargs)
    def assertCode(self,res,status,code):self.assertEqual(status,res[0],res[1]);self.assertEqual(code,res[1]['error']['code'])
    def new(self,file='01-ubl-office.xml'):
        name='HTTP-'+uuid.uuid4().hex[:12];res=self.app.upload(name,file=file);self.assertEqual(201,res[0],res[1]);return res[1]['id']
    def invoice(self,id):res=self.api('GET','/api/invoices/'+id);self.assertEqual(200,res[0]);return res[1]
    def action(self,id,action,role,**fields):return self.api('POST',f'/api/invoices/{id}/{action}',role,data={'version':self.invoice(id)['version'],**fields})
    def test_health_public(self):self.assertEqual('UP',self.api('GET','/health/live',None)[1]['status'])
    def test_language_config_public(self):self.assertEqual(['en','de','fa'],self.api('GET','/api/public/config',None)[1]['languages'])
    def test_cookie_attributes(self):
        # Existing session's server token is inspected through a new low-cost me request; initial cookie policy is also asserted by UI/HTTP login test.
        res=self.api('GET','/api/auth/me');self.assertEqual(200,res[0]);self.assertEqual(self.app.sessions['admin']['csrf'],res[1]['csrf']);self.assertNotIn('passwordHash',res[1]['user'])
    def test_unauthenticated_dashboard(self):self.assertCode(self.api('GET','/api/dashboard',None),401,'AUTHENTICATION_REQUIRED')
    def test_random_session_denied(self):self.assertCode(self.api('GET','/api/dashboard',None,headers={'Cookie':'rf_session='+'a'*43}),401,'SESSION_EXPIRED')
    def test_duplicate_cookies_denied(self):self.assertCode(self.api('GET','/api/dashboard',None,headers={'Cookie':self.app.sessions['admin']['cookie']+'; '+self.app.sessions['admin']['cookie']}),401,'AUTHENTICATION_REQUIRED')
    def test_csrf_missing(self):self.assertCode(self.api('POST','/api/invoices/'+self.seed+'/comment',data={'version':1,'note':'test'},csrf=False),403,'CSRF_INVALID')
    def test_csrf_wrong(self):self.assertCode(self.api('POST','/api/invoices/'+self.seed+'/comment',data={},headers={'X-CSRF-Token':'wrong'}),403,'CSRF_INVALID')
    def test_cross_origin_rejected(self):self.assertCode(self.api('POST','/api/auth/login',None,data={},headers={'Origin':'https://attacker.invalid'}),403,'ORIGIN_REJECTED')
    def test_cross_site_fetch_rejected(self):self.assertCode(self.api('POST','/api/auth/login',None,data={},headers={'Sec-Fetch-Site':'cross-site'}),403,'ORIGIN_REJECTED')
    def test_json_content_type_required(self):self.assertCode(self.api('POST','/api/keys',raw=b'{}',headers={'Content-Type':'text/plain'}),415,'JSON_REQUIRED')
    def test_invalid_json_rejected(self):self.assertEqual(400,self.api('POST','/api/keys',raw=b'{invalid',headers={'Content-Type':'application/json'})[0])
    def test_duplicate_json_key_rejected(self):self.assertEqual(400,self.api('POST','/api/keys',raw=b'{"name":"a","name":"b"}',headers={'Content-Type':'application/json'})[0])
    def test_invalid_utf8_rejected(self):self.assertEqual(400,self.api('POST','/api/keys',raw=b'{"name":"\xff"}',headers={'Content-Type':'application/json'})[0])
    def test_gzip_request_rejected(self):self.assertCode(self.api('POST','/api/keys',raw=b'{}',headers={'Content-Type':'application/json','Content-Encoding':'gzip'}),415,'CONTENT_ENCODING_UNSUPPORTED')
    def test_json_body_size_limit(self):self.assertCode(self.api('POST','/api/keys',raw=b'{}',headers={'Content-Type':'application/json','Content-Length':'70000'}),413,'FILE_TOO_LARGE')
    def test_upload_body_size_limit(self):self.assertCode(self.api('POST','/api/invoices',raw=b'',headers={'Content-Type':'application/xml','Content-Length':'2097153','Idempotency-Key':str(uuid.uuid4())}),413,'FILE_TOO_LARGE')
    def test_upload_content_type(self):self.assertCode(self.api('POST','/api/invoices',raw=b'x',headers={'Content-Type':'application/pdf'}),415,'XML_UPLOAD_REQUIRED')
    def test_upload_requires_idempotency(self):self.assertCode(self.api('POST','/api/invoices',raw=b'<x/>',headers={'Content-Type':'application/xml'}),400,'IDEMPOTENCY_KEY_REQUIRED')
    def test_invalid_document_is_exception(self):
        id=self.new('11-xxe.xml');i=self.invoice(id);self.assertEqual('EXCEPTION',i['stage']);self.assertFalse(i['data']);self.assertNotIn('root:',json.dumps(i))
    def test_replay_same_upload(self):
        key=str(uuid.uuid4());name='REPLAY-'+uuid.uuid4().hex[:8];a=self.app.upload(name,key=key);b=self.app.upload(name,key=key);self.assertEqual(a[1]['id'],b[1]['id']);self.assertTrue(b[1]['replayed'])
    def test_replay_changed_upload_conflict(self):
        key=str(uuid.uuid4());self.app.upload('REPLAY-A-'+key,key=key);self.assertCode(self.app.upload('REPLAY-B-'+key,key=key),409,'IDEMPOTENCY_CONFLICT')
    def test_original_attachment_exact(self):
        id=self.new();i=self.invoice(id);res=self.api('GET',f'/api/invoices/{id}/original');self.assertEqual(200,res[0]);self.assertIn('attachment',res[2]['content-disposition']);import hashlib;self.assertEqual(i['sha256'],hashlib.sha256(res[1]).hexdigest());self.assertEqual('application/octet-stream',res[2]['content-type'])
    def test_evidence_zip(self):
        id=self.new();res=self.api('GET',f'/api/invoices/{id}/evidence','auditor');self.assertEqual(200,res[0]);z=zipfile.ZipFile(io.BytesIO(res[1]));self.assertIn('original.xml',z.namelist());self.assertTrue(json.loads(z.read('manifest.json'))['notAWormArchive'])
    def test_official_report_unavailable_is_not_fabricated(self):id=self.new();self.assertCode(self.api('GET',f'/api/invoices/{id}/report'),404,'REPORT_UNAVAILABLE')
    def test_full_four_eyes_flow_to_export_queue(self):
        id=self.new();self.assertEqual(200,self.action(id,'review','reviewer',costCenter='OPS-1',glAccount='4930',note='Service received')[0]);self.assertEqual(200,self.action(id,'approve','approver',note='Approved')[0]);i=self.invoice(id);self.assertEqual('APPROVED',i['stage'])
        res=self.api('POST',f'/api/invoices/{id}/export','finance',data={'version':i['version']},headers={'Idempotency-Key':str(uuid.uuid4())});self.assertEqual(200,res[0],res[1]);self.assertEqual('PENDING',res[1]['status']);self.assertTrue(res[1]['sandbox']);self.assertEqual('EXPORT_PENDING',self.invoice(id)['stage'])
    def test_stale_comment_version(self):
        id=self.new();v=self.invoice(id)['version'];self.assertEqual(200,self.action(id,'comment','intake',note='First')[0]);self.assertCode(self.api('POST',f'/api/invoices/{id}/comment','intake',data={'version':v,'note':'Stale'}),409,'VERSION_CONFLICT')
    def test_self_review_denied_on_server(self):
        res=self.app.upload('ADMIN-'+uuid.uuid4().hex[:8],role='admin');self.assertCode(self.action(res[1]['id'],'review','admin',costCenter='1',glAccount='4930',note='check'),403,'SEPARATION_OF_DUTIES')
    def test_approval_before_review_denied(self):id=self.new();self.assertCode(self.action(id,'approve','approver',note='check'),409,'INVALID_TRANSITION')
    def test_rejection_preserves_reason(self):id=self.new();self.assertEqual(200,self.action(id,'reject','reviewer',reason='Wrong supplier invoice')[0]);self.assertEqual('Wrong supplier invoice',self.invoice(id)['rejectionReason'])
    def test_retention_hold_recorded(self):id=self.new();self.assertEqual(200,self.action(id,'hold','finance',legalHold=True,reason='Investigation')[0]);self.assertTrue(self.invoice(id)['legalHold'])
    def test_revalidation_does_not_change_hash(self):id=self.new();before=self.invoice(id)['sha256'];self.assertEqual(200,self.action(id,'revalidate','reviewer')[0]);self.assertEqual(before,self.invoice(id)['sha256'])
    def test_comment_xss_is_data(self):id=self.new();self.assertEqual(200,self.action(id,'comment','intake',note='<img src=x onerror=alert(1)>')[0]);self.assertEqual('<img src=x onerror=alert(1)>',self.invoice(id)['comments'][0]['note'])
    def test_supplier_create_verify_and_edit(self):
        name='Supplier '+uuid.uuid4().hex[:8];res=self.api('POST','/api/suppliers','reviewer',data={'name':name,'identity':'taxId:'+uuid.uuid4().hex,'iban':'DE79000000001234567890'});self.assertEqual(201,res[0]);s=res[1]
        res=self.api('POST',f'/api/suppliers/{s["id"]}/verify','approver',data={'version':s['version'],'reason':'Called independent listed contact','confirmedOutOfBand':True});self.assertEqual(200,res[0]);self.assertEqual('VERIFIED',res[1]['status'])
        res=self.api('PUT',f'/api/suppliers/{s["id"]}','reviewer',data={'version':res[1]['version'],'name':name+' changed','iban':'DE79000000001234567890'});self.assertEqual(200,res[0]);self.assertEqual('PENDING',res[1]['status'])
    def test_supplier_verification_confirmation_required(self):self.assertCode(self.api('POST','/api/suppliers/'+str(uuid.uuid4())+'/verify','approver',data={'version':1,'reason':'test'}),400,'VERIFICATION_CONFIRMATION_REQUIRED')
    def test_policy_version_conflict(self):p=self.api('GET','/api/policy')[1];p['policyVersion']=999;self.assertCode(self.api('PUT','/api/policy',data=p),409,'VERSION_CONFLICT')
    def test_policy_mode_is_not_mutable(self):p=self.api('GET','/api/policy')[1];p['mode']='STRICT';res=self.api('PUT','/api/policy',data=p);self.assertEqual(200,res[0]);self.assertEqual('DEMO',res[1]['mode'])
    def test_api_key_create_use_and_revoke(self):
        res=self.api('POST','/api/keys',data={'name':'Automated intake','days':1});self.assertEqual(201,res[0]);key=res[1];h={'Authorization':'Bearer '+key['token'],'Content-Type':'application/xml','Idempotency-Key':str(uuid.uuid4())}
        raw=(ROOT/'examples/invoices/01-ubl-office.xml').read_bytes().replace(b'NW-2026-1001',('KEY-'+uuid.uuid4().hex[:12]).encode());u=self.api('POST','/api/invoices',None,raw=raw,headers=h);self.assertEqual(201,u[0]);self.assertEqual(200,self.api('GET','/api/invoices/'+u[1]['id'],None,headers=h)[0])
        self.assertCode(self.api('GET','/api/invoices/'+self.seed,None,headers=h),404,'NOT_FOUND');self.assertCode(self.api('GET','/api/dashboard',None,headers=h),403,'API_KEY_SCOPE');self.assertEqual(200,self.api('POST','/api/keys/'+key['id']+'/revoke',data={})[0]);self.assertCode(self.api('GET','/api/invoices/'+u[1]['id'],None,headers=h),401,'INVALID_API_KEY')
    def test_api_key_list_hides_secrets(self):res=self.api('GET','/api/keys');self.assertEqual(200,res[0]);self.assertTrue(all('token' not in x and 'hash' not in x for x in res[1]['items']))
    def test_user_create_change_password_logout(self):
        name='user-'+uuid.uuid4().hex[:8];res=self.api('POST','/api/users',data={'login':name,'name':'Test user','password':PASSWORD,'roles':['INTAKE']});self.assertEqual(201,res[0]);self.assertTrue(res[1]['mustChangePassword']);self.app.login(name)
        self.assertCode(self.api('GET','/api/dashboard',name),403,'PASSWORD_CHANGE_REQUIRED');newpass='Changed-Synthetic-Test-Password!';res=self.api('POST','/api/auth/password',name,data={'currentPassword':PASSWORD,'newPassword':newpass});self.assertEqual(200,res[0]);self.assertIn('Max-Age=0',res[2]['set-cookie']);self.assertCode(self.api('GET','/api/dashboard',name),401,'SESSION_EXPIRED');self.app.login(name,newpass);self.assertEqual(200,self.api('GET','/api/dashboard',name)[0]);self.assertEqual(200,self.api('POST','/api/auth/logout',name,data={})[0]);self.assertCode(self.api('GET','/api/dashboard',name),401,'SESSION_EXPIRED')
    def test_last_admin_protected(self):id=self.api('GET','/api/auth/me')[1]['user']['id'];self.assertCode(self.api('PUT','/api/users/'+id,data={'name':'Admin','active':False,'roles':['ADMIN']}),409,'LAST_ADMIN')
    def test_unknown_role_rejected(self):self.assertCode(self.api('POST','/api/users',data={'login':'bad-role','name':'Bad','password':PASSWORD,'roles':['ROOT']}),400,'INVALID_ROLE')
    def test_unknown_invoice_hidden(self):self.assertCode(self.api('GET','/api/invoices/'+str(uuid.uuid4())),404,'NOT_FOUND')
    def test_invalid_invoice_identifier(self):self.assertCode(self.api('GET','/api/invoices/not-uuid'),404,'NOT_FOUND')
    def test_negative_page_rejected(self):self.assertCode(self.api('GET','/api/invoices?page=-1'),400,'INVALID_PAGINATION')
    def test_oversized_page_rejected(self):self.assertCode(self.api('GET','/api/invoices?pageSize=101'),400,'INVALID_PAGINATION')
    def test_duplicate_query_parameter_rejected(self):self.assertCode(self.api('GET','/api/invoices?page=0&page=1'),400,'INVALID_INPUT')
    def test_unknown_method_rejected(self):self.assertCode(self.api('DELETE','/api/invoices/'+self.seed,data={}),405,'METHOD_NOT_ALLOWED')
    def test_original_not_deletable(self):self.assertEqual(405,self.api('DELETE','/api/invoices/'+self.seed+'/original',data={})[0])
    def test_hidden_env_unavailable(self):self.assertEqual(404,self.api('GET','/.env',None)[0])
    def test_git_path_unavailable(self):self.assertEqual(404,self.api('GET','/.git/config',None)[0])
    def test_path_traversal_rejected(self):self.assertEqual(400,self.api('GET','/%2e%2e/master.key',None)[0])
    def test_audit_integrity_real_changes(self):res=self.api('GET','/api/audit','auditor');self.assertTrue(res[1]['integrityValid']);self.assertGreater(res[1]['total'],20)
    def test_audit_pagination(self):res=self.api('GET','/api/audit?page=1&pageSize=3','auditor');self.assertEqual(3,len(res[1]['items']))
    def test_csv_download(self):res=self.api('GET','/api/invoices/export.csv','finance');self.assertEqual(200,res[0]);self.assertTrue(res[1].startswith(b'\xef\xbb\xbf'));self.assertIn('attachment',res[2]['content-disposition'])
    def test_error_envelope_no_stack(self):res=self.api('GET','/api/nonexistent');self.assertEqual(404,res[0]);self.assertIn('requestId',res[1]['error']);self.assertNotIn('java.',json.dumps(res[1]))
    def test_system_capabilities_honest(self):r=self.api('GET','/api/system')[1];self.assertFalse(r['validator']['configured']);self.assertFalse(r['accountingConfigured']);self.assertEqual('encrypted-single-node',r['storage'])
    def test_openapi_served(self):r=self.api('GET','/api/openapi.json');self.assertEqual(200,r[0]);self.assertEqual('3.1.1',r[1]['openapi']);self.assertIn('/api/invoices',r[1]['paths'])
    def test_cookie_policy_on_login(self):
        res=self.api('POST','/api/auth/login',None,data={'tenant':'demo','login':'auditor','password':PASSWORD});self.assertEqual(200,res[0]);h=res[2]['set-cookie'];self.assertIn('HttpOnly',h);self.assertIn('SameSite=Strict',h);self.assertNotIn(res[2]['set-cookie'].split('=')[1].split(';')[0],json.dumps(res[1]))
    def test_invalid_login_generic(self):self.assertCode(self.api('POST','/api/auth/login',None,data={'tenant':'demo','login':'missing','password':'incorrect-password'}),401,'INVALID_CREDENTIALS')

# Each generated case exercises a distinct endpoint/role boundary against the real Java server.
READ_ENDPOINTS={'users':('/api/users',{'admin'}),'keys':('/api/keys',{'admin'}),'system':('/api/system',{'admin'}),'audit':('/api/audit',{'admin','auditor','finance'}),'jobs':('/api/jobs',{'admin','auditor','finance'}),'csv':('/api/invoices/export.csv',{'admin','auditor','finance'})}
for name,(path,allowed) in READ_ENDPOINTS.items():
    for role in ('intake','reviewer','approver','finance','auditor'):
        def check(self,p=path,r=role,ok=role in allowed):
            res=self.api('GET',p,r);self.assertEqual(200 if ok else 403,res[0],res[1])
        setattr(HTTP,f'test_role_{role}_{name}',check)
for lang in ('en','de','fa'):
    def locale(self,l=lang):res=self.api('GET',f'/locales/{l}.json',None);self.assertEqual(200,res[0]);self.assertGreater(len(res[1]),350)
    setattr(HTTP,f'test_locale_{lang}',locale)
for name in ('content-security-policy','x-content-type-options','x-frame-options','referrer-policy','permissions-policy','cache-control','x-request-id'):
    def header(self,n=name):res=self.api('GET','/');self.assertIn(n,res[2]);self.assertTrue(res[2][n])
    setattr(HTTP,'test_security_header_'+name.replace('-','_'),header)
if __name__=='__main__':raise SystemExit(0 if run_suite(unittest.defaultTestLoader.loadTestsFromTestCase(HTTP),ROOT/'build/test-results/http.xml') else 1)
