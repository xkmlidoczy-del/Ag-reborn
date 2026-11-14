"""Run the production archive in an isolated NeoForge client with the optional QA jar."""
import argparse
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]


def args_file(entries, path):
    path.write_text("".join('"' + str(v).replace("\\", "\\\\").replace('"', '\\"') + '"\n' for v in entries))


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("run")
    cli.add_argument("--runtime", type=Path, required=True, help="Prepared NeoForge 21.1.252 client runtime")
    cli.add_argument("--java", type=Path, default=Path(os.environ.get("JAVA_HOME", "")) / "bin/java")
    cli.add_argument("--hidden-agent", type=Path, help="Optional local window-hiding test helper")
    cli.add_argument("--continuation", type=Path, help="Reopen a copy of the proving ground from a successful run")
    cli.add_argument("--production-jar", type=Path, required=True, help="The integrated Ag production archive")
    cli.add_argument("--qa-jar", type=Path, required=True, help="The integration QA driver archive")
    cli.add_argument("--showcase-ffmpeg", type=Path, help="Capture driving demonstration with this FFmpeg executable")
    arg = cli.parse_args()
    if not arg.run.replace("-", "").isalnum():
        raise SystemExit("Use a simple run name")
    if arg.showcase_ffmpeg and arg.continuation:
        cli.error("Recording and world continuation require separate runs")
    if arg.showcase_ffmpeg and not os.access(arg.showcase_ffmpeg, os.X_OK):
        cli.error("The specified FFmpeg is not executable")
    instance = ROOT / "../tmp" / arg.run
    instance.mkdir(parents=True)
    instance = instance.resolve()
    mods = instance / "mods"
    mods.mkdir()
    for jar in (arg.production_jar, arg.qa_jar):
        shutil.copy2(jar, mods / jar.name)

    prepared = arg.runtime.resolve() / "build" / "moddev"
    meta = json.loads((prepared / "showcaseClientLaunch.json").read_text())
    excluded = {str(arg.runtime.resolve() / "build/classes/java/main"), str(arg.runtime.resolve() / "build/resources/main")}
    cp = os.pathsep.join(p for p in meta["classpath"].split(os.pathsep) if p not in excluded)
    local_legacy = instance / "legacy.txt"
    local_legacy.write_text("\n".join(p for p in (prepared / "showcaseClientLegacyClasspath.txt").read_text().splitlines()
                                      if p not in excluded) + "\n")
    vm = [v for v in (prepared / "showcaseClientRunVmArgs.txt").read_text().splitlines()
          if v and not v.startswith(("#", "-javaagent:", "-Dacademy.", "-Dneoforge.enableGameTest", "-Xmx", "-Xms"))]
    vm = [("-DlegacyClassPath.file=" + str(local_legacy)) if v.startswith("-DlegacyClassPath.file=")
          else v for v in vm]
    temp = instance / "java"
    temp.mkdir()
    vm += ["-Dacamech.qa=true", "-Djava.io.tmpdir=" + str(temp), "-XX:ActiveProcessorCount=4",
           "-Xmx3000m"]
    if arg.hidden_agent:
        vm += ["-javaagent:" + str(arg.hidden_agent.resolve())]
    if arg.showcase_ffmpeg:
        vm += ["-Dacamech.qa.ffmpeg=" + str(arg.showcase_ffmpeg.resolve())]
    run = shlex.split((prepared / "showcaseClientRunProgramArgs.txt").read_text(), comments=True)

    def option(key, value):
        if key in run:
            run[run.index(key) + 1] = str(value)
        else:
            run.extend([key, str(value)])

    for key, value in {"--gameDir": instance, "--username": "MechPilot", "--uuid": "69b31536-1fea-3930-a885-3e46513c085e",
                       "--accessToken": "0", "--width": 1280, "--height": 800}.items():
        option(key, value)
    if arg.continuation:
        source = arg.continuation.resolve()
        if not source.is_relative_to((ROOT / "../tmp").resolve()):
            raise SystemExit("Only the project's disposable worlds are accepted by the QA runner")
        target = instance / "saves" / "mech-proving-ground"
        shutil.copytree(source, target)
        option("--quickPlaySingleplayer", target.name)
        vm += ["-Dacamech.qa.reopened=true"]
    args_file(vm, instance / "vm.args")
    args_file(run, instance / "run.args")
    (instance / "config").mkdir()
    (instance / "config/fml.toml").write_text("earlyWindowControl=false\n")
    options = [
        "version:3955", "onboardAccessibility:false", "pauseOnLostFocus:false", "lang:zh_cn",
        "tutorialStep:none", "renderDistance:6", "simulationDistance:5", "enableVsync:false", "maxFps:60",
        "soundCategory_music:0.0", "guiScale:2", "autoJump:false",
    ]
    env = os.environ | meta["environment"] | {"TMPDIR": str(temp)}
    if arg.showcase_ffmpeg:
        options += ["soundCategory_master:0.9", "chatVisibility:2", "showAutosaveIndicator:false"]
        audio_config = instance / "alsoft.conf"
        audio_config.write_text("frequency = 48000\nchannels = stereo\nsample-type = short\nperiod_size = 256\n"
                                f"periods = 3\n[wave]\nfile = {instance / 'game.wav'}\n")
        env.update(ALSOFT_DRIVERS="wave", ALSOFT_CONF=str(audio_config), ALSOFT_LOGFILE=str(instance / "openal.log"))
    else:
        options.append("soundCategory_master:0.0")
    (instance / "options.txt").write_text("\n".join(options) + "\n")
    with (instance / "launch.log").open("w") as log:
        command = [str(arg.java.resolve()), "@" + str(instance / "vm.args"), "-cp", cp, "@" + str(instance / "run.args")]
        print(str(instance), flush=True)
        result = subprocess.run(command, cwd=instance, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=180)
    report = instance / "report.json"
    if report.exists():
        print(report.read_text())
    else:
        print((instance / "launch.log").read_text(errors="replace")[-4500:])
    return 1 if result.returncode or not report.exists() or not json.loads(report.read_text()).get("completed") else 0


if __name__ == "__main__":
    sys.exit(main())
