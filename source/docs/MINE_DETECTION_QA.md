# Mine Detection next native QA

These are proposed parent-owned checks, not tests executed by the isolated implementation worker. Keep existing worlds backed up and use a disposable development client/fixture when possible.

1. Run aggregate check and `runGameTestServer` filtered to `academy_mine_detect`. Five native tests must pass; compile-only evidence does not replace this
2. In Electromaster level3, train Magnetic Manipulation to1, learn Mine Detection using the real developer, then bind it explicitly in a preset. Ensure active ability toggle and slot dispatch work, and a direct raw skill-name wire request remains rejected
3. Place nearby coal/iron/diamond ores behind opaque stone, with an iron block, sculk and plain stone controls. At mastery0 verify blue original mineview faces, Blindness100ticks, sound volume.5following the player, CP1500/overload200 and post-award896tick cooldown
4. Verify precise Float mastery.5at level4 remains novice even though that cast awards.008and crosses the threshold. Next representable Float above.5 at level4 enables gray/cyan/green harvest palette. The same mastery at level3 stays novice
5. Check actual camera orientation and first-/third-person views, fog-free through-wall rendering under blindness, near/far alpha and width0.9inset.05. The original palette's red fifth entry should remain unreachable in normal advanced harvest captures
6. Move exactly the refresh threshold and just beyond it, then return. Verify strict comparison and duplicate accumulation. Mine a previously captured ore; its old marker remains until handler expiry. Large ore fields select first1000by X/Y/Z traversal, not nearest1000
7. Change dimension, disconnect/rejoin and recreate player while an effect is active; queued effects and sound must not cross sessions. Check local-only effect/sound and absence of ore packets to observers
8. Check Fast/Fancy/Fabulous and weather/translucency overlap, including screens, pause/unpause and normal expiry. Capture native screenshots/video and compare with original1.7.10 output before claiming visual/audible parity
9. Verify the modern achievement JSON loads, successful cast grants its cast criterion once, and original localized title/description display. The native iron-ore icon/rootless layout is an explicit adaptation
10. When testing modded ores, verify standard semantic ore-family block/item tags, loaded-chunk/build-height boundaries and configured registry/#tag harvest overrides. Do not identify stone_ore_replaceables as ores

The older `em-native-fixture-diagnosis.md` reflects the previous milestone when Mine Detection was still unported. This slice changes only those preset rejection fixtures to the genuinely unported learned level5 Electromaster `thunder_clap`; it does not relax validation.
