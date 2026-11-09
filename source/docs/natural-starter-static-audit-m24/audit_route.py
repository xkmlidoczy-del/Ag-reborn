#!/usr/bin/env python3
"""Read-only batch accounting from checked-in production JSON; no Minecraft run."""
from collections import Counter
from hashlib import sha256
from math import ceil
from pathlib import Path
from zipfile import ZipFile
import json

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
RECIPES = ROOT / 'src/main/resources/data/academy/recipe/classic'
CACHE = ROOT / '.gradle-user/caches/neoformruntime/intermediate_results'
VANILLA = next(CACHE.glob('stripServer*resourcesOutput.jar'))
NEOFORGE = next(CACHE.glob('sourcesAndCompiledWithNeoForge*output.jar'))
TAGS = {'c:dusts/redstone': 'minecraft:redstone',
        'c:dusts/glowstone': 'minecraft:glowstone_dust',
        'c:ingots/iron': 'minecraft:iron_ingot',
        'c:nuggets/gold': 'minecraft:gold_nugget',
        'c:plates/iron': 'academy:reinforced_iron_plate'}

PRODUCERS = {
    'academy:solar_gen': 'solar_gen_09',
    'academy:portable_developer': 'portable_developer_42',
    'academy:imag_silicon_piece': 'imag_silicon_piece_01',
    'academy:wafer': 'wafer_47',
    'academy:imag_silicon_ingot': 'imag_silicon_ingot_31',
    'academy:reinforced_iron_plate': 'reinforced_iron_plate_06',
    'academy:machine_frame': 'machine_frame_07',
    'academy:energy_unit': 'energy_unit_18',
    'academy:constraint_plate': 'constraint_plate_21',
    'academy:constraint_ingot': 'constraint_ingot_32',
    'academy:brain_component': 'brain_component_35',
    'academy:info_component': 'info_component_34',
    'academy:energy_convert_component': 'energy_convert_component_37',
    'minecraft:iron_ingot': 'iron_ingot_from_smelting_raw_iron',
    'minecraft:gold_ingot': 'gold_ingot_from_smelting_raw_gold',
    'minecraft:gold_nugget': 'gold_nugget',
    'minecraft:glass': 'glass',
    'minecraft:glass_pane': 'glass_pane',
}

manifest = {}
def read_file(path):
    content = (ROOT / path).read_bytes()
    manifest[path] = sha256(content).hexdigest()
    return content

def read_zip(path, name):
    with ZipFile(path) as z:
        content = z.read(name)
    manifest[str(path.relative_to(ROOT)) + '!' + name] = sha256(content).hexdigest()
    return content

def recipe(name):
    if (RECIPES / (name + '.json')).exists():
        path = str((RECIPES / (name + '.json')).relative_to(ROOT))
        return json.loads(read_file(path)), path
    path = 'data/minecraft/recipe/' + name + '.json'
    return json.loads(read_zip(VANILLA, path)), str(VANILLA.relative_to(ROOT)) + '!' + path

def ingredient(value):
    if 'item' in value:
        return value['item']
    if value.get('tag') == 'minecraft:smelts_to_glass':
        return 'minecraft:sand'
    return TAGS[value['tag']]

def inputs(value):
    if 'pattern' in value:
        return [ingredient(value['key'][c]) for row in value['pattern'] for c in row if c != ' ']
    if 'ingredients' in value:
        return [ingredient(v) for v in value['ingredients']]
    return [ingredient(value['ingredient'])]

