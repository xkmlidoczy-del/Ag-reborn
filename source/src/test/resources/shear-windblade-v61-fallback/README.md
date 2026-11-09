# Independent .61 NEW421 fallback proof

Run from the repository root with Java 21:

```sh
python3 scripts/verify-shear-windblade-v61-fallback.py --report /tmp/shear-v61-proof-new
```

The report directory must not exist. `--frozen-only` runs the frozen baseline and four rendering mutation controls; it never claims current fallback equivalence. The script has no refresh or golden-update mode.

## Provenance and execution

`frozen/` contains four complete, unmodified production files extracted with `git show 917d234a19c5def7e4225904802f6638d1ad6661:<path>`: `ShearWindbladeEffects`, `ShearWindbladeRenderGeometry`, `ShearWindbladeState`, and authoritative `ShearWindbladeGeometry`. `provenance.json` and the script pin their SHA-256 hashes and the original `glow_line.png` hash. The current receipt-state and authoritative-geometry files must remain byte-identical to those originals.

Both lanes compile into separate empty class directories. Compilation explicitly disables annotation processing, implicit compilation and source lookup; only the declared complete production sources and the hash-pinned CPU host inventory are available. The current lane also compiles the complete optional volume geometry, material, pass, and client config. No host replaces any of these production classes. Class inventories and all input/runtime/compiler hashes are receipted in each report.

The Java harness drives the real packet-to-frame conversion, real receipt validator, accepted-controller state, lifecycle methods, and real public renderer. CPU platform hosts provide world/player identity, controlled input notifications, chunks/frustum, the original texture stream, JOML-shaped float matrix arithmetic, and renderer buffers. A `VertexConsumer` records raw and transformed coordinate float bits, UV float bits, RGBA, and call order. Buffer hosts record primitive format/mode, texture, shader, blend/depth/cull/write-mask/output-target choices, buffer requests, submissions, and flushes. Every case also captures full controller/fence/visual-owner/input state and verifies pose/model-view/shader-color restoration.

The pinned baseline is 1,351 cases, 1,008 nonempty, 86,208 vertices and a maximum of 512 classic vertices. The compressed full capture's *decompressed* SHA-256 is `0f814650741400a75b5010478c905cac5fe8a4add8ab2ec6d1e3dcb80816aefe`; the case table SHA-256 is `105c8300394a035396f5ac0991747ecbf38a2760b78a7cdfa47aca85aeea78d4`. Both must match a freshly compiled frozen renderer before current comparisons run.

## Coverage and negative controls

- Eight camera/world scenes: ordinary, near eye, inside the front, world border, beyond cull distance, transformed pose, side view, and upward cast
- Early/mid/range-end travel, travelling/blocked/expired fronts, horizontal/vertical/dual fronts, six ages, and interpolation boundaries
- No-controller/no-front/ended/stale/behind-plane empties, duplicate and consecutive receipts, stopped-front revival rejection, pause/death, owner/context invalidation, queued session changes
- Four accepted controllers and a fifth-owner eviction case, with the real .61 output reaching 512 vertices
- Exact raw capture comparisons for OFF, forced fallback, missing/corrupt original texture, unloaded config, pending reload, stale success delivered after newer failure, and generation/config changes during the first chunk query
- Enabled integration must alter output, preserve every captured receipt state, restore render state, respect the 2,048-vertex cap, and retain zero emitted vertices for all classic-empty cases
- A separate two-controller chunk-boundary case places one vertical front at x=15.83. Its original .15 bounds remain loaded while its enhanced shoulder enters unavailable chunk (1,0). All lanes, including enabled, must reproduce both frozen classic controllers exactly; removing the whole-pass missing-chunk refusal must fail
- Five supplemental exception cases exercise first/partial vertex, typed flush, final flush and combined vertex/final-flush failures through every renderer lane. They assert exact exception propagation, model-view/pose/color/receipt restoration and an identical next successful draw. Their traces live in separate `exceptions.txt` files and never change the pinned baseline captures. A nested-finally-removal mutant must fail only the exceptional-flush restoration check
- Separately compiled coordinate, UV, color and classic-width mutants must differ from the frozen capture; removed resource-hash and stale-generation guards must fail their targeted material-lane assertions

## Limits

This is an actual compiled CPU submission differential, not a source-text assertion and not a native pixel comparison. Platform hosts are explicitly listed and pinned; they are not Minecraft, JOML or OpenGL binaries. Native translucent sorting, shader execution, driver behavior, native visuals, input/network eligibility, authoritative damage, and broad gameplay compatibility require separate checks. The builtin shader's selected identity and render configuration are traced; no custom shader compilation is simulated. Java 21 source/target flags are explicit; this does not claim a `--release 21` public-API check.
