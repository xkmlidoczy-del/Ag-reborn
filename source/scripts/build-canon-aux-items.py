#!/usr/bin/env python3
"""Rebuild original particle icons, refined OBJ meshes/materials, and unchanged item data.

Artwork/model sources: canon_aux_pixel_art.py, canon_aux_refined.py and
canon_aux_refined_specials.py; all authored in-repository, no external art or blur. Existing recipes,
registry names and authored four-language tooltips are retained.
Run with the project Pillow environment. Repeat runs produce byte-identical assets.
"""
import json
import re
from pathlib import Path
from canon_aux_pixel_art import (mental_remote, dark_matter_crystal, nitrogen_canister,
    aero_pressure_chip, water_flask, buoyancy_float, origami_paper)
from canon_aux_refined import build_refined_assets
ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
RECIPES = ROOT / "data/academy/recipe"
LANG = ROOT / "assets/academy/lang"


ITEMS = {
    "mental_remote": (mental_remote, 1, {
        "en": ("Mind Remote", "Hold it: the four scan skills reach 24 blocks instead of 16, and scanned enemies get a gold star mark."),
        "zh_cn": ("心理遥控器", "持在手上：四个扫描技能的距离从16格增至24格，被扫描的敌人头顶出现金色星标。"),
        "zh_tw": ("心理遙控器", "持在手上：四個掃描技能的距離從16格增至24格，被掃描的敵人頭頂出現金色星標。"),
        "ja": ("精神リモコン", "手に持つと、4つの探査スキルの距離が16から24ブロックに伸び、探査された敵に金の星印が付く。")}),
    "dark_matter_crystal": (dark_matter_crystal, 16, {
        "en": ("Dark Matter Crystal", "Fuel for Dark Synthesis (one crystal duplicates one item). Also carried: Dark Wings and Dark Matter guards and Wing Shelter last 50% longer, one crystal per cast."),
        "zh_cn": ("未元物质结晶", "未元合成的燃料（1个结晶复制1个物品）。携带时：未元之翼、未元护片类屏障与翼幕护卫的持续时间延长50%，每次施放消耗1个。"),
        "zh_tw": ("未元物質結晶", "未元合成的燃料（1個結晶複製1個物品）。攜帶時：未元之翼、未元護片類屏障與翼幕護衛的持續時間延長50%，每次施放消耗1個。"),
        "ja": ("未元物質の結晶", "未元合成の燃料（結晶1個でアイテム1つを複製）。所持中は未元の翼・未元の防御・翼幕護衛の持続が50%延びる。1回につき1個消費。")}),
    "nitrogen_canister": (nitrogen_canister, 16, {
        "en": ("Nitrogen Canister", "Carry it: Nitrogen Dash and Nitrogen Rocket push 25% harder. One canister per cast."),
        "zh_cn": ("氮气罐", "携带：氮气冲刺与氮气火箭的位移增强25%。每次施放消耗1个。"),
        "zh_tw": ("氮氣罐", "攜帶：氮氣衝刺與氮氣火箭的位移增強25%。每次施放消耗1個。"),
        "ja": ("窒素ボンベ", "所持すると、窒素ダッシュと窒素ロケットの推進が25%強まる。1回につき1個消費。")}),
    "aero_pressure_chip": (aero_pressure_chip, 16, {
        "en": ("Air Pressure Chip", "Carry it: Ring Push hits one more target, and Aero pressure walls stop one more arrow. One chip when the extra is used."),
        "zh_cn": ("气压片", "携带：环形推击多影响1个目标，空力压力墙多拦截1支箭。额外效果生效时消耗1个。"),
        "zh_tw": ("氣壓片", "攜帶：環形推擊多影響1個目標，空力壓力牆多攔截1支箭。額外效果生效時消耗1個。"),
        "ja": ("気圧チップ", "所持すると、リングプッシュの対象が1つ増え、気圧の壁が矢を1本多く止める。追加分が効いた時に1個消費。")}),
    "water_flask": (water_flask, 16, {
        "en": ("Water Flask", "Four charges. Use on a block to place a real water source; use on water to refill. Hydro Hand then works anywhere."),
        "zh_cn": ("水之瓶", "4格水量。对方块使用会放置真实水源，对水使用补满。放下后水流操作即可在任何地方使用。"),
        "zh_tw": ("水之瓶", "4格水量。對方塊使用會放置真實水源，對水使用補滿。放下後水流操作即可在任何地方使用。"),
        "ja": ("水の小瓶", "4回分。ブロックに使うと本物の水源を置き、水に使うと補充する。置けば水流操作をどこでも使える。")}),
    "buoyancy_float": (buoyancy_float, 16, {
        "en": ("Buoyancy Float", "Right-click a block to set a marker; sneak and use in the air to clear. Buoyancy fields then pick enemies and items nearest the marker."),
        "zh_cn": ("浮力浮标", "右键方块设置标记，潜行在空中使用清除。浮力场会优先选取离标记最近的敌人和物品。"),
        "zh_tw": ("浮力浮標", "右鍵方塊設置標記，潛行在空中使用清除。浮力場會優先選取離標記最近的敵人和物品。"),
        "ja": ("浮力ブイ", "ブロックを右クリックで目印を設置、スニーク中に空中で使うと解除。浮力フィールドは目印に近い敵とアイテムを優先して選ぶ。")}),
    "origami_paper": (origami_paper, 64, {
        "en": ("Origami Paper", "Treated sheets for Paper Psychokinesis. Counts as one sheet for paper skills."),
        "zh_cn": ("折纸", "纸媒念动使用的处理纸。纸系技能中按一张纸计。"),
        "zh_tw": ("摺紙", "紙媒念動使用的處理紙。紙系技能中按一張紙計。"),
        "ja": ("折り紙", "紙媒念動のための加工紙。紙系スキルでは紙一枚として扱う。")}),
}

