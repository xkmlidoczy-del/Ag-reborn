# Meltdowner near-eye differential oracle (.54)

The optional local first-person direct Meltdowner material is tested against the
immutable .53 renderer from commit `8cbf2fc49cf5c28231647856eb3033f2614cdaf9`.
The new tests are additive. They do not replace, exclude, edit, or refresh the
historical geometry guards or their expected hashes. A passing new differential
must not be reported as a passing historical guard.

## Run

From the source root, with the cached JDK 21, JOML 1.10.5 and Minecraft source
archive available:

```sh
python3 scripts/verify-md-near-eye-integration.py \
  --report build/reports/md-near-eye-integration/report.json
```

Optional paths are `--joml` and `--pinned-minecraft-sources`. The script does not
start Gradle, download dependencies or update any fixtures. It verifies pinned
JOML bytes and the native Minecraft RenderType/RenderStateShard source hashes
before compiling the two renderer versions. Its report records exact production
source hashes, modes, frame count, trace digest, failures tested and limitations.

The separate `MdNearEyeVisibilityRegressionTest` is a dependency-free Java test
of the actual production policy and numerical fade oracle.

## Compiled renderer coverage

The script compiles unchanged original/current `ClassicMeltdownerBeamEffects`,
the actual timelines, current config, policy, client switch and material. It
calls real public packet ingress, tick and world-render entrypoints against
explicit recording hosts. The original renderer fixture has fixed SHA-256
`b02f2dd687881d80d4c50689821f1a16619483623147db3cd9dd089b394fd1c4`.

Each mode runs 835 frames covering:

- Main direct beams at zero, 0.01, 0.3, 1 and 30 blocks, four axes, multiple ages,
  near and 12-block camera positions, and zero/30-million world coordinates
- Local reflections, remote casters, third person, detached or mismatched
  cameras, dead/removed/spectator casters, UUID/entity/player replacement,
  reconnect, world replacement, and wrong render stage
- Basic, expert and luck mining rays for local and remote casters, mining
  particles and immediate mining END cleanup
- Pause/resume, direct plus reflected beams in one frame, duplicate terminal-ray
  rejection, ray/particle lifetime, and queued callback rejection on session loss

For every frame it compares raw and transformed position float bits, complete
pose matrices, UV/RGBA, count/order, original texture, render-state declarations,
flush order, serialized production Random state, wiggle state, clock,
ray/context/particle state, walking speed and sound-object count.

OFF, unloaded config and fault modes must retain the original effective shader
and material state. The selected-mode comparison normalizes only replacement
material names and their corresponding shader identities; vertex attributes,
RNG and other recorded state remain exact. The selected local direct ray must
submit exactly the original three four-vertex glow boards and two 432-vertex
cylinders, with no replacement geometry.

Fault coverage includes forced/global fallback, either shader load failing,
missing/inactive uniforms, failed link, either/both closed programs, deletion
pending on either still-live program, reload pending, second registration
failure, reload or force-fallback between buffer acquisition and draw, and
material setup failure. These are injected recording-host faults.

Additional lifecycle assertions cover pair invalidation, stale callbacks,
partial callback readiness, successful new generations, failed second
registration, null/wrong-role textures, and a bounded cache under 1,000 unknown
textures. The real client command handler is checked for default OFF, ON/OFF
config saves, read-only status and rejection while config is unloaded.

Five compiled negative controls must fail: changed classic alpha, changed UV,
extra RNG consumption, including reflected rays, and an inverted config gate.
Mutants exist only in temporary compilation directories, never production.

## Pure policy and numerical coverage

The pure regression test checks exact object identities, session continuity,
direct versus reflected/mining eligibility, both camera identities, detachment,
first/third person, death/spectator state and missing context. It checks smooth,
monotonic depth recovery, exact distant alpha, finite fallback for invalid inputs,
and a conservative 256-shot composition bound at zero-contribution depths.
These calculations are not a pixel or readability test.

## Evidence boundaries

This differential uses recording Minecraft/resource/GL hosts. Native JOML does
matrix and quaternion arithmetic. The RenderType wrapper's actual production
setup/clear delegates execute, and the effective underlying state is recorded at
batch flush. Shared-buffer completion fencing is deliberately modeled; native
BufferSource and GL driver behavior are not executed here. Classic glow's cutoff
getter is an explicitly named host, with the original GLSL resource separately
pinned. The script does not execute GLSL, compare pixels, simulate gameplay or
measure performance.

The separate MD shader GL test supplies shader execution evidence. Neither test
alone establishes full native-client visual acceptance, audio correctness,
readability under every overlapping scene, or success of the older closed
historical geometry guards.
