# 0.81.1 HUD / optional 452 material test branch

This branch starts at the authentic frozen .81 commit `939f3f5bd1125669ae806a4d2b929323aae58614`. It backports only recovery-HUD presentation, associated layout checks, and the optional 452 soft-alpha material. It does not include .82 / NEW243 registration, motion, payment or observation changes.

## Installation and save boundary

- Minecraft 1.21.1, NeoForge 21.1.252 and Java 21, matching the tested development environment. Other mod combinations are not certified.
- Back up the complete world and configuration first. In an isolated singleplayer installation, replace the existing AcademyCraft highpower JAR with the one matching this release's SHA256. Never keep two AcademyCraft versions in `mods` together.
- Use a copy of a .81 world or a new world. Do not downgrade a .82 / 243 candidate world into this version: unknown learned-skill bindings and debt fields have not been certified for downgrade.
- This remains a test branch until its actual acceptance results are reviewed. .81 remains the recommended fallback; neither .82 nor the v107 archive is overwritten.

## Visible changes

- Compact basic recovery HUD; GUI scale 2 uses native integer-size text and measured wrapping. GUI scale 3 retains the compact layout. Exact ALT values retain their earlier semantics and small-font limitations.
- `/aca_vfx2 vector_circle_soft on` enables the optional 452 material for this client session. `off` restores its prior detailed material; `status` reports readiness. Restart restores OFF.
- The material has a thinner core and low-opacity shoulder. Actual preview improvement was limited; this is not final cinematic art. Missing material falls back to the existing simple rendering. Defense angles, quota, contact, damage, payment and timing are unchanged.

## Evidence policy

The release report must identify newly run checks separately from reused frozen-.81 evidence. Historical failed checks remain recorded; no all-green claim. The 53 implemented or partially implemented .81 entries are coverage, not overall completion. Unimplemented and partial features remain project requirements; no two-day hypothetical deadline removes them.
