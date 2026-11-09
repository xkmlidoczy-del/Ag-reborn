# NEW223 native learning registration witness, v19

The seven current native learning GameTest sources retain their original tests,
mechanics assertions, ordinal/ID checks, annotations, fixtures and methods. Only
25 named registration clauses have changed for the append of
`scatter_locator_plate`: high-power `ALL`/`IDS` 51→52, registered catalog 101→102,
presets 86→87, and configuration keys 92→93. The current `ALL` clauses additionally
assert the exact new record at ordinal 51: Meltdowner LV2, controllable, learned
`folded_mirror_array` parent with mastery 0, any-learned-level 0, proposal 223.
The other clauses check the new catalog ordinal 101 or exact membership/key.

`baseline-v79/` contains byte-exact copies obtained from Git commit
`27ecc8d869ae6389253c2e5c8fe4df68b6291424`. The prior seven GameTest sources and
their old totals remain intact here. The registry copy and
`registry-records-v79.json` retain all 51 old record bytes, ordinals, saved IDs and
proposal numbers. `contract-v19.json` records their hashes, the exact reversible
edits, current source hashes and the complete 22-file native learning inventory.
The verifier pins the contract hash and checks every snapshot against Git.

Run from the repository root, choosing a fresh report filename:

```sh
python3 scripts/verify-scatter-locator-native-v19.py \
  --report build/reports/scatter-locator-native-v19-new-run.json
```

The source proof reconstructs the seven current files and reverses all 25 edits
back to their authentic historical bytes. The other 15 historical native
learning sources must remain byte-identical. Its 142 negative controls reject
old ordinal changes, missing or wrong NEW223 records/parents/proposals, stale or
inflated totals, omitted membership checks, altered mechanics assertions,
disabled test annotations, changed snapshots and changed evidence.

The unchanged v17 verifier separately checks the exact 51+1 registry append and
its 582 controls, including 568 registry mutations. Existing historical scripts,
test runners and fixture pins are retained unchanged. There is no refresh mode.

This is a source and registration proof. It launches no Gradle, Java compiler,
JVM, server or client. Native GameTest execution, gameplay, visuals and aggregate
check status must be reported independently.