RECIPE_DEFS = {
    "mental_remote": {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" N ", "IRI", "IDI"],
                      "key": {"N": "minecraft:gold_nugget", "I": "minecraft:iron_ingot", "R": "minecraft:redstone", "D": "minecraft:amethyst_shard"}, "count": 1},
    "dark_matter_crystal": {"type": "minecraft:crafting_shapeless", "category": "misc",
                            "ingredients": ["minecraft:amethyst_shard", "minecraft:quartz", "minecraft:ender_pearl", "minecraft:glowstone_dust"], "count": 1},
    "nitrogen_canister": {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" G ", "IPI", " I "],
                          "key": {"G": "minecraft:glass_pane", "I": "minecraft:iron_ingot", "P": "minecraft:packed_ice"}, "count": 1},
    "aero_pressure_chip": {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" F ", "CRC", " F "],
                           "key": {"F": "minecraft:feather", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"}, "count": 2},
    "water_flask": {"type": "minecraft:crafting_shapeless", "category": "misc",
                    "ingredients": ["minecraft:glass_bottle", "minecraft:prismarine_crystals", "minecraft:kelp"], "count": 1},
    "buoyancy_float": {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" P ", "PSP", " P "],
                       "key": {"P": "minecraft:prismarine_shard", "S": "minecraft:slime_ball"}, "count": 1},
    "origami_paper": {"type": "minecraft:crafting_shapeless", "category": "misc",
                      "ingredients": ["minecraft:paper", "minecraft:paper", "minecraft:paper", "minecraft:paper", "minecraft:red_dye"], "count": 4},
}


def write_recipe(name, r):
    out = {"type": r["type"], "category": r["category"]}
    if "pattern" in r:
        out["pattern"] = r["pattern"]; out["key"] = {k: {"item": v} for k, v in r["key"].items()}
    else:
        out["ingredients"] = [{"item": i} for i in r["ingredients"]]
    out["result"] = {"id": f"academy:{name}", "count": r["count"]}
    (RECIPES / f"{name}.json").write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")


EXTRA = {
    "en_us": {"ac.canon.float_marker.set": "Float marker set at %s, %s, %s", "ac.canon.float_marker.cleared": "Float marker cleared"},
    "zh_cn": {"ac.canon.float_marker.set": "浮标已设置在 %s, %s, %s", "ac.canon.float_marker.cleared": "浮标已清除"},
    "zh_tw": {"ac.canon.float_marker.set": "浮標已設置在 %s, %s, %s", "ac.canon.float_marker.cleared": "浮標已清除"},
    "ja_jp": {"ac.canon.float_marker.set": "目印を %s, %s, %s に設置した", "ac.canon.float_marker.cleared": "目印を解除した"},
}


def add_lang(code, entries):
    path = LANG / f"{code}.json"
    text = path.read_text(encoding="utf-8")
    add = {}
    for name, (_, _, names) in ITEMS.items():
        title, tip = names[{"zh_cn": "zh_cn", "zh_tw": "zh_tw", "ja_jp": "ja", "en_us": "en"}[code]]
        add[f"item.academy.{name}"] = title; add[f"item.academy.{name}.desc"] = tip
    extra = EXTRA[code]
    add.update(extra)
    for k, v in add.items():  # refresh values that already exist
        pat = re.compile(r'(  "%s": )"(?:[^"\\]|\\.)*"' % re.escape(k))
        text, n = pat.subn(lambda m: m.group(1) + json.dumps(v, ensure_ascii=False), text, count=1)
    path.write_text(text, encoding="utf-8")
    missing = {k: v for k, v in add.items() if f'"{k}"' not in text}
    if not missing: return 0
    body = ",\n".join(f"  {json.dumps(k, ensure_ascii=False)}: {json.dumps(v, ensure_ascii=False)}" for k, v in missing.items())
    idx = text.rstrip().rfind("}")
    head = text[:idx].rstrip()
    path.write_text(head + ",\n" + body + "\n}\n", encoding="utf-8")
    json.loads(path.read_text(encoding="utf-8"))  # must stay valid JSON
    return len(missing)


if __name__ == "__main__":
    for name, (fn, _, _) in ITEMS.items():
        fn()
        write_recipe(name, RECIPE_DEFS[name])
    print("models:", build_refined_assets())
    print("lang added:", {c: add_lang(c, None) for c in ("en_us", "zh_cn", "zh_tw", "ja_jp")})
