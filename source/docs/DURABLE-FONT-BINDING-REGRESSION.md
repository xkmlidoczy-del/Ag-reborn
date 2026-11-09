# Durable aggregate classic-font binding regression

The no-discard shader repair now has a production-ready Java binding test in addition to the 39,286 independent raster/timing checks. The production repair patch is unchanged.

## Exact additive files to retain in production

- src/test/java/cn/academy/port/client/ClassicFontCoverageRegressionTest.java
- src/test/java/cn/academy/port/client/ClassicFontBindingRegressionTest.java
- src/test/resources/cn/academy/port/client/classic-font-checkpoints/original-text.json
- src/test/resources/cn/academy/port/client/classic-font-checkpoints/NOTICE
- src/test/resources/cn/academy/port/client/classic-font-checkpoints/LAMBDALIB-LICENSE
- docs/DURABLE-FONT-BINDING-REGRESSION.md

The supplied small gradle/live-hud-font-tests.gradle snippet should be appended or copied into the ordinary production build configuration. It adds both classicFontCoverageTest and classicFontBindingTest to check, with testClasses and sourceSets.test.runtimeClasspath. It passes the CURRENT rootDir as academy.sourceRoot. Do not leave the production build depending on a staging-script path.

The additive test/resources/doc patch and exact SHA-256 manifest are supplied in docs/durable-font-binding-additions.patch and docs/durable-font-binding-hashes.json. These files are additions; no production file has been overwritten by this worker.

## What the Java binding test checks

- Reads CURRENT ClassicHudFont.java and ClassicHudShaders.java from the explicitly supplied academy.sourceRoot, with no fallback path
- Checks ordinary glyph drawing selects ClassicHudShaders.font, preserves monochrome routing and passes that selected shader to the glyph canvas
- Checks the source font shader reset and registration callback
- Reads the ACTUAL compiled ClassicHudFont.class / ClassicHudShaders.class from the running test classpath without loading or initializing Minecraft classes
- Decodes JVM instructions in the compiled draw method, requiring real getstatic references to the font and mono shader fields. The compiled registration method must load the classic_font descriptor id
- Loads the actual classic_font.json / classic_font.fsh from the classpath, and requires their exact bytes to match CURRENT production main resource files
- Checks the exact bounded no-discard GLSL product, colored tint, vertex/fragment names, sampler, unique uniform types/counts and identity/white default values
- Checks current unchanged raster/native-alpha/mip/filter/texture-owner source obligations
- Reads the three canonical source witnesses and full notices only from test resources. It pins their SHA-256 values as literal constants in the Java test, preserving canonical no-alpha-test intent, hold-only CP/OL timing and deliberately blank editor rows

It rejects 16 intentional fail-closed mutations: vanilla font route, alpha discard, lost tint, absent registration, wrong sampler, overwritten shader output, stale classpath resource bytes, wrong fragment, wrong vertex, corrupt source witness, changed notice, missing witness, zero default color alpha, invalid shader JSON, stale compiled font field reference and changed glyph mip count.

Pinned noticed witness bytes are small original text only. No legacy checkout or media is required at test runtime. No Minecraft/GL/UI is initialized by the Java binding test. Gson is already an existing Minecraft/NeoForge dependency; no new dependency is added.

## Validation evidence

- Cached JDK21/API compile passes
- ClassicFontCoverageRegressionTest: 39,286 independent raster/coverage/timing checks pass
- ClassicFontBindingRegressionTest: 595 source/classpath/witness checks pass; 16 mutants rejected
- Existing actual font ownership test: 1,172 assertions pass
- Existing standalone Python source/mutation check: 11 tests pass
- Previously established isolated GLSL/EGL shader evidence still passes

An independent temporary recovery project copied only current source files, compiled font/shader/test classes, shader resources and canonical noticed test witnesses. The Java binding test passed with only Gson added to that minimal classpath. It had no Minecraft/NeoForge runtime jars, staging path or original checkout path on its classpath. Missing academy.sourceRoot and missing witness resources failed closed. Putting the actual older production-compiled ClassicHudFont first on the classpath also failed closed on the compiled no-discard shader field obligation. Log: durable-binding-recovery.log in the staging deliverable.

The aggregate Java tests themselves do not use Python, software EGL, Gradle staging scripts, default staging paths or the cached original source checkout. The cached verification script is a preparation convenience; it passes the staged root explicitly while production Gradle passes rootDir.

## Serial integration and remaining limit

1. Verify the original four-file repair's baseline hashes, then apply live-hud-font.patch
2. Apply durable-font-binding-additions.patch, retaining both Java tests and all three noticed test resources
3. Add the supplied Gradle task declarations into production build.gradle or another normally preserved production configuration file
4. Run the parent's normal serial aggregate checks and fresh m13 client verification

The Java checks establish current source/compiled-class/resource/witness agreement. They do not claim actual Minecraft shader registration, rendering parity, resource-reload behavior or screenshots. Those remain the integration owner's serial live-client verification responsibilities. CP/OL timing and intentionally blank empty preset rows remain unchanged.
