# TEST0.78 source-scope companion v15

`scripts/verify-remote-scatter-relay-feedback-v15.py` is an independent,
CPU-only source audit against the complete immutable TEST0.77 tree at
`d6c9eed2544f4743aefd994635cc189121cd5dcb`. It does not replace the v14
Charging differential, change any old guard, or establish a full `check` pass.

## Exact scope

The baseline has 6,100 tracked inputs. At candidate
`74850efe6836924e8a830819f451606c06808b87`, 6,087 are byte-identical. The
13 exceptions are individually constrained or explicitly identified:

- `build.gradle`, `META-INF/neoforge.mods.toml`, `VanillaCombatContract.java`,
  `VanillaMiningContract.java`, and `HighPowerCombatRuntimeTests.java` each
  permit exactly one `0.1.0-m41-highpower.0.77-prototype` to
  `0.1.0-m41-highpower.0.78-prototype` replacement. The entire remaining file
  must match the independent .77 bytes, and reversal must reproduce .77.
- The English, Simplified Chinese, and Traditional Chinese language files
  permit exactly seven added relay keys (`placing`, `clearing`,
  `segment_too_far`, `too_close`, `body_blocked`, `no_space`, `unloaded`) and
  two changed relay values (`measuring`, `too_far`). All retained source
  lines must be byte-identical, including every other key and formatting.
- `RemoteScatterRelay.java`, `RemoteScatterRelayEffects.java`,
  `RemoteScatterRelayState.java`, `RemoteScatterRelayVisualRegressionTest.java`,
  and `RemoteScatterRelayGeometryRegressionTest.java` are the only existing
  relay implementation/test files permitted to differ. Their baseline/current
  hashes are recorded for identification. This audit does not certify the
  behavior inside those files; native tests and client QA remain separate.

The only allowed additions are the placement runtime test, the TEST0.78
feedback document, and this companion's script, document, and evidence JSON.
Untracked main/test source additions are included in the inventory. Generated
Python bytecode under `scripts/__pycache__` is excluded. Every original .77
input outside the 13 exceptions must still exist and match its original Git
blob bytes; no replacement hash list is generated or accepted.

This preserves the entire registry, progression, classic catalog, assets
outside the listed relay translations, other skills/gameplay, build wiring,
old tests, old runners, old fixtures, and historical evidence byte for byte.
In particular, the relay geometry/rules and all non-relay production gameplay
remain .77 bytes, except the two explicit runtime version literals above.

## Registry proof and controls

The immutable v14 runner is read only after its source is checked against
.77. Its existing registry-only functions reconstruct the prior chain and
current registry, reject its 601 registry mutants, exercise the unchanged
v13 rejection and 589 v13 registry mutants, and reject 52 virtual predecessor
tamper cases. The full v14 main/differential is not launched.

The current registry is exactly the existing .77 registry: 51 before and 51
after, with zero new skills. Its SHA-256 is
`7b7c560aec52fba4884d07c48d89346667847131075d9cd183934592d4b9d104`.
The predecessor rejection remains `REGISTRY_233_NONAPPEND_CHANGE`.

The new companion also rejects 21 virtual mutations: extra edits and wrong
versions in each of the five version files; outside-key or outside-line
language edits in each of three locales; and changed registry, relay rules,
classic catalog, v14 runner, and original contract bytes. No real source or
fixture is mutated by these controls. Inputs are re-read before success to
reject changes during the audit. A fresh report file is mandatory; failure
writes a failed result instead of retaining an old passing report.

## Reproduction and limitations

```
python3 scripts/verify-remote-scatter-relay-feedback-v15.py \
  --report /tmp/relay-feedback-v15-fresh.json
```

Recorded evidence is
`docs/runtime-evidence/remote-scatter-relay-feedback-companion-v15.json`.
Its status is `PASS_SOURCE_SCOPE_ONLY`. This command launches no Gradle,
compiler, JVM, server, or client and is safe to run alongside separate client
QA. It checks source bytes, not compiled classes, executed M41 traces, native
behavior, screenshots, multiplayer performance, or gameplay acceptance.

Historical guard failures are preserved as separate results. There is no
hash-refresh option and no aggregate-green claim.
