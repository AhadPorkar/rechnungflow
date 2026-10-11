#!/usr/bin/env python3
"""Offline Java 21 build. Requires a JDK and Python 3.10+, not Maven or network access."""
from pathlib import Path
import os, shutil, subprocess, sys
ROOT=Path(__file__).resolve().parents[1]
def main():
    os.chdir(ROOT)
    for tool in ('java','javac','jar'):
        if not shutil.which(tool): raise SystemExit(f'{tool} not found; install JDK 21+ and configure PATH.')
    out=ROOT/'build/classes'
    if out.exists(): shutil.rmtree(out)
    out.mkdir(parents=True)
    sources=sorted(p for module in ('invoice-core','invoice-ingest','invoice-workflow','invoice-server') for p in (ROOT/module/'src/main/java').rglob('*.java'))
    args=ROOT/'build/sources.args'
    args.write_text('\n'.join('"'+p.relative_to(ROOT).as_posix()+'"' for p in sources),encoding='utf-8')
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(out),'@'+str(args)],check=True)
    shutil.copytree(ROOT/'invoice-server/src/main/resources',out,dirs_exist_ok=True)
    shutil.copytree(ROOT/'examples/invoices',out/'samples',dirs_exist_ok=True)
    if (ROOT/'docs').exists():
        for lang in ('en','de','fa'):
            source=ROOT/'docs'/lang
            if source.exists(): shutil.copytree(source,out/'public/docs'/lang,dirs_exist_ok=True)
    jar=ROOT/'build/rechnungflow-0.1.0.jar'
    subprocess.run(['jar','--create','--file',str(jar),'--main-class','io.ahad.rechnungflow.server.Main','-C',str(out),'.'],check=True)
    print(f'Built {jar.name} ({jar.stat().st_size:,} bytes); {len(sources)} Java source files.')
if __name__=='__main__': main()
