#!/usr/bin/env python3
"""Prepare only the explicitly owned isolated test datapack; refuse different existing contents."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
pack=root/'run-common-tags-m21/m21-common-tag-fixture-world/datapacks/academy-common-tag-test'
files={'pack.mcmeta':{'pack':{'pack_format':48,'description':'Owned M21 native common-tag alternatives QA only'}}}
for tag,item in [('ingots/iron','gold_ingot'),('dusts/redstone','glowstone_dust'),('plates/iron','iron_ingot'),('storage_blocks/redstone','gold_block')]:
    files[f'data/c/tags/item/{tag}.json']={'replace':False,'values':['minecraft:'+item]}
for name,value in files.items():
    target=pack/name
    if target.exists():
        if json.loads(target.read_text())!=value:raise SystemExit('Different existing owned fixture; inspect before changing: '+str(target))
    else:
        target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(value,indent=2)+'\n')
print('Prepared exact isolated native tag fixtures; no client/production resources changed')