def expand(silicon_chips, quartz_calcs):
    leaves, spare, crafts = Counter(), Counter(), Counter()
    steps = []
    used_si = used_quartz = 0
    def need(item, amount):
        nonlocal used_si, used_quartz
        take = min(amount, spare[item])
        spare[item] -= take
        amount -= take
        if not amount:
            return
        if item == 'academy:data_chip':
            # Outputs are singles; choosing a source variant never changes redstone demand.
            for _ in range(amount):
                use_si = used_si < silicon_chips
                used_si += use_si
                build(item, 1, 'data_chip_03' if use_si else 'data_chip_02')
        elif item == 'academy:calc_chip':
            for _ in range(amount):
                use_quartz = used_quartz < quartz_calcs
                used_quartz += use_quartz
                build(item, 1, 'calc_chip_04' if use_quartz else 'calc_chip_05')
        elif item in PRODUCERS:
            build(item, amount, PRODUCERS[item])
        else:
            leaves[item] += amount
    def build(item, amount, name):
        value, path = recipe(name)
        result = value['result']
        result = {'id': result, 'count': 1} if isinstance(result, str) else result
        assert result['id'] == item, (name, result, item)
        count = result.get('count', 1)
        times = ceil(amount / count)
        required = Counter(inputs(value))
        for other, per_craft in required.items():
            need(other, per_craft * times)
        spare[item] += count * times - amount
        crafts[name] += times
        steps.append({'recipe': name, 'path': path, 'crafts': times,
                      'result': item, 'output_count_per_craft': count,
                      'inputs_per_craft': dict(required)})
    need('academy:portable_developer', 1)
    need('academy:solar_gen', 1)
    assert sum(crafts[x] for x in ['data_chip_02', 'data_chip_03']) == 10
    assert sum(crafts[x] for x in ['calc_chip_04', 'calc_chip_05']) == 5
    assert crafts['energy_unit_18'] == 3
    expected = {'academy:constraint_metal_ore': 18, 'academy:crystal_low': 4,
                'academy:imag_silicon_ore': 1 + ceil(silicon_chips / 2),
                'academy:reso_crystal': 8 - quartz_calcs,
                'minecraft:glowstone_dust': 1, 'minecraft:raw_gold': 1,
                'minecraft:raw_iron': 3 * ceil((14 - silicon_chips) / 2),
                'minecraft:redstone': 33, 'minecraft:sand': 6}
    if quartz_calcs:
        expected['minecraft:quartz'] = quartz_calcs * 2
    assert dict(leaves) == expected, (silicon_chips, quartz_calcs, dict(leaves), expected)
    smelts = sum(crafts[x] for x in ['constraint_ingot_32', 'imag_silicon_ingot_31',
                                    'iron_ingot_from_smelting_raw_iron',
                                    'gold_ingot_from_smelting_raw_gold', 'glass'])
    # Replay dependencies-first ledger, using exactly these leaves. This verifies batch leftovers.
    ledger = Counter(leaves)
    for step in steps:
        for other, count in step['inputs_per_craft'].items():
            assert ledger[other] >= count * step['crafts'], (step['recipe'], other)
            ledger[other] -= count * step['crafts']
        ledger[step['result']] += step['output_count_per_craft'] * step['crafts']
    ledger['academy:portable_developer'] -= 1
    ledger['academy:solar_gen'] -= 1
    assert {k:v for k,v in ledger.items() if v} == {k:v for k,v in spare.items() if v}
    return {'silicon_data_chips': silicon_chips, 'quartz_calculation_chips': quartz_calcs,
            'device_primitive_leaves': dict(sorted(leaves.items())),
            'leftovers': {k:v for k,v in sorted(spare.items()) if v},
            'recipe_crafts': dict(sorted(crafts.items())), 'furnace_smelts_devices': smelts,
            'coal_fuel_devices_plus_one_iron_pickaxe_ideal_continuous': ceil((smelts+3)/8),
            'furnace_smelts_plus_one_iron_pickaxe': smelts+3, 'steps': steps}

routes = {f'silicon_{si}_quartz_{q}': expand(si, q) for si in range(0, 11, 2) for q in range(6)}
old = json.loads(read_file('docs/runtime-evidence/m13-natural-starter-static-audit.json'))
assert routes['silicon_0_quartz_0']['device_primitive_leaves'] == old['device_material_leaves_excluding_tools_fuel_food_portal_and_factor']
read_file('docs/m14-pristine-natural-partial.md')
for path in [
    '.reference/AcademyCraft-1.0.7/src/main/resources/assets/academy/recipes/default.recipe',
    '.reference/AcademyCraft-1.0.7/src/main/java/cn/academy/crafting/ModuleCrafting.java',
    '.reference/AcademyCraft-1.0.7/src/main/java/cn/academy/crafting/block/BlockGenericOre.java',
    '.reference/AcademyCraft-1.0.7/src/main/java/cn/academy/crafting/world/ACWorldGen.java',
    '.reference/AcademyCraft-1.0.7/src/main/java/cn/academy/crafting/world/CustomWorldGen.java',
    '.reference/AcademyCraft-1.0.7/src/main/java/cn/academy/ability/develop/action/DevelopActionLevel.java',
    '.reference/AcademyCraft-1.0.7/src/main/java/cn/academy/ability/develop/LearningHelper.java',
    '.reference/AcademyCraft-1.0.7/src/main/java/cn/academy/core/block/TileGeneratorBase.java',
    '.reference/AcademyCraft-1.0.7/src/main/java/cn/academy/energy/block/TileSolarGen.java',
    '.reference/AcademyCraft-1.0.7/src/main/scala/cn/academy/vanilla/electromaster/skill/ArcGen.scala',
    '.reference/AcademyCraft-1.0.7/src/main/scala/cn/academy/vanilla/meltdowner/skill/ElectronBomb.scala',
    '.reference/AcademyCraft-1.0.7/src/main/scala/cn/academy/vanilla/teleporter/skill/ThreateningTeleport.scala',
    '.reference/AcademyCraft-1.0.7/src/main/scala/cn/academy/vanilla/vecmanip/skill/DirectedShock.scala',
    '.reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/util/generic/RandUtils.java',
    '.reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/crafting/RecipeRegistry.java',
    '.reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/crafting/CustomMappingHelper.java',
    'src/main/java/cn/academy/port/survival/ClassicOreRules.java',
    'src/main/java/cn/academy/port/survival/ClassicMaterials.java',
    'src/main/java/cn/academy/port/survival/ClassicFactorLoot.java',
    'src/main/java/cn/academy/port/survival/ClassicOverworldPlacement.java',
    'src/main/java/cn/academy/port/survival/ClassicWorldgenConfig.java',
    'src/main/java/cn/academy/port/develop/DevelopmentActions.java',
    'src/main/java/cn/academy/port/develop/DeveloperType.java',
    'src/main/java/cn/academy/port/develop/DevelopmentProcess.java',
    'src/main/java/cn/academy/port/develop/DevelopmentController.java',
    'src/main/java/cn/academy/port/AcademyGameplay.java',
    'src/main/java/cn/academy/port/solar/ClassicSolarRules.java',
    'src/main/java/cn/academy/port/solar/ClassicSolarBuffer.java',
    'src/main/java/cn/academy/port/solar/ClassicSolarBlockEntity.java',
    'src/main/java/cn/academy/port/energy/ClassicEnergyUnitRecipe.java',
    'src/main/java/cn/academy/port/SkillAvailability.java',
    'src/main/java/cn/academy/port/preset/PresetSkills.java',
    'src/main/resources/classic-skills.json',
    'src/main/resources/data/minecraft/tags/block/needs_iron_tool.json',
    'src/main/resources/data/minecraft/tags/block/needs_stone_tool.json',
    'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json',
    'src/main/resources/data/c/tags/item/plates/iron.json',
    'src/main/resources/data/academy/neoforge/biome_modifier/classic_ores.json',
]:
    read_file(path)
