#!/usr/bin/env python3
"""Launch city checks in new local QA directories and preserve per-run evidence."""
import argparse
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

SOURCE = Path(__file__).resolve().parents[1]
ROOT = SOURCE.parent
BATCH = ROOT / "tmp/city-prototype/batch"
TEMP = ROOT / "tmp/story-city-20261104/closeout"


def launch(name, steps, output, *, shaders=False, templates=()):
    TEMP.mkdir(parents=True, exist_ok=True)
    directory = Path(tempfile.mkdtemp(prefix=name + "-", dir=TEMP))
    (directory / "options.txt").write_text(
        "version:3955\nonboardAccessibility:false\npauseOnLostFocus:false\nlang:zh_cn\n"
        "tutorialStep:none\nsoundCategory_master:0.0\nrenderDistance:12\nsimulationDistance:5\n"
        "enableVsync:false\nmaxFps:60\nautoJump:false\n"
    )
    if shaders:
        configured = SOURCE / "run-shader"
        shutil.copytree(configured / "shaderpacks", directory / "shaderpacks")
        (directory / "mods").mkdir()
        (directory / "config").mkdir()
        for pattern in ("iris-*.jar", "sodium-*.jar"):
            for mod in (configured / "mods").glob(pattern):
                shutil.copy2(mod, directory / "mods")
        shutil.copy2(configured / "config/iris.properties", directory / "config")
    if templates:
        fixture = directory / "city_templates"
        fixture.mkdir()
        for template in templates:
            shutil.copy2(template, fixture)
    cmd = [sys.executable, str(SOURCE / "qa/run-prepared-client.py"), name,
           "--directory", str(directory), "--out", str(output), "--script", "|".join(steps)]
    print(f"{name} QA directory: {directory}", flush=True)
    subprocess.run(cmd, check=True)


def train_steps():
    return [
        "cmd:gamemode survival",
        "cmd:execute in academy:story_city_map run tp @s 0.5 25 395.5 180 20", "wait:200",
        "gui:on", "cmd:aca_city map", "shot:train_map", "clearchat",
        "cmd:academy_train arrive 2 7", "cityqa:phase,APPROACHING,2",
        "rot:85,3", "shot:train_approaching", "cityqa:phase,BOARDING,2",
        "aim:0.5,25.5,393.5", "shot:train_boarding_panel", "use", "wait:30",
        "cityqa:train,boarded", "vcamrel:0,1.64,-1.1,-7,1.64,-1.1",
        "shot:train_cabin", "vcam:off", "cityqa:phase,CLOSING,2",
        "vcam:13,28,391,0,27,400", "shot:train_departure_warning", "vcam:off", "wait:115",
        "cityqa:train,moving", "keydown:340", "wait:16", "keyup:340", "cityqa:train,moving",
        "rot:0,0", "shot:train_cabin_moving", "cityqa:next_stop,3",
        "keydown:340", "wait:16", "keyup:340", "wait:20", "cityqa:train,exit",
        "rot:-42,0", "shot:train_east_city_exit",
    ]


def full_train_steps(east_first):
    stations = (-1800, -1000, 0, 1000, 1800)
    stops = (0, 1, 2, 3, 4, 3) if east_first else (4, 3, 2, 1, 0, 1)
    name = "east_north" if east_first else "west_south"
    player_z, panel_z, yaw = (395.5, 393.5, 180) if east_first else (404.5, 407.5, 0)
    first = stops[0]
    steps = [
        "cmd:gamerule doMobSpawning false", "cmd:gamerule doDaylightCycle false",
        "cmd:time set 6000", "cmd:weather clear", "cmd:gamemode survival",
        f"cmd:execute in academy:story_city_map run tp @s {stations[first]+0.5} 25 {player_z} {yaw} 20",
        "wait:220", f"cmd:academy_train arrive {first} 7", f"cityqa:phase,BOARDING,{first}",
        f"aim:{stations[first]+0.5},25.5,{panel_z}", "use", "wait:30", "cityqa:train,boarded",
    ]
    for index, (start, end) in enumerate(zip(stops, stops[1:])):
        steps += [f"cityqa:phase,DEPARTING,{start}", "wait:35", "cityqa:train,moving"]
        if index == 0:
            steps += ["keydown:340", "wait:16", "keyup:340", "cityqa:train,moving"]
        steps += [f"cityqa:next_stop,{end}", "keydown:340", "wait:16", "keyup:340", "wait:20",
                  "cityqa:train,exit", f"mark:{name}_{start}_to_{end}", f"shot:{name}_station_{end}_exit_{index}"]
        if index < len(stops) - 2:
            steps += [f"aim:{stations[end]+0.5},25.5,{panel_z}", "use", "wait:30", "cityqa:train,boarded"]
    return steps


