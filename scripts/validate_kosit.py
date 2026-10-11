#!/usr/bin/env python3
"""Run the REAL installed KoSIT API worker. Missing binaries or wrong expectation FAIL; never silently skip."""
import argparse,json,os,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--kosit',type=Path,required=True);p.add_argument('--input',type=Path,required=True);p.add_argument('--expect',choices=['PASS','FAIL','ERROR'],required=True);p.add_argument('--report',type=Path,default=ROOT/'build/real-kosit-result.json');a=p.parse_args()
    jar=ROOT/'build/rechnungflow-0.1.0.jar'
    if not jar.is_file():raise SystemExit('Build the application first.')
    command=['java','-cp',str(jar),'io.ahad.rechnungflow.server.ValidateDocument',str(a.kosit.resolve()),str(a.input.resolve()),str(a.report.resolve())]
    subprocess.run(command,check=True,timeout=140)
    result=json.loads(a.report.read_text(encoding='utf-8'))
    print(json.dumps({k:v for k,v in result.items() if k!='report'},indent=2,ensure_ascii=False))
    if result['status']!=a.expect:raise SystemExit(f'Expected {a.expect}; actual {result["status"]}. Preserve and inspect the report.')
if __name__=='__main__':main()