for group in ['loot_table/blocks', 'worldgen/configured_feature', 'worldgen/placed_feature']:
    for ore in ['reso_crystal_ore', 'constraint_metal_ore', 'crystal_ore', 'imag_silicon_ore']:
        read_file(f'src/main/resources/data/academy/{group}/{ore}.json')
for name in ['chests/bastion_hoglin_stable', 'entities/witch', 'blocks/glowstone', 'blocks/redstone_ore', 'blocks/iron_ore',
             'blocks/gold_ore', 'blocks/coal_ore', 'blocks/nether_quartz_ore', 'blocks/gravel']:
    read_zip(VANILLA, 'data/minecraft/loot_table/' + name + '.json')
for name in ['wooden_pickaxe', 'stone_pickaxe', 'iron_pickaxe', 'bucket', 'flint_and_steel',
             'stick', 'furnace', 'crafting_table', 'spruce_planks']:
    read_zip(VANILLA, 'data/minecraft/recipe/' + name + '.json')
for name in ['incorrect_for_stone_tool', 'incorrect_for_iron_tool', 'needs_iron_tool', 'needs_stone_tool']:
    read_zip(VANILLA, 'data/minecraft/tags/block/' + name + '.json')
for name in ['dusts/glowstone', 'dusts/redstone', 'ingots/iron', 'nuggets/gold']:
    read_zip(NEOFORGE, 'data/c/tags/item/' + name + '.json')
for name in ['world/item/Tiers', 'world/item/Tier', 'world/level/block/entity/AbstractFurnaceBlockEntity',
             'world/level/block/Blocks', 'world/entity/npc/VillagerTrades']:
    read_zip(NEOFORGE, 'net/minecraft/' + name + '.java')

skills = json.loads((ROOT / 'src/main/resources/classic-skills.json').read_text())['skills']
roots = [s for s in skills if s['level'] == 1 and not s['requirements'] and not s['generic']]
assert {s['id'] for s in roots} == {'arc_gen','electron_bomb','threatening_teleport','dir_shock'}
output = {
    'scope': 'Static source/current production audit and Python ingredient accounting only. No Gradle/client/server/CUA run; no claim of physical acquisition, crafting, generated-world success or new-JVM validation.',
    'baseline': 'm14 proves only three naturally collected dirt. m22 proves supplied-device solar/development, not natural materials. m23 is outside this audit.',
    'preferred_bounded_recipe_route': 'silicon_10_quartz_0; adapt silicon/quartz recipe counts to actually found native resources',
    'routes': routes,
    'random_category_roots': [{k:s[k] for k in ['category','id','minimum_developer','learning_stimulations','source']} for s in roots],
    'energy': {'solar_clear_IF_per_tick':3, 'solar_rain_IF_per_tick':0.6,
               'portable_capacity_IF':10000, 'acquisition_ticks':130, 'acquisition_IF':3900,
               'root_learning_ticks':78, 'root_learning_IF':2340, 'total_IF':6240,
               'minimum_clear_generation_ticks_empty_solar_and_portable':2080,
               'minimum_clear_generation_seconds_at_20TPS':104,
               'full_portable_clear_generation_ticks':3334},
    'source_hashes_sha256': dict(sorted(manifest.items())),
    'verification': {'route_variants_asserted_and_dependency_ledgers_replayed':len(routes),
                     'm13_iron_route_leaves_reproduced':True,
                     'production_mutations':False, 'runtime_invocations':False},
}
(OUT / 'natural-starter-static-audit.json').write_text(json.dumps(output, indent=2)+'\n')
print(json.dumps({'verified_route_variants':len(routes),
                  'preferred_leaves':routes['silicon_10_quartz_0']['device_primitive_leaves'],
                  'preferred_furnace_smelts_plus_iron_pickaxe':routes['silicon_10_quartz_0']['furnace_smelts_plus_one_iron_pickaxe'],
                  'source_files_hashed':len(manifest)}, indent=2))
