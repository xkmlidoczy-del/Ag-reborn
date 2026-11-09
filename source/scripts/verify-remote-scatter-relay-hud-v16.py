#!/usr/bin/env python3
"""Independent CPU-only .78 -> .79 HUD source-scope companion.

The immutable .78 Git tree is the authority. No refreshed pins, old-runner
edits, source normalization, Gradle, compilation, JVM, server, or client launch.
PASS_SOURCE_SCOPE_ONLY is not an aggregate pass or a HUD behavioral proof.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import sys

sys.dont_write_bytecode = True
BASELINE = "0e7b5a4fe37195d1b383d0d57b324b6988e2c18f"
OLD_VERSION = b"0.1.0-m41-highpower.0.78-prototype"
NEW_VERSION = b"0.1.0-m41-highpower.0.79-prototype"
JAVA = "src/main/java/cn/academy/port/"
EFFECTS = JAVA + "client/RemoteScatterRelayEffects.java"
STATE = JAVA + "client/RemoteScatterRelayState.java"
RECOVERY = JAVA + "client/HighPowerRecoveryHud.java"
CLIENT = JAVA + "client/AcademyClient.java"
VISUAL_TEST = "src/test/java/cn/academy/port/client/RemoteScatterRelayVisualRegressionTest.java"
REGISTRY = JAVA + "highpower/HighPowerSkills.java"
SERVER = JAVA + "highpower/RemoteScatterRelay.java"
RULES = JAVA + "highpower/core/RemoteScatterRelayRules.java"
FIXTURE = "src/test/resources/current-geometry-proof/charging-core/"
V14 = "scripts/verify-charging-m41-core-v14.py"
V15 = "scripts/verify-remote-scatter-relay-feedback-v15.py"
EVIDENCE = "docs/runtime-evidence/remote-scatter-relay-hud-companion-v16.json"
VERSION_ONLY = {
    "src/main/resources/META-INF/neoforge.mods.toml",
    JAVA + "highpower/VanillaCombatContract.java",
    JAVA + "highpower/VanillaMiningContract.java",
    JAVA + "gametest/HighPowerCombatRuntimeTests.java",
}
LANGUAGES = {"src/main/resources/assets/academy/lang/" + locale + ".json"
             for locale in ("en_us", "zh_cn", "zh_tw")}
PREFIX = "academy.highpower.remote_scatter_relay."
NEW_KEYS = ({PREFIX + "brief." + key for key in (
    "prepared", "endpoint", "placing", "clearing", "segment_too_far", "too_close",
    "body_blocked", "no_space", "unloaded", "measuring", "blocked", "refused",
    "degenerate", "miss", "damaged", "absorbed", "ended", "full", "too_far")}
    | {PREFIX + group + "." + key for group in ("help", "help_short")
       for key in ("prepared", "endpoint", "placing", "clearing", "measuring", "route")}
    | {PREFIX + "result." + key for key in ("damaged", "absorbed", "miss", "blocked")}
    | {PREFIX + key for key in ("history_title", "last_result")})
ADDITIONS = {
    JAVA + "client/RemoteScatterRelayHudLayout.java",
    "src/test/java/cn/academy/port/client/RemoteScatterRelayHudLayoutRegressionTest.java",
    "docs/remote-scatter-relay-hud-v79.md",
    "scripts/verify-remote-scatter-relay-hud-v16.py",
    "docs/remote-scatter-relay-hud-companion-v16.md",
    EVIDENCE,
}
BUILD_APPEND = b"""
// NEW253 client-only readable layout; original geometry/resource regressions remain mandatory.
tasks.register('remoteScatterRelayHudLayoutTest', JavaExec) { dependsOn testClasses; classpath=sourceSets.test.runtimeClasspath; mainClass='cn.academy.port.client.RemoteScatterRelayHudLayoutRegressionTest'; enableAssertions=true }
tasks.named('check') { dependsOn 'remoteScatterRelayHudLayoutTest' }
"""
RECOVERY_APPEND = b"""    /** Read-only bounds for the relay panel. Reserve the normal position even while its snapshot is absent. */
    static HighPowerRecoveryHudModel.Box relayReservedBox(int width, int height) {
        return layout(Minecraft.getInstance(), width, height);
    }