def street_scene_steps():
    return [
        "cmd:gamerule doMobSpawning false", "cmd:gamerule doDaylightCycle false", "cmd:weather clear",
        "cmd:time set 6000", "cmd:gamemode spectator",
        "cmd:execute in academy:story_city_map run tp @s -80 23 -4 0 0", "wait:300",
        "cityqa:wind,-80,24,20", "gui:off", "shot:wind_d7_first", "wait:30", "shot:wind_d7_second",
        "cmd:tp @s -65 2 -15 0 -20", "wait:160", "shot:d7_street",
        "cmd:tp @s -65 2 130 0 -18", "wait:160", "shot:d7_boulevard",
        "cmd:tp @s -98 6 380 -45 -10", "wait:180", "shot:central_station_structure",
        "cmd:tp @s -65 2 530 0 -15", "wait:180", "shot:d15_plaza",
        "cmd:tp @s -804 45 -1620 0 0", "wait:300", "cityqa:wind,-804,46,-1596",
        "shot:d22_wind_farm", "wait:20",
    ]


def street_scene_iris_steps():
    return [
        "cmd:gamerule doMobSpawning false", "cmd:gamerule doDaylightCycle false", "cmd:weather clear",
        "cmd:time set 18000", "cmd:gamemode spectator",
        "cmd:execute in academy:story_city_map run tp @s -65 2 -15 0 -20", "wait:280",
        "cityqa:wind,-80,24,20", "cityqa:lamp,-70,4,22,2", "gui:off", "shot:d7_street_iris_night",
        "cmd:tp @s -65 2 530 0 -15", "wait:220", "cityqa:wind,-88,24,552", "shot:d15_plaza_iris_night",
        "cmd:time set 6000", "cmd:tp @s -804 45 -1620 0 0", "wait:300",
        "cityqa:wind,-804,46,-1596", "shot:d22_iris_day", "wait:20",
    ]


def scenery():
    return [
        "cmd:gamerule doDaylightCycle false", "cmd:gamerule doMobSpawning false",
        "cmd:time set 6000", "cmd:weather clear", "cmd:gamemode spectator",
        "cmd:execute in academy:story_city_map run tp @s -1598 116 -919 18 36",
        "wait:200", "gui:off", "shot:d21_ridge_and_reservoir",
        "cmd:tp @s -1628 25 -790 103 -12", "wait:140", "shot:d21_shallow_shore",
    ]


def route_steps():
    steps = [
        "cmd:gamerule doDaylightCycle false", "cmd:gamerule doMobSpawning false",
        "cmd:time set 6000", "cmd:weather clear", "cmd:gamemode survival",
        "cmd:aca_city route", "wait:180",
        "cityqa:route,campus,-64.5,0,-68.5", "shot:d7_campus_interior",
    ]
    for z in range(-38, 383, 30):
        steps.append(f"walk:-64.5,{z + 0.5}")
        if z == -8:
            steps += ["rot:180,-2", "shot:d7_campus_gate"]
    steps += [
        "walk:-64.5,384.5", "walk:-75.5,384.5", "walk:-75.5,393.5",
        "walk:-42.5,393.5", "cityqa:route,station,-42.5,31,393.5",
        "rot:-45,6", "shot:central_station_crossing",
        "walk:-42.5,407.5", "walk:-75.5,407.5", "walk:-75.5,421.5",
        "walk:-64.5,421.5", "cityqa:route,south_exit,-64.5,0,421.5",
        "rot:180,-8", "shot:central_station_south_exit",
    ]
    for z in (451.5, 481.5, 511.5, 541.5, 566.5):
        steps.append(f"walk:-64.5,{z}")
    steps += ["cityqa:route,d15,-64.5,0,566.5", "rot:12,-4", "shot:d15_welcome_plaza"]
    return steps


