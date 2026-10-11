"""Real UI actions against the real Java server. Native navigation is the default.
Set RF_UI_TRANSPORT=harness explicitly only in environments that block navigation.
The harness does NOT verify native cookie transport, browser navigation, or CSP.
"""
from pathlib import Path
import base64,json,os,sys,time,unittest,uuid
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from support import ROOT,RunningApp,PASSWORD,run_suite
from playwright.sync_api import sync_playwright,expect
TRANSPORT=os.environ.get('RF_UI_TRANSPORT','native')
if TRANSPORT not in ('native','harness'):raise RuntimeError('RF_UI_TRANSPORT must be native or harness')
ASSETS=ROOT/'invoice-server/src/main/resources/public'
class Browser(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.app=RunningApp();[cls.app.login(r) for r in ['admin','intake','reviewer','approver','finance','auditor']]
  cls.p=sync_playwright().start();kw={'headless':True}
  if os.environ.get('RF_CHROMIUM'):kw['executable_path']=os.environ['RF_CHROMIUM']
  cls.browser=cls.p.chromium.launch(**kw)
 @classmethod
 def tearDownClass(cls):cls.browser.close();cls.p.stop();cls.app.close()
 def setUp(self):
  self.context=self.browser.new_context(viewport={'width':1440,'height':980},reduced_motion='reduce');self.page=self.context.new_page();self.errors=[];self.page.on('pageerror',lambda e:self.errors.append(str(e)));self.state={};self.loaded=False
 def tearDown(self):
  if self.errors:
   (ROOT/'build/browser').mkdir(parents=True,exist_ok=True);self.page.screenshot(path=str(ROOT/'build/browser'/f'{self._testMethodName}.png'),full_page=False)
  self.context.close();self.assertEqual([],self.errors)
 def boot(self,role='admin',lang='en',route='dashboard'):
  self.state={'cookie':self.app.sessions[role]['cookie'] if role else ''}
  if TRANSPORT=='native':
   if self.state['cookie']:
    name,value=self.state['cookie'].split('=',1);self.context.add_cookies([{'name':name,'value':value,'url':self.app.base,'httpOnly':True,'sameSite':'Strict'}])
   self.page.add_init_script('localStorage.setItem("rf.language",'+json.dumps(lang)+');')
   self.page.goto(self.app.base+'/#'+route,wait_until='domcontentloaded')
  else:
   def bridge(data):
    h=data.get('headers',{});h['Cookie']=self.state['cookie'];raw=base64.b64decode(data['body']) if data.get('body') is not None else None
    status,body,headers=self.app.request(data.get('method','GET'),data['url'],raw=raw,headers=h)
    if 'set-cookie' in headers:self.state['cookie']=headers['set-cookie'].split(';')[0]
    body=body if isinstance(body,bytes) else json.dumps(body,ensure_ascii=False).encode()
    return {'status':status,'body':base64.b64encode(body).decode(),'headers':headers}
   self.page.expose_function('rfBridge',bridge)
   self.page.set_content('<html><head><title>RechnungFlow</title></head><body><div id="app"></div><div id="toasts" aria-live="polite"></div></body></html>')
   self.page.add_style_tag(content=(ASSETS/'styles.css').read_text(encoding='utf-8'))
   self.page.evaluate('''([language,route])=>{const memory=new Map([['rf.language',language]]);Object.defineProperty(window,'localStorage',{value:{getItem:k=>memory.get(k)||null,setItem:(k,v)=>memory.set(k,String(v)),removeItem:k=>memory.delete(k)}});location.hash=route;
   window.fetch=async(url,o={})=>{let body=null;if(o.body!==undefined){let b=o.body;if(b instanceof Blob)b=await b.arrayBuffer();let bytes=typeof b==='string'?new TextEncoder().encode(b):new Uint8Array(b);let text='';for(let i=0;i<bytes.length;i+=8192)text+=String.fromCharCode(...bytes.subarray(i,i+8192));body=btoa(text);}let r=await rfBridge({url:String(url),method:o.method||'GET',headers:Object.fromEntries(new Headers(o.headers||{})),body});return new Response(Uint8Array.from(atob(r.body),x=>x.charCodeAt(0)),{status:r.status,headers:r.headers});};
   if(!crypto.randomUUID)crypto.randomUUID=()=> '10000000-1000-4000-8000-100000000000'.replace(/[018]/g,c=>(c^crypto.getRandomValues(new Uint8Array(1))[0]&15>>c/4).toString(16));}''',[lang,route])
   logo='data:image/svg+xml;base64,'+base64.b64encode((ASSETS/'icon.svg').read_bytes()).decode()
   self.page.evaluate('''logo=>{new MutationObserver(()=>document.querySelectorAll('img[src="/icon.svg"]').forEach(x=>x.src=logo)).observe(document.body,{childList:true,subtree:true});}''',logo)
   self.page.add_script_tag(content='(async()=>{'+(ASSETS/'app.js').read_text(encoding='utf-8')+'})().catch(e=>{throw e;});')
  if role:self.ready()
  else:expect(self.page.locator('#login-form')).to_be_visible()
  self.loaded=True
 def viewport_screenshot(self,path):
  # Chromium RTL layout origins can differ from logical window.scrollX.
  # Capture the actual layout viewport, not document coordinate (0, 0).
  session=self.context.new_cdp_session(self.page)
  try:
   viewport=session.send('Page.getLayoutMetrics')['cssLayoutViewport']
   result=session.send('Page.captureScreenshot',{'format':'png','captureBeyondViewport':False,'fromSurface':True,'clip':{'x':viewport['pageX'],'y':viewport['pageY'],'width':viewport['clientWidth'],'height':viewport['clientHeight'],'scale':1}})
   path.write_bytes(base64.b64decode(result['data']))
  finally:session.detach()
 def ready(self):
  expect(self.page.locator('#content')).to_be_visible(timeout=12000);expect(self.page.locator('#content .loading')).to_have_count(0,timeout=12000)
 def go(self,route):self.page.evaluate('(r)=>location.hash=r',route);self.ready()
 def invoice(self,name=None):
  name=name or 'UI-'+uuid.uuid4().hex[:10];status,body,_=self.app.upload(name);self.assertEqual(201,status,(status,body));return body['id'],name
 def submit(self):self.page.locator('dialog [type=submit]').click();expect(self.page.locator('dialog')).to_have_count(0,timeout=12000);self.ready()
 def test_upload_dialog_cancel(self):
  self.boot();self.page.locator('[data-action=upload]').click();expect(self.page.locator('#invoice-files')).to_be_visible();self.page.locator('dialog [data-close-dialog]').last.click();expect(self.page.locator('dialog')).to_have_count(0)
 def test_upload_real_xml(self):
  self.boot('intake');name='UI-FILE-'+uuid.uuid4().hex[:8];data=(ROOT/'examples/invoices/01-ubl-office.xml').read_bytes().replace(b'NW-2026-1001',name.encode());self.page.locator('[data-action=upload]').click();self.page.locator('#invoice-files').set_input_files({'name':'invoice.xml','mimeType':'application/xml','buffer':data});self.submit();expect(self.page.locator('.table')).to_contain_text(name)
 def test_upload_unsafe_xml_becomes_exception(self):
  self.boot('intake');self.page.locator('[data-action=upload]').click();self.page.locator('#invoice-files').set_input_files(str(ROOT/'examples/invoices/11-xxe.xml'));self.submit();expect(self.page.locator('.table')).to_contain_text('Exception')
 def test_review_action_reaches_approval(self):
  i,_=self.invoice();self.boot('reviewer',route='invoice/'+i);self.page.locator('[data-action=review]').click();self.page.locator('[name=costCenter]').fill('UI-100');self.page.locator('[name=glAccount]').fill('4930');self.page.locator('dialog [name=note]').fill('Independent UI business review.');self.submit();status,b,_=self.app.request('GET','/api/invoices/'+i,'reviewer');self.assertEqual('APPROVAL',b['stage'])
 def test_approve_action(self):
  i,_=self.invoice();_,v,_=self.app.request('GET','/api/invoices/'+i,'reviewer');self.app.request('POST',f'/api/invoices/{i}/review','reviewer',data={'version':v['version'],'costCenter':'UI-100','glAccount':'4930','note':'Reviewed.'});self.boot('approver',route='invoice/'+i);self.page.locator('[data-action=approve]').click();self.page.locator('dialog [name=note]').fill('Independent UI approval.');self.submit();_,b,_=self.app.request('GET','/api/invoices/'+i,'approver');self.assertEqual('APPROVED',b['stage'])
 def test_reject_with_reason(self):
  i,_=self.invoice();self.boot('reviewer',route='invoice/'+i);self.page.locator('[data-action=reject]').click();self.page.locator('dialog [name=reason]').fill('No matching delivery.');self.submit();_,b,_=self.app.request('GET','/api/invoices/'+i,'reviewer');self.assertEqual('REJECTED',b['stage'])
 def test_comment_xss_is_text(self):
  i,_=self.invoice();self.boot('reviewer',route='invoice/'+i);self.page.locator('[data-tab=comments]').click();expect(self.page.locator('#comment-form')).to_be_visible();payload='<img src=x onerror="window.invoiceXSS=true">';self.page.locator('#comment-form [name=note]').fill(payload);self.page.locator('#comment-form [type=submit]').click();expect(self.page.locator('.comment')).to_contain_text(payload);self.assertFalse(self.page.evaluate('()=>Boolean(window.invoiceXSS)'));expect(self.page.locator('.comment img')).to_have_count(0)
 def test_retention_hold_action(self):
  i,_=self.invoice();self.boot('finance',route='invoice/'+i);self.page.locator('[data-action=hold]').click();self.page.locator('[name=legalHold]').check();self.page.locator('dialog [name=reason]').fill('Synthetic litigation hold test.');self.submit();_,b,_=self.app.request('GET','/api/invoices/'+i,'finance');self.assertTrue(b['legalHold'])
 def test_revalidation_history_action(self):
  i,_=self.invoice();self.boot('reviewer',route='invoice/'+i);self.page.locator('[data-action=revalidate]').click();self.submit();_,b,_=self.app.request('GET','/api/invoices/'+i,'reviewer');self.assertEqual(2,len(b['validationHistory']))
 def test_intake_cannot_see_approval_action(self):
  i,_=self.invoice();self.boot('intake',route='invoice/'+i);expect(self.page.locator('[data-action=approve]')).to_have_count(0);expect(self.page.locator('[data-action=review]')).to_have_count(0)
 def test_auditor_is_read_only(self):
  i,_=self.invoice();self.boot('auditor',route='invoice/'+i)
  for action in ['approve','review','reject','hold','export']:expect(self.page.locator('[data-action='+action+']')).to_have_count(0)
 def test_navigation_hides_admin_pages(self):
  self.boot('reviewer');expect(self.page.locator('.nav-link[href="#users"]')).to_have_count(0);expect(self.page.locator('.nav-link[href="#system"]')).to_have_count(0)
 def test_direct_route_still_denied(self):
  self.boot('reviewer',route='users');expect(self.page.locator('#content .notice.error')).to_be_visible();expect(self.page.locator('[data-action=user-create]')).to_have_count(0)
 def test_filter_exact_invoice(self):
  i,name=self.invoice();self.boot(route='invoices');self.page.locator('#invoice-search').fill(name);self.page.locator('#filters [type=submit]').click();expect(self.page.locator('.table tbody tr')).to_have_count(1);expect(self.page.locator('.table')).to_contain_text(name)
 def test_filter_no_match(self):
  self.boot(route='invoices');self.page.locator('#invoice-search').fill('no-such-invoice-99999');self.page.locator('#filters [type=submit]').click();expect(self.page.locator('.empty')).to_be_visible()
 def test_stage_filter(self):
  self.boot(route='invoices');self.page.locator('#stage-filter').select_option('EXCEPTION');expect(self.page.locator('.table')).to_contain_text('Exception');self.assertTrue(all('Exception' in x for x in self.page.locator('.table tbody tr').all_text_contents()))
 def test_supplier_create(self):
  self.boot(route='suppliers');self.page.locator('[data-action=supplier-create]').click();name='UI supplier '+uuid.uuid4().hex[:8];self.page.locator('[name=name]').fill(name);self.page.locator('[name=identity]').fill('registration:'+uuid.uuid4().hex);self.page.locator('[name=iban]').fill('');self.submit();expect(self.page.locator('.table')).to_contain_text(name)
 def test_supplier_verification_requires_checkbox(self):
  name='UI-check-'+uuid.uuid4().hex[:8];s,b,_=self.app.request('POST','/api/suppliers','reviewer',data={'name':name,'identity':'registration:'+name,'iban':''});self.assertEqual(201,s);self.boot('approver',route='suppliers');self.page.locator('[data-action=supplier-verify][data-id="'+b['id']+'"]').click();expect(self.page.locator('[name=confirmedOutOfBand]')).to_have_attribute('required','');self.page.locator('[name=confirmedOutOfBand]').check();self.page.locator('dialog [name=reason]').fill('Confirmed with known procurement contact.');self.submit();_,listing,_=self.app.request('GET','/api/suppliers','admin');self.assertEqual('VERIFIED',next(x for x in listing['items'] if x['id']==b['id'])['status'])
 def test_api_key_create_and_secret_once(self):
  self.boot(route='users');self.page.locator('[data-action=key-create]').click();name='UI-key-'+uuid.uuid4().hex[:8];self.page.locator('dialog [name=name]').fill(name);self.page.locator('dialog [type=submit]').click();expect(self.page.locator('.key-secret')).to_be_visible();token=self.page.locator('.key-secret').inner_text();self.assertGreater(len(token),30);self.page.locator('dialog .dialog-footer button').click();self.ready();expect(self.page.locator('body')).not_to_contain_text(token);_,keys,_=self.app.request('GET','/api/keys','admin');self.assertNotIn(token,json.dumps(keys))
 def test_password_dialog_has_minimum(self):
  self.boot();self.page.locator('[data-action=password]').click();expect(self.page.locator('[name=newPassword]')).to_have_attribute('minlength','14');self.page.locator('dialog [data-close-dialog]').first.click();expect(self.page.locator('dialog')).to_have_count(0)
 def test_user_create_form_roles(self):
  self.boot(route='users');self.page.locator('[data-action=user-create]').click();expect(self.page.locator('input[name=roles]')).to_have_count(6);expect(self.page.locator('input[name=password]')).to_have_attribute('minlength','14')
 def test_policy_form_roundtrip(self):
  self.boot(route='policy');self.page.locator('#policy-form [name=name]').fill('RechnungFlow UI verification');self.page.locator('#policy-form [type=submit]').click();expect(self.page.locator('.workspace-chip')).to_contain_text('RechnungFlow UI verification');self.page.locator('#policy-form [name=name]').fill('RechnungFlow workspace');self.page.locator('#policy-form [type=submit]').click();expect(self.page.locator('.workspace-chip')).to_contain_text('RechnungFlow workspace')
 def test_form_cannot_delete_last_admin(self):
  self.boot(route='users');row=self.page.locator('.table tbody tr').filter(has=self.page.locator('td',has_text='admin')).first
  # Identity-based selection avoids matching role labels on other rows.
  _,users,_=self.app.request('GET','/api/users','admin');admin=next(x for x in users['items'] if x['login']=='admin')
  self.page.locator('[data-action=user-edit][data-id="'+admin['id']+'"]').click();self.page.locator('[name=active]').uncheck();self.page.locator('dialog [type=submit]').click();expect(self.page.locator('dialog .form-error')).not_to_be_empty();expect(self.page.locator('dialog')).to_be_visible()
 def test_mobile_menu_navigation(self):
  self.page.set_viewport_size({'width':390,'height':844});self.boot(lang='fa');expect(self.page.locator('.sidebar')).not_to_be_visible();self.page.locator('[data-action=menu]').click();expect(self.page.locator('.sidebar')).to_be_visible();self.page.locator('.nav-link[href="#invoices"]').click();expect(self.page.locator('#filters')).to_be_visible();expect(self.page.locator('.sidebar')).not_to_be_visible()
 def test_close_pending_dialog_no_crash(self):
  self.boot();self.page.locator('[data-action=upload]').click();data=(ROOT/'examples/invoices/01-ubl-office.xml').read_bytes().replace(b'NW-2026-1001',('UI-ASYNC-'+uuid.uuid4().hex[:8]).encode());self.page.locator('#invoice-files').set_input_files({'name':'async.xml','mimeType':'application/xml','buffer':data})
  self.page.evaluate('''()=>{const original=window.fetch;window.fetch=async(url,opts)=>{const r=await original(url,opts);if(String(url)==='/api/invoices'&&opts?.method==='POST')await new Promise(resolve=>setTimeout(resolve,150));return r;};}''')
  self.page.locator('dialog [type=submit]').click();self.page.locator('dialog [data-close-dialog]').first.click();expect(self.page.locator('dialog')).to_have_count(0);expect(self.page.locator('#filters')).to_be_visible(timeout=10000)
 def test_native_csp_rejects_inline_script(self):
  if TRANSPORT!='native':self.skipTest('Native CSP enforcement cannot be verified with harness transport.')
  self.boot();self.page.evaluate("()=>{let s=document.createElement('script');s.textContent='window.cspBypass = true';document.head.append(s);}");self.assertFalse(self.page.evaluate('()=>Boolean(window.cspBypass)'))
 def test_native_session_cookie_flags(self):
  if TRANSPORT!='native':self.skipTest('Harness does not exercise browser cookie transport.')
  self.boot(role=None);self.page.locator('[name=login]').fill('auditor');self.page.locator('[name=password]').fill(PASSWORD);self.page.locator('#login-form [type=submit]').click();self.ready();cookies=self.context.cookies();self.assertTrue(any(c['httpOnly'] and c['sameSite']=='Strict' for c in cookies))

# Meaningful page/language combinations: all ten views must render in every language.
for lang in ['en','de','fa']:
 for view,selector in [('dashboard','.stats'),('invoices','#filters'),('approvals','#filters'),('suppliers','.table'),('jobs','.card'),('audit','.table'),('users','[data-action=user-create]'),('policy','#policy-form'),('system','.stats'),('docs','.doc-markdown')]:
  def make(l,v,s):
   def test(self):
    self.boot(lang=l,route=v);expect(self.page.locator(s).first).to_be_visible();expect(self.page.locator('html')).to_have_attribute('lang',l);expect(self.page.locator('html')).to_have_attribute('dir','rtl' if l=='fa' else 'ltr');expect(self.page.locator('#content .notice.error')).to_have_count(0)
    if v=='docs':self.assertGreater(len(self.page.locator('.doc-markdown').inner_text()),600)
    if v=='dashboard':
     directory=ROOT/'build/browser';directory.mkdir(parents=True,exist_ok=True);self.page.screenshot(path=str(directory/f'dashboard-{l}.png'),full_page=True)
   return test
  setattr(Browser,f'test_view_{lang}_{view}',make(lang,view,selector))
 for view in ['dashboard','invoices','suppliers','policy']:
  def make_mobile(l,v):
   def test(self):
    self.page.set_viewport_size({'width':390,'height':844});self.boot(lang=l,route=v);self.assertFalse(self.page.evaluate('()=>document.documentElement.scrollWidth>innerWidth+1'));self.assertTrue(self.page.evaluate("()=>Array.from(document.querySelectorAll('.main,.topbar,.content,.page-heading')).every(e=>{const r=e.getBoundingClientRect();return r.left>=-1&&r.right<=innerWidth+1;})"))
    if v=='dashboard':
     d=ROOT/'build/browser';d.mkdir(parents=True,exist_ok=True);self.viewport_screenshot(d/f'mobile-{l}.png')
   return test
  setattr(Browser,f'test_mobile_{lang}_{view}',make_mobile(lang,view))
 for tab in ['overview','validation','history','comments']:
  def make_tab(l,tab):
   def test(self):
    i,_=self.invoice();self.boot(lang=l,route='invoice/'+i);self.page.locator('[data-tab='+tab+']').click();expect(self.page.locator('[data-tab='+tab+']')).to_have_attribute('aria-selected','true');expect(self.page.locator('.detail-grid')).to_be_visible()
   return test
  setattr(Browser,f'test_invoice_tab_{lang}_{tab}',make_tab(lang,tab))
if __name__=='__main__':
 print('Browser transport:',TRANSPORT,flush=True)
 suite=unittest.defaultTestLoader.loadTestsFromTestCase(Browser)
 sys.exit(0 if run_suite(suite,ROOT/'build/test-results'/f'browser-{TRANSPORT}.xml') else 1)
