#!/usr/bin/env python3
"""Convert the original four .lang dictionaries to modern JSON while preserving port-only keys.
Legacy numeric printf tokens become Minecraft component-compatible %s tokens.
The originals and source hashes remain in docs/reference-visuals/languages.
"""
from pathlib import Path
import json,re,hashlib,argparse
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'.reference/AcademyCraft-1.0.7/src/main/resources/assets/academy/lang'
TARGET=ROOT/'src/main/resources/assets/academy/lang'
REFERENCE=ROOT/'docs/reference-visuals/languages'
def main():
 REFERENCE.mkdir(parents=True,exist_ok=True); records=[]
 for src in sorted(SOURCE.glob('*.lang')):
  data=src.read_bytes();text=data.decode('utf-8-sig');locale=src.stem.lower();existing=TARGET/f'{locale}.json';new={}
  for line in text.splitlines():
   if not line.strip() or line.lstrip().startswith('#') or '=' not in line:continue
   key,value=line.split('=',1)
   value=re.sub(r'%(\d+\$)?[-+0 #]*\d*(?:\.\d+)?[df]',lambda m:'%'+(m.group(1)or'')+'s',value)
   new[key.strip()]=value
  # User-facing bridge labels explicitly describe this development stage.
  if existing.exists():
   extras=json.loads(existing.read_text())
   for key,value in extras.items():
    if key.startswith(('key.','screen.','item.academy.')):new[key]=value
  new['item.academy.needle']=new.get('item.ac_needle.name','Needle')
  new['item.academy.coin']=new.get('item.ac_coin.name','Coin')
  existing.write_text(json.dumps(new,ensure_ascii=False,indent=2)+'\n')
  (REFERENCE/src.name).write_bytes(data)
  records.append({'source':f'AcademyCraft1.0.7/assets/academy/lang/{src.name}','sha256':hashlib.sha256(data).hexdigest(),'keys':len(new),'output':f'assets/academy/lang/{locale}.json','numeric_printf_adapted':True})
 (REFERENCE/'manifest.json').write_text(json.dumps(records,indent=2)+'\n')
 print(json.dumps(records,indent=2))
if __name__=='__main__':main()