def npc_steps():
    def summon(look, activity, x, y, z, yaw):
        return (f'cmd:summon academy:city_npc {x} {y} {z} '
                f'{{Look:"{look}",Activity:"{activity}",Rotation:[{yaw}.0f,0.0f]}}')

    return [
        "cmd:gamerule doDaylightCycle false", "cmd:gamerule doMobSpawning false",
        "cmd:time set 6000", "cmd:weather clear", "cmd:gamemode spectator", "gui:off",
        "cmd:execute in academy:story_city_map run tp @s -65 0.25 -15.7", "wait:175",
        summon("student_bag", "stand", -67.6, 0, -22.2, 0),
        summon("student_sport", "stand", -64.9, 0, -22.4, 0),
        summon("student_books", "read", -62.4, 0, -22.1, 0),
        "cityqa:npc_looks,student_bag:stand,student_sport:stand,student_books:read",
        "aim:-65,1,-22.2", "shot:d7_students",
        "cmd:tp @s -65.1 0.25 -5.2", "aim:-65,1,-22.2", "shot:d7_at_distance",
        "cmd:tp @s -61 0.15 -18.6", "aim:-62.4,1,-22.1", "shot:d7_reading",
        "cmd:tp @s -64.5 0.25 -27.3", "aim:-65.2,0.95,-22.3", "shot:d7_backs",
        "cmd:tp @s -67.2 0.18 -11.0", "aim:-64.9,0.95,-15",
        "cityqa:npc_walk,student_sport,-64.9,0,-7",
        "cityqa:npc_moving,student_sport", "shot:d7_walking",
        "cmd:tp @s -83.4 0.20 556.5", "wait:175",
        "cmd:setblock -84 0 562 minecraft:spruce_slab[type=bottom]",
        "cmd:setblock -83 0 562 minecraft:spruce_slab[type=bottom]",
        summon("shop_clerk", "stand", -86.3, 0, 563.1, 180),
        summon("visitor", "sit", -83.5, 0.5, 562.5, 180),
        summon("commuter", "phone", -80.6, 0, 562.8, 180),
        "cityqa:npc_looks,shop_clerk:stand,visitor:sit,commuter:phone",
        "aim:-83.4,0.95,562.8", "shot:d15_daily_life",
        "cmd:tp @s -81.9 0.16 559.7", "aim:-80.6,0.94,562.8", "shot:d15_phone",
        "cmd:tp @s -84.8 0.05 559.8", "aim:-83.5,0.86,562.5", "shot:d15_bench",
        "cmd:tp @s -81.3 0.05 561.7", "aim:-83.5,0.82,562.4", "shot:d15_bench_side",
    ]


def crowd_steps():
    return [
        "cmd:gamerule doDaylightCycle false", "cmd:gamerule doMobSpawning true",
        "cmd:difficulty peaceful", "cmd:time set 1000", "cmd:weather clear",
        "cmd:gamemode spectator", "gui:off",
        "cmd:execute in academy:story_city_map run tp @s -65.4 4 -11.0", "wait:250",
        "cityqa:crowd,d7_morning,7,9,32", "aim:-65,0,-32", "shot:d7_morning",
        "wait:100", "cityqa:crowd,d7_later,7,9,32",
        "cmd:time set 6000", "cmd:tp @s -64.5 4.5 553.7", "wait:240",
        "cityqa:crowd,d15_day,15,9,32", "aim:-66,0,574", "shot:d15_day",
        "cmd:time set 1000", "cmd:tp @s 806.5 5 6.5", "wait:230",
        "cityqa:crowd,d6_morning,6,2,25",
        "cmd:time set 15000", "wait:220",
        "cityqa:crowd,d6_night,6,5,32", "cityqa:crowd_more,d6_morning,d6_night",
        "aim:806,0,-21", "shot:d6_night",
        "cmd:time set 6000", "cmd:tp @s -804.5 5 -1585.5", "wait:230",
        "cityqa:crowd,d22_day,22,1,12", "aim:-817,0,-1575", "shot:d22_day",
        "cmd:tp @s 1624.5 5 -1730.5", "wait:230",
        "cityqa:crowd,d23_day,23,2,24", "aim:1615,0,-1767", "shot:d23_day",
        "cmd:gamerule doMobSpawning false", "wait:30", "cityqa:crowd,disabled,23,0,0",
        "cmd:tp @s -83.5 0.22 550.7", "wait:155",
        'cmd:summon academy:city_npc -88.5 0 561.8 {Look:"guard",Activity:"scan",Rotation:[180.0f,0.0f]}',
        'cmd:summon academy:city_npc -86.2 0 561.7 {Look:"vendor",Activity:"tend",Rotation:[180.0f,0.0f]}',
        'cmd:summon academy:city_npc -83.7 0 561.9 {Look:"performer",Activity:"perform",Rotation:[180.0f,0.0f]}',
        'cmd:summon academy:city_npc -81.4 0 561.7 {Look:"researcher",Activity:"check",Rotation:[180.0f,0.0f]}',
        'cmd:summon academy:city_npc -79.1 0 561.7 {Look:"casual_visitor",Activity:"photo",Rotation:[180.0f,0.0f]}',
        'cmd:summon academy:city_npc -77.0 0 561.7 {Look:"resident",Activity:"wave",Expression:"surprised",Rotation:[180.0f,0.0f]}',
        "cityqa:npc_looks,guard:scan,vendor:tend,performer:perform,researcher:check,casual_visitor:photo,resident:wave",
        "aim:-82.8,1.0,561.8", "shot:daily_gestures",
        "cmd:tp @s -82.8 0.18 557.4", "aim:-82.65,1.0,561.8", "shot:performer_and_researcher",
        "cmd:tp @s -77.8 0.12 558.4", "aim:-77.6,1.1,561.7", "shot:surprised_wave",
    ]


