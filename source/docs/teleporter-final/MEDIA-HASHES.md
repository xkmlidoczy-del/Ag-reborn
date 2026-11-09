# Original Teleporter final media hashes

AcademyCraft 1.0.7, commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`; LambdaLib dependency `1.2.3`. SHA-256 hashes are pinned from the archived original media, not from generated replacements. The classpath test validates all listed runtime files against the hash-pinned manifest.

MarkRender asks Resources.getEffectSeq("tp_mark", 7), so the original animation renders frames 0 through 6. Original frame 7 is retained and pinned for provenance.

`TeleporterFinalSourceFixtures` verifies the manifest's own SHA-256 before accepting any expected hashes and rejects unlisted, changed, or empty bytes. Its 44 original source files are bundled in test resources, so restored builds require no `.reference` directory. `TeleporterFinalOracleTest` compiles unchanged original LambdaLib MathUtils and Motion3D method bodies, exact original Flashing destination and tick bodies, and mechanically adapted source scalar expressions into an isolated temporary oracle. The small Minecraft 1.7 host supplies the original lookup-table trigonometry and Vec3 rotation conventions; it never starts Minecraft.

Cached official JDK 21 verification passed 82,077 oracle checks, 95 focused boundary/source-contract/mutation checks, 644 genuine learning/implemented-registry checks, 1,041 visual source/classfile/timeline/input/audio-contract checks, and dedicated-server cold linkage of 37 common and native-fixture classes. The arithmetic checks cover every float experience sample from 0 to 1 in increments of .001, capped/minimum Location CP arithmetic, strict updated-exp and post-move-distance behavior, 12,000 original Flash direction and Shift fallback comparisons, 12,000 original strict line-box comparisons, all Flash block faces with clear/blocked head checks, and six fail-closed provenance mutations. Source contracts pin saved-location retention across player clone/death through original LambdaLib EntityData/DataPart. Learning checks verify original developer constants and prerequisites, real implemented/preset gates, finite 147/176/240-tick learning, zero-mastery earned skills, aborts, depleted batteries, and completion-time prerequisite loss. Visual checks inspect actual compiled client methods with the official JDK javap tool without client initialization: non-recursive screen removal, independent panel/list clocks, failed-teleport cleanup, fresh-marker animation ages, exact Shift marker dimensions, original button hitboxes/tints, HUD/packet routing, and original actor-following audio with float coordinates and session cleanup. Cold linkage forbids Minecraft/NeoForge and port client namespaces while permitting clientless common helpers. This is deterministic common/source/media/classfile validation, not game or client runtime validation.

| Runtime resource | SHA-256 |
| --- | --- |
| `assets/academy/guis/loctele_new.xml` | `ecde1442fb18afbe0e097aaf8f0a1e8765b033fc6e1cf8a970c34c16bb2e51a6` |
| `assets/academy/textures/abilities/teleporter/skills/location_teleport.png` | `b973fc700be0c0ec75bc12e3b0b9be9a3fa83c5fb29009ef46a5e04ee67548ec` |
| `assets/academy/textures/abilities/teleporter/skills/shift_tp.png` | `2907d7fa43a0b6135b1e7819e07c1f8eb4f251255c36838b1419b6b0e76059c9` |
| `assets/academy/textures/abilities/teleporter/skills/flashing.png` | `9fe82ede1a808ac1a4c5c46b079ac1c4c26d6dedb339ec5b2098555406ca1769` |
| `assets/academy/textures/abilities/teleporter/icon.png` | `0c7fb7cb9c2c94bad7f73bbf3d0a7ef2ead099ed90ac956f4665f955cddcce49` |
| `assets/academy/textures/abilities/teleporter/icon_overlay.png` | `94af1e7950fbd30ce461d43c2b24b4dce38ea1121c02d1a2a5848d4129f32369` |
| `assets/academy/textures/guis/icons/icon_location_on.png` | `beddcb4fce5a5d6d3d64d4f3c055014d66f5c2cf19ec67317ef7215fe0b58894` |
| `assets/academy/textures/guis/icons/icon_clear.png` | `3e76e988a627734821573394cff85b302cc5b8184e135f77cd8c84269244fd79` |
| `assets/academy/textures/guis/check.png` | `d699158bd8b79eb3f252e8ad690234dcbcca1e6caee2e145e8d40870d5a11e4b` |
| `assets/academy/textures/guis/mark/mark_idle.png` | `4a37d0c00875ad0fa80e79cef6418f247084d1fd1a622c32f8f48d2185a39150` |
| `assets/academy/textures/guis/mark/mark_ball.png` | `a44f2457ef4ff955adabd876b963848ce0c11566d32ff5a7398db33753b08154` |
| `assets/academy/textures/guis/mark/mark_ball_highlighted.png` | `7c71b597b10e4f243457bc1845670829f0dd9e1d0371146d595ef57ad772311d` |
| `assets/academy/textures/effects/tp_particle.png` | `891dee2e1a087dcd917f3e224e1c5723fd949b71f7d65a47fd31709924b3bd00` |
| `assets/academy/sounds/tp/tp.ogg` | `abd492c26232106683c59e5039418c0aeb2b41269eacb26dcdde8e0762cfa2fa` |
| `assets/academy/sounds/tp/tp_shift.ogg` | `ec3e4fd43368932b7f84a097bd9d96735346449a8a36bbfd79ab2e50abeff8d8` |
| `assets/academy/sounds/tp/tp_flashing.ogg` | `bdfc3eb52dffb2ec0e6ecae26eb615b4edfc1fd19c22e41e7b628b222f64e205` |
| `assets/academy/textures/abilities/teleporter/flashing/a.png` | `00fe7b49c4ee45e7a299deed18279f6a9808ca1d25bec148fbcc3db2666f0734` |
| `assets/academy/textures/abilities/teleporter/flashing/d.png` | `775c5c2b97535b54430ac8af51300c2018a0d17b96ade6b4e1770e75306b5b23` |
| `assets/academy/textures/abilities/teleporter/flashing/w.png` | `e054c18ddbcc652f3e749c64c31d55724b9f63c9aa9e592fc5951883d7c4c069` |
| `assets/academy/textures/abilities/teleporter/flashing/s.png` | `246ae21071f10ca98f4c881343b3234f48599e4baed91d921c99261102b85e7b` |
| `assets/academy/textures/effects/tp_mark/0.png` | `952cca68c146cdde4f69b86be92ef0c0b0d79857f93aedb1a2225dba52a4b3ff` |
| `assets/academy/textures/effects/tp_mark/1.png` | `edf556933b5a773699938386caa07c3cdb23cd9c7ca1ee7db4cdeb5aaa361b60` |
| `assets/academy/textures/effects/tp_mark/2.png` | `72c6836532204fe659b9e3951c2efa6b60c4584f6b875f7b78e89e455d24c68d` |
| `assets/academy/textures/effects/tp_mark/3.png` | `1e99c3a45293bf26b293fa9b465e770871cbcebf24b600e55f7695cbc999bd9b` |
| `assets/academy/textures/effects/tp_mark/4.png` | `9e29f5c1002e46c930fe69ba4455d7343f90a184f595917863fd9ea2225a0bde` |
| `assets/academy/textures/effects/tp_mark/5.png` | `72f1adc9482023aacbf7e522d56d28358332b28474082a9510f2084ca1bf9f75` |
| `assets/academy/textures/effects/tp_mark/6.png` | `561d2d25207ef04d3b4d802f29064067733ba4eda0d3c0ac0c28f520b43baaf4` |
| `assets/academy/textures/effects/tp_mark/7.png` | `c3ad174eb4eae501cf89d799ac98cdf794891442a52e39a434346dcfd1851d11` |
