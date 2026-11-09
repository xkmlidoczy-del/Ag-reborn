# Cloud client runtime environment

Verified on 2026-10-01 UTC on the dot cloud computer only. No user computer was read or operated. These checks establish environment capability; actual client results are recorded separately in `client-runtime-smoke.md` and do not establish audiovisual parity.

## Observed capabilities

- The supported CUA native desktop is available: Xfce, X.Org 21.1.16, `DISPLAY=:0`, current desktop size 1364 × 1024
- The project workspace is visible both to normal command execution and to a terminal launched on that cloud desktop
- A ctypes/libGL diagnostic created and activated an actual OpenGL 3.2 core context, then queried the driver: GLX 1.4; Mesa llvmpipe (LLVM 19.1.7, 256 bits); OpenGL 4.5 Core Profile Mesa 25.0.7-2+deb13u1; GLSL 4.50
- Rendering is software rendering. No `/dev/dri` GPU device is available; frame rate remains untested
- OpenAL Soft's default-device probe failed (`alcOpenDevice` returned false, error 40964). There is no `/dev/snd` device. Setting `ALSOFT_DRIVERS=null` for an isolated probe allowed device creation and clean closure. This is a silent startup fallback, **not** audible sound verification
- No packages were installed, no security/display settings were changed, and no Minecraft process was started during these probes

Sanitized probe output is retained in `runtime-evidence/cloud-environment-probe.log`.

## Reliable launch and visual inspection route

The normal command environment has no `DISPLAY` and does not expose the cloud X socket. Explicit `DISPLAY=:0` failed with both normal and escalated command execution. This does not make the supported native cloud desktop unavailable.

1. Populate the toolchain, game libraries, and assets with `scripts/gradle-cloud.sh prepareClientRun --console=plain` from normal command execution
2. Wait for the authorized native server-test outcome and client-launch readiness
3. Launch a cloud terminal with the supported CUA app inventory. Execute the cached project's client launcher there, using its inherited desktop display environment, for example `bash scripts/gradle-cloud.sh runClient --offline --console=plain` from the project root
4. Preserve the Gradle/client log in the project runtime-evidence directory. Use `ALSOFT_DRIVERS=null` only if a silent fallback is needed, and explicitly record that limitation
5. Discover the actual Minecraft window using `cua.listWindows()`, bind its observed window ID with `cua.getApp({windowId})`, then inspect screenshots and use the supported window-bound CUA input methods
6. The client uses the isolated `run-client` directory. Minecraft's F2 action saves game-native screenshot evidence under `run-client/screenshots`; test worlds belong under `run-client/saves`. Verify the resulting file and inspect its image bytes before reporting results

The native terminal's `typeText`/paste path currently reports `window does not expose a Paste action`; individual `pressKey` calls work, including Return. A short command to a shared launcher script is therefore preferable to typing a long launch command. For a non-accessible game window, use fresh screenshots and observed window-relative coordinates rather than inventing accessibility controls.

Client startup, actual mod-resource rendering, repeated/interrupted input and integrated-world save/reopen received a bounded real smoke test after these probes; see `client-runtime-smoke.md`. Sound, multiplayer and broader restart/visual-fidelity behavior remain separate QA stages. A successful OpenGL diagnostic alone does not establish them.