"""
RECOVERY_ANCHOR = b"    /** Queried only by the pinned client renderHearts adapter. Never hides absorption, air, armor or vehicle layers. */\n"
CLIENT_ANCHOR = b"            remoteScatterRelayInputs.begin(identity,node.key,down(mc.options.keyShift),remoteScatterRelayDispatchContext(mc));\n"
CLIENT_APPEND = b"            RemoteScatterRelayEffects.clearResultNote();\n"
STATE_START = b"    /** Text only: notify after receipt acceptance and local identity gates, never from prediction or a render pass. */\n"
STATE_END = b"    /** Server refreshes may lower this lifetime; placement, endpoint selection and firing cannot renew it. */\n"
HUD_OLD = b"    /** One unobtrusive receipt-only line below the classic CP bar; it never covers the reticle or claims preview hits. */\n"
HUD_NEW = b"    /** Native-font, wrapped client information; the separate result note never keeps rays or server leases alive. */\n"
RENDER_TAIL = b"    /** Use the proven exact chunk corridor, including corner grazes, before any world geometry is emitted. */\n"
OLD_HUD_ASSERT = b'        check(effects.contains("HighPowerRecoveryHudModel.fittedScale(width, mc.font.width(text), .85f)"),"localized one-line HUD fits the actual GUI width");\n'
NEW_HUD_ASSERT = r'''        check(effects.contains("RemoteScatterRelayHudLayout.fit(")
                && effects.contains("mc.font.split(Component.literal(text), width), mc.font::width")
                && effects.contains("HighPowerRecoveryHud.relayReservedBox(width, height)"),
                "localized HUD wraps at native size around the actual reserved instrument layout");
        check(effects.indexOf("if (!STATE.accept(receipt, local)) return;") < effects.indexOf("RESULT.received(receipt);")
                && effects.contains("if (local) {") && effects.contains("RESULT.tick(mc.isPaused(), localEligible() && !mc.options.hideGui"),
                "result note is wired after authentication and clears when paused or hidden");
        check(effects.contains("void reload(RegisterShadersEvent event) { resultGeneration++; RESULT.clear(); }")
                && effects.contains("terminalStatus = null; statusTicks = 0; RESULT.clear();")
                && effects.contains("hudText(\"last_result\", hudText(\"result.\" + result.key()))"),
                "reload and abort clear the explicitly labelled actual result note");
        check(client.contains("remoteScatterRelayInputs.begin(identity,node.key,down(mc.options.keyShift),remoteScatterRelayDispatchContext(mc));\n            RemoteScatterRelayEffects.clearResultNote();"), "fresh local relay input clears only the previous result before a new receipt");
'''.encode()

# Exact, reviewable hooks into the old receipt/tick lifecycle. Everything else
# before the named HUD methods must reconstruct the original .78 bytes.
EFFECT_HOOKS = (
    (b"import cn.academy.port.SkillCatalog;\n",
     b"import cn.academy.port.SkillCatalog;\nimport cn.academy.port.ClassicHudConfig;\n"),
    (b"import net.neoforged.neoforge.client.event.RenderGuiEvent;\n",
     b"import net.neoforged.neoforge.client.event.RenderGuiEvent;\nimport net.neoforged.neoforge.client.event.RegisterShadersEvent;\n"),
    (b"    private static final RemoteScatterRelayState STATE = new RemoteScatterRelayState();\n",
     b"    private static final RemoteScatterRelayState STATE = new RemoteScatterRelayState();\n"
     b"    private static final RemoteScatterRelayState.ResultNote RESULT = new RemoteScatterRelayState.ResultNote();\n"),
    (b"    public static final class Setup {\n",
     b"    public static final class Setup {\n"
     b"        @SubscribeEvent public static void reload(RegisterShadersEvent event) { resultGeneration++; RESULT.clear(); }\n"),
    (b"    private static volatile long generation;\n",
     b"    private static volatile long generation;\n    private static volatile long resultGeneration;\n"),
    (b"        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation;\n",
     b"        long capturedSession = AcademyClient.inputSessionEpoch(), capturedGeneration = generation, capturedResultGeneration = resultGeneration;\n"),
    (b"            if (local) AcademyClient.remoteScatterRelayAcceptedInput(packet.input(), packet.epoch());\n",
     b"            if (local) {\n"
     b"                AcademyClient.remoteScatterRelayAcceptedInput(packet.input(), packet.epoch());\n"
     b"                if (capturedResultGeneration == resultGeneration) RESULT.received(receipt);\n"
     b"            }\n"),
    (b"    public static void abortLocal(long input, long epoch) {\n",
     b"    /** A fresh accepted local input discards the previous outcome before waiting for its next receipt. */\n"
     b"    public static void clearResultNote() { RESULT.newInput(); }\n"
     b"    public static void abortLocal(long input, long epoch) {\n"),
    (b"        terminalStatus = null; statusTicks = 0;\n",
     b"        terminalStatus = null; statusTicks = 0; RESULT.clear();\n"),
    (b"    public static void clear() { generation++; STATE.clear(); MARKERS.clear(); terminalStatus = null; statusTicks = 0; }\n",
     b"    public static void clear() { generation++; STATE.clear(); MARKERS.clear(); terminalStatus = null; statusTicks = 0; RESULT.clear(); }\n"),
    (b"    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {\n"
     b"        Minecraft mc = Minecraft.getInstance(); synchronize(mc);\n",
     b"    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {\n"
     b"        Minecraft mc = Minecraft.getInstance(); synchronize(mc);\n"
     b"        RESULT.tick(mc.isPaused(), localEligible() && !mc.options.hideGui && !mc.getDebugOverlay().showDebugScreen());\n"),
    (b"                if (marker.owner == mc.player) AcademyClient.remoteScatterRelayEndedInput(marker.frame.id().input(), marker.frame.id().epoch());\n",
     b"                if (marker.owner == mc.player) {\n"
     b"                    RESULT.clear();\n"
     b"                    AcademyClient.remoteScatterRelayEndedInput(marker.frame.id().input(), marker.frame.id().epoch());\n"
     b"                }\n"),
)


def require(condition, marker):
    if not condition:
        raise AssertionError(marker)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def git(project, *args):
    return subprocess.check_output(["git", "-C", str(project), *args])


def tree(project):
    result = {}
    for entry in git(project, "ls-tree", "-r", "-z", BASELINE).split(b"\0"):
        if entry:
            meta, path = entry.split(b"\t", 1)
            mode, kind, oid = meta.decode().split()
            require(kind == "blob" and mode in ("100644", "100755"), "BASELINE_NONREGULAR_INPUT")
            result[path.decode()] = oid
    require(result, "BASELINE_TREE_EMPTY")
    return result


def check_unchanged(path, data, expected_oid):
    oid = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    require(oid == expected_oid, "BASELINE_BYTES_CHANGED " + path)


def split_once(data, marker, failure):
    require(data.count(marker) == 1, failure)
    return data.split(marker, 1)


def one_java_block(data, signature, marker):
    """Consume one declared block; braces inside literals/comments are inert.

    Refuse Java Unicode prelexing and text blocks in these narrow new regions
    rather than risk treating executable trailing members as block contents.
    This is a boundary check, not Java compilation or semantic analysis.
    """
    data = data.lstrip(b" \t\r\n")
    signature = signature.lstrip(b" \t\r\n")
    require(data.startswith(signature) and signature.endswith(b"{"), marker)
    require(b"\\u" not in data and b'"""' not in data, marker)
    index, depth = len(signature) - 1, 0
    while index < len(data):
        if data[index:index + 2] == b"//":
            end = data.find(b"\n", index + 2)
            require(end >= 0, marker)
            index = end + 1
            continue
        if data[index:index + 2] == b"/*":
            end = data.find(b"*/", index + 2)
            require(end >= 0, marker)
            index = end + 2
            continue
        if data[index] in (34, 39):
            quote = data[index]
            index += 1
            while index < len(data) and data[index] != quote:
                index += 2 if data[index] == 92 else 1
            require(index < len(data), marker)
        elif data[index] == 123:
            depth += 1
        elif data[index] == 125:
            depth -= 1
            if depth == 0:
                return data[index + 1:]
        index += 1
    raise AssertionError(marker)


