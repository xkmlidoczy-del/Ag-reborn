# NEW252 frozen .57 renderer companion

`scripts/verify-zero-gap-armor-v57-fallback.py` compiles ten complete production
renderer, state, material and geometry files independently from frozen commit
`f07214e561359f83ccdc8b66411b0884e6d10cbf`. The frozen files were obtained with
`git show <commit>:<path>`. Their SHA-256 values, original resource hashes,
case table and complete compressed submission capture are immutable inputs.
The runner has no refresh or bless operation.

The ordinary invocation additionally compiles the current renderer and its
explicit optional dependency closure. Every OFF or failed-material lane must
match both the frozen per-case table and the entire decompressed capture.
Twenty-five lanes cover OFF with a successfully registered shader, missing/corrupt
original resources, forced fallback, unloaded config, compile/link failures,
pending/stale registration, all four missing required uniforms, and generation
or availability loss during frame planning. Eight additional lanes mutate the
actual shader JSON type or count for each required uniform; two more combine
malformed descriptors with config OFF. A separate enabled smoke lane must
actually submit the optional shader while preserving every receipt/lease-state
capture and the original startup, break, ending, and empty-render output.
`--frozen-only` verifies the frozen capture and mutation controls alone and
reports a distinct result. It never establishes current fallback equivalence.

Example:

```sh
python scripts/verify-zero-gap-armor-v57-fallback.py \
  --java-home .tools/jdk-21.0.12.1+1 \
  --report /tmp/zero-gap-armor-proof
```

Choose a fresh report directory for every run. The report includes exact source,
host, resource and Java toolchain receipts, compiled-class inventories,
per-case hashes, full captures, differences for each negative control, and
`evidence.json`. Inputs are checked again before a PASS result is written.

## Evidence boundary

The harness calls the real `ZeroGapArmorEffects.receive`, `tick`, and `render`
methods. It records raw and transformed coordinate bits, UV bits, RGBA, vertex
order, texture and render-state declarations, buffer requests and flushes,
Folded Mirror uniform calls, accepted frames, contact ages, replay/lease state,
and controlled input-host notifications. Matrix, pose and shader-color state
must be restored after rendering.

The 810 frozen cases contain 618 nonempty captures, 190,108 emitted vertices,
and 2,236 batches. They cover all eight funded subsets across six facing yaw
choices and six physical phases; startup, activity, all four break counters,
ending/fade, ordinary/breaking/corner/historical HIT receipts; ages 0 through 7;
near-plane, inside/upward, transformed and world-border views; and cleanup,
replay, pause, ownership and queued-session invalidation.

Five accepted owners demonstrate the actual four-owner rendering cap. A separate
case deliberately supplies twelve sets of real baseline mesh faces to the real
post-clip ordering and private submission methods to reach exactly 768 quads.
That is a bounded budget test, not a claim that four ordinary owners naturally
reach the cap.

Four independently compiled negative controls alter one coordinate, one UV,
one color channel, or the live-mask mapping. Every control must change the full
capture and at least one case. Nonvacuity checks apply to ordinary captures;
the two exact budget-count assertions are disabled in mutants because a
live-mask mutation legitimately reduces their injected face count.

A fifth negative control restores a 65,536-byte texture bound. It must reject
the authentic 102,384-byte `mdshield.png` before shader construction. This
retains a concrete readiness regression found while building the companion.
Two further controls remove only the production type or count comparison.
The corresponding native-shaped integer VisualPhase (type 0/count 1) and
short ModelViewMat buffer (type 10/count 1) must expose the missing rejection.

## Explicit limitations

The `hosts` directory provides only the referenced Minecraft, transport,
eligibility, GL, shader, vertex-buffer and affine-matrix methods. It is a closed
CPU fixture inventory; it is not the game runtime. Required unknown uniform
queries and undeclared source dependencies fail closed. The production source
closure, including original `FoldedMirrorMaterial` and `FoldedMirrorRenderTypes`,
is compiled unchanged.

Shader and original texture bytes are actual, hashed resources. The shader host
extracts uniform names, types and counts from the actual JSON declaration. Its
`Uniform.getType()` and `getCount()` reflect the native IDs and vector-count
adjustment verified in the pinned NeoForge 21.1.252 sources. Typed lanes change
one JSON descriptor and use a single broadcast value, which is valid under the
native JSON parser, so they cannot pass merely because a values-array length
error prevented construction. Unknown uniform queries fail closed; float writes
to integer descriptors and writes exceeding their declared buffer count fail.
The original immutable .57 source and full capture hashes remain unchanged. Compile/link
outcomes and missing-uniform injections are controlled host conditions. The
mid-frame guard tests use an explicit one-shot chunk-query callback; they do
not reproduce native resource-reload scheduling. No native shader compilation, GPU
pixels, driver state, real engine batching, server gameplay, or historical test
acceptance is established by this companion. Java 21 source/target mode is used
because the supplied installation lacks `ct.sym`; this does not claim
`--release 21` public API compatibility.
