# NEW151 frozen .55 fallback proof

This independent CPU differential compiles the complete .55 `RailThunderArrayEffects`, `RailThunderArrayGeometry`, `RailThunderArrayState`, and `ClassicRenderTypes` source from commit `87dbcc2aa8cad739db1662a52746445ec8726681`. Those exact, unmodified files are stored under `frozen/`, with immutable SHA-256 values in `frozen-v55.json` and the runner. There is no refresh command.

The current lane separately compiles the current complete renderer and the actual named geometry/state/render-type/material/config/preview sources. It does not remove or extract the optional branch, nor substitute the material's readiness check. Actual material resource reload reads the four original textures and executes its SHA verification. The optional preview property is absent, so the actual preview helper is disabled.

The two versions execute in separate JVMs with the same 49 explicit host sources. These hosts model Minecraft world/entity/session/config/event services, matrices, vertex submission, and render state declarations. They are deliberately small and visible; none is a replacement for the tested renderer, geometry, state, material, or `ClassicRenderTypes` code.

## What is recorded

Each snapshot retains every submitted raw float coordinate bit (plus modeled pose transformation), UV bit, integer RGBA, texture and buffer request, flush, declared composite state, and model-view operation in `capture.txt.gz`. `cases.tsv` contains the per-case vertex/batch counts and SHA-256 of the complete render and lifecycle records. The actual renderer's State receipt/fence counters, marker payloads, lease ages, and flash ages are recorded separately. Every frame checks model-view restoration and complete quads.

Eight complete current lanes must match the frozen version exactly:

- Config OFF, with valid and verified material resources
- Config ON, with a missing material resource
- Config ON, with a corrupt material resource
- Config ON, with verified resources and the forced-unavailable property
- Config ON, with a controlled shader-construction failure
- Config ON, with a controlled failed link (the rejected shader must close)
- Config ON, with the shader-ready callback still pending
- Config ON, with an old ready callback delivered after a newer failed registration

These shader lanes compile and execute the actual `RailThunderArraySoftMaterial` registration implementation. The host reads the actual shader resource bytes, controls construction/link outcomes, and delivers real callback objects in the selected order. It does not compile GLSL or run OpenGL.

An additional 288-case REFUSED lane has ready, enabled materials. It enters the actual `grandMarker`, returns false before submission, and must match the corresponding frozen cases.

The complete matrix contains 1,485 snapshots, including 932 nonempty frames and 33,360 emitted vertices. It covers PREPARING/ARMED previews; TAP/HOLD MISS/HIT/REFUSED/BLOCKED; flash ages 0–5; both hands, third person and remote owners; near-eye, center-clear, transformed-pose, behind-eye and world-border geometry; unloaded/occluded/removed targets; pause, cancellation, session/world/player/connection changes; quiet expiry; stale packets and empty heartbeats; context/visibility/flash/segment limits. The complete frozen capture and per-case ledger digests are pinned independently of the current sources.

Six frozen-differential negative controls are compiled and executed. Changing an endpoint, UV, color, OFF fallback branch, failed-submission fallback branch, or declared write-mask state must produce actual captured differences. They cannot pass through an input hash check: mutants compile, run, and differ from the frozen oracle at output comparison.

## Successful enabled integration

A separate current-source harness executes 177 checks across 45 captured snapshots with explicitly registered resources and shader readiness. It verifies:

- HIT adds its two contact quads; MISS and BLOCKED have identical emitted paths
- The soft shader applies only to the original glow texture; other textures keep the classic material
- A shot received at cast age 200 starts at flash age zero and matches a fresh shot
- Empty heartbeats preserve the envelope and cannot extend the five-tick flash
- Pausing freezes lease/flash clocks, and preparation animates from the lease between packets without jumping at a heartbeat
- Callbacks queued before clear, session change or end cannot recreate geometry; already confirmed terminal flashes still expire
- Five heavy controllers emit exactly the concatenation of four individually recorded optional controllers and one complete classic controller. This fixture produces 3,072 optional vertices and 1,024 classic vertices, with the optional portion bounded by 4,096
- An oversized controller falls back completely through the real render event

The real private planning method is also called with ample, exact, and one-quad-short budgets. Ample and exact budgets reproduce the event submission. The short budget must emit nothing and leave the budget unchanged; calling the real classic path afterward must reproduce the complete OFF submission. This is explicitly a method contract: with four controllers capped at 256 quads and a 1,024-quad global allowance, shortage cannot arise naturally before the controller cap in an ordinary event.

Nine additional compiled mutations must fail the corresponding enabled assertion: forged HIT authority, cast-age-based flash clock, fifth optional controller, removed budget guard, missing clear/session generations, heartbeat refresh, packet-based preparation clock, and applying the soft shader beyond glow. They execute the actual mutated methods; source admission does not reject them first.

## Running

Run `python3 scripts/verify-rail-thunder-array-v55-fallback.py --report /tmp/new-empty-report-path` from the source checkout, or run Gradle task `railThunderArrayV55FallbackProof`. Each run requires a new report path and produces source/class receipts, all captures, negative-control differences, and an `evidence.json`. A failure cannot leave a stale success marker. The pinned JDK defaults to `../../aca-tools/jdk-21.0.12.1+1`; `--java-home` can locate the same exact binaries elsewhere.

## Limits

This proves complete renderer CPU submission and modeled lifecycle equivalence for the exercised matrix. It does not prove native GPU pixels, engine batching, shader execution, GL state restoration, live gameplay, networking, performance, or native client acceptance. Host batch callbacks are recording behavior, not real `MultiBufferSource` draw execution. `ClassicRenderTypes` itself is actual source, so its declared blend/cull/write/output/texture state is compared. Successful optional rendering also has this integration harness and a separate geometry test. Shader resources and readiness lifecycles execute, while shader compilation/link outcomes remain explicit controlled hosts.

Historical classic guards and tests are unchanged, including their existing failures. This test provides no universal source-hash approval and does not rewrite historical admission rules.
