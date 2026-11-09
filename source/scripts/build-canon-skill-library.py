#!/usr/bin/env python3
"""Generate the player skill reference from the same authoring data as the assets."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
skills = json.loads((root / 'docs/canon/skills.json').read_text())
categories = {'mental_out': '心理掌握', 'aero_hand': '空力使',
              'nitrogen_armor': '氮气装甲', 'dark_matter': '未元物质',
              'hydro_hand': '水流操作', 'float_dial': '浮力操作',
              'paper_psychokinesis': '纸媒念动', 'liquid_shadow': '液化人影'}
assert len(skills) == 194 and len({s['id'] for s in skills}) == 194
lines = ['# 原著能力技能库', '',
         '八系各 24 项，心理掌握与未元物质各增 1 项，共 194 项。本表由 `source/docs/canon/skills.json` 生成；运行、多人、特效和剧情验证见交付报告。细分技能名称与等级为 ACA 游戏化设计，原作能力边界见 [来源核查](CANON_SOURCE_NOTES_20261027.md)。', '',
         '通用操作：开发机学习技能，装入预设并激活能力后，通过当前绑定按键施放。所有操作由服务端检查等级、冷却、资源、媒介、目标与世界权限。', '']
for category, title in categories.items():
    entries = sorted((s for s in skills if s['category'] == category), key=lambda s: (s['level'], s['id']))
    assert len(entries) >= 24
    lines += [f'## {title}', '', '| 技能 | 等级 | 前置 | 实际用途与限制 |', '|---|---:|---|---|']
    for s in entries:
        parent = '、'.join(s['prerequisites']) or '起始'
        lines.append(f"| {s['zh']['name']} `{s['id']}` | {s['level']} | {parent} | {s['zh']['description'].replace('|', '／')} |")
    lines.append('')
target = root.parent / 'docs/CANON_SKILL_LIBRARY_20261027.md'
target.write_text('\n'.join(lines) + '\n')
print(target)
