Isolated NEW422 Post identity proof collaborators

Run scripts/verify-anchor-post-proof.py with a JDK 21 installation.
The script compiles the actual production AnchorDamagePostProof.java alongside
these deliberately minimal collaborators into a fresh temporary directory.
These Java sources are resources, not project classes, and must never be added
to the production or normal test compile classpath.

Coverage: object identity, one-use receipts, synthetic and same-object nested
posts, accepted nested native hurt, defense/accounting-guard separation, callback
exceptions, unavailable observer, unapproved bus and bounded-stack refusal.

The fixture EventBus reproduces only the exact private dispatch signature so
the real production bounded StackWalker can be exercised. It is NOT evidence
that a Minecraft or NeoForge transformation ran. AnchorPostNativeSeamRegressionTest
pins actual cached NeoForge/bus bytecode and optionally inspects real exported
transformed classes; integrated GameTests verify real combat accounting.
