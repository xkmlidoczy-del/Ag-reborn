# NEW431 .66 OFF and failed-resource differential

Run from the project root with Python 3 and Java 21:

```sh
python3 scripts/verify-trajectory-return-v66-fallback.py --report /tmp/trajectory-return-v66-proof-new
```

The report directory must not exist. The runner defaults to `.tools/jdk-21.0.12.1+1`; pass `--java-home` for another Java 21 installation. It invokes the Java compiler directly, without Gradle, external services, or production-tree writes. `--frozen-only` tests the frozen fixture plus its three original-output negative controls; that mode makes no current-equivalence claim.

## Independent baseline

`frozen-v66.json` records exact bytes retrieved with `git show` from existing commit `dbe8100aa9c222cfea16fdcf6329c2da2dca4dd3`. Four full production files are frozen: `TrajectoryReturnEffects`, `TrajectoryReturnState`, `TrajectoryReturnMesh`, and `SafeLandingProbeGeometry`. The runner independently compiles these in one temporary tree and the current production closure (including the real config, presentation and enhanced mesh) in another. It also verifies those Git bytes when the commit is present. It never fabricates repository metadata.

The baseline manifest, every host API fixture, harness, original `glow_line.png`, and previously captured frozen output have immutable SHA-256 anchors. Current Effects is compiled and exercised, not accepted by blessing its changed source hash. Current receipt state, classic mesh and Point geometry must still be byte-identical to .66. There is no refresh mode. Existing original-three regression guards are not edited by this proof.

## What is compared

The harness calls the actual production `receive`, `tick`, `render`, and registered reload listener. Each lane replays the same 886 cases, including:

- One and four arrow slots, sequential FLIGHT receipts, time gaps, spatial discontinuities, duplicate and skipped sequences, and REFUSED/DAMAGED/ABSORBED contact
- All three air-hammer outcomes; immediate ENDED removal and late receipt rejection
- Thirteen ages from zero through expiry, with exact and fractional render ages
- Six camera/world cases: external view, near eye, oblique view with a nonidentity pose transform, world-border coordinates, behind the camera, and an opaque-plane collision response
- Empty/paused/expired casts, session/world/player changes, invalid local controls, death, teleport, queued stale callbacks, unloaded chunks, frustum rejection, wrong render stage, and the four-controller limit

OFF, unloaded config, forced fallback, missing/corrupt/oversized resource, pending reload, and stale reload each match the entire frozen capture exactly. The original frozen capture has 363 nonempty cases, 35,048 emitted vertices and a 672-vertex peak. Captures include raw and transformed vertex float bits, UV bits, RGBA, material/render state, buffer requests and flush order, terrain-query counts, and authenticated state/fence/input-notification snapshots. Rendering must preserve authenticated state, pose/model-view stacks, and shader color. Three injected vertex/flush failures also check restoration.

The enabled lane must produce different actual production submissions while preserving the identical authenticated state. This is a smoke test of the real branch, not an independent correctness claim for every enhanced visual.

Seven deliberate negative controls must fail equivalence or the corresponding readiness assertion: emitted alpha, classic shoulder width, receipt trail lifetime, OFF cast-latch bypass, resource selector bypass, texture hash guard, and stale reload guard. Mutations are compiled only in temporary trees. Reports include class inventories, all input hashes, full compressed captures, per-case hashes, differences and machine-readable `evidence.json`.

## Host boundary and limits

The explicit platform hosts were adapted from the existing .62 CPU differential. The NEW431 extensions are the receipt transport record, controlled local-input notifications/cancellation, `Entity.position`, native `AABB(Vec3,Vec3)` constructor, clip-query boundary, literal component signature, and a ResourceManager that opens the real original glow-line file and injects bounded failures. None replaces production receipt validation, mesh selection, geometry, profile, billboard clipping, or vertex emission.

The host collision plane and chunk responses are controlled stimuli, not Minecraft's collision implementation. The proof does not load native Minecraft/NeoForge/JOML binaries, compile GPU shaders, rasterize pixels, run gameplay, or establish native screenshot/visual acceptance. It does not exercise concurrent reload during a render frame; pending/stale reload here means readiness before the render starts. Java source/target 21 is explicit; `--release` API compatibility is not claimed.
