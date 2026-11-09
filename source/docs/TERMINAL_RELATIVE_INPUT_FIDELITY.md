# Terminal relative-input acceptance

This narrow increment follows the terminal projection acceptance. Reference: AcademyCraft classic1.0.7 commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`, LambdaLib1.2.3, and the cached official Minecraft1.21.1 /NeoForge21.1.252 source artifact. It corrects the modern input transport while preserving the source virtual pointer, selection order, activation and projection.

## Original input and the modern boundary

Original `TerminalMouseHelper.mouseXYChange` assigns `dx = Mouse.getDX()` and `dy = Mouse.getDY()`. `TerminalUI.onAdded` installs that helper; `onDisposed` restores the previous helper. `TerminalUI.draw` first computes the selected3×3 cell from the previous virtual `(mouseX,mouseY)`, performs edge scroll, then applies `mouseX += helper.dx * 0.7` and `mouseY -= helper.dy * 0.7`, clamps to605×740, balances the rendered pointer, and resets helper deltas to0. LambdaLib's dynamic `KeyManager` invokes the original `LeftClickHandler.onKeyUp` on a physical release; the handler starts the selected app environment. The selected cell is deliberately one rendered update behind a newly applied motion sample.

The existing modern Y sign was correct. Legacy LWJGL relative Y is positive upward; GLFW cursor Y is positive downward. Adding the modern down-positive relative Y therefore matches subtracting original `Mouse.getDY()`.

The original modern adapter derived movement from `MouseHandler.xpos() - lastX` and `ypos() - lastY`. Those values contain more than movement. The exact official `MouseHandler` methods demonstrate:

- `onMove` writes xpos/ypos on an ignored first callback, but leaves accumulated movement zero
- `onMove` writes xpos/ypos while the window is inactive, but only accumulates motion while active
- `grabMouse` resets both positions to screen center and requests an ignored first callback
- `releaseMouse` also resets the positions to screen center
- `cursorEntered` marks the next callback as a baseline without movement

Differencing those cursor positions can invent a large virtual pointer change during focus, grab or foreground-screen transitions. This is a confirmed platform adaptation flaw. Whether it caused the particular live lower-row CUA failure remains provisional until the main serial observations establish the same transition; this increment makes no claim of that live causation.

## Narrow correction

Minecraft's `handleAccumulatedMovement` calls `turnPlayer` while the actual window is active, the mouse is grabbed and a player exists, and then clears `accumulatedDX/accumulatedDY`. `turnPlayer` posts NeoForge's `CalculatePlayerTurnEvent` before sensitivity calculations. The official public `getXVelocity/getYVelocity` return the unscaled accumulated relative values at that point.

The existing terminal turn hook now captures those two values in a small `TerminalPointerInput`. The HUD consumes the captured pair once after the original selection/edge-scroll calculation, applies the original0.7 sensitivity/clamps, and then uses the existing balance and projection. Foreground rendering drains pending motion without applying it; disposal clears it. Multiple pre-render relative samples accumulate and fractional GLFW motion is retained. Nonfinite samples are rejected locally.

The correction does not call a native cursor setter or alter grabbed state, frame size, pointer position through a QA hook, app state, app hitregions or projection. It retains the existing mouse release activation and camera suppression. Main's gated observations remain present; their raw cursor fields query xpos/ypos directly after the obsolete lastX/lastY fields are removed.

## Finite verification

`TerminalInputRegressionTest` executes the unchanged original `TerminalMouseHelper` and `TerminalUI` against finite GL/GUI/LWJGL boundary shims. It compares20,000 original input frames against the actual staged `TerminalPointerInput` and an extracted exact current HUD input/balance prefix. The comparison covers direction,0.7 scaling,605×740 clamps, edge handling, the source selection-before-delta order, fixed initial balance time origin and delta draining.

Both genuine lower-row cell indices3/4 are reachable with ordinary relative motion. From the default virtual `(150,150)`, captured native down-positive deltas `(-100,+220)` and `(+200,+220)` reach those cells respectively after the source next-frame selection update. The app order and actual server activation remain the installed-app implementation's responsibility; the test sets neither production app nor production pointer state.

Six exact official methods (`onMove`, both velocity accessors, `grabMouse`, `releaseMouse`, `cursorEntered`) execute in an isolated enclosing field model. Its native grab function is a finite no-op. The methods prove ignored, inactive and rebase position changes have zero relative velocity while true active movement retains its delta. These fixtures are pinned independently in `classic-terminal-input/source-manifest.json`. The test reuses the already promoted immutable projection acceptance witnesses and full source notices; it requires no `.reference` or `.staging` directory at runtime.

The cached official API compiler covers nine sources, including the modified client/HUD and new input helper. Four portable packaged suites passed331,850 counted assertions: new input140,045; original client-source145,611; source/resource integration157; projection46,037. Main owns Gradle, native and fresh-client acceptance. Observation-only logs should record actual focus/grabbed state, pre-drain accumulated velocities, cursor positions, selected virtual cell, release callbacks and actual framebuffer dimensions. A render-stage velocity observation normally sees zero because Minecraft has already drained it; the pre-turn observation establishes whether physical movement arrived.

A separate observation remains useful if focus is active but the mouse is ungrabbed: terminal cancellation of `MouseButton.Pre` occurs before vanilla's first-click `grabMouse` path. This increment does not change that path without live evidence. The original relative-input correction and this possible focus-reacquisition issue must not be conflated.
