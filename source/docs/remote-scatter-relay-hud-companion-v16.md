# TEST0.79 HUD source-scope companion v16

`scripts/verify-remote-scatter-relay-hud-v16.py` is a new, independent,
CPU-only source audit against the complete immutable TEST0.78 tree at
`0e7b5a4fe37195d1b383d0d57b324b6988e2c18f`. Its only success label is
`PASS_SOURCE_SCOPE_ONLY`. It does not replace any historical guard, establish
an aggregate `check` pass, or prove the behavior of the new HUD.

## Exact baseline and allowed changes

The baseline contains 6,105 tracked inputs. A passing candidate preserves
6,092 of them byte for byte. The remaining 13 paths have explicit constraints:

- Four files permit exactly one `.78` to `.79` artifact-version literal:
  `META-INF/neoforge.mods.toml`, `VanillaCombatContract.java`,
  `VanillaMiningContract.java`, and `HighPowerCombatRuntimeTests.java`.
  Reversing that literal must reproduce the entire baseline file.
- `build.gradle` permits the same one-literal change plus the exact appended
  `remoteScatterRelayHudLayoutTest` JavaExec registration and `check`
  dependency, including its comment. All previous build wiring is unchanged.
- `RemoteScatterRelayEffects.java` permits 12 exact lifecycle/import hooks
  for result-note presentation and replacement of the HUD callback region
  with its two named text/reservation helpers. Reversing the hooks restores
  the original pre-HUD bytes. The note-only generation field, captured value,
  and receipt gate prevent pre-reload work from restoring cleared text;
  their exact additions are included in the 12 hooks. `Flash` and `Marker`
  definitions and the entire
  world-render tail, starting with the `loadedPath` documentation, must match
  .78 byte for byte. The changed HUD region is hashed for identification;
  its behavior is tested separately.
- `RemoteScatterRelayState.java` permits only insertion of the `ResultNote`
  nested-class region immediately before the existing `Lease` documentation.
  Removing it must restore the whole original file. All original receipt
  fences, geometry validation, constants, status mapping, and the complete
  existing `Lease` body remain byte-identical.
- `HighPowerRecoveryHud.java` permits only the exact read-only
  `relayReservedBox` accessor and its comment. It delegates to the existing
  layout method; all original recovery code is byte-identical.
- `AcademyClient.java` permits exactly one `clearResultNote` call immediately
  after the original relay input-begin call. Removing that one line restores
  the entire input handler and all other client code byte for byte.
- `RemoteScatterRelayVisualRegressionTest.java` permits only the new
  `resultNotes` method and one invocation, plus exact replacement of the
  obsolete one-line `fittedScale` source assertion with wrapping, accepted
  receipt, cleanup, and input-clear integration assertions. Reversing those
  changes restores the entire original test source. All other prior
  assertions, including gameplay and geometry tests, are preserved.
- The English, Simplified Chinese, and Traditional Chinese locale files
  each append exactly 37 relay HUD keys: 19 `brief`, six `help`, six
  `help_short`, four `result`, `history_title`, and `last_result` keys. All
  original values and bytes remain unchanged except the necessary comma
  after the previous final property. Duplicate or unknown keys fail.

Only six new paths are admitted: `RemoteScatterRelayHudLayout.java`, its
regression test, `docs/remote-scatter-relay-hud-v79.md`, this companion's
script and document, and its evidence JSON. Every non-report addition is
hashed in the output, including the runner itself. A report cannot hash
itself; it is explicitly identified as the output rather than an audited
behavioral input.

The inventory includes all baseline paths, ordinary Git-visible additions,
and ignored additions under `src/main` and `src/test`. There are no old-input
exclusions and no Python-cache exemption. Ordinary ignored build/run output
is not source input. An untracked `scripts/__pycache__` must be removed before
running this proof. Every audited file must be regular and present, and its
bytes are checked again before a pass to detect concurrent edits.

Consequently, all server logic, relay rules, geometry, registry/progression,
protocol, classic HUD, other skills, non-language assets, original tests,
historical runners, fixtures, pinned contracts, and historical evidence
remain original .78 bytes, subject only to the explicit version exceptions
above. No new expected hashes are substituted for any baseline input.

## Negative controls and predecessor separation

The new companion rejects 36 virtual mutations without touching live files:
extra or wrong version changes in all five version-bearing files, changes
to old build-task wiring, old locale values/formatting, unknown locale keys,
server level logic, relay lifetime rules, registry bytes, classic catalog,
old runners and contract, world-render code, marker flash capacity, the
existing receipt gate, original state constants, original recovery layout,
an old input-handler statement, and an old visual-test invocation. Extra
members outside the new nested class or three HUD methods are also rejected;
a string/comment-aware brace check constrains these boundaries. Each control
must fail with its specific marker.

The already byte-checked v14 runner's registry-only functions reconstruct
the existing chain and exercise 601 v14 registry controls, 589 unchanged v13
registry controls, and 52 predecessor tamper controls.
The registered count remains 51 before and 51 after, with zero new skills.
The full v14 Charging differential is not launched.

The unchanged v15 source-scope runner is also called and must reject the
.79 additions with `UNAPPROVED_ADDED_INPUT`. This expected failure is reported
separately as `unchanged_v15_current_scope`; it is never relabeled as a v15
pass. Older failures and evidence remain intact. The new v16 pass covers only
the stated .78-to-.79 source delta.

## Reproduction and interpretation

After the candidate's source and documents are finalized, run:

```
python3 scripts/verify-remote-scatter-relay-hud-v16.py \
  --report /tmp/relay-hud-v16-fresh.json
```

The checked-in report destination is
`docs/runtime-evidence/remote-scatter-relay-hud-companion-v16.json`.
The destination must not already exist: exclusive creation prevents a stale
pass from standing in for the current invocation. Failure records `FAIL`
and its reason in the fresh report. There is no refresh or exclusion option.

This proof starts no Gradle, compiler, JVM, server, or client. HUD layout,
25-tick result-note correctness, native font rendering, GUI scales, visual
readability, fallback appearance, and gameplay acceptance require their own
tests and client evidence. Unchanged source bytes do not establish identical
compiled class bytes or certify newly introduced code behavior.