def check_version(path, baseline, current):
    require(baseline.count(OLD_VERSION) == 1 and NEW_VERSION not in baseline,
            "VERSION_BASELINE_SITE_CHANGED " + path)
    require(current == baseline.replace(OLD_VERSION, NEW_VERSION),
            "VERSION_NONLITERAL_CHANGE " + path)
    require(current.replace(NEW_VERSION, OLD_VERSION) == baseline,
            "VERSION_REVERSE_ROUNDTRIP_CHANGED " + path)


def check_build(baseline, current):
    require(current.endswith(BUILD_APPEND) and current.count(BUILD_APPEND) == 1,
            "BUILD_TASK_APPEND_CHANGED")
    check_version("build.gradle", baseline, current[:-len(BUILD_APPEND)])


def check_language(path, baseline, current):
    def parse(data):
        pairs = json.loads(data, object_pairs_hook=lambda values: values)
        require(isinstance(pairs, list) and all(isinstance(x, tuple) and len(x) == 2 for x in pairs),
                "LANGUAGE_NOT_FLAT_OBJECT " + path)
        require(len(pairs) == len(dict(pairs)), "LANGUAGE_DUPLICATE_KEY " + path)
        return dict(pairs)
    before, after = parse(baseline), parse(current)
    require(set(after) - set(before) == NEW_KEYS and not set(before) - set(after),
            "LANGUAGE_KEY_SCOPE_CHANGED " + path)
    require(all(after[key] == value for key, value in before.items()),
            "LANGUAGE_OLD_VALUE_CHANGED " + path)
    require(all(isinstance(after[key], str) and after[key] for key in NEW_KEYS),
            "LANGUAGE_HUD_VALUE_INVALID " + path)
    require(baseline.endswith(b"\n}\n"), "LANGUAGE_BASELINE_END_CHANGED " + path)
    # Append only: the sole modification to original bytes is the necessary
    # comma after its final property. No filtering/normalization of old lines.
    prefix = baseline[:-3] + b",\n"
    require(current.startswith(prefix) and current.endswith(b"}\n"),
            "LANGUAGE_OTHER_BYTES_CHANGED " + path)
    lines = current[len(prefix):-2].splitlines()
    require(len(lines) == len(NEW_KEYS), "LANGUAGE_APPEND_LINES_CHANGED " + path)
    require({json.loads(b"{" + line.rstrip(b",") + b"}").popitem()[0] for line in lines} == NEW_KEYS,
            "LANGUAGE_APPEND_KEYS_CHANGED " + path)


