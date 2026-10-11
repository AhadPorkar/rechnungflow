#!/usr/bin/env python3
"""Install explicit KoSIT releases from official GitHub assets. Network access is required.
Does not claim an installation is certified or that an invoice passed validation.
"""
from __future__ import annotations
import argparse, hashlib, json, os, shutil, stat, tempfile, urllib.request, urllib.parse, zipfile
from pathlib import Path, PurePosixPath
VERSION='1.6.3'
CONFIG='2026-08-31'
MAX_DOWNLOAD=220*1024*1024

def sha(path:Path)->str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''): h.update(chunk)
    return h.hexdigest()

def safe_extract(archive:Path,target:Path)->None:
    target.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        entries=z.infolist()
        if len(entries)>20000 or sum(x.file_size for x in entries)>600*1024*1024: raise ValueError('Archive limits exceeded')
        seen=set()
        for info in entries:
            name=info.filename
            p=PurePosixPath(name)
            if not name or '\\' in name or p.is_absolute() or '..' in p.parts or ':' in name or '\x00' in name: raise ValueError('Unsafe archive path')
            if stat.S_ISLNK(info.external_attr>>16): raise ValueError('Symbolic link in archive')
            key=str(p).casefold()
            if key in seen: raise ValueError('Duplicate archive path')
            seen.add(key)
            if any(part.rstrip(' .')!=part or part.split('.')[0].upper() in {'CON','PRN','AUX','NUL',*[f'COM{i}' for i in range(1,10)],*[f'LPT{i}' for i in range(1,10)]} for part in p.parts): raise ValueError('Nonportable archive path')
            path=target.joinpath(*p.parts)
            if info.is_dir(): path.mkdir(parents=True,exist_ok=True);continue
            if info.file_size>150*1024*1024:raise ValueError('Archive member too large')
            path.parent.mkdir(parents=True,exist_ok=True)
            with z.open(info) as src,path.open('xb') as dst: shutil.copyfileobj(src,dst)

def fetch_json(url:str)->dict:
    request=urllib.request.Request(url,headers={'User-Agent':'RechnungFlow-installer/0.1.0','Accept':'application/vnd.github+json'})
    with urllib.request.urlopen(request,timeout=45) as response:
        data=response.read(4*1024*1024+1)
    if len(data)>4*1024*1024:raise ValueError('API response too large')
    return json.loads(data)

def release(repo:str,tag:str)->dict:
    result=fetch_json(f'https://api.github.com/repos/itplr-kosit/{repo}/releases/tags/{tag}')
    if result.get('tag_name')!=tag or result.get('draft') or result.get('prerelease'):raise ValueError('Unexpected release metadata')
    return result

def download(asset:dict,repo:str,target:Path,allow_no_digest:bool)->dict:
    url=asset['browser_download_url']
    if not url.startswith(f'https://github.com/itplr-kosit/{repo}/releases/download/'):raise ValueError('Asset is outside the official repository')
    digest=asset.get('digest') or ''
    if not digest.startswith('sha256:') and not allow_no_digest:raise ValueError('Asset has no published SHA-256. Review upstream provenance, then explicitly use --allow-no-published-digest if appropriate.')
    req=urllib.request.Request(url,headers={'User-Agent':'RechnungFlow-installer/0.1.0'})
    with urllib.request.urlopen(req,timeout=90) as src,target.open('xb') as dst:
        final=urllib.parse.urlparse(src.url)
        if final.scheme!='https' or not (final.hostname=='github.com' or (final.hostname or '').endswith('.githubusercontent.com')):raise ValueError('Unexpected download redirect')
        total=0
        while chunk:=src.read(1024*1024):
            total+=len(chunk)
            if total>MAX_DOWNLOAD:raise ValueError('Download size limit')
            dst.write(chunk)
    actual=sha(target)
    if digest.startswith('sha256:') and digest[7:]!=actual:raise ValueError('Published asset digest mismatch')
    return {'url':url,'sha256':actual,'publishedDigestVerified':digest.startswith('sha256:')}

def select(assets:list,kind:str)->dict:
    if kind=='validator':
        direct=[a for a in assets if a['name'].endswith('.jar') and 'standalone' in a['name'].lower()]
        if len(direct)==1:return direct[0]
        candidates=[a for a in assets if a['name'].endswith('.zip') and not any(w in a['name'].lower() for w in ('source','javadoc'))]
    else:candidates=[a for a in assets if a['name'].endswith('.zip') and 'xrechnung' in a['name'].lower()]
    if len(candidates)!=1:raise ValueError(f'Ambiguous {kind} assets: {[a["name"] for a in assets]}. Review the official release; no guessed URL is used.')
    return candidates[0]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--destination',type=Path,default=Path('tools/kosit'));p.add_argument('--allow-no-published-digest',action='store_true');args=p.parse_args()
    dest=args.destination.resolve()
    if dest.exists():raise SystemExit('Destination already exists. Install upgrades into a new directory and re-run acceptance tests.')
    dest.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='rf-kosit-',dir=dest.parent) as temporary:
        temp=Path(temporary);install=temp/'install';install.mkdir();evidence=[]
        vr=release('validator','v'+VERSION);va=select(vr['assets'],'validator');vf=temp/va['name'];evidence.append(download(va,'validator',vf,args.allow_no_published_digest))
        if vf.suffix=='.jar':jar=vf
        else:
            unpack=temp/'validator';safe_extract(vf,unpack);jars=list(unpack.rglob('*standalone*.jar'))
            if len(jars)!=1:raise ValueError('Expected exactly one standalone validator JAR')
            jar=jars[0]
        shutil.copy2(jar,install/f'validator-{VERSION}-standalone.jar')
        cr=release('validator-configuration-xrechnung','v'+CONFIG);ca=select(cr['assets'],'configuration');cf=temp/ca['name'];evidence.append(download(ca,'validator-configuration-xrechnung',cf,args.allow_no_published_digest))
        config=temp/'configuration';safe_extract(cf,config);scenarios=list(config.rglob('scenarios.xml'))
        if len(scenarios)!=1:raise ValueError('Expected exactly one scenarios.xml')
        shutil.copytree(scenarios[0].parent,install/'config')
        hashes={p.relative_to(install/'config').as_posix():sha(p) for p in sorted((install/'config').rglob('*')) if p.is_file()}
        manifest={'version':VERSION,'configuration':CONFIG,'jarSha256':sha(install/f'validator-{VERSION}-standalone.jar'),'configurationFiles':hashes,'downloads':evidence,'officialAcceptanceExecuted':False}
        (install/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
        install.rename(dest)
    print(f'Installed pinned inputs at {dest}. Set RF_KOSIT_DIR to this directory.')
    print('Next run: python scripts/validate_kosit.py --kosit '+str(dest)+' --input <invoice.xml> --expect PASS')
    print('Installation alone is NOT official invoice acceptance or product certification.')
if __name__=='__main__':main()
