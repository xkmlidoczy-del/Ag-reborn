# Immutable Meltdowner .53 renderer

`ClassicMeltdownerBeamEffects.java.txt` contains the exact bytes of
`src/main/java/cn/academy/port/client/ClassicMeltdownerBeamEffects.java` from commit
`8cbf2fc49cf5c28231647856eb3033f2614cdaf9` (the .53 source before the optional MD near-eye integration).

SHA-256: `b02f2dd687881d80d4c50689821f1a16619483623147db3cd9dd089b394fd1c4`

The fixture was captured from that Git object before the .54 production edits.
It is not a regenerated current-source oracle. Do not refresh it when production
changes. `scripts/verify-md-near-eye-integration.py` verifies the fixed SHA-256,
and additionally compares the Git object when available. Archive-only source
copies retain the same independent byte oracle.

The script compiles this original and the unchanged current renderer into
separate temporary class directories. It does not rewrite either emitter or
substitute a reconstructed emitter body. Recording Minecraft hosts are explicit
and shared between those executions; pinned native JOML performs matrix math.
