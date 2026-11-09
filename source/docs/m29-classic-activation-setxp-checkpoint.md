# M29 activation and direct XP checkpoint

Development checkpoint for Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21. Full faithful port acceptance remains open.

Server activation now checks category legality before equality, mutates the raw flag before synchronous Activate/Deactivate observers, and emits only on a real change. Actual authenticated toggle, category removal, death and death clone use that contract while retaining modern context, controller, refund and cooldown cleanup. Same-state calls are event-silent; no-category deactivation is legal.

The direct learned-only raw float XP setter now posts Changed even for equal writes, without Added, learning, CP refill, maxima recalc or level-progress mutation. The actual operator command uses it with its existing finite 0..1 validation. There is no untrusted wire XP setter. Trusted raw API values retain original behavior, including out-of-range and nonfinite values. Save encoding preserves those values; existing cold decode repairs them. This is not save-time sanitization.

Unchanged original AbilityData/CPData business code passes 6,167 state/event/raw-bit comparisons with zero differences. Actual cached official NeoForge priority bus and compressed native NBT pass 45 checks; 13 actual common classes cold-link with client namespaces denied. All 23 relevant standalone suites pass, including M28 progression and prior numeric/category/invalid-state regressions.

Owner full check/JAR and all 366 required actual native world tests pass. Four new native fixtures exercise real authenticated toggle, nested category removal, actual death/death clone, real bounded operator commands and silent cache drop/rebind. Independent six-command and three-configuration probes pass, followed by distinct native seed/verify JVM disk phases. These are declared fixtures, not complete unassisted survival.

Client early activation prediction, remote observer transport and original 25-tick/dynamic CP synchronization timing remain separate increments. This checkpoint does not invent client LevelChange or XP observer messages absent from original source. Actual M29 client continuation is pending. M27 earned GUI/root/block Arc remains independently accepted with its fixture limits; full audiovisual and natural-world progression acceptance remains open.