def ceiling_steps():
    return [
        "cmd:gamerule doMobSpawning false", "cmd:gamerule doDaylightCycle false",
        "cmd:weather clear", "cmd:time set 6000", "cmd:gamemode survival", "gui:off",
        "cmd:aca_city route", "wait:200", "aim:-64.5,2.55,-52.8",
        "cmd:fill -68 5 -88 -62 5 -51 academy:lit_window replace academy:ceiling_light",
        "wait:75", "shot:d7_original_day",
        "cmd:fill -68 5 -88 -62 5 -51 academy:ceiling_light replace academy:lit_window",
        "wait:80", "cityqa:lamp,-65,5,-70,4", "shot:d7_ceiling_day",
        "cmd:time set 18000", "wait:75", "shot:d7_ceiling_night",
        "cmd:gamemode spectator", "cmd:tp @s -61.8 2.1 -67.3",
        "aim:-64.2,5.81,-69.5", "wait:20", "shot:d7_fixture_close",
        "cmd:tp @s -14.5 25 394.5", "wait:165", "cityqa:lamp,0,33,394,7",
        "aim:5,30.7,397", "shot:station_canopy_night",
    ]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("templates", "scenery", "shader", "route", "transit",
                                          "transit-east-north", "transit-west-south", "street-scene",
                                          "street-scene-iris", "npc", "crowd", "ceiling"))
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--first", type=int, default=0)
    parser.add_argument("--count", type=int, default=40)
    parser.add_argument("--ceiling-shaders", action="store_true")
    options = parser.parse_args()
    if options.phase == "templates":
        files = sorted(BATCH.glob("*.nbt"))
        if len(files) != 40:
            raise RuntimeError(f"expected 40 templates in {BATCH}, got {len(files)}")
        templates = files[options.first:options.first + options.count]
        steps = ["cmd:gamerule doDaylightCycle false", "cmd:gamerule doMobSpawning false",
                 "cmd:time set 6000", "cmd:weather clear", "cmd:gamemode spectator", "gui:off"]
        for i, template in enumerate(templates):
            x, z = (i % 5) * 240, (i // 5) * 240
            steps += [f"cityqa:template,{template.stem},{x},{z}", "wait:35", f"shot:{template.stem}"]
        launch(f"templates-{options.first:02d}-{len(templates)}", steps, options.out, templates=templates)
    elif options.phase == "route":
        launch("walk-route", route_steps(), options.out)
    elif options.phase == "transit":
        launch("city-transit", ["cmd:gamerule doMobSpawning false"] + train_steps(), options.out)
    elif options.phase in ("transit-east-north", "transit-west-south"):
        east_first = options.phase == "transit-east-north"
        launch("east_north" if east_first else "west_south", full_train_steps(east_first), options.out)
    elif options.phase == "street-scene":
        launch("street-scene", street_scene_steps(), options.out)
    elif options.phase == "street-scene-iris":
        launch("street-scene-iris", street_scene_iris_steps(), options.out, shaders=True)
    elif options.phase == "npc":
        launch("city-npc", npc_steps(), options.out)
    elif options.phase == "crowd":
        launch("city-crowd", crowd_steps(), options.out)
    elif options.phase == "ceiling":
        launch("ceiling-iris" if options.ceiling_shaders else "ceiling-day", ceiling_steps(),
               options.out, shaders=options.ceiling_shaders)
    else:
        shaders = options.phase == "shader"
        steps = scenery()
        if shaders:
            steps += ["cmd:time set 18000"]
        launch("city-shaders" if shaders else "city-day", steps + train_steps(), options.out, shaders=shaders)


if __name__ == "__main__":
    main()
