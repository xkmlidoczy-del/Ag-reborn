# Terminal projection and Settings acceptance

This narrow acceptance increment follows the complete terminal/app port. Its reference is AcademyCraft classic1.0.7 commit `00d19ec0cf538f61c1095c9292f5ee6863db4521` and LambdaLib1.2.3. The unchanged reference files and full GPLv3/MIT notices are packaged under `src/test/resources/classic-terminal-acceptance`; the test does not need a `.reference` or `.staging` directory.

## Right edge at 1180 × 812

The clipping follows the original perspective. AcademyCraft `TerminalUI.java:170–205` uses the actual display aspect, a 50-degree vertical perspective with near/far 1/100, translation `(.35 * aspect, 1.2, -4)`, pivot `(1,-1.8,0)`, the three source rotations, inverse pivot, and model scale `(1/310,-1/310,1/310)`. There is no aspect-dependent fit or margin correction. `terminal.xml` defines the back as 640 × 785.

Executing the unchanged original `TerminalUI.draw` with its initial virtual pointer `(150,150)` and animation time 0 gives the following points at 1180 × 812:

| Source point | Normalized device X | Physical X |
| --- | ---: | ---: |
| Back top-right `(640,0,0)` | 1.05936446 | 1215.02503 px |
| Username right anchor `(600,41.66666666666667,15)` | 1.00037824 | 1180.22316 px |

The viewport ends at physical X1180 / normalized X1. The frame therefore crosses the right edge by about35 pixels. The exact visible text edge also depends on the selected system font and glyph raster bounds; the frame proof and text anchor do not require font assumptions. The source rotations vary with pointer position and time, so these are fixed witnesses, not bounds for every animation frame.

At1440 × 810 (16:9), the same original frame corner has normalized X0.90494231 and both listed anchors fit. The port's current JOML pose/projection expression matches the captured original GL chain within float precision over500 independent aspect/pointer/time frames and10,000 projected local points. The increment preserves this source behavior.

## Settings activation and background drawing

AcademyCraft `AppSettings.java:33–40` creates an environment whose `onStart` only calls `Minecraft.getMinecraft().displayGuiScreen(new SettingsUI())`. `TerminalUI.java:472–485` injects `env.app` and `env.terminal` and calls `env.onStart()` without disposal. `TerminalUI.isForeground()` returns false.

LambdaLib `AuxGuiHandler.java:85–92` draws each undisposed AuxGui during the EXPERIENCE overlay event. It has no `currentScreen` condition. Its draw handler also does not suppress background drawing when the game is paused; pause affects the tick cleanup in `clientTick`, not the draw traversal. Opening Settings therefore retains the perspective terminal behind the ordinary screen. Dismissing Settings returns to that terminal. By comparison, the original Frequency Transmitter's `AppFreqTransmitter.onStart` calls `TerminalUI.passOn(new FreqTransmitterUI())`, which explicitly disposes and replaces the terminal.

The executable witness invokes the unchanged original left-click handler and `AppSettings.onStart`, verifies that Settings opens without disposing the original terminal, and executes the unchanged `AuxGuiHandler.drawHudEvent` behind both paused and unpaused Settings. Explicit disposal stops those draws. The modern activation/render bindings are checked against this result.

## Three exact text Y corrections

The adaptation did contain a separate coordinate error. LambdaLib `TextBox.java:239–244` computes its vertical origin as `Math.max(0, widget.transform.height - option.fontSize) * heightAlign.factor + yOffset`. When font size exceeds the widget height, CENTER adds zero.

| Text | Source widget height/font size | Previous port Y | Correct source Y |
| --- | --- | ---: | ---: |
| TERMINAL | 30 /40 |69 |74 |
| DATA |30 /40 |37 |42 |
| App label relative to tile |21 /32 |142.5 |148 |

The correction changes only those three Y constants in `TerminalHud`. Username/app-count anchors, perspective, aspect translation, rotations, source art, and activation lifecycle retain their existing values. The exact unchanged private `TextBox.origin` method body executes in the portable witness using the original XML dimensions; this confirms the zero clamp rather than duplicating a corrected formula.

## Verification and limits

`terminalAcceptanceProjectionTest` compiles and executes six unchanged original classes (`TerminalUI`, `AppSettings`, `App`, `AppEnvironment`, `AuxGui`, `AuxGuiHandler`) against finite game/GL/GUI boundary shims. The GL shim implements independent column-major double postmultiplication and matrix-mode stacks; it invokes no native GL. A compiled probe uses the exact current `TerminalHud` JOML expressions with the cached official JOML dependency. The original `TextBox.origin` method body is extracted verbatim into a finite field model. The complete `CGui`, `TextBox`, `TrueTypeFont` and XML files are preserved as attribution/inspection witnesses; only `TextBox.origin` from those three implementation files executes.

The final corrected staged file compiled against the cached official Minecraft1.21.1 /NeoForge21.1.252 API. The packaged promoted-layout test passed46,037 assertions. Running the same test against the then-current uncorrected production layout failed specifically at the three source-origin coordinates, after the original projection and Settings checks had passed. That negative control demonstrates that the test detects this actual adaptation error.

This increment does not claim a new live/native/client/pixel pass. Main owns the serial client lane and can collect corrected screenshots after applying the narrow patch; source-compatible16:9 and the observed1180 × 812 aspect are both useful checks. It adds no media content or acquisition semantics.
