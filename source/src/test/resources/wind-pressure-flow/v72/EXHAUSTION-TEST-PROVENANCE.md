# Isolated exhaustion-test companion provenance

`HighPowerWindPressureLiftRuntimeTests.java.txt` is the exact source blob at
`src/main/java/cn/academy/port/gametest/HighPowerWindPressureLiftRuntimeTests.java`
in commit `f7399a6cdec425aa1ca2b685a8d5d961ae33112e`.

SHA-256: `b3fcc01c914932889d0ccf5bb440cea56e9b362512cc8994a585b465d528384b`

The v2 companion permits exactly one source replacement: the exhaustion test's
`@GameTest` batch changes from `BATCH` to `BATCH+"_exhaustion_order"`. The timeout,
20-tick closure assertion, resource assertions, fixture, and every other byte
remain unchanged. Restoring that annotation must reproduce this frozen source.

The existing `verify-wind-lift-flow-v72.py` verifier is unchanged and pinned by
SHA-256 `97705b5417df799cda13f56db3a607838f931f8e03e6467aeccc0d8e55536572`.
It continues to execute against the actual distributable JAR and the original
class/source/texture evidence, with no new class-hash exceptions.

GameTest classes were excluded from the .72 and .73 distributable JARs and are
absent from `class-hashes.json`. The new test-class proof therefore compiles the
restored and current sources separately with the existing JDK 21 and native
classpath. It requires identical class families with only the top class changed,
then requires the current family to match `compileJava` byte for byte. These
reconstructed baseline class hashes are not a historical compiled-class archive.

The v2 proof also requires malformed batch edits, changed timeout, delayed
20-tick assertion, weakened closure assertion, changed CP depletion, and altered
or missing compiled test classes to be rejected. A native GameTest run remains a
separate required validation; this identity proof does not substitute for it.
