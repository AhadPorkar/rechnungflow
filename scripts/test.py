#!/usr/bin/env python3
"""Reproducible offline tests. Real KoSIT and browser acceptance are separate explicit suites."""
from pathlib import Path
import os,subprocess,sys,shutil
ROOT=Path(__file__).resolve().parents[1]
def run(command):
    print('\n> '+' '.join(map(str,command)),flush=True)
    subprocess.run(list(map(str,command)),cwd=ROOT,check=True)
def main():
    os.chdir(ROOT);run([sys.executable,'scripts/build.py'])
    out=ROOT/'build/test-classes'
    if out.exists():shutil.rmtree(out)
    out.mkdir(parents=True)
    sources=ROOT/'build/test-sources.args';sources.write_text('\n'.join('"'+p.relative_to(ROOT).as_posix()+'"' for p in sorted((ROOT/'tests/java').rglob('*.java'))),encoding='utf-8')
    run(['javac','--release','21','-encoding','UTF-8','-cp',ROOT/'build/classes','-d',out,'@'+str(sources)])
    cp=os.pathsep.join(map(str,[ROOT/'build/classes',out]))
    for suite in ['CoreTest','IngestTest','WorkflowTest','AuthTest','OutboxTest','ValidationTest','ServerResponseTest']:
        run(['java','-cp',cp,'io.ahad.rechnungflow.testing.'+suite])
    for script in ['http_test.py','operations_test.py','installer_test.py','repository_test.py']:
        if (ROOT/'tests'/script).exists():run([sys.executable,'tests/'+script])
    print('\nOffline suites passed. This does NOT imply real KoSIT, Windows, Docker, or native-browser acceptance.')
if __name__=='__main__':main()