def check_state(baseline, current):
    before, rest = split_once(current, STATE_START, "RESULT_NOTE_INSERTION_CHANGED")
    note, after = split_once(rest, STATE_END, "RESULT_NOTE_END_CHANGED")
    require(note.startswith(b"    public static final class ResultNote {\n")
            and note.endswith(b"    }\n"), "RESULT_NOTE_CLASS_BOUNDARY_CHANGED")
    require(not one_java_block(note, b"public static final class ResultNote {",
                              "RESULT_NOTE_CLASS_BOUNDARY_CHANGED").strip(), "RESULT_NOTE_EXTRA_MEMBER")
    require(before + STATE_END + after == baseline, "STATE_EXISTING_BYTES_CHANGED")
    old_before, old_lease = split_once(baseline, STATE_END, "LEASE_BASELINE_SITE_CHANGED")
    require(after == old_lease, "LEASE_BYTES_CHANGED")
    return {"result_note_sha256": digest(STATE_START + note),
            "unchanged_lease_and_tail_sha256": digest(STATE_END + old_lease),
            "unchanged_state_prefix_sha256": digest(old_before)}


def check_recovery(baseline, current):
    require(baseline.count(RECOVERY_ANCHOR) == 1 and current.count(RECOVERY_APPEND) == 1,
            "RECOVERY_ACCESSOR_SITE_CHANGED")
    require(current == baseline.replace(RECOVERY_ANCHOR, RECOVERY_APPEND + RECOVERY_ANCHOR),
            "RECOVERY_EXISTING_BYTES_CHANGED")


def check_client(baseline, current):
    require(baseline.count(CLIENT_ANCHOR) == 1 and current.count(CLIENT_APPEND) == 1,
            "CLIENT_NOTE_CLEAR_SITE_CHANGED")
    require(current == baseline.replace(CLIENT_ANCHOR, CLIENT_ANCHOR + CLIENT_APPEND),
            "CLIENT_EXISTING_BYTES_CHANGED")


