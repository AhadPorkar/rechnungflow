"""Portable repository/package contract checks, distinct from runtime acceptance."""
from pathlib import Path
import json,re,sys,unittest,zipfile,xml.etree.ElementTree as ET
from support import ROOT,run_suite
PUBLIC=ROOT/'invoice-server/src/main/resources/public'
GUIDES={'quickstart','user-guide','architecture','api','security','validation','operations','testing','deployment','commercial-readiness','research','github'}
LANGS=('en','de','fa')
class Repository(unittest.TestCase):
 def test_translation_key_parity(self):
  values=[json.loads((PUBLIC/'locales'/f'{lang}.json').read_text(encoding='utf-8')) for lang in LANGS]
  self.assertEqual(set(values[0]),set(values[1]));self.assertEqual(set(values[0]),set(values[2]));self.assertGreater(len(values[0]),300)
 def test_translations_are_nonempty_strings(self):
  for lang in LANGS:
   for key,value in json.loads((PUBLIC/'locales'/f'{lang}.json').read_text(encoding='utf-8')).items():self.assertIsInstance(value,str);self.assertTrue(value.strip(),(lang,key))
 def test_literal_ui_translation_keys_exist(self):
  keys=json.loads((PUBLIC/'locales/en.json').read_text(encoding='utf-8'))
  for key in set(re.findall(r"\bt\(['\"]([^'\"]+)['\"]\s*[,)]",(PUBLIC/'app.js').read_text(encoding='utf-8'))):self.assertIn(key,keys)
 def test_all_language_guides_present_and_substantive(self):
  for lang in LANGS:
   self.assertEqual(GUIDES,{p.stem for p in (ROOT/'docs'/lang).glob('*.md')})
   for name in GUIDES:self.assertGreater(len((ROOT/'docs'/lang/(name+'.md')).read_text(encoding='utf-8')),500)
 def test_local_markdown_links_resolve(self):
  for path in ROOT.rglob('*.md'):
   if any(x in path.relative_to(ROOT).parts for x in ('build','.git')):continue
   for link in re.findall(r'!?\[[^\]]*\]\(([^\s)]+)(?:\s+[^)]*)?\)',path.read_text(encoding='utf-8')):
    if link.startswith(('http:','https:','mailto:','#')):continue
    target=link.split('#')[0]
    if target:self.assertTrue((path.parent/target).exists(),f'{path.relative_to(ROOT)} -> {link}')
 def test_documentation_utf8_without_replacement_characters(self):
  for path in list((ROOT/'docs').rglob('*.md'))+list(ROOT.glob('README*.md')):self.assertNotIn('\ufffd',path.read_text(encoding='utf-8'))
 def test_api_schema_version_and_unique_operations(self):
  spec=json.loads((ROOT/'invoice-server/src/main/resources/openapi.json').read_text(encoding='utf-8'))
  self.assertEqual('3.1.1',spec['openapi']);ids=[op['operationId'] for path in spec['paths'].values() for method,op in path.items() if method in ('get','post','put','patch','delete','head','options')]
  self.assertEqual(len(ids),len(set(ids)));self.assertEqual(38,len(ids))
 def test_local_api_references_resolve(self):
  spec=json.loads((ROOT/'invoice-server/src/main/resources/openapi.json').read_text(encoding='utf-8'))
  def visit(v):
   if isinstance(v,dict):
    if '$ref' in v:
     ref=v['$ref'];self.assertTrue(ref.startswith('#/'));node=spec
     for part in ref[2:].split('/'):node=node[part.replace('~1','/').replace('~0','~')]
    for item in v.values():visit(item)
   elif isinstance(v,list):
    for item in v:visit(item)
  visit(spec)
 def test_json_examples_parse(self):
  paths=list((ROOT/'examples/api').glob('*.json'));self.assertGreaterEqual(len(paths),6)
  for p in paths:self.assertIsInstance(json.loads(p.read_text(encoding='utf-8')),dict)
 def test_research_registry_has_primary_https_references(self):
  data=json.loads((ROOT/'docs/research-sources.json').read_text(encoding='utf-8'))
  text=json.dumps(data);urls=re.findall(r'https://[^"\s]+',text);self.assertGreaterEqual(len(set(urls)),20)
 def test_source_modules_have_production_classes(self):
  for module in ('invoice-core','invoice-ingest','invoice-workflow','invoice-server'):self.assertGreater(len(list((ROOT/module/'src/main/java').rglob('*.java'))),0)
 def test_jar_has_executable_manifest(self):
  with zipfile.ZipFile(ROOT/'build/rechnungflow-0.1.0.jar') as z:self.assertIn('Main-Class: io.ahad.rechnungflow.server.Main',z.read('META-INF/MANIFEST.MF').decode())
 def test_jar_language_assets_match_source(self):
  with zipfile.ZipFile(ROOT/'build/rechnungflow-0.1.0.jar') as z:
   for lang in LANGS:self.assertEqual((PUBLIC/'locales'/f'{lang}.json').read_bytes(),z.read(f'public/locales/{lang}.json'))
 def test_jar_includes_all_translated_guides(self):
  with zipfile.ZipFile(ROOT/'build/rechnungflow-0.1.0.jar') as z:
   for lang in LANGS:
    for name in GUIDES:self.assertEqual((ROOT/'docs'/lang/(name+'.md')).read_bytes(),z.read(f'public/docs/{lang}/{name}.md'))
 def test_jar_has_no_keys_or_vendor_binaries(self):
  with zipfile.ZipFile(ROOT/'build/rechnungflow-0.1.0.jar') as z:
   for name in z.namelist():self.assertFalse(name.lower().endswith(('.key','.jar','.p12','.pem')),name)
 def test_jar_static_resources_are_current(self):
  with zipfile.ZipFile(ROOT/'build/rechnungflow-0.1.0.jar') as z:
   for name in ('app.js','styles.css','index.html','icon.svg'):self.assertEqual((PUBLIC/name).read_bytes(),z.read('public/'+name))
 def test_vendor_pins_are_explicit(self):
  text=(ROOT/'scripts/setup_kosit.py').read_text(encoding='utf-8');self.assertIn("VERSION='1.6.3'",text);self.assertIn("CONFIG='2026-08-31'",text)
 def test_browser_ci_requires_native_transport(self):
  text=(ROOT/'.github/workflows/ci.yml').read_text();self.assertIn('RF_UI_TRANSPORT: native',text);self.assertNotIn('RF_UI_TRANSPORT: harness',text)
 def test_ci_matrix_retains_failures_and_limits_permissions(self):
  text=(ROOT/'.github/workflows/ci.yml').read_text();self.assertIn('fail-fast: false',text);self.assertIn('contents: read',text);self.assertIn('if: always()',text);self.assertNotIn('continue-on-error: true',text)
 def test_official_workflow_reports_have_distinct_paths(self):
  text=(ROOT/'.github/workflows/official-validation.yml').read_text();self.assertIn('--report build/kosit-acceptance/positive.json',text);self.assertIn('--report build/kosit-acceptance/negative.json',text);self.assertIn('workflow_dispatch:',text)
 def test_runtime_sensitive_paths_are_ignored(self):
  rules=(ROOT/'.gitignore').read_text().splitlines()
  for rule in ('/var*/','/tools/','*.key','*.jar','/.env'):self.assertIn(rule,rules)
 def test_cmd_entrypoints_exist_without_powershell(self):
  for name in ('build','test','run-demo'):
   text=(ROOT/'scripts'/(name+'.cmd')).read_text().lower();self.assertNotIn('powershell',text);self.assertIn('%~dp0',text)
 def test_docker_runtime_is_nonroot(self):
  self.assertIn('USER 10001:10001',(ROOT/'Dockerfile').read_text())
 def test_no_private_key_material_in_public_examples(self):
  for p in (ROOT/'examples').rglob('*'):
   if p.is_file():self.assertNotIn(b'-----BEGIN PRIVATE KEY-----',p.read_bytes())
if __name__=='__main__':sys.exit(0 if run_suite(unittest.defaultTestLoader.loadTestsFromTestCase(Repository),ROOT/'build/test-results/repository.xml') else 1)
