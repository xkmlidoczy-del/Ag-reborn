#!/usr/bin/env python3
"""Adds the HUD names of the eight canon category shields (hud.academy.highpower.shield.<category>), idempotent."""
from pathlib import Path
import json
LANG = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/academy/lang'
T = {  # category: (zh_cn, zh_tw, en_us, ja_jp)
 'mental_out': ('精神壁垒', '精神壁壘', 'Mind Ward', '精神障壁'),
 'aero_hand': ('气压屏障', '氣壓屏障', 'Pressure Barrier', '気圧バリア'),
 'float_dial': ('浮力缓冲', '浮力緩衝', 'Buoyancy Cushion', '浮力クッション'),
 'nitrogen_armor': ('氮气外壳', '氮氣外殼', 'Nitrogen Shell', '窒素シェル'),
 'dark_matter': ('未元羽障', '未元羽障', 'Feather Veil', '未元の羽障'),
 'hydro_hand': ('水膜护层', '水膜護層', 'Water Film', '水膜'),
 'paper_psychokinesis': ('纸甲', '紙甲', 'Paper Mail', '紙鎧'),
 'liquid_shadow': ('影之帷', '影之帷', 'Shadow Veil', '影の帳'),
}
for i, loc in enumerate(('zh_cn', 'zh_tw', 'en_us', 'ja_jp')):
    path = LANG / f'{loc}.json'; data = json.loads(path.read_text())
    for cat, names in T.items(): data[f'hud.academy.highpower.shield.{cat}'] = names[i]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
print('shield names', len(T))