def check_effects(baseline, current):
    old_prefix, old_hud = split_once(baseline, HUD_OLD, "EFFECTS_OLD_HUD_SITE_CHANGED")
    old_hud, old_tail = split_once(old_hud, RENDER_TAIL, "EFFECTS_OLD_RENDER_SITE_CHANGED")
    prefix, hud = split_once(current, HUD_NEW, "EFFECTS_NEW_HUD_SITE_CHANGED")
    hud, tail = split_once(hud, RENDER_TAIL, "EFFECTS_NEW_RENDER_SITE_CHANGED")
    require(tail == old_tail, "EFFECTS_RENDER_TAIL_CHANGED")
    # Pin explicit Flash and Marker definitions in addition to reconstruction.
    classes_start = b"    private static final class Flash {\n"
    classes_end = b"    private RemoteScatterRelayEffects() {}\n"
    def classes(data):
        _, rest = split_once(data, classes_start, "EFFECTS_FLASH_SITE_CHANGED")
        value, _ = split_once(rest, classes_end, "EFFECTS_MARKER_END_CHANGED")
        return classes_start + value
    require(classes(prefix) == classes(old_prefix), "EFFECTS_FLASH_MARKER_CHANGED")
    reconstructed = prefix
    for index, (before, after) in enumerate(EFFECT_HOOKS):
        require(old_prefix.count(before) == 1 and reconstructed.count(after) == 1,
                "EFFECTS_HOOK_SITE_CHANGED " + str(index))
        reconstructed = reconstructed.replace(after, before)
    require(reconstructed == old_prefix, "EFFECTS_EXISTING_PREFIX_CHANGED")
    # The changed contiguous region is inventoried; semantic safety and HUD
    # correctness come from the separate tests/QA, not a source-scope claim.
    remaining = hud
    for signature in (b"@SubscribeEvent public static void hud(RenderGuiEvent.Post event) {",
                      b"private static String hudText(String key, Object... args) {",
                      b"/** Same configured instrument geometry as the existing HUD; no snapshots or resource values are changed. */\n"
                      b"    private static List<HighPowerRecoveryHudModel.Box> reservedHudBoxes(int width, int height) {"):
        require(hud.count(signature) == 1, "EFFECTS_HUD_METHOD_BOUNDARY_CHANGED")
        remaining = one_java_block(remaining, signature, "EFFECTS_HUD_METHOD_BOUNDARY_CHANGED")
    require(not remaining.strip(), "EFFECTS_HUD_EXTRA_MEMBER")
    return {"exact_lifecycle_hooks": len(EFFECT_HOOKS), "hud_region_sha256": digest(HUD_NEW + hud),
            "unchanged_render_tail_sha256": digest(RENDER_TAIL + tail),
            "unchanged_flash_marker_sha256": digest(classes(prefix)),
            "reconstructed_original_prefix_sha256": digest(reconstructed)}


def check_visual_test(baseline, current):
    before, rest = split_once(current, b"    private static void resultNotes() {\n",
                              "VISUAL_RESULT_TEST_SITE_CHANGED")
    note_tests, after = split_once(rest, b"    private static void lifetime() {\n",
                                  "VISUAL_LIFETIME_SITE_CHANGED")
    require(not one_java_block(b"private static void resultNotes() {\n" + note_tests,
                              b"private static void resultNotes() {", "VISUAL_RESULT_TEST_BOUNDARY_CHANGED").strip(),
            "VISUAL_RESULT_TEST_EXTRA_MEMBER")
    reconstructed = before + b"    private static void lifetime() {\n" + after
    new_call, old_call = b"feedback(); resultNotes(); lifetime();", b"feedback(); lifetime();"
    require(reconstructed.count(new_call) == 1 and baseline.count(old_call) == 1,
            "VISUAL_RESULT_CALL_SITE_CHANGED")
    require(reconstructed.count(NEW_HUD_ASSERT) == 1 and baseline.count(OLD_HUD_ASSERT) == 1,
            "VISUAL_HUD_ASSERTION_SITE_CHANGED")
    reconstructed = reconstructed.replace(NEW_HUD_ASSERT, OLD_HUD_ASSERT).replace(new_call, old_call)
    require(reconstructed == baseline, "VISUAL_OLD_TEST_BYTES_CHANGED")
    return {"appended_result_tests_sha256": digest(note_tests)}


def expected_rejection(name, callback, marker):
    try:
        callback()
    except AssertionError as error:
        require(str(error).startswith(marker), "WRONG_REJECTION " + name + ": " + str(error))
        return {"mutation": name, "result": "EXPECTED_REJECTION", "marker": marker}
    raise AssertionError("MUTANT_SURVIVED " + name)


