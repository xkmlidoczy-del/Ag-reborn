# NEW351 compiled .56 classic fallback differential

This is a separate CPU recording proof for the optional .57 landing-array publication/contact cues. It does not modify or grant acceptance to an existing classic guard, regenerate any historical oracle, or claim a Minecraft client, GPU, gameplay, or pixel result.

## Run

From the project root:

```
python3 scripts/verify-landing-array-v56-fallback.py --report /tmp/landing-array-v56-proof
```

The report directory must not already exist. Use `--java-home` or `JAVA_HOME` to select an installed Java 21 JDK. The runner verifies Java/compiler version 21 and records SHA-256 for the executable, compiler-containing `lib/modules`, and release metadata; both lanes use the same toolchain. The frozen source/output hashes stay mandatory across runtimes: a different runtime that cannot reproduce the frozen capture fails explicitly, without auto-blessing. This cloud runtime has a compiler module but no usable `ct.sym` release signatures, so the bounded compilation uses `-source 21 -target 21`, not `--release 21`. It is not an API compatibility check against a complete JDK distribution.

All inputs are receipted, checked again before completion, and compiled in temporary empty class directories with an empty source path, disabled annotation processing, and disabled implicit source compilation. Only the named production closure and 58 inventoried hosts are admitted. A new dependency without an explicit host/source fails compilation. No fallback to Gradle, an ambient classpath, downloading dependencies, or generating stubs exists.

## Frozen provenance

The nine files under `frozen/` are exact bytes from commit:

```
a1a78a3ee316b87659d5bf7c943650c7af6ba96b
```

They were extracted individually with `git show <commit>:<source-path>`. `frozen-v56.json` names each source and its SHA-256; the runner independently embeds those hashes and rejects manifest/source drift. The runner has no capture-refresh mode and never writes to the frozen fixture.

The frozen compiler actually executes the complete original `MultidimensionalLandingArrayEffects`, `MultidimensionalLandingArrayMesh`, `SafeLandingProbeMesh`, `SafeLandingProbeGeometry`, `ClassicTeleporterTimeline`, and named layout/timeline helpers. The current compiler takes the complete current counterparts plus actual optional geometry, landing material, soft shader lifecycle, and visual configuration.

The initial reviewed frozen capture is fixed in the runner:

- Cases: 1,051; nonempty cases: 724
- Emitted vertices: 379,312; flushed batches: 2,140
- `cases.tsv` SHA-256: `67d848455a73b759207c8f7609f863061ae637387c1032bf6d862d87d519c560`
- Uncompressed `capture.txt.gz` SHA-256: `3951ea9593d380e45e64c69c4c7bf2ff5092cbc82f76b98d77fb21426ab7d16a`

## Comparison

Ten complete current lanes must exactly match the frozen capture: OFF, missing texture, corrupt texture, forced unavailable, unloaded config, failed shader construction, failed shader link, pending shader callback, stale shader callback, and an enabled material with an injected invalid render fraction. Real resource bytes run through the actual material SHA checks; the shader host reads the actual shader resources while controlling construction/link/callback outcomes.

A separate enabled-material near-eye lane exercises the actual geometry planner declining a complete publication cue and matches the corresponding 16 frozen cases, including visible and hidden GUI. This is an ordinary valid view, not a stubbed optional-helper return value.

The capture compares ordered raw float bits for coordinates, UVs and normals, integer color/alpha, transformed coordinates, texture and buffer selection, actual render-state declarations and flushes, label/native preview calls, pose/model-view/shader-color restoration, actual receipt state and shot/cue history. Hashes are comparison summaries; full ordered captures are emitted in the report.

Coverage includes all landing phases, source versus accepted/rejected contact, ages 0–7, near/inside/behind the eye, upward view, world-border coordinates, nonidentity poses, pause/death and environment changes, visibility/LOS gates, queued invalidation, stale/duplicate receipts, clean/cancelled end, and six real source/contact cues. Rejected impacts are asserted not to invent a contact cue.

Five separately recompiled negative controls must be detected:

- Drop the complete classic cue: 526 differing cases
- Shift a classic coordinate: 724 differing cases
- Alter a classic UV: 724 differing cases
- Alter a classic color: 724 differing cases
- Swallow fallback after an enabled optional planner returns zero: 14 differing near-eye cases

## Current enabled smoke

A small additional current-only harness executes successful source and accepted-contact rendering, verifies distinct captures, confirms a rejected impact retains the original source cue, and submits all six source/contact cues at 864 vertices against the 2,048 scene limit. It invokes the actual private planner with 143 remaining vertices and requires zero return, zero vertices, and zero buffer/state calls; an exact 144 budget must admit the complete cue. The ordinary six-cue scene cannot naturally exhaust 2,048. The zero-return classic fallback branch is independently covered by the enabled near-eye frozen differential. This smoke lane is not an enabled beauty oracle.

## Explicit limits

The 58 hosts model Minecraft events/world queries, packet shape, input/session eligibility, JOML operations, render buffers, font/item calls, resources and shader outcomes. They are plainly present in `hosts/` and inventoried in `host-inventory.json`; they are not replacement production implementations. Original receipt validation and state transitions execute in the actual Mesh/Effects. The protocol host does not establish server validation or transport behavior.

The input ownership lifetime (240) and unused safe-probe range (12) are small declared constant hosts. GUI-only recovery/config hosts throw on attempted execution: this proof concerns world rendering, not guidance GUI. The renderer's original layout/timeline dependencies compile unchanged. Item previews and font labels are recorded calls, not native model/font vertices. Shader/buffer state is a controlled recording model, not GPU state, driver behavior or real engine batching.

`evidence.json`, input SHA receipts, per-lane class inventories, compile/execution logs, full compressed captures, and negative-control differences are written to the requested report directory. Only a completed run writes `PASS_CPU_FROZEN_DIFFERENTIAL`.
