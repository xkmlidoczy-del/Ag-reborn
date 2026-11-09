# Mapped skill consumption ingresses

All35 active skill IDs in production and the three frozen final lanes were inspected against canonical sources before changing callers. Port APIs use CP, overload order. Normal=N; force=F; captured=C; current mastery=D. No cost formula, mastery capture location, raw precheck, free final tick or source swapped-argument quirk was rewritten.

| Skill | Source CP/overload contract | Capture/quirk retained |
|---|---|---|
| arc_gen | N30→70 /18→11 | Immediate context, CP captured; overload current |
| railgun | N200→450 /180→120 | Current at firing; ammo removed before debit |
| charging | N startup0 /65→48; tick3→7 /0 | Captured; block transfer/EXP precede debit, item debit precedes transfer |
| body_intensify | N startup0 /200→120; tick20→15 /0 | Captured CP; only ticks≤40 pay |
| thunder_clap | N startup0 /390→252; tick18→25 /0 | Captured; only ticks≤40 pay |
| mag_movement | N startup0 /60→30; tick15→8 /0 | Captured; startup debit before target check |
| mag_manip | N140→270 /35→20 | Captured; strict distance²<25 |
| mine_detect | N1500→1000 /200→180 | Current in instant consumption |
| thunder_bolt | N int(280→420) /50→27 | Captured; integer CP conversion retained |
| electron_bomb | N35→80 /16→13 | Current at immediate consumption |
| scatter_bomb | N startup0 /80→60; tick3→6 /0 | Captured; balls created before failed final debit |
| light_shield | N startup0 /110→60; tick9→4 /0; contact50→30 /5→3 | Captured; incoming absorption intentionally reverses CP/O |
| meltdowner | N startup0 /200→170; tick10→15 /0 | Captured |
| mine_ray_basic | N startup0 /200→150; tick12→7 /0 | Captured; mining body still runs after ordinary CP failure |
| mine_ray_expert | N startup0 /300→200; tick25→15 /0 | Captured, distinct tier |
| mine_ray_luck | N startup0 /350→300; tick50→35 /0 | Captured, distinct tier |
| ray_barrage | N450→380 /300→140 | Source attack still executes after failed initial consume |
| jet_engine | N60→50 /170→140 | Captured source argument swap; raw affordability still170→140 |
| electron_missile | N startup0 /200; tick12→5 /0; attack60→25 /9→4 | Captured |
| threatening_teleport | N35→100 /18→10 | Captured debit; release resolves current item |
| penetrate_teleport | F distance×(14→9) /80→50 | Captured debit; current-mastery/raw-CP destination |
| mark_teleport | F distance×(12→4) /40→20 | Captured debit; current-mastery/raw-CP destination |
| flesh_ripping | F130→270 /60→50 | Captured; raw tick affordability preserved |
| vec_accel | N120→80 /30→15 | CP captured, overload current |
| vec_deviation | N startup0 /80→50; first tick13→5 /0; second5→2.5 /.5→.2 | First captured, second current; second return intentionally ignored |
| vec_deviation entity | F15→12 /0 | Captured fixed cost, difficulty affects EXP only |
| vec_deviation hurt | N min(rawCP,15→12) /0 | Current; post-award dynamic reduction retained |
| dir_shock | N50→100 /18→12 | Current cost, separately captured damage |
| ground_shock | N80→150 /15→10 | Captured, grounded eligibility before debit |
| location_teleport | F source distance/dimension CP formula /240 | Current; raw affordability before force |
| shift_tp | N260→320 /40→30 | Captured; placement/protection precede debit |
| flashing | N startup80→60 /250→180; hop13→6 /0 | Captured; raw simulated affordability unchanged |
| vec_reflection | N startup0 /350→250; entity difficulty×(300→160) /0; tick15→11 /0 | Startup captured; entity/tick current |
| vec_reflection hurt | F damage×(20→15) /0 | Current; pre-award reflection ratio and free passby branch retained |
| plasma_cannon | N startup0 /500→400; charging18→25 /0 | Charge threshold captured, CP current; ready hold/travel free |
| dir_blast | N160→200 /50→30 | CP captured, overload current |
| blood_retro | N280→350 /55→40 | Current cost, separately captured damage |
| storm_wing | N40→25 /10→7 | Captured; EXP precedes active debit even on failure; transition free |

Exact original call sites are frozen under `src/test/resources/classic-oracles/shared-skill-consumption/academycraft/src/main`. Shared source links are AbilityContext.java93–105/145–150, CPData.java291–329/337–400 and Skill.java199–214. Source scala classes use the same skill names shown above (ShiftTeleport's configured ID is shift_tp; DirectedBlastwave is dir_blast; BloodRetrograde is blood_retro). `ingress-manifest.json` maps each changed normal/force Java caller to its production target and prerequisite lane. `admission-guards.json` maps constructor/native start admission edits.