def replace_mutant(data, before, after):
    require(data.count(before) == 1 and before != after, "MUTATION_SITE_CHANGED " + repr(before))
    return data.replace(before, after)


def candidate_inventory(project):
    candidate_paths = {path.decode() for path in git(project, "ls-files", "--cached", "--others",
                       "--exclude-standard", "-z").split(b"\0") if path}
    # Include ignored additions under both source trees, too. There are no
    # exemptions for old fixtures/runners, source assets, or Python caches.
    for root in (project / "src/main", project / "src/test"):
        candidate_paths.update(path.relative_to(project).as_posix()
                               for path in root.rglob("*") if path.is_file() or path.is_symlink())
    return candidate_paths


def prove(project):
    baseline = tree(project)
    source = lambda path: git(project, "show", BASELINE + ":" + path)
    candidate_paths = candidate_inventory(project)
    added = candidate_paths - set(baseline)
    require(added <= ADDITIONS, "UNAPPROVED_ADDED_INPUT " + repr(sorted(added - ADDITIONS)))
    require(ADDITIONS - {EVIDENCE} <= added, "REQUIRED_ADDITION_MISSING " + repr(sorted(ADDITIONS - {EVIDENCE} - added)))
    addition_audit = []
    for path in sorted(added):
        file = project / path
        require(file.is_file() and not file.is_symlink(), "ADDED_INPUT_NOT_REGULAR " + path)
        if path != EVIDENCE:  # The report cannot contain a hash of itself.
            addition_audit.append({"path": path, "sha256": digest(file.read_bytes())})
    changed, unchanged, controls, live_bytes, details = [], [], [], {}, {}
    for path, oid in sorted(baseline.items()):
        file = project / path
        require(file.is_file() and not file.is_symlink(), "BASELINE_INPUT_MISSING " + path)
        data = file.read_bytes()
        live_bytes[path] = digest(data)
        row = {"path": path, "sha256": digest(data)}
        if path in VERSION_ONLY or path == "build.gradle":
            old = source(path)
            check = check_build if path == "build.gradle" else lambda a, b: check_version(path, a, b)
            check(old, data)
            row["status"] = "ONE_EXACT_78_TO_79_LITERAL" + ("_AND_EXACT_APPENDED_HUD_TASK" if path == "build.gradle" else "_ONLY")
            # Change a retained byte while preserving any appended build task.
            controls.append(expected_rejection(path + ":extra-edit",
                lambda: check(old, b"\n" + data), "VERSION_NONLITERAL_CHANGE"))
            controls.append(expected_rejection(path + ":wrong-version",
                lambda: check(old, data.replace(NEW_VERSION, b"other-artifact")), "VERSION_NONLITERAL_CHANGE"))
        elif path in LANGUAGES:
            old = source(path)
            check_language(path, old, data)
            row["status"] = "37_APPENDED_HUD_KEYS_OLD_VALUES_AND_BYTES_PRESERVED"
            controls.append(expected_rejection(path + ":old-value",
                lambda: check_language(path, old, data.replace(b'"academy.highpower.remote_scatter_relay.cooldown": "',
                    b'"academy.highpower.remote_scatter_relay.cooldown": "MUTATED ', 1)), "LANGUAGE_OLD_VALUE_CHANGED"))
            controls.append(expected_rejection(path + ":old-formatting",
                lambda: check_language(path, old, b" " + data), "LANGUAGE_OTHER_BYTES_CHANGED"))
            controls.append(expected_rejection(path + ":unknown-key",
                lambda: check_language(path, old, data.replace((PREFIX + "brief.prepared").encode(),
                    (PREFIX + "brief.unapproved").encode())), "LANGUAGE_KEY_SCOPE_CHANGED"))
        elif path in (EFFECTS, STATE, RECOVERY, CLIENT, VISUAL_TEST):
            old = source(path)
            check = {EFFECTS: check_effects, STATE: check_state,
                     RECOVERY: check_recovery, CLIENT: check_client, VISUAL_TEST: check_visual_test}[path]
            details[path] = check(old, data)
            row["status"] = "NAMED_HUD_ADDITION_ONLY_OLD_REGIONS_RECONSTRUCT_EXACTLY"
        else:
            check_unchanged(path, data, oid)
            unchanged.append(row)
            continue
        row["baseline_sha256"] = digest(source(path))
        changed.append(row)
    require(len(changed) == 13, "EXPECTED_CHANGE_SCOPE_CHANGED")
    # Virtual mutations only: no source, fixture, runner, or historical result
    # is modified. Both logical edits and arbitrary retained-byte edits fail.
    immutable_mutants = (
        (SERVER, b"MIN_LEVEL=5,MAX_LEVEL=5", b"MIN_LEVEL=4,MAX_LEVEL=5"),
        (RULES, b"LIFETIME = 400", b"LIFETIME = 401"),
        (REGISTRY, b"public final class HighPowerSkills", b"public final class ChangedSkills"),
    )
    for path, before, after in immutable_mutants:
        mutated = replace_mutant((project / path).read_bytes(), before, after)
        controls.append(expected_rejection(path + ":logic-edit",
            lambda: check_unchanged(path, mutated, baseline[path]), "BASELINE_BYTES_CHANGED"))
    for path in ("src/main/resources/classic-skills.json", V14, V15, FIXTURE + "contract-v1.json"):
        controls.append(expected_rejection(path + ":extra-byte",
            lambda: check_unchanged(path, (project / path).read_bytes() + b"\n", baseline[path]), "BASELINE_BYTES_CHANGED"))
    effects, state, recovery, visual = ((project / path).read_bytes() for path in (EFFECTS, STATE, RECOVERY, VISUAL_TEST))
    for name, data, before, after, check, path, marker in (
        ("render-tail", effects, b"if (world == null) return false;", b"if (world == null) return true;", check_effects, EFFECTS, "EFFECTS_RENDER_TAIL_CHANGED"),
        ("marker-flash-budget", effects, b"if (flashes.size() == 2)", b"if (flashes.size() == 3)", check_effects, EFFECTS, "EFFECTS_FLASH_MARKER_CHANGED"),
        ("receipt-fence", effects, b"if (!STATE.accept(receipt, local)) return;", b"if (STATE.accept(receipt, local)) return;", check_effects, EFFECTS, "EFFECTS_EXISTING_PREFIX_CHANGED"),
        ("old-state-flash-lifetime", state, b"FLASH_TICKS = 6", b"FLASH_TICKS = 25", check_state, STATE, "STATE_EXISTING_BYTES_CHANGED"),
        ("old-recovery-body", recovery, b"return HighPowerRecoveryHudModel.layout(width, height, cp, keys, notification, chatBox);", b"return null;", check_recovery, RECOVERY, "RECOVERY_EXISTING_BYTES_CHANGED"),
        ("old-client-input", (project / CLIENT).read_bytes(), CLIENT_ANCHOR, CLIENT_ANCHOR + b"            singleInputCounter++;\n", check_client, CLIENT, "CLIENT_EXISTING_BYTES_CHANGED"),
        ("old-visual-test", visual, b"recallDuringCooldown(); receipts();", b"receipts();", check_visual_test, VISUAL_TEST, "VISUAL_OLD_TEST_BYTES_CHANGED"),
    ):
        mutated = replace_mutant(data, before, after)
        controls.append(expected_rejection(name, lambda: check(source(path), mutated), marker))
    controls.append(expected_rejection("build-old-task-wiring",
        lambda: check_build(source("build.gradle"), (project / "build.gradle").read_bytes().replace(
            b"dependsOn 'remoteScatterRelayVisualTest'", b"dependsOn 'remoteScatterRelayHudLayoutTest'")), "VERSION_NONLITERAL_CHANGE"))
    controls.append(expected_rejection("result-note-extra-member",
        lambda: check_state(source(STATE), state.replace(STATE_END,
            b"    static void unrelatedNewMethod() {}\n" + STATE_END)), "RESULT_NOTE_CLASS_BOUNDARY_CHANGED"))
    controls.append(expected_rejection("hud-extra-member",
        lambda: check_effects(source(EFFECTS), effects.replace(RENDER_TAIL,
            b"    static void unrelatedNewMethod() {}\n" + RENDER_TAIL)), "EFFECTS_HUD_EXTRA_MEMBER"))

    # Import only already-audited, byte-identical predecessor sources. These
    # registry-only functions do not enter the v14 differential or launch Java.
    v14 = runpy.run_path(str(project / V14))
    contract_bytes = (project / FIXTURE / "contract-v1.json").read_bytes()
    contract = json.loads(contract_bytes)
    amendment, old_registry, reconstructed, prior = v14["load_registry_chain"](
        project, project / FIXTURE, contract_bytes, contract, lambda path: path.read_bytes())
    registry = (project / REGISTRY).read_bytes()
    require(registry == reconstructed, "V14_RECONSTRUCTION_DIFFERS")
    v14["check_registry_append"](registry, old_registry, amendment)
    registry_controls = v14["registry_append_controls"](registry, old_registry, amendment)
    predecessor = v14["predecessor_guard_controls"](project, project / FIXTURE,
        contract_bytes, contract, amendment, old_registry, registry, lambda path: path.read_bytes())
    require(len(v14["registry_records"](registry)) == 51, "REGISTRY_COUNT_CHANGED")
    v15 = runpy.run_path(str(project / V15))
    v15_rejection = expected_rejection("unchanged-v15-rejects-new-scope",
        lambda: v15["prove"](project), "UNAPPROVED_ADDED_INPUT")
    for path, expected in live_bytes.items():
        file = project / path
        require(file.is_file() and not file.is_symlink() and digest(file.read_bytes()) == expected,
                "INPUT_CHANGED_DURING_RUN " + path)
    for entry in addition_audit:
        require(digest((project / entry["path"]).read_bytes()) == entry["sha256"],
                "ADDED_INPUT_CHANGED_DURING_RUN " + entry["path"])
    require(candidate_inventory(project) == candidate_paths, "INPUT_PATHS_CHANGED_DURING_RUN")
    return {
        "result": "PASS_SOURCE_SCOPE_ONLY",
        "contract": "remote-scatter-relay-hud-source-companion-v16",
        "baseline_commit": BASELINE,
        "candidate_head": git(project, "rev-parse", "HEAD").decode().strip(),
        "baseline_tree_inputs": len(baseline), "unchanged_baseline_inputs": len(unchanged),
        "audited_roots": dict(sorted(Counter(path.split("/")[0] for path in live_bytes).items())),
        "input_inventory_sha256": digest(json.dumps(live_bytes, sort_keys=True).encode()),
        "named_changes": changed, "bounded_client_regions": details,
        "added_inputs": sorted(added), "added_input_audit": addition_audit,
        "registry": {"registered_before": 51, "registered_after": 51, "new_skills": 0,
            "source_sha256": digest(registry), "v14_registry_negative_controls": len(registry_controls),
            "v14_predecessor_inputs": len(prior),
            "v13_registry_negative_controls": predecessor["unchanged_v13_registry_controls"],
            "v13_current_rejection": predecessor["unchanged_v13_current"],
            "predecessor_tamper_controls": len(predecessor["tamper_controls"])},
        "companion_negative_controls": controls,
        "unchanged_v15_current_scope": v15_rejection,
        "historical_guards": "UNCHANGED_SEPARATE_FAILURES_NOT_REPAIRED",
        "limitations": [
            "Source-scope proof only; no executed Charging/M41 trace, class-byte comparison, or aggregate check pass.",
            "The changed HUD region, ResultNote, layout, new tests and translation text need separate behavior tests and native-client QA.",
            "All .78 baseline inputs outside 13 constrained paths are byte-identical. Existing client regions reconstruct .78 exactly.",
            "Server, rules, geometry, registry, protocol, classic HUD, assets, old fixtures, runners and evidence are unchanged except four exact version literals and 37 appended keys per locale.",
            "No historical pin, contract, fixture, runner or historical evidence was refreshed; the unchanged v15 rejects this new scope.",
            "No Gradle, compiler, JVM, server, or client was started."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--report", type=Path, required=True, help="Fresh JSON path; existing files refused")
    args = parser.parse_args()
    project = args.project.resolve()
    args.report.parent.mkdir(parents=True, exist_ok=True)
    with args.report.open("x") as output:
        try:
            result = prove(project)
        except Exception as error:
            json.dump({"result": "FAIL", "baseline_commit": BASELINE, "reason": str(error)}, output, indent=2)
            output.write("\n")
            raise
        json.dump(result, output, indent=2)
        output.write("\n")
    print(json.dumps({key: result[key] for key in ("result", "baseline_commit", "candidate_head",
          "baseline_tree_inputs", "unchanged_baseline_inputs", "registry", "unchanged_v15_current_scope")}, indent=2))


if __name__ == "__main__":
    main()
