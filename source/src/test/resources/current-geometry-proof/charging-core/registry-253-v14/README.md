# Charging core companion v14: NEW253-only registry append

This independent companion admits only `remote_scatter_relay` (proposal 253)
after the exact 50 registrations preserved by v13. The appended record is
Meltdowner LV5, controllable, with `FOLDED_MIRROR_ARRAY` at zero mastery and no
alternate learned-level requirement. Its high-power ordinal is 50; its combined
catalog ordinal is 100. All prior fields, IDs, proposals, prerequisites and
ordering remain byte-exact.

`HighPowerSkills.v76.java` comes from commit
`80a1f7d95dda7404b6ce153a7ffd40841db67e7c`, captured before NEW253 editing. Its bytes
also equal v13's reconstructed output. It was not derived by stripping the
new registration from current source.

`contract-v14.json` permits exactly three reversible edits: the NEW253 constants,
its final `IDS` entry, and its final `ALL` record. Applying them must reproduce the
whole current source byte for byte; reversing them must restore the frozen
predecessor. The existing v2–v13 reconstruction chain is verified first.

The new runner is `scripts/verify-charging-m41-core-v14.py`. The unchanged v13
runner and all previous contracts, fixtures, trace drivers and evidence remain
available separately. V14 pins 52 predecessor inputs and virtually tampers with
each one. It requires unchanged v13 to accept the exact .76 source, reject NEW253
with its original marker, and reject its original 589 registry mutants.

V14 requires 601 registry mutants to fail: six fields across 51 records, all 51
saved IDs, 50 proposal constants, both identity and experience for 47
prerequisites, and both adjacent-order variants across 50 pairs. It reproduces
the pinned old and new registry classes, and compares the new class to the
actual `compileJava` output. The executed positive remains the actual output,
not the evidence recompile.

All original M41 archive, non-registry source/class, dependency, classic catalog,
closed-loader and trace-driver pins are unchanged. No hash-refresh mode exists.
A fresh report directory is mandatory. Historical geometry failures and the
v13 current-registry rejection remain separate results; this does not establish
an aggregate `check` pass.

After the coordinated build has produced main classes/resources/classpath:

```
python3 scripts/verify-charging-m41-core-v14.py --jdk /path/to/jdk-21 --report /tmp/charging-core-v14-fresh
```

Full differential validation is pending the coordinated run. Source
reconstruction and syntax checks are separate from executed M41 equivalence.
